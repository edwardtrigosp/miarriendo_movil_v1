// Piezas puras de la integración con Wompi (firmas, eventos, celulares). Sin red ni Firebase, para
// poder probarlas con `npm test`. Documentación: https://docs.wompi.co/docs/colombia/

const crypto = require('node:crypto');

const MONEDA = 'COP';
const URL_SANDBOX = 'https://sandbox.wompi.co/v1';
const URL_PRODUCCION = 'https://production.wompi.co/v1';

function sha256(texto) {
  return crypto.createHash('sha256').update(texto).digest('hex');
}

/** SHA256("<referencia><monto en centavos><moneda><secreto de integridad>"). */
function firmaIntegridad({ referencia, montoCentavos, moneda = MONEDA, secreto }) {
  return sha256(`${referencia}${montoCentavos}${moneda}${secreto}`);
}

function valorEn(objeto, ruta) {
  return ruta.split('.').reduce((actual, clave) => (actual == null ? undefined : actual[clave]), objeto);
}

/**
 * Checksum esperado de un evento: valores de `signature.properties` (tomados de `data`, en ese orden),
 * luego `timestamp` y el secreto de eventos. La lista de propiedades no se fija en el código: la
 * documentación avisa de que puede cambiar. Devuelve null si el evento no trae lo necesario.
 */
function checksumEvento(evento, secretoEventos) {
  const propiedades = evento && evento.signature && evento.signature.properties;
  if (!Array.isArray(propiedades) || propiedades.length === 0 || evento.timestamp == null) {
    return null;
  }
  const valores = propiedades.map((ruta) => valorEn(evento.data, ruta));
  if (valores.some((valor) => valor === undefined || valor === null)) {
    return null;
  }
  return sha256(valores.join('') + evento.timestamp + secretoEventos);
}

/** El evento viene de Wompi si su checksum coincide con el calculado con nuestro secreto. */
function eventoAutentico(evento, secretoEventos) {
  const esperado = checksumEvento(evento, secretoEventos);
  const recibido = evento && evento.signature && evento.signature.checksum;
  if (!esperado || typeof recibido !== 'string') {
    return false;
  }
  const a = Buffer.from(esperado);
  const b = Buffer.from(recibido.toLowerCase());
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}

const MEDIOS = {
  NEQUI: 'Nequi',
  PSE: 'PSE',
  CARD: 'Tarjeta',
  BANCOLOMBIA_TRANSFER: 'Bancolombia',
  BANCOLOMBIA_COLLECT: 'Bancolombia',
  DAVIPLATA: 'Daviplata',
};

/** "NEQUI" → "Nequi"; un medio desconocido se guarda tal cual lo manda Wompi. */
function nombreDelMedio(tipo) {
  return MEDIOS[tipo] || tipo || null;
}

/** "300 123 4567" o "+57 300 123 4567" → "3001234567"; null si no es un celular colombiano. */
function normalizarCelular(valor) {
  if (typeof valor !== 'string') {
    return null;
  }
  let digitos = valor.replace(/[\s().-]/g, '');
  if (digitos.startsWith('+57')) {
    digitos = digitos.slice(3);
  } else if (digitos.startsWith('57') && digitos.length === 12) {
    digitos = digitos.slice(2);
  }
  return /^3\d{9}$/.test(digitos) ? digitos : null;
}

/** pub_test_… usa el sandbox y pub_prod_… producción; las llaves de cada ambiente no se mezclan. */
function ambienteDe(llave) {
  if (typeof llave === 'string' && /^(pub|prv)_test_/.test(llave)) {
    return { nombre: 'sandbox', baseUrl: URL_SANDBOX };
  }
  if (typeof llave === 'string' && /^(pub|prv)_prod_/.test(llave)) {
    return { nombre: 'produccion', baseUrl: URL_PRODUCCION };
  }
  return null;
}

module.exports = {
  firmaIntegridad,
  checksumEvento,
  eventoAutentico,
  nombreDelMedio,
  normalizarCelular,
  ambienteDe,
  MONEDA,
};
