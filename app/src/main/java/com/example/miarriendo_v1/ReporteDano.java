package com.example.miarriendo_v1;

/**
 * Un daño que el inquilino le reporta al propietario (Figma: Inquilino / Reportar un daño, 587:2131).
 * Se guarda en Realtime Database en "reportes/{id}". El inquilino lo crea abierto; el propietario solo puede
 * marcarlo como resuelto (database.rules.json). La foto, opcional, va en base64 como las de los inmuebles.
 */
public class ReporteDano {

    public static final String ESTADO_ABIERTO = "abierto";
    public static final String ESTADO_RESUELTO = "resuelto";

    public static final String CATEGORIA_AGUA = "agua";
    public static final String CATEGORIA_ELECTRICIDAD = "electricidad";
    public static final String CATEGORIA_PUERTAS = "puertas";
    public static final String CATEGORIA_ELECTRODOMESTICOS = "electrodomesticos";
    public static final String CATEGORIA_OTRO = "otro";

    public String id;
    public String propiedadId;
    public String propiedadTitulo;
    public String propietarioId;
    public String inquilinoId;
    public String inquilinoNombre;
    public String categoria;
    public boolean urgente;
    public String descripcion;
    /** JPEG en base64; null si no adjuntó foto. */
    public String foto;
    public String estado;
    public long creadoEn;

    /** Constructor vacío requerido por Firebase. */
    public ReporteDano() {
    }

    boolean abierto() {
        return estado == null || ESTADO_ABIERTO.equals(estado);
    }
}
