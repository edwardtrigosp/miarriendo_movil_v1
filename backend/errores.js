/** Error con el código HTTP que debe recibir la app. */
class ErrorHttp extends Error {
  constructor(estado, mensaje) {
    super(mensaje);
    this.estado = estado;
  }
}

module.exports = { ErrorHttp };
