// Calendario de cuotas, abonos, comprobantes y estado de pago. Funciones puras.

const HORA_MS = 60 * 60 * 1000;
const DIA_MS = 24 * HORA_MS;
/** Colombia es UTC-5 todo el año (sin horario de verano). */
const DESFASE_BOGOTA_MS = 5 * HORA_MS;
/** Con una cuota que vence en menos de estos días, la propiedad pasa a "pendiente". */
const DIAS_AVISO = 5;
const MESES = ['ENE', 'FEB', 'MAR', 'ABR', 'MAY', 'JUN', 'JUL', 'AGO', 'SEP', 'OCT', 'NOV', 'DIC'];

function finDelDia(anio, mes, dia) {
  return Date.UTC(anio, mes, dia, 23, 59, 59) + DESFASE_BOGOTA_MS;
}

/**
 * Vencimientos mensuales (ms) hasta las 23:59:59 de Bogotá. Con {@code diaDePago} (1 a 28) vencen ese
 * día de cada mes, empezando por el primero que llegue a partir de {@code inicioMs}; sin él, el mismo
 * día en que empieza el arriendo (si el mes no lo tiene, el último día).
 */
function fechasDeCuotas(inicioMs, cantidad, diaDePago) {
  const local = new Date(inicioMs - DESFASE_BOGOTA_MS);
  const anio = local.getUTCFullYear();
  let mes = local.getUTCMonth();
  let dia = local.getUTCDate();

  if (Number.isInteger(diaDePago) && diaDePago >= 1 && diaDePago <= 28) {
    if (dia > diaDePago) {
      mes += 1;
    }
    dia = diaDePago;
  }

  const fechas = [];
  for (let i = 0; i < cantidad; i++) {
    const mesTotal = mes + i;
    const anioCuota = anio + Math.floor(mesTotal / 12);
    const mesCuota = mesTotal % 12;
    const ultimoDia = new Date(Date.UTC(anioCuota, mesCuota + 1, 0)).getUTCDate();
    fechas.push(finDelDia(anioCuota, mesCuota, Math.min(dia, ultimoDia)));
  }
  return fechas;
}

/** Mes de un vencimiento: "2026-11". */
function periodoDe(venceMs) {
  const local = new Date(venceMs - DESFASE_BOGOTA_MS);
  return `${local.getUTCFullYear()}-${String(local.getUTCMonth() + 1).padStart(2, '0')}`;
}

/** "2026-11" → "NOV26". */
function codigoDePeriodo(periodo) {
  const [anio, mes] = periodo.split('-');
  return `${MESES[Number(mes) - 1]}${anio.slice(2)}`;
}

/** Reparte {@code monto} en {@code cantidad} abonos; el último absorbe lo que sobre al redondear. */
function montosDeAbonos(monto, cantidad) {
  const base = Math.floor(monto / cantidad);
  const montos = new Array(cantidad - 1).fill(base);
  montos.push(monto - base * (cantidad - 1));
  return montos;
}

/** "MA-NOV26-A1-365": mes, tipo de pago y los 3 últimos caracteres del id de la transacción. */
function comprobanteDe({ periodo, tipo, numero, transaccionId }) {
  const marca = tipo === 'unico' ? 'PU' : tipo === 'saldo' ? 'SA' : `A${numero}`;
  const sufijo = String(transaccionId || '').replace(/[^A-Za-z0-9]/g, '').slice(-3).toUpperCase().padStart(3, '0');
  return `MA-${codigoDePeriodo(periodo)}-${marca}-${sufijo}`;
}

/**
 * "atrasado" si hay una cuota sin pagar del todo ya vencida; "pendiente" si la próxima vence en
 * {@link DIAS_AVISO} días o menos; "al_dia" en cualquier otro caso. Una cuota parcial cuenta como sin pagar.
 */
function estadoPagoDe(pagos, ahoraMs) {
  const sinPagar = pagos.filter((pago) => pago.estado !== 'pagado' && typeof pago.vence === 'number');
  if (sinPagar.length === 0) {
    return 'al_dia';
  }
  const proxima = Math.min(...sinPagar.map((pago) => pago.vence));
  if (proxima < ahoraMs) {
    return 'atrasado';
  }
  return proxima - ahoraMs <= DIAS_AVISO * DIA_MS ? 'pendiente' : 'al_dia';
}

module.exports = {
  fechasDeCuotas,
  periodoDe,
  codigoDePeriodo,
  montosDeAbonos,
  comprobanteDe,
  estadoPagoDe,
  DIAS_AVISO,
};
