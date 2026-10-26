package com.example.miarriendo_v1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;

/** Fechas de Mi contrato: desde que el propietario aceptó al inquilino y por la duración que publicó. */
public class ContratoArriendoTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private static Propiedad propiedad(LocalDate desde, int meses) {
        Propiedad p = new Propiedad();
        p.inquilinoDesde = desde == null ? 0 : desde.atStartOfDay(BOGOTA).toInstant().toEpochMilli();
        p.duracionMeses = meses;
        return p;
    }

    @Test
    public void doceMesesTerminanElDiaAnteriorAlAniversario() {
        ContratoArriendo c = ContratoArriendo.de(propiedad(LocalDate.of(2026, 11, 1), 12), BOGOTA);
        assertEquals(LocalDate.of(2026, 11, 1), c.inicio);
        assertEquals(LocalDate.of(2027, 10, 31), c.fin);
    }

    @Test
    public void vigenteHastaElUltimoDiaIncluido() {
        ContratoArriendo c = ContratoArriendo.de(propiedad(LocalDate.of(2026, 11, 1), 6), BOGOTA);
        assertFalse(c.vigente(LocalDate.of(2026, 10, 31)));
        assertTrue(c.vigente(LocalDate.of(2026, 11, 1)));
        assertTrue(c.vigente(LocalDate.of(2027, 4, 30)));
        assertFalse(c.vigente(LocalDate.of(2027, 5, 1)));
    }

    @Test
    public void sinDuracionNoHayFinYSigueVigente() {
        ContratoArriendo c = ContratoArriendo.de(propiedad(LocalDate.of(2026, 11, 1), 0), BOGOTA);
        assertNull(c.fin);
        assertTrue(c.vigente(LocalDate.of(2030, 1, 1)));
    }

    @Test
    public void sinInquilinoNoHayContrato() {
        ContratoArriendo c = ContratoArriendo.de(propiedad(null, 12), BOGOTA);
        assertNull(c.inicio);
        assertFalse(c.vigente(LocalDate.of(2026, 11, 1)));
    }
}
