const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');

const { firmaIntegridad, checksumEvento, eventoAutentico, nombreDelMedio, normalizarCelular, ambienteDe } = require('../wompi');
const { leerConfig } = require('../config');

const sha256 = (texto) => crypto.createHash('sha256').update(texto).digest('hex');

test('la firma de integridad concatena referencia, monto, moneda y secreto', () => {
  const firma = firmaIntegridad({ referencia: 'pago-1', montoCentavos: 9500000, secreto: 'test_integrity_x' });
  assert.equal(firma, sha256('pago-19500000COPtest_integrity_x'));
});

function eventoDeEjemplo(secreto) {
  const evento = {
    event: 'transaction.updated',
    data: { transaction: { id: '1234-1610641025-49201', status: 'APPROVED', amount_in_cents: 4490000 } },
    timestamp: 1530291411,
    signature: {
      properties: ['transaction.id', 'transaction.status', 'transaction.amount_in_cents'],
      checksum: '',
    },
  };
  evento.signature.checksum = sha256(`1234-1610641025-49201APPROVED4490000${evento.timestamp}${secreto}`);
  return evento;
}

test('el checksum del evento sigue el ejemplo de la documentación', () => {
  const evento = eventoDeEjemplo('prod_events_x');
  assert.equal(checksumEvento(evento, 'prod_events_x'), evento.signature.checksum);
});

test('un evento con la firma correcta es auténtico', () => {
  assert.equal(eventoAutentico(eventoDeEjemplo('prod_events_x'), 'prod_events_x'), true);
});

test('un secreto distinto, un dato alterado o un checksum ausente se rechazan', () => {
  assert.equal(eventoAutentico(eventoDeEjemplo('prod_events_x'), 'otro_secreto'), false);

  const alterado = eventoDeEjemplo('prod_events_x');
  alterado.data.transaction.amount_in_cents = 1;
  assert.equal(eventoAutentico(alterado, 'prod_events_x'), false);

  const sinChecksum = eventoDeEjemplo('prod_events_x');
  delete sinChecksum.signature.checksum;
  assert.equal(eventoAutentico(sinChecksum, 'prod_events_x'), false);

  assert.equal(eventoAutentico({}, 'prod_events_x'), false);
  assert.equal(eventoAutentico(null, 'prod_events_x'), false);
});

test('un evento al que le falta una propiedad firmada no se acepta', () => {
  const evento = eventoDeEjemplo('prod_events_x');
  delete evento.data.transaction.status;
  assert.equal(checksumEvento(evento, 'prod_events_x'), null);
});

test('los medios de pago se muestran con su nombre', () => {
  assert.equal(nombreDelMedio('NEQUI'), 'Nequi');
  assert.equal(nombreDelMedio('CARD'), 'Tarjeta');
  assert.equal(nombreDelMedio('ALGO_NUEVO'), 'ALGO_NUEVO');
  assert.equal(nombreDelMedio(undefined), null);
});

test('el celular Nequi se normaliza a 10 dígitos o se rechaza', () => {
  assert.equal(normalizarCelular('300 123 4567'), '3001234567');
  assert.equal(normalizarCelular('+57 300 123 4567'), '3001234567');
  assert.equal(normalizarCelular('573001234567'), '3001234567');
  assert.equal(normalizarCelular('(300) 123-4567'), '3001234567');
  assert.equal(normalizarCelular('1001234567'), null, 'no empieza por 3');
  assert.equal(normalizarCelular('300123456'), null, 'corto');
  assert.equal(normalizarCelular('30012345678'), null, 'largo');
  assert.equal(normalizarCelular(3001234567), null, 'no es texto');
  assert.equal(normalizarCelular(undefined), null);
});

test('el ambiente sale del prefijo de la llave', () => {
  assert.equal(ambienteDe('pub_test_abc').nombre, 'sandbox');
  assert.equal(ambienteDe('prv_prod_abc').nombre, 'produccion');
  assert.equal(ambienteDe('pub_test_abc').baseUrl, 'https://sandbox.wompi.co/v1');
  assert.equal(ambienteDe('otra'), null);
});

test('la configuración exige las cuatro variables de Wompi y ambientes coherentes', () => {
  const base = {
    WOMPI_PUBLIC_KEY: 'pub_test_a', WOMPI_PRIVATE_KEY: 'prv_test_b',
    WOMPI_INTEGRITY_SECRET: 'test_integrity_c', WOMPI_EVENTS_SECRET: 'test_events_d',
  };
  const ok = leerConfig(base);
  assert.equal(ok.ambiente, 'sandbox');
  assert.equal(ok.urlApiWompi, 'https://sandbox.wompi.co/v1');
  assert.throws(() => leerConfig({ ...base, WOMPI_PRIVATE_KEY: '' }), /WOMPI_PRIVATE_KEY/);
  assert.throws(() => leerConfig({ ...base, WOMPI_PRIVATE_KEY: 'prv_prod_b' }), /ambientes distintos/);
  assert.throws(() => leerConfig({ ...base, WOMPI_PUBLIC_KEY: 'xyz' }), /deben empezar/);
});

test('las llaves de producción solo se aceptan si se pide expresamente', () => {
  const produccion = {
    WOMPI_PUBLIC_KEY: 'pub_prod_a', WOMPI_PRIVATE_KEY: 'prv_prod_b',
    WOMPI_INTEGRITY_SECRET: 'prod_integrity_c', WOMPI_EVENTS_SECRET: 'prod_events_d',
  };
  assert.throws(() => leerConfig(produccion), /PRODUCCIÓN/);
  assert.throws(() => leerConfig({ ...produccion, PERMITIR_PRODUCCION: 'no' }), /PRODUCCIÓN/);
  const config = leerConfig({ ...produccion, PERMITIR_PRODUCCION: 'si' });
  assert.equal(config.ambiente, 'produccion');
  assert.equal(config.urlApiWompi, 'https://production.wompi.co/v1');
});
