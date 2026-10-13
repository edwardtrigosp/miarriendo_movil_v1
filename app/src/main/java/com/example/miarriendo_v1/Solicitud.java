package com.example.miarriendo_v1;

/**
 * Solicitud (daño o reparación) que reporta el inquilino sobre una propiedad.
 * Se guarda en Realtime Database en "solicitudes/{propiedadId}/{id}".
 */
public class Solicitud {

    public static final String ESTADO_NUEVA = "nueva";
    public static final String ESTADO_EN_PROCESO = "en_proceso";
    public static final String ESTADO_RESUELTA = "resuelta";

    public String titulo;
    public long creadaEn;
    public String estado;

    /** Constructor vacío requerido por Firebase. */
    public Solicitud() {
    }
}
