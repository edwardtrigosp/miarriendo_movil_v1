package com.example.miarriendo_v1;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;

/**
 * Un cobro de una cuota: el pago único, uno de los 3 abonos o el saldo. Se guarda en
 * "pagos/{propiedadId}/{cuotaId}/abonos/a{numero}"; solo el servidor escribe aquí.
 */
@IgnoreExtraProperties
public class Abono {

    public static final String PENDIENTE = "pendiente";
    public static final String EN_PROCESO = "en_proceso";
    public static final String APROBADO = "aprobado";
    public static final String RECHAZADO = "rechazado";

    public static final String TIPO_UNICO = "unico";
    public static final String TIPO_ABONO = "abono";
    public static final String TIPO_SALDO = "saldo";

    /** Clave en la base ("a1"); no se guarda dentro del abono. */
    @Exclude
    public String clave;

    public int numero;
    public String tipo;
    public long monto;
    public String estado;

    // Según el estado
    public long pagadoEn;
    public String medio;
    /** Referencia del comprobante, por ejemplo "MA-NOV26-A1-365". */
    public String comprobante;
    public String wompiTransaccionId;
    public long rechazadoEn;
    public String motivo;
    /** Cuándo miarriendo transfirió este pago al propietario; 0 si todavía no. */
    public long transferidoEn;
    public Intento intento;

    /** Datos del cobro en curso (estado "en_proceso"). */
    @IgnoreExtraProperties
    public static class Intento {
        public String referencia;
        /** Últimos 4 dígitos del celular Nequi al que se envió el cobro. */
        public String celularFin;
        public String transaccionId;
        public long iniciadoEn;

        /** Constructor vacío requerido por Firebase. */
        public Intento() {
        }
    }

    /** Constructor vacío requerido por Firebase. */
    public Abono() {
    }

    @Exclude
    public boolean aprobado() {
        return APROBADO.equals(estado);
    }

    @Exclude
    public boolean enProceso() {
        return EN_PROCESO.equals(estado);
    }
}
