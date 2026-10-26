// Prueba de punta a punta contra los emuladores de Firebase (Auth + Realtime Database) y un Wompi
// simulado: nada de esto toca el proyecto real ni Wompi. Se salta sola si no están corriendo:
//
//   firebase emulators:exec --only auth,database --project demo-miarriendo "npm --prefix backend test"

const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const { initializeApp, deleteApp } = require('firebase-admin/app');
const { getDatabase } = require('firebase-admin/database');
const { getAuth } = require('firebase-admin/auth');

const { crearApp } = require('../servidor');
const { ErrorHttp } = require('../errores');
const { firmaIntegridad } = require('../wompi');

const EMULADORES = process.env.FIREBASE_DATABASE_EMULATOR_HOST && process.env.FIREBASE_AUTH_EMULATOR_HOST;
const sha256 = (texto) => crypto.createHash('sha256').update(texto).digest('hex');

const CONFIG = {
  secretoIntegridad: 'test_integrity_x',
  secretoEventos: 'test_events_x',
  secretoCron: 'cron_x',
};
const AHORA = Date.UTC(2026, 9, 11, 15, 0, 0);

test('flujo completo: aceptar → pago del mes con Nequi → webhook', { skip: !EMULADORES && 'sin emuladores de Firebase' }, async (t) => {
  const app = initializeApp({ projectId: 'demo-miarriendo', databaseURL: 'https://demo-miarriendo-default-rtdb.firebaseio.com' });
  const db = getDatabase(app);
  const auth = getAuth(app);

  // Wompi simulado: guarda lo que recibe y deja que la prueba decida el resultado de cada transacción.
  const llamadas = [];
  const transacciones = {};
  let siguiente = 1;
  let wompiCaido = false;
  const wompi = {
    async aceptacion() {
      return { acceptanceToken: 'tok-terminos', personalAuthToken: 'tok-datos', terminosUrl: 'https://wompi.test/terminos.pdf', datosUrl: 'https://wompi.test/datos.pdf' };
    },
    async crearTransaccionNequi(p) {
      if (wompiCaido) {
        throw new ErrorHttp(502, 'Wompi no pudo procesar la solicitud.');
      }
      llamadas.push(p);
      const id = `tx-${siguiente++}`;
      transacciones[id] = { id, reference: p.referencia, amount_in_cents: p.montoCentavos, currency: 'COP', status: 'PENDING', payment_method_type: 'NEQUI' };
      return { ...transacciones[id] };
    },
    async consultarTransaccion(id) {
      return { ...transacciones[id] };
    },
  };

  const servidor = crearApp({ db, auth, wompi, config: CONFIG, reloj: () => AHORA }).listen(0);
  const base = `http://127.0.0.1:${servidor.address().port}`;
  t.after(async () => {
    servidor.close();
    await deleteApp(app);
  });

  async function iniciarSesion(uid) {
    await auth.createUser({ uid, email: `${uid}@ejemplo.test` });
    const tokenCustom = await auth.createCustomToken(uid);
    const r = await fetch(`http://${process.env.FIREBASE_AUTH_EMULATOR_HOST}/identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key=falsa`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ token: tokenCustom, returnSecureToken: true }),
    });
    return (await r.json()).idToken;
  }

  async function pedir(ruta, token, cuerpo, metodo) {
    const r = await fetch(base + ruta, {
      method: metodo || (cuerpo === undefined ? 'GET' : 'POST'),
      headers: { 'content-type': 'application/json', ...(token ? { authorization: `Bearer ${token}` } : {}) },
      body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
    });
    return { estado: r.status, cuerpo: await r.json().catch(() => ({})) };
  }

  const tokenPropietario = await iniciarSesion('propietario');
  const tokenInquilino = await iniciarSesion('inquilino');
  const tokenOtro = await iniciarSesion('otro');

  await db.ref().update({
    'propiedades/casa1': { propietarioId: 'propietario', titulo: 'Casa', precio: 1200000, duracionMeses: 4, diaDePago: 5 },
    'solicitudes_arriendo/s1': { propiedadId: 'casa1', propietarioId: 'propietario', inquilinoId: 'inquilino', inquilinoNombre: 'Ana Pérez', estado: 'pendiente' },
    'solicitudes_arriendo/s2': { propiedadId: 'casa1', propietarioId: 'propietario', inquilinoId: 'otro', inquilinoNombre: 'Luis Gómez', estado: 'pendiente' },
  });

  // ---------- Activar el arriendo ----------

  await t.test('sin sesión no se puede aceptar, y solo el propietario acepta', async () => {
    assert.equal((await pedir('/api/solicitudes/s1/aceptar', null, {})).estado, 401);
    assert.equal((await pedir('/api/solicitudes/s1/aceptar', 'token-falso', {})).estado, 401);
    assert.equal((await pedir('/api/solicitudes/s1/aceptar', tokenInquilino, {})).estado, 403);
    assert.equal((await pedir('/api/solicitudes/no-existe/aceptar', tokenPropietario, {})).estado, 404);
  });

  await t.test('aceptar activa el arriendo y crea una cuota por mes con día de pago fijo', async () => {
    const r = await pedir('/api/solicitudes/s1/aceptar', tokenPropietario, {});
    assert.equal(r.estado, 200);
    assert.equal(r.cuerpo.cuotas, 4);

    const propiedad = (await db.ref('propiedades/casa1').get()).val();
    assert.equal(propiedad.inquilinoId, 'inquilino');
    assert.equal(propiedad.inquilinoDesde, AHORA);
    assert.equal(propiedad.estadoPago, 'al_dia', 'la primera cuota vence el 5 de noviembre, a 25 días');

    const cuotas = Object.values((await db.ref('pagos/casa1').get()).val()).sort((a, b) => a.numero - b.numero);
    assert.deepEqual(cuotas.map((c) => c.periodo), ['2026-11', '2026-12', '2027-01', '2027-02']);
    assert.ok(cuotas.every((c) => c.monto === 1200000 && c.estado === 'pendiente' && c.pagado === 0 && c.inquilinoId === 'inquilino'));
    assert.equal((await db.ref('solicitudes_arriendo/s2/estado').get()).val(), 'rechazada');
    assert.equal((await pedir('/api/solicitudes/s1/aceptar', tokenPropietario, {})).estado, 409);
  });

  const cuotasPorNumero = (await db.ref('pagos/casa1').get()).val();
  const idDe = (numero) => Object.keys(cuotasPorNumero).find((id) => cuotasPorNumero[id].numero === numero);
  const [c1, c2, c3, c4] = [1, 2, 3, 4].map(idDe);
  const leerCuota = async (id) => (await db.ref(`pagos/casa1/${id}`).get()).val();

  function cobro(cuotaId, extra = {}) {
    return { propiedadId: 'casa1', cuotaId, celular: '300 123 4567', acceptanceToken: 'tok-terminos', personalAuthToken: 'tok-datos', ...extra };
  }
  function evento(transaccion, secreto = CONFIG.secretoEventos) {
    const e = {
      event: 'transaction.updated',
      data: { transaction: { currency: 'COP', payment_method_type: 'NEQUI', ...transaccion } },
      timestamp: 1530291411,
      signature: { properties: ['transaction.id', 'transaction.status', 'transaction.amount_in_cents'] },
    };
    const tx = e.data.transaction;
    e.signature.checksum = sha256(`${tx.id}${tx.status}${tx.amount_in_cents}${e.timestamp}${secreto}`);
    return e;
  }
  /** Como lo haría Wompi: cambia el estado de la transacción y avisa por webhook. */
  async function resolver(respuesta, status, extra = {}) {
    transacciones[respuesta.cuerpo.transaccionId].status = status;
    return pedir('/webhooks/wompi', null, evento({ ...transacciones[respuesta.cuerpo.transaccionId], ...extra }));
  }

  // ---------- Contratos y validaciones ----------

  await t.test('los tokens de aceptación de Wompi solo se entregan con sesión', async () => {
    assert.equal((await pedir('/api/pagos/aceptacion', null)).estado, 401);
    const r = await pedir('/api/pagos/aceptacion', tokenInquilino);
    assert.equal(r.estado, 200);
    assert.equal(r.cuerpo.acceptanceToken, 'tok-terminos');
    assert.equal(r.cuerpo.datosUrl, 'https://wompi.test/datos.pdf');
  });

  await t.test('un cobro con datos incompletos, en abonos o de otra persona se rechaza antes de llamar a Wompi', async () => {
    const intentar = (cuerpo, token = tokenInquilino) => pedir('/api/pagos/nequi', token, cuerpo);
    assert.equal((await intentar(cobro(c1, { celular: '123' }))).estado, 400);
    assert.equal((await intentar(cobro(c1, { acceptanceToken: '' }))).estado, 400);
    assert.equal((await intentar(cobro(c1, { personalAuthToken: undefined }))).estado, 400);
    assert.equal((await intentar(cobro(c1, { modalidad: 'quincenal' }))).estado, 400);
    const abonos = await intentar(cobro(c1, { modalidad: 'abonos' }));
    assert.equal(abonos.estado, 400, 'ya no se paga en abonos');
    assert.match(abonos.cuerpo.error, /abonos/);
    assert.equal((await intentar(cobro('x/y'))).estado, 400);
    assert.equal((await intentar(cobro(c1), tokenOtro)).estado, 403);
    assert.equal((await intentar(cobro(c1), null)).estado, 401);
    assert.equal((await intentar(cobro('inexistente'))).estado, 404);
    assert.equal(llamadas.length, 0, 'ninguna de esas peticiones llegó a Wompi');
    assert.equal((await leerCuota(c1)).abonos, undefined, 'y la cuota sigue intacta');
  });

  // ---------- Cuota 1: el mes completo, con un rechazo de Nequi ----------

  let rechazado;
  await t.test('el mes se cobra completo por Nequi con monto, correo y firma correctos', async () => {
    rechazado = await pedir('/api/pagos/nequi', tokenInquilino, cobro(c1));
    assert.equal(rechazado.estado, 201);
    assert.equal(rechazado.cuerpo.abono, 1);
    assert.equal(rechazado.cuerpo.monto, 1200000);
    assert.equal(rechazado.cuerpo.celularFin, '4567');

    const p = llamadas[0];
    assert.equal(p.montoCentavos, 120000000);
    assert.equal(p.correo, 'inquilino@ejemplo.test');
    assert.equal(p.celular, '3001234567');
    assert.equal(p.acceptanceToken, 'tok-terminos');
    assert.equal(p.personalAuthToken, 'tok-datos');
    assert.equal(p.firma, firmaIntegridad({ referencia: p.referencia, montoCentavos: 120000000, secreto: CONFIG.secretoIntegridad }));

    const cuota = await leerCuota(c1);
    assert.equal(cuota.modalidad, 'unico');
    assert.deepEqual(Object.values(cuota.abonos).map((a) => [a.numero, a.tipo, a.monto, a.estado]), [[1, 'unico', 1200000, 'en_proceso']]);
    assert.equal(cuota.abonos.a1.intento.celularFin, '4567');
    assert.equal(cuota.abonos.a1.intento.transaccionId, rechazado.cuerpo.transaccionId);
  });

  await t.test('con un cobro en proceso no se puede iniciar otro (evita el doble cobro)', async () => {
    const r = await pedir('/api/pagos/nequi', tokenInquilino, cobro(c1));
    assert.equal(r.estado, 409);
    assert.equal(llamadas.length, 1);
  });

  await t.test('un cobro rechazado por Nequi queda disponible para reintentar', async () => {
    await resolver(rechazado, 'DECLINED', { status_message: 'Cobro rechazado' });
    const a1 = (await leerCuota(c1)).abonos.a1;
    assert.equal(a1.estado, 'rechazado');
    assert.equal(a1.motivo, 'Cobro rechazado');
  });

  let pago;
  await t.test('el reintento usa otra referencia y un evento viejo no lo pisa', async () => {
    pago = await pedir('/api/pagos/nequi', tokenInquilino, cobro(c1));
    assert.equal(pago.estado, 201);
    assert.notEqual(pago.cuerpo.referencia, rechazado.cuerpo.referencia);
    await pedir('/webhooks/wompi', null, evento({ ...transacciones[rechazado.cuerpo.transaccionId], status: 'DECLINED' }));
    assert.equal((await leerCuota(c1)).abonos.a1.estado, 'en_proceso');
  });

  await t.test('un evento con firma falsa, otro monto o una referencia ajena no marca nada', async () => {
    const tx = { ...transacciones[pago.cuerpo.transaccionId], status: 'APPROVED' };
    assert.equal((await pedir('/webhooks/wompi', null, evento(tx, 'secreto-falso'))).estado, 400);
    assert.equal((await pedir('/webhooks/wompi', null, evento({ ...tx, amount_in_cents: 100 }))).estado, 200);
    assert.equal((await pedir('/webhooks/wompi', null, evento({ ...tx, reference: 'pago-ajeno' }))).estado, 200);
    assert.equal((await leerCuota(c1)).abonos.a1.estado, 'en_proceso');
  });

  await t.test('el pago aprobado deja la cuota pagada, con medio y comprobante', async () => {
    assert.equal((await resolver(pago, 'APPROVED')).estado, 200);
    const cuota = await leerCuota(c1);
    assert.equal(cuota.abonos.a1.estado, 'aprobado');
    assert.equal(cuota.abonos.a1.medio, 'Nequi');
    assert.equal(cuota.abonos.a1.pagadoEn, AHORA);
    assert.match(cuota.abonos.a1.comprobante, /^MA-NOV26-PU-[A-Z0-9]{3}$/);
    assert.equal(cuota.abonos.a1.intento, undefined);
    assert.equal(cuota.pagado, 1200000);
    assert.equal(cuota.estado, 'pagado');
    assert.equal((await pedir('/api/pagos/nequi', tokenInquilino, cobro(c1))).estado, 409);
  });

  await t.test('reenviar el evento no vuelve a registrar el pago', async () => {
    await db.ref(`pagos/casa1/${c1}/abonos/a1/pagadoEn`).set(1);
    assert.equal((await resolver(pago, 'APPROVED')).estado, 200);
    assert.equal((await leerCuota(c1)).abonos.a1.pagadoEn, 1);
    assert.equal((await leerCuota(c1)).pagado, 1200000);
  });

  // ---------- Cuota 2: un mes viejo que quedó a medias en abonos ----------

  await t.test('un mes viejo a medias en abonos se termina cobrando solo lo que falta', async () => {
    await db.ref(`pagos/casa1/${c2}`).update({
      modalidad: 'abonos',
      pagado: 400000,
      estado: 'parcial',
      abonos: {
        a1: { numero: 1, tipo: 'abono', monto: 400000, estado: 'aprobado', pagadoEn: AHORA - 1000, comprobante: 'MA-DIC26-A1-001' },
        a2: { numero: 2, tipo: 'abono', monto: 400000, estado: 'pendiente' },
        a3: { numero: 3, tipo: 'abono', monto: 400000, estado: 'pendiente' },
      },
    });
    const saldo = await pedir('/api/pagos/nequi', tokenInquilino, cobro(c2));
    assert.equal(saldo.estado, 201);
    assert.equal(saldo.cuerpo.monto, 800000);
    assert.equal(llamadas.at(-1).montoCentavos, 80000000);
    const enProceso = await leerCuota(c2);
    assert.deepEqual(Object.keys(enProceso.abonos).sort(), ['a1', 'a4'], 'los abonos 2 y 3 se reemplazan por uno solo');
    assert.equal(enProceso.abonos.a4.tipo, 'saldo');

    await resolver(saldo, 'APPROVED');
    const cuota = await leerCuota(c2);
    assert.equal(cuota.estado, 'pagado');
    assert.equal(cuota.pagado, 1200000);
    assert.match(cuota.abonos.a4.comprobante, /^MA-DIC26-SA-/);
  });

  // ---------- Cuota 3: "Consultar estado" ----------

  await t.test('"consultar estado" averigua el resultado en Wompi aunque el webhook no haya llegado', async () => {
    const unico = await pedir('/api/pagos/nequi', tokenInquilino, cobro(c3, { modalidad: 'unico' }));
    assert.equal(unico.estado, 201);
    assert.equal(unico.cuerpo.monto, 1200000);
    assert.equal(llamadas.at(-1).montoCentavos, 120000000);

    const ruta = `/api/pagos/casa1/${c3}/abonos/1/estado`;
    assert.equal((await pedir(ruta, null)).estado, 401);
    assert.equal((await pedir(ruta, tokenOtro)).estado, 404);
    assert.equal((await pedir(ruta, tokenInquilino)).cuerpo.estado, 'en_proceso');

    transacciones[unico.cuerpo.transaccionId].status = 'APPROVED'; // sin webhook
    const r = await pedir(ruta, tokenInquilino);
    assert.equal(r.estado, 200);
    assert.equal(r.cuerpo.estado, 'aprobado');
    assert.equal(r.cuerpo.cuotaEstado, 'pagado');
    assert.equal(r.cuerpo.saldo, 0);
    assert.match(r.cuerpo.comprobante, /^MA-ENE27-PU-/);
  });

  // ---------- Cuota 4: Wompi no responde ----------

  await t.test('si Wompi falla, el cobro se libera y se puede reintentar', async () => {
    wompiCaido = true;
    const fallo = await pedir('/api/pagos/nequi', tokenInquilino, cobro(c4));
    assert.equal(fallo.estado, 502);
    wompiCaido = false;
    assert.ok(Object.values((await leerCuota(c4)).abonos).every((a) => a.estado === 'pendiente'));

    const unico = await pedir('/api/pagos/nequi', tokenInquilino, cobro(c4));
    assert.equal(unico.estado, 201);
    assert.equal(unico.cuerpo.monto, 1200000);
    assert.equal((await leerCuota(c4)).modalidad, 'unico');
    assert.deepEqual(Object.keys((await leerCuota(c4)).abonos), ['a1']);
  });

  // ---------- Estado de la propiedad ----------

  await t.test('el cron exige su secreto y deja atrasada la cuota vencida sin pagar', async () => {
    const llamar = (secreto) => fetch(`${base}/api/cron/estados`, { method: 'POST', headers: secreto ? { 'x-cron-secret': secreto } : {} });
    assert.equal((await llamar(null)).status, 401);
    assert.equal((await llamar('otro')).status, 401);

    await db.ref(`pagos/casa1/${c4}/vence`).set(AHORA - 1000);
    assert.equal((await llamar(CONFIG.secretoCron)).status, 200);
    assert.equal((await db.ref('propiedades/casa1/estadoPago').get()).val(), 'atrasado');
  });
});
