// Crea en el proyecto REAL de Firebase dos cuentas de prueba (una propietaria y una inquilina) y una propiedad
// de la propietaria, para probar los pagos sin usar cuentas de personas reales. Es idempotente: lo que ya
// existe no se vuelve a crear. Las claves se generan al azar y se guardan en backend/.cuentas-prueba.json
// (fuera de Git); no se imprimen.
//
//   node --env-file=.env scripts/crear-cuentas-prueba.js --confirmo-proyecto-real

const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const { initializeApp, applicationDefault } = require('firebase-admin/app');
const { getAuth } = require('firebase-admin/auth');
const { getDatabase } = require('firebase-admin/database');

if (!process.argv.includes('--confirmo-proyecto-real')) {
  console.error('Este script escribe en el proyecto REAL. Añade --confirmo-proyecto-real si es lo que quieres.');
  process.exit(1);
}
if (process.env.FIREBASE_AUTH_EMULATOR_HOST || process.env.FIREBASE_DATABASE_EMULATOR_HOST) {
  console.error('Hay variables de emuladores activas: este script es para el proyecto real.');
  process.exit(1);
}

const ARCHIVO = path.join(__dirname, '..', '.cuentas-prueba.json');
const guardadas = fs.existsSync(ARCHIVO) ? JSON.parse(fs.readFileSync(ARCHIVO, 'utf8')) : {};

const CUENTAS = [
  { clave: 'propietario', email: 'propietario.prueba@miarriendo.test', nombre: 'Propietario', apellido: 'Prueba', rol: 'propietario', telefono: '3001234567' },
  // 3991111111 es el celular de prueba de Nequi que Wompi aprueba en el sandbox.
  { clave: 'inquilino', email: 'inquilino.prueba@miarriendo.test', nombre: 'Inquilino', apellido: 'Prueba', rol: 'inquilino', telefono: '3991111111' },
];

function claveAleatoria() {
  return `Mr-${crypto.randomBytes(9).toString('base64url')}-7`;
}

const app = initializeApp({ credential: applicationDefault(), databaseURL: 'https://miarriendo-ff0a9-default-rtdb.firebaseio.com' });
const auth = getAuth(app);
const db = getDatabase(app);

(async () => {
  const uids = {};
  for (const c of CUENTAS) {
    let usuario;
    try {
      usuario = await auth.getUserByEmail(c.email);
      console.log(`${c.clave}: ya existía (${usuario.uid})`);
    } catch (e) {
      if (e.code !== 'auth/user-not-found') throw e;
      const password = claveAleatoria();
      usuario = await auth.createUser({ email: c.email, password, displayName: `${c.nombre} ${c.apellido}` });
      guardadas[c.clave] = { email: c.email, password, uid: usuario.uid };
      fs.writeFileSync(ARCHIVO, JSON.stringify(guardadas, null, 2));
      console.log(`${c.clave}: creada (${usuario.uid})`);
    }
    uids[c.clave] = usuario.uid;
    const perfil = await db.ref(`usuarios/${usuario.uid}`).get();
    if (!perfil.exists()) {
      await db.ref(`usuarios/${usuario.uid}`).set({
        id: usuario.uid, nombre: c.nombre, apellido: c.apellido, telefono: c.telefono, email: c.email, rol: c.rol,
      });
      console.log(`${c.clave}: perfil creado`);
    }
  }

  const existente = await db.ref('propiedades').orderByChild('propietarioId').equalTo(uids.propietario).get();
  if (existente.exists()) {
    console.log(`propiedad: ya existía (${Object.keys(existente.val())[0]})`);
  } else {
    const ref = db.ref('propiedades').push();
    await ref.set({
      id: ref.key, propietarioId: uids.propietario, titulo: 'Apartamento de prueba (pagos)', nombreInterno: 'Apto de prueba',
      descripcion: 'Propiedad de prueba para probar solicitudes, cuotas y pagos con Nequi en el sandbox.', tipo: 'Apartamento',
      ciudad: 'Valledupar, Cesar', barrio: 'Centro', direccion: 'Cra. 9 #16-40', lat: 10.4631, lng: -73.2532,
      habitaciones: 2, banos: 1, area: 60, precio: 450000, comodidades: [], numFotos: 0,
      duracionMeses: 12, deposito: 0, disponibilidad: 'inmediata', clausulas: '', diaDePago: 5, inquilinoDesde: 0, creadoEn: Date.now(),
    });
    console.log(`propiedad: creada (${ref.key})`);
  }
  console.log('Claves guardadas en backend/.cuentas-prueba.json (no se sube a Git).');
  process.exit(0);
})().catch((e) => { console.error('FALLÓ:', e.message); process.exit(1); });
