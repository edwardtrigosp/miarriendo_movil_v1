// Configuración del servidor. Todo viene de variables de entorno; en local, del archivo .env
// (ver .env.example). Los secretos nunca van en el código ni en la app.

const { ambienteDe } = require('./wompi');

const OBLIGATORIAS = ['WOMPI_PUBLIC_KEY', 'WOMPI_PRIVATE_KEY', 'WOMPI_INTEGRITY_SECRET', 'WOMPI_EVENTS_SECRET'];

function leerConfig(env = process.env) {
  const faltan = OBLIGATORIAS.filter((nombre) => !env[nombre]);
  if (faltan.length > 0) {
    throw new Error(`Faltan variables de entorno: ${faltan.join(', ')}`);
  }
  const publica = ambienteDe(env.WOMPI_PUBLIC_KEY);
  const privada = ambienteDe(env.WOMPI_PRIVATE_KEY);
  if (!publica || !privada) {
    throw new Error('Las llaves de Wompi deben empezar por pub_test_/prv_test_ (sandbox) o pub_prod_/prv_prod_ (producción).');
  }
  if (publica.nombre !== privada.nombre) {
    throw new Error('La llave pública y la privada de Wompi son de ambientes distintos.');
  }
  // Con llaves de producción cada cobro de prueba sería un cobro real: hay que pedirlo expresamente.
  if (publica.nombre === 'produccion' && env.PERMITIR_PRODUCCION !== 'si') {
    throw new Error('Las llaves de Wompi son de PRODUCCIÓN y los cobros serían reales. Usa las de pruebas (pub_test_…) '
      + 'o define PERMITIR_PRODUCCION=si si de verdad quieres producción.');
  }
  return {
    llavePublica: env.WOMPI_PUBLIC_KEY,
    llavePrivada: env.WOMPI_PRIVATE_KEY,
    secretoIntegridad: env.WOMPI_INTEGRITY_SECRET,
    secretoEventos: env.WOMPI_EVENTS_SECRET,
    ambiente: publica.nombre,
    /** Solo para pruebas: WOMPI_API_URL apunta a un Wompi simulado (backend/scripts/wompi-falso.js). */
    urlApiWompi: env.WOMPI_API_URL || publica.baseUrl,
    /** Opcional: si no está, el endpoint de recálculo diario queda apagado. */
    secretoCron: env.CRON_SECRET || null,
    puerto: Number(env.PORT) || 8080,
  };
}

module.exports = { leerConfig };
