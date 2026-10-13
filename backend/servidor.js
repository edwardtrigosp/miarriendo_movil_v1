// Servidor HTTP de miarriendo.
//
//   POST /api/solicitudes/:id/aceptar                          propietario → activa el arriendo y crea las cuotas
//   GET  /api/pagos/aceptacion                                 inquilino   → tokens y enlaces de los contratos de Wompi
//   POST /api/pagos/nequi                                      inquilino   → cobra un abono con Nequi (push al celular)
//   GET  /api/pagos/:propiedadId/:cuotaId/abonos/:n/estado     inquilino   → "Consultar estado"
//   POST /webhooks/wompi                                       Wompi       → evento transaction.updated
//   POST /api/cron/estados                                     cron externo→ recalcula "al día / pendiente / atrasado"
//   GET  /salud
//
// Las rutas /api (menos el cron) exigen el ID token de Firebase: "Authorization: Bearer <token>".

const crypto = require('node:crypto');
const express = require('express');

const { leerConfig } = require('./config');
const { eventoAutentico } = require('./wompi');
const { crearClienteWompi } = require('./wompiApi');
const { ErrorHttp } = require('./errores');
const acciones = require('./acciones');

function crearApp({ db, auth, wompi, config, reloj = Date.now }) {
  const ctx = { db, wompi, config };
  const app = express();
  app.disable('x-powered-by');
  app.use(express.json({ limit: '100kb' }));

  /** Pone el usuario verificado en req.usuario; sin token válido responde 401. */
  async function exigirUsuario(req, res, next) {
    const cabecera = req.get('authorization') || '';
    const token = cabecera.startsWith('Bearer ') ? cabecera.slice(7) : null;
    if (!token) {
      res.status(401).json({ error: 'Inicia sesión para continuar.' });
      return;
    }
    try {
      const decodificado = await auth.verifyIdToken(token);
      req.usuario = { uid: decodificado.uid, correo: decodificado.email || null };
      next();
    } catch (e) {
      res.status(401).json({ error: 'Tu sesión no es válida.' });
    }
  }

  app.get('/salud', (req, res) => {
    res.json({ ok: true });
  });

  app.post('/api/solicitudes/:id/aceptar', exigirUsuario, async (req, res) => {
    res.json(await acciones.aceptarSolicitud(ctx, req.usuario.uid, req.params.id, reloj()));
  });

  app.get('/api/pagos/aceptacion', exigirUsuario, async (req, res) => {
    res.json(await acciones.obtenerAceptacion(ctx));
  });

  app.post('/api/pagos/nequi', exigirUsuario, async (req, res) => {
    res.status(201).json(await acciones.iniciarCobroNequi(ctx, req.usuario, req.body || {}, reloj()));
  });

  app.get('/api/pagos/:propiedadId/:cuotaId/abonos/:n/estado', exigirUsuario, async (req, res) => {
    const numero = Number(req.params.n);
    res.json(await acciones.consultarEstadoAbono(ctx, req.usuario.uid, req.params.propiedadId, req.params.cuotaId, numero, reloj()));
  });

  // La app no prueba nada: solo este evento, firmado, da un pago por hecho.
  app.post('/webhooks/wompi', async (req, res) => {
    if (!eventoAutentico(req.body, config.secretoEventos)) {
      console.warn('Evento de Wompi con firma inválida');
      res.status(400).send('Firma inválida');
      return;
    }
    if (req.body.event === 'transaction.updated') {
      const transaccion = req.body.data.transaction;
      const resultado = await acciones.aplicarTransaccion(ctx, transaccion, reloj());
      console.log(`Wompi ${transaccion.reference}: ${resultado}`);
    }
    res.status(200).send('ok');
  });

  /** El secreto llega en x-cron-secret (cron externo) o como "Bearer" (así lo manda el cron de Vercel). */
  async function recalcularEstados(req, res) {
    const cabecera = req.get('authorization') || '';
    const enviado = Buffer.from(req.get('x-cron-secret') || (cabecera.startsWith('Bearer ') ? cabecera.slice(7) : ''));
    const esperado = Buffer.from(config.secretoCron || '');
    const valido = config.secretoCron && enviado.length === esperado.length && crypto.timingSafeEqual(enviado, esperado);
    if (!valido) {
      res.status(401).json({ error: 'No autorizado.' });
      return;
    }
    res.json({ propiedades: await acciones.recalcularTodos(ctx, reloj()) });
  }
  app.post('/api/cron/estados', recalcularEstados);
  app.get('/api/cron/estados', recalcularEstados);

  // Errores esperados → su código; cualquier otro → 500 (Wompi reintenta el webhook).
  // eslint-disable-next-line no-unused-vars
  app.use((error, req, res, next) => {
    if (error instanceof ErrorHttp) {
      res.status(error.estado).json({ error: error.message });
      return;
    }
    console.error(error);
    res.status(500).json({ error: 'Algo salió mal.' });
  });

  return app;
}

function iniciarFirebase() {
  const { initializeApp, cert, applicationDefault } = require('firebase-admin/app');
  const { getDatabase } = require('firebase-admin/database');
  const { getAuth } = require('firebase-admin/auth');
  const app = initializeApp({
    credential: process.env.FIREBASE_SERVICE_ACCOUNT
      ? cert(JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT))
      : applicationDefault(),
    databaseURL: process.env.FIREBASE_DATABASE_URL || 'https://miarriendo-ff0a9-default-rtdb.firebaseio.com',
  });
  return { db: getDatabase(app), auth: getAuth(app) };
}

/** La app con Firebase y Wompi reales, configurada desde las variables de entorno. */
function crearAppReal() {
  const config = leerConfig();
  const { db, auth } = iniciarFirebase();
  const wompi = crearClienteWompi({
    baseUrl: config.urlApiWompi,
    llavePublica: config.llavePublica,
    llavePrivada: config.llavePrivada,
  });
  return { app: crearApp({ db, auth, wompi, config }), config };
}

if (require.main === module) {
  const { app, config } = crearAppReal();
  app.listen(config.puerto, () => {
    console.log(`miarriendo-backend escuchando en el puerto ${config.puerto} (Wompi ${config.ambiente})`);
  });
}

module.exports = { crearApp, crearAppReal };
