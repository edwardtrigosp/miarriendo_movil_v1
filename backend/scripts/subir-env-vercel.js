// Sube las variables del backend al proyecto de Vercel vinculado (carpeta backend/.vercel), leyendo backend/.env.
// Los valores van por la entrada estándar: no se imprimen ni quedan en la línea de comandos.
//
//   node scripts/subir-env-vercel.js               → solo acepta llaves de sandbox (pub_test_…)
//   node scripts/subir-env-vercel.js --produccion  → acepta llaves de producción y define PERMITIR_PRODUCCION=si
//
// Requiere GOOGLE_APPLICATION_CREDENTIALS (ruta al JSON de la cuenta de servicio) y CRON_SECRET en .env.
const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const dir = path.join(__dirname, '..');
const produccion = process.argv.includes('--produccion');

const env = Object.fromEntries(fs.readFileSync(path.join(dir, '.env'), 'utf8').split(/\r?\n/)
  .filter((l) => /^[A-Z_]+=/.test(l))
  .map((l) => { const i = l.indexOf('='); return [l.slice(0, i), l.slice(i + 1).trim()]; }));

const vars = {
  WOMPI_PUBLIC_KEY: env.WOMPI_PUBLIC_KEY,
  WOMPI_PRIVATE_KEY: env.WOMPI_PRIVATE_KEY,
  WOMPI_INTEGRITY_SECRET: env.WOMPI_INTEGRITY_SECRET,
  WOMPI_EVENTS_SECRET: env.WOMPI_EVENTS_SECRET,
  FIREBASE_SERVICE_ACCOUNT: JSON.stringify(JSON.parse(fs.readFileSync(env.GOOGLE_APPLICATION_CREDENTIALS, 'utf8'))),
  CRON_SECRET: env.CRON_SECRET,
};
for (const [nombre, valor] of Object.entries(vars)) {
  if (!valor) throw new Error(`Falta ${nombre} en .env`);
}

const sonDeProduccion = /^pub_prod_/.test(vars.WOMPI_PUBLIC_KEY);
if (sonDeProduccion && !produccion) {
  throw new Error('Las llaves de .env son de PRODUCCIÓN. Si de verdad quieres cobros reales, usa --produccion.');
}
if (sonDeProduccion) vars.PERMITIR_PRODUCCION = 'si';

const sesion = spawnSync('vercel', ['whoami'], { cwd: dir, shell: true, encoding: 'utf8' });
if (sesion.status !== 0) {
  console.error('Esta terminal no tiene sesión en Vercel. Ejecuta "vercel login" y vuelve a intentarlo.');
  process.exit(1);
}

let fallos = 0;
for (const [nombre, valor] of Object.entries(vars)) {
  const r = spawnSync('vercel', ['env', 'add', nombre, 'production', '--sensitive', '--force', '--yes'],
    { cwd: dir, input: valor, shell: true, encoding: 'utf8' });
  if (r.status !== 0) fallos++;
  console.log(nombre.padEnd(26), r.status === 0 ? 'ok' : `FALLÓ\n${r.stderr}`);
}
if (fallos > 0) {
  console.error(`\n${fallos} variable(s) no se subieron: no despliegues todavía.`);
  process.exit(1);
}
console.log(`\nAmbiente: ${sonDeProduccion ? 'PRODUCCIÓN (cobros reales)' : 'sandbox'}. Ahora: vercel deploy --prod --cwd backend`);
