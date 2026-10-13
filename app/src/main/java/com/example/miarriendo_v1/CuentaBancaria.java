package com.example.miarriendo_v1;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;

/**
 * Cuenta bancaria de un usuario. Se guarda en "usuarios/{uid}/cuenta", que solo lee y escribe su dueño.
 * El propietario la usa para recibir sus arriendos; el inquilino, para devoluciones.
 */
@IgnoreExtraProperties
public class CuentaBancaria {

    public static final String TIPO_AHORROS = "Ahorros";
    public static final String TIPO_CORRIENTE = "Corriente";

    public String banco;
    public String tipo;
    public String numero;
    public String titular;
    /** Cédula de ciudadanía del titular (solo dígitos). */
    public String documento;
    public long actualizadaEn;

    /** Constructor vacío requerido por Firebase. */
    public CuentaBancaria() {
    }

    // ---------- Validación ----------

    /** Entre 6 y 20 dígitos, sin espacios ni guiones. */
    static boolean numeroValido(String numero) {
        return numero != null && numero.matches("\\d{6,20}");
    }

    /** Entre 5 y 15 dígitos. */
    static boolean documentoValido(String documento) {
        return documento != null && documento.matches("\\d{5,15}");
    }

    /** Al menos dos palabras, sin dígitos, para que no se guarde un nombre incompleto. */
    static boolean titularValido(String titular) {
        if (titular == null) {
            return false;
        }
        String limpio = titular.trim().replaceAll("\\s+", " ");
        return limpio.length() >= 5 && limpio.contains(" ") && !limpio.matches(".*\\d.*");
    }

    // ---------- Presentación ----------

    /** "4821" al final y el resto oculto: "•••• •••• 4821". */
    static String enmascararNumero(String numero) {
        if (numero == null || numero.length() < 4) {
            return "••••";
        }
        return "•••• •••• " + numero.substring(numero.length() - 4);
    }

    /** "CC •••• 4567" */
    static String enmascararDocumento(String documento) {
        if (documento == null || documento.length() < 4) {
            return "CC ••••";
        }
        return "CC •••• " + documento.substring(documento.length() - 4);
    }

    @Exclude
    public boolean completa() {
        return banco != null && tipo != null && numeroValido(numero) && titularValido(titular) && documentoValido(documento);
    }
}
