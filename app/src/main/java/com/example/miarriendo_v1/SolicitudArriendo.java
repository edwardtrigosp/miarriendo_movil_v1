package com.example.miarriendo_v1;

/**
 * Solicitud de arriendo que un inquilino envía al propietario de una propiedad.
 * Se guarda en Realtime Database en "solicitudes_arriendo/{id}". Copia los datos básicos de la
 * propiedad para poder listarla sin leer cada propiedad.
 */
public class SolicitudArriendo {

    public static final String ESTADO_PENDIENTE = "pendiente";
    public static final String ESTADO_ACEPTADA = "aceptada";
    public static final String ESTADO_RECHAZADA = "rechazada";

    public String id;
    public String propiedadId;
    public String propietarioId;
    public String propietarioNombre;
    public String inquilinoId;
    public String inquilinoNombre;
    /** Opcional desde la pantalla única de solicitud. */
    public String mensaje;
    /** Día de la visita que eligió el inquilino (inicio del día, hora del teléfono); 0 en solicitudes viejas. */
    public long visitaEn;
    /** Cuántas personas vivirían; 0 en solicitudes viejas. */
    public int personas;
    public boolean mascotas;
    public String estado;
    public String propiedadTitulo;
    public String propiedadDireccion;
    public long precio;
    public long creadaEn;

    /** Constructor vacío requerido por Firebase. */
    public SolicitudArriendo() {
    }

    boolean pendiente() {
        return estado == null || ESTADO_PENDIENTE.equals(estado);
    }
}
