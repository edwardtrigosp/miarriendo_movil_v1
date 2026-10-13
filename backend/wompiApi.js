// Cliente de la API de Wompi: tokens de aceptación, crear una transacción Nequi y consultarla.
// Crear y consultar transacciones exige la llave PRIVADA (Authorization: Bearer prv_…): solo desde aquí.

const { ErrorHttp } = require('./errores');
const { MONEDA } = require('./wompi');

function crearClienteWompi({ baseUrl, llavePublica, llavePrivada, fetchFn = fetch }) {
  async function pedir(ruta, { metodo = 'GET', cabeceras = {}, cuerpo } = {}) {
    let respuesta;
    try {
      respuesta = await fetchFn(baseUrl + ruta, {
        method: metodo,
        headers: { 'content-type': 'application/json', ...cabeceras },
        body: cuerpo ? JSON.stringify(cuerpo) : undefined,
      });
    } catch (e) {
      console.error('No se pudo contactar a Wompi', e);
      throw new ErrorHttp(502, 'No se pudo contactar a Wompi. Intenta de nuevo.');
    }
    const json = await respuesta.json().catch(() => null);
    if (!respuesta.ok) {
      console.error('Wompi respondió', respuesta.status, JSON.stringify(json && json.error));
      throw new ErrorHttp(respuesta.status === 422 ? 422 : 502,
        respuesta.status === 422 ? 'Wompi no aceptó los datos del cobro.' : 'Wompi no pudo procesar la solicitud.');
    }
    return json;
  }

  const conLlavePrivada = { authorization: `Bearer ${llavePrivada}` };

  return {
    /** Tokens y enlaces de los dos contratos que el usuario debe aceptar antes de pagar. */
    async aceptacion() {
      const { data } = await pedir('/merchants/info', { cabeceras: { 'x-merchant-public-key': llavePublica } });
      const terminos = data && data.presigned_acceptance;
      const datos = data && data.presigned_personal_data_auth;
      if (!terminos || !datos) {
        throw new ErrorHttp(502, 'Wompi no entregó los tokens de aceptación.');
      }
      return {
        acceptanceToken: terminos.acceptance_token,
        personalAuthToken: datos.acceptance_token,
        terminosUrl: terminos.permalink,
        datosUrl: datos.permalink,
      };
    },

    /** Cobro por Nequi: Wompi manda la notificación push al celular y la transacción queda PENDING. */
    async crearTransaccionNequi({ acceptanceToken, personalAuthToken, montoCentavos, correo, referencia, firma, celular }) {
      const { data } = await pedir('/transactions', {
        metodo: 'POST',
        cabeceras: conLlavePrivada,
        cuerpo: {
          acceptance_token: acceptanceToken,
          accept_personal_auth: personalAuthToken,
          amount_in_cents: montoCentavos,
          currency: MONEDA,
          customer_email: correo,
          reference: referencia,
          signature: firma,
          payment_method: { type: 'NEQUI', phone_number: celular },
        },
      });
      return data;
    },

    async consultarTransaccion(id) {
      const { data } = await pedir(`/transactions/${encodeURIComponent(id)}`, { cabeceras: conLlavePrivada });
      return data;
    },
  };
}

module.exports = { crearClienteWompi };
