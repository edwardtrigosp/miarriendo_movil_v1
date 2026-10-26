package com.example.miarriendo_v1;

/**
 * Qué se va a cobrar con el siguiente pago. Desde el 20/10/2026 el arriendo se paga completo: el mes entero
 * si no hay nada pagado, o lo que falta si un mes viejo quedó a medias en abonos. Sirve para mostrar el
 * resumen antes de pagar; el servidor calcula el cobro real con las mismas reglas.
 */
final class PlanDeCobro {

    enum Tipo {
        /** Todo el mes de una vez. */
        UNICO,
        /** Lo que falta de un mes viejo que quedó a medias en abonos. */
        SALDO
    }

    final Tipo tipo;
    final long monto;

    private PlanDeCobro(Tipo tipo, long monto) {
        this.tipo = tipo;
        this.monto = monto;
    }

    /** @return null si no hay nada que cobrar. */
    static PlanDeCobro de(Cuota cuota) {
        if (cuota == null || cuota.pagada()) {
            return null;
        }
        return cuota.pagado > 0 ? new PlanDeCobro(Tipo.SALDO, ReglasPago.saldo(cuota))
                : new PlanDeCobro(Tipo.UNICO, cuota.monto);
    }

    /** Lo que se envía al servidor como "modalidad" (también lo entienden las versiones anteriores del servidor). */
    String modalidad() {
        return tipo == Tipo.SALDO ? VistasPago.MODALIDAD_SALDO : Cuota.MODALIDAD_UNICO;
    }
}
