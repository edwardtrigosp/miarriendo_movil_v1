// Wompi simulado, SOLO para pruebas locales (no tiene nada que ver con el Wompi real). Imita lo que usa el
// servidor: tokens de aceptación, crear una transacción Nequi y consultarla. Resuelve cada transacción a
// los pocos segundos según el celular, como el sandbox de Wompi:
//   3991111111 → APPROVED · 3992222222 → DECLINED · cualquier otro → ERROR
//
//   node backend/scripts/wompi-falso.js [puerto]      (por defecto 9100)
//   y en el servidor: WOMPI_API_URL=http://127.0.0.1:9100

const http = require('node:http');

const puerto = Number(process.argv[2]) || 9100;
const DEMORA_MS = Number(process.env.WOMPI_FALSO_DEMORA_MS) || 3000;
const transacciones = new Map();
let siguiente = 1;

function responder(res, estado, cuerpo) {
  res.writeHead(estado, { 'content-type': 'application/json' });
  res.end(JSON.stringify(cuerpo));
}

function leerCuerpo(req) {
  return new Promise((resolver) => {
    let texto = '';
    req.on('data', (parte) => { texto += parte; });
    req.on('end', () => { try { resolver(JSON.parse(texto || '{}')); } catch (e) { resolver({}); } });
  });
}

http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://localhost');
  if (req.method === 'GET' && url.pathname === '/merchants/info') {
    return responder(res, 200, { data: {
      presigned_acceptance: { acceptance_token: 'tok-terminos-falso', permalink: 'https://wompi.com/terminos-falsos.pdf', type: 'END_USER_POLICY' },
      presigned_personal_data_auth: { acceptance_token: 'tok-datos-falso', permalink: 'https://wompi.com/datos-falsos.pdf', type: 'PERSONAL_DATA_AUTH' },
    } });
  }
  if (req.method === 'POST' && url.pathname === '/transactions') {
    if (!/^Bearer prv_/.test(req.headers.authorization || '')) {
      return responder(res, 401, { error: { type: 'INPUT_VALIDATION_ERROR' } });
    }
    const cuerpo = await leerCuerpo(req);
    const celular = cuerpo.payment_method && cuerpo.payment_method.phone_number;
    if (!cuerpo.signature || !cuerpo.acceptance_token || !cuerpo.reference || !/^3\d{9}$/.test(celular || '')) {
      return responder(res, 422, { error: { type: 'INPUT_VALIDATION_ERROR', messages: { signature: ['falta'] } } });
    }
    const id = `falso-${siguiente++}`;
    const final = celular === '3991111111' ? 'APPROVED' : celular === '3992222222' ? 'DECLINED' : 'ERROR';
    const transaccion = {
      id, reference: cuerpo.reference, amount_in_cents: cuerpo.amount_in_cents, currency: cuerpo.currency,
      customer_email: cuerpo.customer_email, payment_method_type: 'NEQUI', status: 'PENDING', status_message: null,
    };
    transacciones.set(id, transaccion);
    setTimeout(() => {
      transaccion.status = final;
      transaccion.status_message = final === 'APPROVED' ? null : 'Cobro rechazado en Nequi (simulado)';
      console.log(`[wompi-falso] ${id} → ${final}`);
    }, DEMORA_MS);
    return responder(res, 201, { data: { ...transaccion } });
  }
  const consulta = /^\/transactions\/([^/]+)$/.exec(url.pathname);
  if (req.method === 'GET' && consulta) {
    const transaccion = transacciones.get(decodeURIComponent(consulta[1]));
    return transaccion ? responder(res, 200, { data: { ...transaccion } }) : responder(res, 404, { error: { type: 'NOT_FOUND_ERROR' } });
  }
  return responder(res, 404, { error: { type: 'NOT_FOUND_ERROR' } });
}).listen(puerto, '127.0.0.1', () => console.log(`[wompi-falso] escuchando en http://127.0.0.1:${puerto}`));
