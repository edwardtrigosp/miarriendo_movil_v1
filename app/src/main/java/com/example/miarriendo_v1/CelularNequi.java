package com.example.miarriendo_v1;

/** Número de celular de una cuenta Nequi: 10 dígitos colombianos que empiezan por 3. */
final class CelularNequi {

    private CelularNequi() {
    }

    /** "300 123 4567" o "+57 300 123 4567" → "3001234567"; null si no es un celular colombiano. */
    static String normalizar(String valor) {
        if (valor == null) {
            return null;
        }
        String digitos = valor.replaceAll("[\\s().-]", "");
        if (digitos.startsWith("+57")) {
            digitos = digitos.substring(3);
        } else if (digitos.startsWith("57") && digitos.length() == 12) {
            digitos = digitos.substring(2);
        }
        return digitos.matches("3\\d{9}") ? digitos : null;
    }

    /** "3001234567" → "300 123 4567"; lo que no sea un celular válido se devuelve igual. */
    static String formatear(String valor) {
        String celular = normalizar(valor);
        if (celular == null) {
            return valor;
        }
        return celular.substring(0, 3) + " " + celular.substring(3, 6) + " " + celular.substring(6);
    }
}
