// Entrada para Vercel: la misma app de servidor.js como función. vercel.json manda aquí todas las rutas.
const { crearAppReal } = require('../servidor');

module.exports = crearAppReal().app;
