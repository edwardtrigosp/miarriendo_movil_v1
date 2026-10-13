package com.example.miarriendo_v1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CuentaBancariaTest {

    @Test
    public void elNumeroDeCuentaSonSoloDigitosEntre6Y20() {
        assertTrue(CuentaBancaria.numeroValido("48210012345"));
        assertTrue(CuentaBancaria.numeroValido("123456"));
        assertFalse(CuentaBancaria.numeroValido("12345"));
        assertFalse(CuentaBancaria.numeroValido("123456789012345678901"));
        assertFalse(CuentaBancaria.numeroValido("4821 0012"));
        assertFalse(CuentaBancaria.numeroValido("4821-0012"));
        assertFalse(CuentaBancaria.numeroValido("abc12345"));
        assertFalse(CuentaBancaria.numeroValido(null));
    }

    @Test
    public void elDocumentoSonSoloDigitos() {
        assertTrue(CuentaBancaria.documentoValido("1065123456"));
        assertFalse(CuentaBancaria.documentoValido("1234"));
        assertFalse(CuentaBancaria.documentoValido("1.065.123.456"));
        assertFalse(CuentaBancaria.documentoValido(null));
    }

    @Test
    public void elTitularLlevaNombreYApellidoSinNumeros() {
        assertTrue(CuentaBancaria.titularValido("Juan Pérez"));
        assertTrue(CuentaBancaria.titularValido("  María   del Carmen Gómez "));
        assertFalse(CuentaBancaria.titularValido("Juan"));
        assertFalse(CuentaBancaria.titularValido("Juan 123"));
        assertFalse(CuentaBancaria.titularValido(""));
        assertFalse(CuentaBancaria.titularValido(null));
    }

    @Test
    public void elNumeroYElDocumentoSeMuestranEnmascarados() {
        assertEquals("•••• •••• 4821", CuentaBancaria.enmascararNumero("00004821"));
        assertEquals("•••• •••• 2345", CuentaBancaria.enmascararNumero("48210012345"));
        assertEquals("••••", CuentaBancaria.enmascararNumero("12"));
        assertEquals("CC •••• 3456", CuentaBancaria.enmascararDocumento("1065123456"));
        assertEquals("CC ••••", CuentaBancaria.enmascararDocumento(null));
    }

    @Test
    public void unaCuentaCompletaTieneTodosSusDatosValidos() {
        CuentaBancaria cuenta = new CuentaBancaria();
        assertFalse(cuenta.completa());
        cuenta.banco = "Bancolombia";
        cuenta.tipo = CuentaBancaria.TIPO_AHORROS;
        cuenta.numero = "48210012345";
        cuenta.titular = "Juan Pérez";
        cuenta.documento = "1065123456";
        assertTrue(cuenta.completa());
        cuenta.numero = "12";
        assertFalse(cuenta.completa());
    }
}
