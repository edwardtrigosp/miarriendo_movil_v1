package com.example.miarriendo_v1;

import java.util.Collection;
import java.util.List;

/**
 * Lo que muestra Módulo / Recaudo (446:3584): cuánto pagaron los inquilinos de la cuota del momento de cada
 * propiedad y qué pasó con ese dinero. Usa los mismos estados de transferencia que Pagos por inmueble.
 * Java puro: se prueba con JUnit.
 */
final class RecaudoDelMes {

    enum Estado {
        MES_EN_CURSO,
        /** Hay dinero pagado que no se puede transferir porque falta la cuenta bancaria. */
        RETENIDO,
        AL_DIA
    }

    long esperado;
    long recibido;
    long transferido;
    long porTransferir;
    long retenido;
    int inquilinos;
    int inquilinosAlDia;
    /** Cuota que da nombre al mes ("Recaudo de noviembre"): la que vence primero. */
    Cuota referencia;

    private RecaudoDelMes() {
    }

    /** @param cuotasPorPropiedad cuotas del inquilino actual de cada propiedad arrendada. */
    static RecaudoDelMes calcular(Collection<List<Cuota>> cuotasPorPropiedad, boolean tieneCuenta, long ahora) {
        RecaudoDelMes r = new RecaudoDelMes();
        for (List<Cuota> cuotas : cuotasPorPropiedad) {
            Cuota cuota = ReglasPago.cuotaActiva(cuotas, ahora);
            if (cuota == null) {
                continue;
            }
            r.inquilinos++;
            r.esperado += cuota.monto;
            r.recibido += cuota.pagado;
            if (cuota.pagada()) {
                r.inquilinosAlDia++;
            }
            if (r.referencia == null || cuota.vence < r.referencia.vence) {
                r.referencia = cuota;
            }
            for (Abono abono : ReglasPago.abonos(cuota)) {
                if (!abono.aprobado()) {
                    continue;
                }
                if (abono.transferidoEn > 0) {
                    r.transferido += abono.monto;
                } else if (tieneCuenta) {
                    r.porTransferir += abono.monto;
                } else {
                    r.retenido += abono.monto;
                }
            }
        }
        return r;
    }

    /** Sin propiedades arrendadas no hay recaudo que mostrar: el módulo no se dibuja. */
    boolean vacio() {
        return inquilinos == 0;
    }

    long pendiente() {
        return Math.max(0, esperado - recibido);
    }

    Estado estado() {
        if (retenido > 0) {
            return Estado.RETENIDO;
        }
        return inquilinosAlDia == inquilinos ? Estado.AL_DIA : Estado.MES_EN_CURSO;
    }

    /** Recibido sobre esperado, de 0 a 100 (BarraRecaudo, 438:2690). */
    int progreso() {
        if (esperado <= 0) {
            return 0;
        }
        return (int) Math.min(100, recibido * 100 / esperado);
    }
}
