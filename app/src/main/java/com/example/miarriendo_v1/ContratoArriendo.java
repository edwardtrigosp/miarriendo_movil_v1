package com.example.miarriendo_v1;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Las fechas del contrato que se ven en Mi contrato y en su PDF de resumen: empieza el día en que el propietario
 * aceptó al inquilino (Propiedad.inquilinoDesde) y dura Propiedad.duracionMeses. Java puro: se prueba con JUnit.
 */
final class ContratoArriendo {

    /** Null si la propiedad no tiene inquilino. */
    final LocalDate inicio;
    /** Último día del contrato; null si no se sabe la duración. */
    final LocalDate fin;

    private ContratoArriendo(LocalDate inicio, LocalDate fin) {
        this.inicio = inicio;
        this.fin = fin;
    }

    static ContratoArriendo de(Propiedad p, ZoneId zona) {
        if (p == null || p.inquilinoDesde <= 0) {
            return new ContratoArriendo(null, null);
        }
        LocalDate inicio = Instant.ofEpochMilli(p.inquilinoDesde).atZone(zona).toLocalDate();
        LocalDate fin = p.duracionMeses > 0 ? inicio.plusMonths(p.duracionMeses).minusDays(1) : null;
        return new ContratoArriendo(inicio, fin);
    }

    /** Vigente hasta el último día incluido; sin duración conocida se toma como vigente. */
    boolean vigente(LocalDate hoy) {
        return inicio != null && !hoy.isBefore(inicio) && (fin == null || !hoy.isAfter(fin));
    }
}
