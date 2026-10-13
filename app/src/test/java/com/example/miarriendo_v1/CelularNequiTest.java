package com.example.miarriendo_v1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class CelularNequiTest {

    @Test
    public void normalizaLosFormatosHabituales() {
        assertEquals("3001234567", CelularNequi.normalizar("300 123 4567"));
        assertEquals("3001234567", CelularNequi.normalizar("+57 300 123 4567"));
        assertEquals("3001234567", CelularNequi.normalizar("573001234567"));
        assertEquals("3001234567", CelularNequi.normalizar("(300) 123-4567"));
        assertEquals("3991111111", CelularNequi.normalizar("3991111111"));
    }

    @Test
    public void rechazaLoQueNoEsUnCelularColombiano() {
        assertNull(CelularNequi.normalizar(null));
        assertNull(CelularNequi.normalizar(""));
        assertNull(CelularNequi.normalizar("1001234567"));
        assertNull(CelularNequi.normalizar("300123456"));
        assertNull(CelularNequi.normalizar("30012345678"));
        assertNull(CelularNequi.normalizar("abc"));
    }

    @Test
    public void formateaConEspaciosSoloLosValidos() {
        assertEquals("300 123 4567", CelularNequi.formatear("3001234567"));
        assertEquals("123", CelularNequi.formatear("123"));
    }
}
