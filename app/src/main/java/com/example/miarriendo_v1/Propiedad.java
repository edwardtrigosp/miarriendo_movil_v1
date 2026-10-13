package com.example.miarriendo_v1;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;

import java.io.Serializable;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Propiedad publicada por un propietario. Se guarda en Realtime Database en "propiedades/{id}".
 * Las fotos completas van aparte en "fotos_propiedades/{id}"; aquí solo queda la portada pequeña.
 */
@IgnoreExtraProperties
public class Propiedad implements Serializable {

    public static final String DISPONIBILIDAD_INMEDIATA = "inmediata";

    public String id;
    public String propietarioId;

    // Paso 1
    public String titulo;
    public String descripcion;
    public String tipo;

    // Paso 2
    public String ciudad;
    public String barrio;
    public String nombreInterno;
    public String direccion;
    public Double lat;
    public Double lng;

    // Paso 3
    public int habitaciones;
    public int banos;
    public Double area;
    public long precio;
    public List<String> comodidades = new ArrayList<>();

    // Paso 4 (se llenan al publicar)
    public String portada;
    public int numFotos;

    // Paso 5
    public int duracionMeses;
    public long deposito;
    /** "inmediata" o una fecha en formato yyyy-MM-dd. */
    public String disponibilidad;
    public String clausulas;
    /** Día del mes (1 a 28) en que vence cada cuota; 0 si no se definió (vence el día en que empieza el arriendo). */
    public int diaDePago;

    // Estado del arriendo
    public static final String PAGO_AL_DIA = "al_dia";
    public static final String PAGO_PENDIENTE = "pendiente";
    public static final String PAGO_ATRASADO = "atrasado";

    /** Uid del inquilino actual; lo pone el servidor al aceptar la solicitud. */
    public String inquilinoId;
    public String inquilinoNombre;
    /** Desde cuándo arrienda el inquilino actual (milisegundos); 0 si no se conoce. */
    public long inquilinoDesde;
    /** "al_dia", "pendiente" o "atrasado". */
    public String estadoPago;

    public long creadoEn;

    /** Constructor vacío requerido por Firebase. */
    public Propiedad() {
    }

    @Exclude
    public String nombreParaTarjeta() {
        return nombreInterno != null && !nombreInterno.isEmpty() ? nombreInterno : titulo;
    }

    /** Título del anuncio que ven los inquilinos; si no hay, el nombre interno. */
    @Exclude
    public String tituloPublico() {
        return titulo != null && !titulo.trim().isEmpty() ? titulo.trim() : nombreParaTarjeta();
    }

    /** Se puede solicitar en arriendo: todavía no tiene inquilino. */
    @Exclude
    public boolean disponible() {
        return !tieneInquilino();
    }

    @Exclude
    public boolean tieneInquilino() {
        return inquilinoNombre != null && !inquilinoNombre.trim().isEmpty();
    }

    @Exclude
    public String direccionCompleta() {
        if (ciudad == null || ciudad.isEmpty()) {
            return direccion;
        }
        return direccion + ", " + ciudad;
    }

    /** 1350000 → "1.350.000" */
    @Exclude
    public static String formatearMiles(long valor) {
        return NumberFormat.getIntegerInstance(new Locale("es", "CO")).format(valor);
    }
}
