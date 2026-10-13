// Datos de mentira para probar la app contra los EMULADORES de Firebase. Se niega a correr si no están
// activas las variables de los emuladores, para no escribir nunca en el proyecto real.
//
//   FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9199 FIREBASE_DATABASE_EMULATOR_HOST=127.0.0.1:9401 \
//     node backend/scripts/sembrar-pruebas.js
//
// Todos los usuarios entran con la clave CLAVE y un correo <uid>@prueba.test.

const { initializeApp } = require('firebase-admin/app');
const { getAuth } = require('firebase-admin/auth');
const { getDatabase } = require('firebase-admin/database');
const { periodoDe } = require('../cuotas');

if (!process.env.FIREBASE_AUTH_EMULATOR_HOST || !process.env.FIREBASE_DATABASE_EMULATOR_HOST) {
  console.error('Faltan FIREBASE_AUTH_EMULATOR_HOST y FIREBASE_DATABASE_EMULATOR_HOST: no se siembra el proyecto real.');
  process.exit(1);
}

const CLAVE = 'Prueba123!';
const DIA = 24 * 60 * 60 * 1000;
const ahora = Date.now();
const MONTO = 450000;

const app = initializeApp({ projectId: 'miarriendo-ff0a9', databaseURL: 'https://miarriendo-ff0a9-default-rtdb.firebaseio.com' });
const auth = getAuth(app);
const db = getDatabase(app);

const usuarios = {};
const datos = { propiedades: {}, pagos: {}, solicitudes_arriendo: {}, referencias_pago: {} };

function usuario(uid, nombre, apellido, rol, extra = {}) {
  usuarios[uid] = { id: uid, nombre, apellido, email: `${uid}@prueba.test`, telefono: '3991111111', rol, ...extra };
}

function propiedad(id, propietarioId, extra = {}) {
  datos.propiedades[id] = {
    id, propietarioId, titulo: `Apartamento ${id}`, nombreInterno: `Apartamento ${id}`, ciudad: 'Valledupar',
    direccion: 'Cra. 9 #16-40', precio: MONTO, duracionMeses: 12, habitaciones: 2, banos: 1, creadoEn: ahora, ...extra,
  };
}

function cuota(propiedadId, id, inquilinoId, numero, vence, extra = {}) {
  datos.pagos[propiedadId] = datos.pagos[propiedadId] || {};
  datos.pagos[propiedadId][id] = {
    numero, periodo: periodoDe(vence), monto: MONTO, vence, inquilinoId, pagado: 0, estado: 'pendiente', ...extra,
  };
}

function abono(numero, tipo, monto, estado, extra = {}) {
  return { numero, tipo, monto, estado, ...extra };
}

// Propietarios
usuario('dueno', 'Carlos', 'Gómez', 'propietario');
usuario('dueno2', 'Laura', 'Ríos', 'propietario', {
  cuenta: { banco: 'Bancolombia', tipo: 'Ahorros', numero: '48210012345', titular: 'Laura Ríos', documento: '1065123456', actualizadaEn: ahora },
});

// Un inquilino por escenario: cada uno con su propiedad y sus cuotas.
const escenarios = ['pendiente', 'aviso', 'parcial', 'pagado', 'vencido', 'enproceso', 'rechazado', 'flujo', 'transferida'];
for (const e of escenarios) {
  const uid = `inq_${e}`;
  const casa = `casa_${e}`;
  const dueno = e === 'transferida' ? 'dueno2' : 'dueno';
  usuario(uid, 'Juan', `Pérez ${e}`, 'inquilino');
  propiedad(casa, dueno, { inquilinoId: uid, inquilinoNombre: `Juan Pérez ${e}`, inquilinoDesde: ahora - 40 * DIA, estadoPago: 'pendiente', diaDePago: 5 });
}

cuota('casa_pendiente', 'c1', 'inq_pendiente', 1, ahora + 20 * DIA);
cuota('casa_aviso', 'c1', 'inq_aviso', 1, ahora + 3 * DIA);
cuota('casa_flujo', 'c1', 'inq_flujo', 1, ahora + 20 * DIA);
cuota('casa_vencido', 'c1', 'inq_vencido', 1, ahora - 2 * DIA);

cuota('casa_parcial', 'c1', 'inq_parcial', 1, ahora + 10 * DIA, {
  modalidad: 'abonos', pagado: 150000, estado: 'parcial',
  abonos: {
    a1: abono(1, 'abono', 150000, 'aprobado', { pagadoEn: ahora - 1 * DIA, medio: 'Nequi', wompiTransaccionId: 'falso-seed-1', comprobante: 'MA-NOV26-A1-001' }),
    a2: abono(2, 'abono', 150000, 'pendiente'),
    a3: abono(3, 'abono', 150000, 'pendiente'),
  },
});

cuota('casa_pagado', 'c1', 'inq_pagado', 1, ahora - 5 * DIA, {
  modalidad: 'unico', pagado: MONTO, estado: 'pagado',
  abonos: { a1: abono(1, 'unico', MONTO, 'aprobado', { pagadoEn: ahora - 6 * DIA, medio: 'Nequi', wompiTransaccionId: 'falso-seed-2', comprobante: 'MA-OCT26-PU-002' }) },
});
cuota('casa_pagado', 'c2', 'inq_pagado', 2, ahora + 40 * DIA);

cuota('casa_enproceso', 'c1', 'inq_enproceso', 1, ahora + 10 * DIA, {
  modalidad: 'abonos',
  abonos: {
    a1: abono(1, 'abono', 150000, 'en_proceso', { intento: { referencia: 'pago-c1-seed', celularFin: '4567', iniciadoEn: ahora - 60000, transaccionId: 'falso-seed-3' } }),
    a2: abono(2, 'abono', 150000, 'pendiente'),
    a3: abono(3, 'abono', 150000, 'pendiente'),
  },
});

cuota('casa_rechazado', 'c1', 'inq_rechazado', 1, ahora + 10 * DIA, {
  modalidad: 'abonos',
  abonos: {
    a1: abono(1, 'abono', 150000, 'rechazado', { rechazadoEn: ahora - 60000, motivo: 'Cobro rechazado en Nequi (simulado)' }),
    a2: abono(2, 'abono', 150000, 'pendiente'),
    a3: abono(3, 'abono', 150000, 'pendiente'),
  },
});

cuota('casa_transferida', 'c1', 'inq_transferida', 1, ahora - 3 * DIA, {
  modalidad: 'unico', pagado: MONTO, estado: 'pagado',
  abonos: { a1: abono(1, 'unico', MONTO, 'aprobado', { pagadoEn: ahora - 8 * DIA, medio: 'Nequi', wompiTransaccionId: 'falso-seed-4', comprobante: 'MA-OCT26-PU-004', transferidoEn: ahora - 2 * DIA }) },
});
cuota('casa_transferida', 'c2', 'inq_transferida', 2, ahora + 40 * DIA);

// Propiedad libre con dos solicitudes pendientes para la bandeja del propietario
propiedad('casa_libre', 'dueno2');
for (const [i, uid] of ['cand1', 'cand2'].entries()) {
  usuario(uid, i === 0 ? 'María' : 'Pedro', 'López', 'inquilino');
  datos.solicitudes_arriendo[`sol_${uid}`] = {
    id: `sol_${uid}`, propiedadId: 'casa_libre', propietarioId: 'dueno2', propietarioNombre: 'Laura',
    inquilinoId: uid, inquilinoNombre: `${i === 0 ? 'María' : 'Pedro'} López`,
    mensaje: 'Hola, me interesa el apartamento. Somos 3 personas y sin mascotas.', estado: 'pendiente',
    propiedadTitulo: 'Apartamento casa_libre', propiedadDireccion: 'Cra. 9 #16-40, Valledupar', precio: MONTO, creadaEn: ahora - (i + 1) * DIA,
  };
}

(async () => {
  for (const [uid, perfil] of Object.entries(usuarios)) {
    await auth.createUser({ uid, email: perfil.email, password: CLAVE });
  }
  await db.ref('usuarios').set(usuarios);
  await db.ref('propiedades').set(datos.propiedades);
  await db.ref('pagos').set(datos.pagos);
  await db.ref('solicitudes_arriendo').set(datos.solicitudes_arriendo);
  console.log(`Sembrados ${Object.keys(usuarios).length} usuarios, ${Object.keys(datos.propiedades).length} propiedades.`);
  console.log(`Clave de todos: ${CLAVE} · correo: <uid>@prueba.test`);
  process.exit(0);
})().catch((e) => { console.error(e); process.exit(1); });
