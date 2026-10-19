// Registra en el proyecto REAL de Firebase 20 propiedades de demostración (5 en Río de Oro, 5 en Aguachica,
// 5 en Valledupar y 5 en Ocaña) a nombre de un propietario de demostración que no tiene cuenta para entrar.
// No llevan fotos: la app muestra el ícono de portada. Todos los ids empiezan por "demo_" para poder borrarlos.
//
//   node --env-file=.env scripts/sembrar-demo.js --confirmo-proyecto-real
//   node --env-file=.env scripts/sembrar-demo.js --confirmo-proyecto-real --borrar

const { initializeApp, applicationDefault } = require('firebase-admin/app');
const { getDatabase } = require('firebase-admin/database');

if (!process.argv.includes('--confirmo-proyecto-real')) {
  console.error('Este script escribe en el proyecto REAL. Añade --confirmo-proyecto-real si es lo que quieres.');
  process.exit(1);
}
if (process.env.FIREBASE_AUTH_EMULATOR_HOST || process.env.FIREBASE_DATABASE_EMULATOR_HOST) {
  console.error('Hay variables de emuladores activas: este script es para el proyecto real.');
  process.exit(1);
}

const PROPIETARIO = 'demo_propietario';
const HORA = 60 * 60 * 1000;

// Centro aproximado de cada municipio; cada propiedad se corre un poco para que no queden encimadas en el mapa.
const CIUDADES = [
  {
    clave: 'rio_de_oro', ciudad: 'Río de Oro', lat: 8.2917, lng: -73.3858,
    casas: [
      ['Casa', 'Casa El Carmen', 'El Carmen', 'Calle 4 #3-15', 3, 2, 90, 650000, ['Parqueadero', 'Terraza']],
      ['Apartamento', 'Apartamento Plaza Principal', 'Centro', 'Carrera 5 #4-22', 2, 1, 60, 480000, ['Balcón']],
      ['Estudio', 'Estudio San Martín', 'San Martín', 'Calle 6 #2-40', 1, 1, 30, 350000, ['Amoblado']],
      ['Casa', 'Casa La Esperanza', 'La Esperanza', 'Carrera 2 #7-11', 3, 1, 85, 550000, ['Mascotas', 'Terraza']],
      ['Habitación', 'Habitación Villa del Río', 'Villa del Río', 'Calle 8 #5-03', 1, 1, 16, 250000, ['Amoblado']],
    ],
  },
  {
    clave: 'aguachica', ciudad: 'Aguachica', lat: 8.3084, lng: -73.6166,
    casas: [
      ['Casa', 'Casa San Eduardo', 'San Eduardo', 'Calle 5 #18-32', 3, 2, 110, 900000, ['Parqueadero', 'Mascotas']],
      ['Apartamento', 'Apartamento Centro', 'Centro', 'Carrera 16 #4-15', 2, 1, 65, 700000, ['Portería 24h']],
      ['Apartamento', 'Apartamento Villa Paraíso', 'Villa Paraíso', 'Calle 12 #22-08', 3, 2, 80, 850000, ['Parqueadero', 'Balcón']],
      ['Estudio', 'Estudio Las Palmas', 'Las Palmas', 'Carrera 20 #9-51', 1, 1, 35, 500000, ['Amoblado']],
      ['Casa', 'Casa El Carmen', 'El Carmen', 'Calle 3 #14-27', 4, 2, 130, 1000000, ['Parqueadero', 'Terraza', 'Mascotas']],
    ],
  },
  {
    clave: 'valledupar', ciudad: 'Valledupar', lat: 10.4631, lng: -73.2532,
    casas: [
      ['Apartamento', 'Apartamento La Nevada', 'La Nevada', 'Calle 44 #19-60', 3, 2, 85, 1200000, ['Parqueadero', 'Piscina', 'Portería 24h']],
      ['Casa', 'Casa Los Mayales', 'Los Mayales', 'Calle 20 #12-35', 3, 2, 120, 1500000, ['Parqueadero', 'Terraza']],
      ['Apartamento', 'Apartaestudio Novalito', 'Novalito', 'Carrera 7 #13-21', 1, 1, 40, 750000, ['Amoblado', 'Balcón']],
      ['Casa', 'Casa Villa Clara', 'Villa Clara', 'Calle 30 #4-18', 4, 3, 150, 1400000, ['Parqueadero', 'Mascotas', 'Terraza']],
      ['Apartamento', 'Apartamento Centro', 'Centro', 'Cra. 9 #16-40', 2, 2, 70, 950000, ['Gimnasio', 'Portería 24h']],
    ],
  },
  {
    clave: 'ocana', ciudad: 'Ocaña', lat: 8.2378, lng: -73.3560,
    casas: [
      ['Casa', 'Casa La Primavera', 'La Primavera', 'Calle 11 #25-14', 3, 2, 100, 950000, ['Parqueadero', 'Terraza']],
      ['Apartamento', 'Apartamento Santa Clara', 'Santa Clara', 'Carrera 13 #8-36', 2, 1, 60, 700000, ['Balcón', 'Portería 24h']],
      ['Estudio', 'Estudio Centro Histórico', 'Centro', 'Calle 10 #12-05', 1, 1, 32, 550000, ['Amoblado']],
      ['Casa', 'Casa El Llano', 'El Llano', 'Carrera 30 #6-47', 4, 2, 140, 1100000, ['Parqueadero', 'Mascotas']],
      ['Habitación', 'Habitación Buenos Aires', 'Buenos Aires', 'Calle 7 #18-20', 1, 1, 14, 300000, ['Amoblado']],
    ],
  },
];

// Desplazamientos (grados) para repartir las 5 propiedades alrededor del centro.
const DESPLAZAMIENTOS = [[0.004, -0.003], [-0.002, 0.005], [-0.005, -0.004], [0.006, 0.006], [0.001, -0.007]];

function propiedades(ahora) {
  const lista = {};
  let orden = 0;
  for (const c of CIUDADES) {
    c.casas.forEach(([tipo, nombre, barrio, direccion, habitaciones, banos, area, precio, comodidades], i) => {
      const id = `demo_${c.clave}_${i + 1}`;
      lista[id] = {
        id,
        propietarioId: PROPIETARIO,
        titulo: nombre,
        nombreInterno: nombre,
        descripcion: `${tipo} de ${habitaciones} ${habitaciones === 1 ? 'habitación' : 'habitaciones'} en ${barrio}, ${c.ciudad}. `
          + 'Propiedad de demostración de miarriendo.',
        tipo,
        ciudad: c.ciudad,
        barrio,
        direccion,
        lat: +(c.lat + DESPLAZAMIENTOS[i][0]).toFixed(6),
        lng: +(c.lng + DESPLAZAMIENTOS[i][1]).toFixed(6),
        habitaciones,
        banos,
        area,
        precio,
        comodidades,
        numFotos: 0,
        duracionMeses: 12,
        deposito: 0,
        disponibilidad: 'inmediata',
        clausulas: 'Contrato de arrendamiento de vivienda urbana según la Ley 820 de 2003.',
        diaDePago: 5,
        // Escalonadas para que "Recién publicados" tenga un orden estable.
        creadoEn: ahora - orden++ * HORA,
      };
    });
  }
  return lista;
}

(async () => {
  const app = initializeApp({ credential: applicationDefault(), databaseURL: 'https://miarriendo-ff0a9-default-rtdb.firebaseio.com' });
  const db = getDatabase(app);
  const ids = Object.keys(propiedades(Date.now()));

  if (process.argv.includes('--borrar')) {
    const cambios = { [`usuarios/${PROPIETARIO}`]: null };
    for (const id of ids) {
      cambios[`propiedades/${id}`] = null;
    }
    await db.ref().update(cambios);
    console.log(`Borradas ${ids.length} propiedades de demostración y el perfil ${PROPIETARIO}.`);
    process.exit(0);
  }

  // No pisa datos que no sean de demostración: si ya existe alguna, se detiene.
  const existentes = [];
  for (const id of ids) {
    if ((await db.ref(`propiedades/${id}`).get()).exists()) {
      existentes.push(id);
    }
  }
  if (existentes.length) {
    console.error(`Ya existen: ${existentes.join(', ')}. Bórralas primero con --borrar.`);
    process.exit(1);
  }

  const cambios = {
    [`usuarios/${PROPIETARIO}`]: { id: PROPIETARIO, nombre: 'Propietario', apellido: 'Demo', rol: 'propietario' },
  };
  for (const [id, p] of Object.entries(propiedades(Date.now()))) {
    cambios[`propiedades/${id}`] = p;
  }
  await db.ref().update(cambios);
  console.log(`Registradas ${ids.length} propiedades de demostración a nombre de ${PROPIETARIO}.`);
  process.exit(0);
})().catch((e) => {
  console.error(e.message);
  process.exit(1);
});
