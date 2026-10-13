// Reglas de seguridad de la base de datos (app/database.rules.json) contra el emulador. Se salta sola
// si no está corriendo:
//
//   firebase emulators:exec --only auth,database --project demo-miarriendo "npm --prefix backend test"

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { initializeTestEnvironment, assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { ref, get, set, update, query, orderByChild, equalTo } = require('firebase/database');

const HOST = process.env.FIREBASE_DATABASE_EMULATOR_HOST;

test('reglas de la base de datos: pagos, solicitudes y referencias', { skip: !HOST && 'sin emulador de base de datos' }, async (t) => {
  const [host, puerto] = HOST.split(':');
  const env = await initializeTestEnvironment({
    projectId: 'demo-miarriendo-reglas',
    database: {
      host,
      port: Number(puerto),
      rules: fs.readFileSync(path.join(__dirname, '..', '..', 'app', 'database.rules.json'), 'utf8'),
    },
  });
  t.after(() => env.cleanup());

  await env.withSecurityRulesDisabled(async (contexto) => {
    const db = contexto.database();
    await set(ref(db), {
      propiedades: {
        casa1: { propietarioId: 'propietario', inquilinoId: 'inquilino', precio: 100 },
        casa2: { propietarioId: 'propietario', precio: 100 },
      },
      pagos: {
        casa1: {
          viejo: { inquilinoId: 'exinquilino', monto: 100, estado: 'pagado' },
          actual: { inquilinoId: 'inquilino', monto: 100, estado: 'pendiente' },
        },
      },
      referencias_pago: { r1: { propiedadId: 'casa1' } },
      solicitudes_arriendo: {
        s1: { propiedadId: 'casa2', propietarioId: 'propietario', inquilinoId: 'candidato', estado: 'pendiente' },
      },
    });
  });
  const como = (uid) => env.authenticatedContext(uid).database();
  const anonimo = () => env.unauthenticatedContext().database();

  await t.test('el propietario y el inquilino actual leen todos los pagos del inmueble', async () => {
    await assertSucceeds(get(ref(como('propietario'), 'pagos/casa1')));
    await assertSucceeds(get(ref(como('inquilino'), 'pagos/casa1')));
  });

  await t.test('un extraño no lee los pagos, ni de forma directa ni consultando por otro inquilino', async () => {
    await assertFails(get(ref(como('extrano'), 'pagos/casa1')));
    await assertFails(get(ref(como('extrano'), 'pagos/casa1/actual')));
    await assertFails(get(ref(anonimo(), 'pagos/casa1')));
    await assertFails(get(query(ref(como('extrano'), 'pagos/casa1'), orderByChild('inquilinoId'), equalTo('inquilino'))));
  });

  await t.test('un exinquilino conserva su historial pero solo el suyo', async () => {
    const db = como('exinquilino');
    await assertSucceeds(get(ref(db, 'pagos/casa1/viejo')));
    await assertSucceeds(get(query(ref(db, 'pagos/casa1'), orderByChild('inquilinoId'), equalTo('exinquilino'))));
    await assertFails(get(ref(db, 'pagos/casa1')));
    await assertFails(get(ref(db, 'pagos/casa1/actual')));
  });

  await t.test('nadie escribe pagos ni referencias desde la app', async () => {
    for (const uid of ['propietario', 'inquilino', 'exinquilino']) {
      const db = como(uid);
      await assertFails(set(ref(db, 'pagos/casa1/actual/estado'), 'pagado'));
      await assertFails(set(ref(db, 'pagos/casa1/nueva'), { inquilinoId: uid, monto: 1 }));
      await assertFails(update(ref(db, 'pagos/casa1/actual'), { pagado: 100 }));
      await assertFails(set(ref(db, 'referencias_pago/r2'), { propiedadId: 'casa1' }));
      await assertFails(get(ref(db, 'referencias_pago/r1')));
    }
  });

  await t.test('un inquilino solo crea solicitudes pendientes a su nombre', async () => {
    const db = como('candidato');
    await assertSucceeds(set(ref(db, 'solicitudes_arriendo/s2'), { propiedadId: 'casa2', propietarioId: 'propietario', inquilinoId: 'candidato', estado: 'pendiente' }));
    await assertFails(set(ref(db, 'solicitudes_arriendo/s3'), { propiedadId: 'casa2', propietarioId: 'propietario', inquilinoId: 'candidato', estado: 'aceptada' }));
    await assertFails(set(ref(db, 'solicitudes_arriendo/s4'), { propiedadId: 'casa2', propietarioId: 'propietario', inquilinoId: 'otro', estado: 'pendiente' }));
  });

  await t.test('el propietario solo puede rechazar una solicitud pendiente; aceptar es cosa del servidor', async () => {
    const db = como('propietario');
    await assertFails(set(ref(db, 'solicitudes_arriendo/s1/estado'), 'aceptada'));
    await assertSucceeds(set(ref(db, 'solicitudes_arriendo/s1/estado'), 'rechazada'));
    await assertFails(set(ref(db, 'solicitudes_arriendo/s1/estado'), 'aceptada'), 'ya no está pendiente');
  });

  await t.test('un candidato no cambia el estado de su propia solicitud', async () => {
    await env.withSecurityRulesDisabled(async (contexto) => {
      await set(ref(contexto.database(), 'solicitudes_arriendo/s5'), { propiedadId: 'casa2', propietarioId: 'propietario', inquilinoId: 'candidato', estado: 'pendiente' });
    });
    await assertFails(set(ref(como('candidato'), 'solicitudes_arriendo/s5/estado'), 'aceptada'));
  });
});
