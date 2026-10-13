package com.example.miarriendo_v1;

/**
 * Qué se va a cobrar con el siguiente pago, según la cuota y la forma de pago pedida. Sirve para mostrar
 * el resumen antes de pagar; el servidor calcula el cobro real con las mismas reglas.
 */
final class PlanDeCobro {

    enum Tipo {
        /** Todo el mes de una vez. */
        UNICO,
        /** Uno de los 3 abonos. */
        ABONO,
        /** Lo que falta del mes de una vez. */
        SALDO
    }

    static final int NUM_ABONOS = 3;

    final Tipo tipo;
    /** Número de abono (1 para pago único y saldo no se muestra). */
    final int numero;
    /** Cuántos abonos tiene el mes. */
    final int total;
    final long monto;
    /** Lo que quedará por pagar después de este cobro. */
    final long saldoDespues;

    private PlanDeCobro(Tipo tipo, int numero, int total, long monto, long saldoDespues) {
        this.tipo = tipo;
        this.numero = numero;
        this.total = total;
        this.monto = monto;
        this.saldoDespues = saldoDespues;
    }

    /**
     * @param modalidad "unico", "abonos" o "saldo" si el inquilino acaba de elegirla; null si sigue con el
     *                  plan que ya tiene la cuota
     * @return null si no hay nada que cobrar o falta elegir cómo pagar
     */
    static PlanDeCobro de(Cuota cuota, String modalidad) {
        if (cuota == null || cuota.pagada()) {
            return null;
        }
        if (VistasPago.MODALIDAD_SALDO.equals(modalidad)) {
            return cuota.pagado > 0 ? new PlanDeCobro(Tipo.SALDO, 0, 0, ReglasPago.saldo(cuota), 0) : null;
        }
        // Hasta el primer abono aprobado la forma de pago se puede cambiar.
        String elegida = cuota.pagado == 0 && modalidad != null ? modalidad : cuota.modalidad;
        if (elegida == null) {
            return null;
        }
        if (Cuota.MODALIDAD_UNICO.equals(elegida)) {
            return new PlanDeCobro(Tipo.UNICO, 1, 1, cuota.monto - cuota.pagado, 0);
        }
        boolean planNuevo = cuota.pagado == 0 && (!Cuota.MODALIDAD_ABONOS.equals(cuota.modalidad) || cuota.abonos.isEmpty());
        if (planNuevo) {
            long primero = ReglasPago.montosDeAbonos(cuota.monto, NUM_ABONOS)[0];
            return new PlanDeCobro(Tipo.ABONO, 1, NUM_ABONOS, primero, cuota.monto - primero);
        }
        Abono siguiente = ReglasPago.siguientePorPagar(cuota);
        if (siguiente == null) {
            return null;
        }
        long despues = Math.max(0, cuota.monto - cuota.pagado - siguiente.monto);
        Tipo tipo = Abono.TIPO_UNICO.equals(siguiente.tipo) ? Tipo.UNICO
                : Abono.TIPO_SALDO.equals(siguiente.tipo) ? Tipo.SALDO : Tipo.ABONO;
        return new PlanDeCobro(tipo, siguiente.numero, Math.max(1, cuota.abonos.size()), siguiente.monto, despues);
    }
}
