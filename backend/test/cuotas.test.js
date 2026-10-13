const test = require('node:test');
const assert = require('node:assert/strict');

const { fechasDeCuotas, estadoPagoDe } = require('../cuotas');

const DIA = 24 * 60 * 60 * 1000;
/** Hora de Bogotá (UTC-5) como milisegundos. */
const bogota = (anio, mes, dia, hora = 0, min = 0, seg = 0) => Date.UTC(anio, mes - 1, dia, hora + 5, min, seg);

test('las cuotas vencen el mismo día de cada mes a las 23:59:59 de Bogotá', () => {
  const fechas = fechasDeCuotas(bogota(2026, 10, 11, 9, 30), 3);
  assert.deepEqual(fechas, [
    bogota(2026, 10, 11, 23, 59, 59),
    bogota(2026, 11, 11, 23, 59, 59),
    bogota(2026, 12, 11, 23, 59, 59),
  ]);
});

test('pasado el año, el calendario sigue en enero', () => {
  const fechas = fechasDeCuotas(bogota(2026, 12, 15), 2);
  assert.equal(fechas[1], bogota(2027, 1, 15, 23, 59, 59));
});

test('si el mes no tiene ese día, vence el último; la fecha no se corre en los meses siguientes', () => {
  const fechas = fechasDeCuotas(bogota(2027, 1, 31), 4);
  assert.deepEqual(fechas, [
    bogota(2027, 1, 31, 23, 59, 59),
    bogota(2027, 2, 28, 23, 59, 59),
    bogota(2027, 3, 31, 23, 59, 59),
    bogota(2027, 4, 30, 23, 59, 59),
  ]);
});

test('una hora de madrugada en UTC sigue contando como el día anterior en Bogotá', () => {
  // 2026-10-12 02:00 UTC = 2026-10-11 21:00 en Bogotá.
  const fechas = fechasDeCuotas(Date.UTC(2026, 9, 12, 2), 1);
  assert.equal(fechas[0], bogota(2026, 10, 11, 23, 59, 59));
});

test('sin cuotas pendientes la propiedad está al día', () => {
  assert.equal(estadoPagoDe([], 0), 'al_dia');
  assert.equal(estadoPagoDe([{ estado: 'pagado', vence: 1 }], 100), 'al_dia');
});

test('una cuota sin pagar y vencida deja la propiedad atrasada', () => {
  const ahora = 10 * DIA;
  assert.equal(estadoPagoDe([{ estado: 'pendiente', vence: ahora - 1 }, { estado: 'pendiente', vence: ahora + 30 * DIA }], ahora), 'atrasado');
});

test('una cuota que vence pronto la deja pendiente', () => {
  const ahora = 10 * DIA;
  assert.equal(estadoPagoDe([{ estado: 'pendiente', vence: ahora + 5 * DIA }], ahora), 'pendiente');
});

test('una cuota lejana no cambia el estado al día', () => {
  const ahora = 10 * DIA;
  assert.equal(estadoPagoDe([{ estado: 'pendiente', vence: ahora + 6 * DIA }], ahora), 'al_dia');
});

const { periodoDe, codigoDePeriodo, montosDeAbonos, comprobanteDe } = require('../cuotas');

test('con día de pago fijo, la primera cuota es la primera fecha que llega', () => {
  // Empieza el 11 de octubre y se paga el día 5: la primera es el 5 de noviembre.
  const fechas = fechasDeCuotas(bogota(2026, 10, 11, 9), 3, 5);
  assert.deepEqual(fechas, [bogota(2026, 11, 5, 23, 59, 59), bogota(2026, 12, 5, 23, 59, 59), bogota(2027, 1, 5, 23, 59, 59)]);
});

test('con día de pago fijo, si todavía no pasó ese día del mes la primera vence ese mes', () => {
  const fechas = fechasDeCuotas(bogota(2026, 10, 3), 2, 5);
  assert.deepEqual(fechas, [bogota(2026, 10, 5, 23, 59, 59), bogota(2026, 11, 5, 23, 59, 59)]);
});

test('un día de pago fuera de 1 a 28 se ignora', () => {
  const fechas = fechasDeCuotas(bogota(2026, 10, 11), 1, 31);
  assert.deepEqual(fechas, [bogota(2026, 10, 11, 23, 59, 59)]);
});

test('el periodo y su código salen de la fecha de vencimiento en Bogotá', () => {
  assert.equal(periodoDe(bogota(2026, 11, 5, 23, 59, 59)), '2026-11');
  assert.equal(periodoDe(bogota(2027, 1, 31, 23, 59, 59)), '2027-01');
  assert.equal(codigoDePeriodo('2026-11'), 'NOV26');
  assert.equal(codigoDePeriodo('2027-01'), 'ENE27');
});

test('los abonos suman siempre el monto; el último absorbe el resto', () => {
  assert.deepEqual(montosDeAbonos(450000, 3), [150000, 150000, 150000]);
  assert.deepEqual(montosDeAbonos(1000000, 3), [333333, 333333, 333334]);
  assert.deepEqual(montosDeAbonos(100, 3), [33, 33, 34]);
  for (const monto of [1, 2, 7, 999999, 1350000]) {
    assert.equal(montosDeAbonos(monto, 3).reduce((a, b) => a + b, 0), monto);
  }
});

test('el comprobante identifica mes, tipo de pago y transacción', () => {
  assert.equal(comprobanteDe({ periodo: '2026-11', tipo: 'abono', numero: 1, transaccionId: '01-1531231271-19365' }), 'MA-NOV26-A1-365');
  assert.equal(comprobanteDe({ periodo: '2026-11', tipo: 'unico', numero: 1, transaccionId: 'tx-abc' }), 'MA-NOV26-PU-ABC');
  assert.equal(comprobanteDe({ periodo: '2026-11', tipo: 'saldo', numero: 2, transaccionId: '7' }), 'MA-NOV26-SA-007');
});

test('una cuota parcial sigue contando como sin pagar para el atraso', () => {
  const ahora = 10 * DIA;
  assert.equal(estadoPagoDe([{ estado: 'parcial', vence: ahora - 1 }], ahora), 'atrasado');
});
