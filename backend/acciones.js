// Reglas de negocio: activar un arriendo, cobrar el mes con Nequi y registrar los pagos. Reciben un
// contexto { db, wompi, config } y el reloj, para poder probarlas contra el emulador de Firebase.
//
// Modelo en la base de datos (las claves de abono llevan prefijo "a" porque la base convierte en
// arreglo un mapa con claves 1, 2, 3):
//   pagos/{propiedadId}/{cuotaId}: numero, periodo, vence, monto, inquilinoId, pagado, estado
//     (pendiente | parcial | pagado), modalidad (unico; "abonos" solo en meses viejos) y
//     abonos/a1…: cada cobro del mes. numero, tipo (unico | saldo; "abono" en meses viejos), monto,
//     estado (pendiente | en_proceso | aprobado | rechazado) y, según el estado, intento, pagadoEn,
//     medio, comprobante…
// Desde el 20/10/2026 ya no se paga en abonos: cada mes se cobra completo. Un mes viejo que quedó a
// medias en abonos se termina con un cobro de "saldo" por lo que falta.

const crypto = require('node:crypto');

const { ErrorHttp } = require('./errores');
const { firmaIntegridad, nombreDelMedio, normalizarCelular, MONEDA } = require('./wompi');
const { fechasDeCuotas, periodoDe, comprobanteDe, estadoPagoDe } = require('./cuotas');

const DURACION_POR_DEFECTO = 12;
const DURACION_MAXIMA = 36;
/**
 * Un cobro de Nequi no se puede cancelar por API: queda pendiente hasta que Nequi dé un resultado.
 * Pasado este tiempo sin resultado se permite un nuevo intento (Wompi no documenta cuánto dura la
 * notificación push; ajustar cuando se sepa).
 */
const EN_PROCESO_MAX_MS = 30 * 60 * 1000;
/** "saldo" se acepta por las versiones de la app que aún lo envían; el servidor decide igual qué cobrar. */
const MODALIDADES = ['unico', 'saldo'];
const ESTADOS_FALLIDOS = ['DECLINED', 'ERROR', 'VOIDED'];
/** Identificadores que se usan como clave en la base: sin '/', '.', '#', '$', '[' ni ']'. */
const CLAVE_VALIDA = /^[A-Za-z0-9_-]{1,128}$/;

function clave(valor, campo) {
  if (typeof valor !== 'string' || !CLAVE_VALIDA.test(valor)) {
    throw new ErrorHttp(400, `${campo} no es válido.`);
  }
  return valor;
}

function claveDeAbono(numero) {
  if (!Number.isInteger(numero) || numero < 1 || numero > 99) {
    throw new ErrorHttp(400, 'El número de abono no es válido.');
  }
  return `a${numero}`;
}

async function leer(db, ruta) {
  return (await db.ref(ruta).get()).val();
}

function sumaAprobados(abonos) {
  return Object.values(abonos || {})
    .filter((abono) => abono.estado === 'aprobado')
    .reduce((suma, abono) => suma + abono.monto, 0);
}

/** Abono sin los datos de un intento anterior. */
function abonoEn(abono, estado, extra = {}) {
  return { numero: abono.numero, tipo: abono.tipo, monto: abono.monto, estado, ...extra };
}

// ---------- Activar un arriendo ----------

/**
 * El propietario acepta una solicitud: la propiedad queda con inquilino, las demás solicitudes
 * pendientes de esa propiedad se rechazan y se crea una cuota por cada mes de contrato.
 */
async function aceptarSolicitud({ db }, uid, solicitudId, ahora = Date.now()) {
  clave(solicitudId, 'solicitudId');
  const solicitud = await leer(db, `solicitudes_arriendo/${solicitudId}`);
  if (!solicitud) {
    throw new ErrorHttp(404, 'La solicitud no existe.');
  }
  if (solicitud.propietarioId !== uid) {
    throw new ErrorHttp(403, 'Solo el propietario puede aceptar la solicitud.');
  }
  if (solicitud.estado !== 'pendiente') {
    throw new ErrorHttp(409, 'La solicitud ya fue respondida.');
  }

  const propiedadId = clave(solicitud.propiedadId, 'propiedadId');
  const propiedad = await leer(db, `propiedades/${propiedadId}`);
  if (!propiedad || propiedad.propietarioId !== uid) {
    throw new ErrorHttp(404, 'La propiedad no existe.');
  }
  if (propiedad.inquilinoId || (propiedad.inquilinoNombre || '').trim()) {
    throw new ErrorHttp(409, 'La propiedad ya tiene un inquilino.');
  }
  if (!Number.isInteger(propiedad.precio) || propiedad.precio <= 0) {
    throw new ErrorHttp(409, 'La propiedad no tiene un precio válido.');
  }

  const meses = Math.min(Math.max(Number(propiedad.duracionMeses) || DURACION_POR_DEFECTO, 1), DURACION_MAXIMA);
  const cambios = {
    [`propiedades/${propiedadId}/inquilinoId`]: solicitud.inquilinoId,
    [`propiedades/${propiedadId}/inquilinoNombre`]: solicitud.inquilinoNombre,
    [`propiedades/${propiedadId}/inquilinoDesde`]: ahora,
    [`solicitudes_arriendo/${solicitudId}/estado`]: 'aceptada',
  };

  fechasDeCuotas(ahora, meses, propiedad.diaDePago).forEach((vence, i) => {
    const cuotaId = db.ref(`pagos/${propiedadId}`).push().key;
    cambios[`pagos/${propiedadId}/${cuotaId}`] = {
      numero: i + 1,
      periodo: periodoDe(vence),
      monto: propiedad.precio,
      vence,
      inquilinoId: solicitud.inquilinoId,
      pagado: 0,
      estado: 'pendiente',
    };
  });

  const delPropietario = await db.ref('solicitudes_arriendo').orderByChild('propietarioId').equalTo(uid).get();
  delPropietario.forEach((hijo) => {
    const otra = hijo.val();
    if (hijo.key !== solicitudId && otra.propiedadId === propiedadId && otra.estado === 'pendiente') {
      cambios[`solicitudes_arriendo/${hijo.key}/estado`] = 'rechazada';
    }
  });

  await db.ref().update(cambios);
  await recalcularEstadoPago(db, propiedadId, ahora);
  return { propiedadId, cuotas: meses };
}

// ---------- Cobrar con Nequi ----------

/** Tokens y enlaces de los contratos de Wompi que la app muestra con su casilla de aceptación. */
async function obtenerAceptacion({ wompi }) {
  return wompi.aceptacion();
}

/**
 * Reserva el abono que se va a cobrar y lo deja "en_proceso". Es una transacción sobre la cuota: dos
 * pedidos a la vez no pueden reservar el mismo abono ni dejar dos cobros activos.
 */
async function reservarAbono(db, p) {
  const ref = db.ref(`pagos/${p.propiedadId}/${p.cuotaId}`);
  let rechazo = null;
  let elegido = null;

  const { committed } = await ref.transaction((cuota) => {
    rechazo = null;
    elegido = null;
    // El primer intento corre sin caché local (null); el SDK lo repite con el valor real.
    if (cuota === null) {
      return null;
    }
    const falla = (estado, mensaje) => {
      rechazo = new ErrorHttp(estado, mensaje);
      return undefined;
    };
    if (cuota.inquilinoId !== p.uid) {
      return falla(403, 'Este pago no es tuyo.');
    }
    if (cuota.estado === 'pagado') {
      return falla(409, 'Esta cuota ya está pagada.');
    }
    let abonos = { ...(cuota.abonos || {}) };
    const hayCobroVigente = Object.values(abonos).some((a) => a.estado === 'en_proceso'
      && p.ahora - ((a.intento && a.intento.iniciadoEn) || 0) < EN_PROCESO_MAX_MS);
    if (hayCobroVigente) {
      return falla(409, 'Ya hay un cobro en proceso para esta cuota. Revisa Nequi o espera el resultado.');
    }

    // Siempre se cobra lo que falta del mes: completo si no hay nada pagado; el saldo si un mes viejo
    // quedó a medias en abonos. Los cobros sin aprobar se reemplazan por uno solo.
    const pagado = sumaAprobados(abonos);
    // Número nuevo para el saldo: no reutiliza el de un cobro anterior (sus eventos viejos no lo pisan).
    const siguiente = Math.max(0, ...Object.values(abonos).map((a) => a.numero)) + 1;
    for (const [k, a] of Object.entries(abonos)) {
      if (a.estado !== 'aprobado') {
        delete abonos[k];
      }
    }
    let modalidad = cuota.modalidad || 'unico';
    let claveElegida;
    if (pagado > 0) {
      claveElegida = `a${siguiente}`;
      abonos[claveElegida] = { numero: siguiente, tipo: 'saldo', monto: cuota.monto - pagado, estado: 'pendiente' };
    } else {
      modalidad = 'unico';
      claveElegida = 'a1';
      abonos = { a1: { numero: 1, tipo: 'unico', monto: cuota.monto, estado: 'pendiente' } };
    }

    abonos[claveElegida] = abonoEn(abonos[claveElegida], 'en_proceso', {
      intento: { referencia: p.referencia, celularFin: p.celularFin, iniciadoEn: p.ahora },
    });
    elegido = { clave: claveElegida, abono: abonos[claveElegida] };
    return { ...cuota, modalidad, abonos };
  });

  if (!committed) {
    throw rechazo || new ErrorHttp(409, 'No se pudo reservar el cobro. Intenta de nuevo.');
  }
  if (!elegido) {
    throw new ErrorHttp(404, 'El pago no existe.');
  }
  return elegido;
}

async function liberarAbono(db, propiedadId, cuotaId, claveAbono, referencia) {
  await db.ref(`pagos/${propiedadId}/${cuotaId}/abonos/${claveAbono}`).transaction((a) => {
    if (a === null) {
      return null;
    }
    if (a.estado !== 'en_proceso' || !a.intento || a.intento.referencia !== referencia) {
      return undefined;
    }
    return abonoEn(a, 'pendiente');
  });
}

/**
 * El inquilino paga el mes con Nequi. El monto sale de la base, nunca de la app. Wompi manda la
 * notificación push al celular; el resultado llega por webhook (o al consultar el estado).
 */
async function iniciarCobroNequi(ctx, usuario, body, ahora = Date.now()) {
  const { db, wompi, config } = ctx;
  const propiedadId = clave(body.propiedadId, 'propiedadId');
  const cuotaId = clave(body.cuotaId, 'cuotaId');
  const celular = normalizarCelular(body.celular);
  if (!celular) {
    throw new ErrorHttp(400, 'Escribe un celular Nequi de 10 dígitos que empiece por 3.');
  }
  if (typeof body.acceptanceToken !== 'string' || !body.acceptanceToken
    || typeof body.personalAuthToken !== 'string' || !body.personalAuthToken) {
    throw new ErrorHttp(400, 'Debes aceptar los términos y la autorización de datos de Wompi.');
  }
  if (body.modalidad === 'abonos') {
    throw new ErrorHttp(400, 'Ya no se puede pagar en abonos: el arriendo se paga completo.');
  }
  if (body.modalidad != null && !MODALIDADES.includes(body.modalidad)) {
    throw new ErrorHttp(400, 'La forma de pago no es válida.');
  }
  if (!usuario.correo) {
    throw new ErrorHttp(400, 'Tu cuenta no tiene un correo registrado.');
  }

  const propiedad = await leer(db, `propiedades/${propiedadId}`);
  if (!propiedad || propiedad.inquilinoId !== usuario.uid) {
    throw new ErrorHttp(403, 'Este pago no es tuyo.');
  }
  // Si quedó un cobro viejo en proceso, se consulta a Wompi antes de rechazar el nuevo intento.
  await sincronizarCuota(ctx, propiedadId, cuotaId, ahora);

  const referencia = `pago-${cuotaId}-${crypto.randomBytes(4).toString('hex')}`;
  const { clave: claveAbono, abono } = await reservarAbono(db, {
    propiedadId,
    cuotaId,
    uid: usuario.uid,
    referencia,
    celularFin: celular.slice(-4),
    ahora,
  });

  const montoCentavos = abono.monto * 100;
  await db.ref(`referencias_pago/${referencia}`).set({
    propiedadId, cuotaId, abono: claveAbono, inquilinoId: usuario.uid, montoCentavos, creadaEn: ahora,
  });

  let transaccion;
  try {
    transaccion = await wompi.crearTransaccionNequi({
      acceptanceToken: body.acceptanceToken,
      personalAuthToken: body.personalAuthToken,
      montoCentavos,
      correo: usuario.correo,
      referencia,
      firma: firmaIntegridad({ referencia, montoCentavos, secreto: config.secretoIntegridad }),
      celular,
    });
  } catch (error) {
    await liberarAbono(db, propiedadId, cuotaId, claveAbono, referencia);
    await db.ref(`referencias_pago/${referencia}`).remove();
    throw error;
  }

  await db.ref(`pagos/${propiedadId}/${cuotaId}/abonos/${claveAbono}/intento/transaccionId`).set(transaccion.id);
  if (transaccion.status && transaccion.status !== 'PENDING') {
    await aplicarTransaccion(ctx, { ...transaccion, reference: referencia }, ahora);
  }
  return {
    abono: abono.numero,
    monto: abono.monto,
    estado: 'en_proceso',
    celularFin: celular.slice(-4),
    referencia,
    transaccionId: transaccion.id,
  };
}

// ---------- Resultado del cobro ----------

/**
 * Transacción de Wompi (de un evento firmado o de una consulta con la llave privada). Solo una
 * APPROVED, con el monto del cobro que creamos, marca el abono como pagado. Devuelve qué hizo.
 */
async function aplicarTransaccion({ db }, transaccion, ahora = Date.now()) {
  const referencia = transaccion && transaccion.reference;
  if (typeof referencia !== 'string' || !CLAVE_VALIDA.test(referencia)) {
    return 'sin_referencia';
  }
  const intento = await leer(db, `referencias_pago/${referencia}`);
  if (!intento) {
    return 'referencia_desconocida';
  }
  if (transaccion.amount_in_cents !== intento.montoCentavos || transaccion.currency !== MONEDA) {
    return 'monto_distinto';
  }
  if (transaccion.status === 'APPROVED') {
    return aprobarAbono(db, intento, transaccion, ahora);
  }
  if (ESTADOS_FALLIDOS.includes(transaccion.status)) {
    return rechazarAbono(db, intento, referencia, transaccion, ahora);
  }
  return 'sin_aprobar';
}

async function aprobarAbono(db, intento, transaccion, ahora) {
  const { propiedadId, cuotaId, abono: claveAbono } = intento;
  let resultado = 'abono_desconocido';
  const { committed } = await db.ref(`pagos/${propiedadId}/${cuotaId}`).transaction((cuota) => {
    resultado = 'abono_desconocido';
    if (cuota === null) {
      return null;
    }
    const abono = cuota.abonos && cuota.abonos[claveAbono];
    if (!abono) {
      return undefined;
    }
    if (abono.estado === 'aprobado') {
      resultado = 'ya_pagado';
      return undefined;
    }
    const abonos = {
      ...cuota.abonos,
      [claveAbono]: abonoEn(abono, 'aprobado', {
        pagadoEn: ahora,
        medio: nombreDelMedio(transaccion.payment_method_type),
        wompiTransaccionId: transaccion.id,
        comprobante: comprobanteDe({
          periodo: cuota.periodo || periodoDe(cuota.vence),
          tipo: abono.tipo,
          numero: abono.numero,
          transaccionId: transaccion.id,
        }),
      }),
    };
    const pagado = sumaAprobados(abonos);
    resultado = 'pagado';
    return { ...cuota, abonos, pagado, estado: pagado >= cuota.monto ? 'pagado' : 'parcial' };
  });
  if (committed) {
    await recalcularEstadoPago(db, propiedadId, ahora);
  }
  return resultado;
}

async function rechazarAbono(db, intento, referencia, transaccion, ahora) {
  const { propiedadId, cuotaId, abono: claveAbono } = intento;
  const { committed } = await db.ref(`pagos/${propiedadId}/${cuotaId}/abonos/${claveAbono}`).transaction((a) => {
    if (a === null) {
      return null;
    }
    // Solo el intento vigente: un evento viejo no pisa un abono ya aprobado ni uno reintentado.
    if (a.estado !== 'en_proceso' || !a.intento || a.intento.referencia !== referencia) {
      return undefined;
    }
    const extra = { rechazadoEn: ahora };
    if (transaccion.status_message) {
      extra.motivo = String(transaccion.status_message).slice(0, 200);
    }
    return abonoEn(a, 'rechazado', extra);
  });
  return committed ? 'rechazado' : 'sin_cambios';
}

/** Consulta a Wompi los cobros en proceso de una cuota y aplica su resultado. */
async function sincronizarCuota(ctx, propiedadId, cuotaId, ahora) {
  const cuota = await leer(ctx.db, `pagos/${propiedadId}/${cuotaId}`);
  for (const abono of Object.values((cuota && cuota.abonos) || {})) {
    const id = abono.estado === 'en_proceso' && abono.intento && abono.intento.transaccionId;
    if (!id) {
      continue;
    }
    try {
      await aplicarTransaccion(ctx, await ctx.wompi.consultarTransaccion(id), ahora);
    } catch (e) {
      console.warn('No se pudo consultar la transacción', id, e.message);
    }
  }
}

/** "Consultar estado": el resultado sale de la base, tras pedirle a Wompi lo que haya pendiente. */
async function consultarEstadoAbono(ctx, uid, propiedadId, cuotaId, numero, ahora = Date.now()) {
  clave(propiedadId, 'propiedadId');
  clave(cuotaId, 'cuotaId');
  const claveAbono = claveDeAbono(numero);
  const antes = await leer(ctx.db, `pagos/${propiedadId}/${cuotaId}`);
  if (!antes || antes.inquilinoId !== uid) {
    throw new ErrorHttp(404, 'El pago no existe.');
  }
  await sincronizarCuota(ctx, propiedadId, cuotaId, ahora);
  const cuota = await leer(ctx.db, `pagos/${propiedadId}/${cuotaId}`);
  const abono = cuota.abonos && cuota.abonos[claveAbono];
  if (!abono) {
    throw new ErrorHttp(404, 'El abono no existe.');
  }
  return {
    abono: abono.numero,
    estado: abono.estado,
    cuotaEstado: cuota.estado,
    pagado: cuota.pagado || 0,
    saldo: cuota.monto - (cuota.pagado || 0),
    comprobante: abono.comprobante || null,
  };
}

// ---------- Estado de pago de cada propiedad ----------

async function recalcularEstadoPago(db, propiedadId, ahora = Date.now()) {
  const pagos = (await leer(db, `pagos/${propiedadId}`)) || {};
  await db.ref(`propiedades/${propiedadId}/estadoPago`).set(estadoPagoDe(Object.values(pagos), ahora));
}

/** Una cuota que vence no avisa sola: se revisan todas las propiedades con inquilino. */
async function recalcularTodos({ db }, ahora = Date.now()) {
  const ocupadas = await db.ref('propiedades').orderByChild('inquilinoId').startAt('').get();
  const ids = [];
  ocupadas.forEach((hijo) => {
    ids.push(hijo.key);
  });
  await Promise.all(ids.map((id) => recalcularEstadoPago(db, id, ahora)));
  return ids.length;
}

module.exports = {
  ErrorHttp,
  aceptarSolicitud,
  obtenerAceptacion,
  iniciarCobroNequi,
  aplicarTransaccion,
  consultarEstadoAbono,
  recalcularEstadoPago,
  recalcularTodos,
};
