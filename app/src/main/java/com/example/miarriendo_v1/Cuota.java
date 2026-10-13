package com.example.miarriendo_v1;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Arriendo de un mes. Se guarda en "pagos/{propiedadId}/{id}"; la app solo lo lee, el servidor lo crea
 * al aceptar la solicitud y lo actualiza con cada abono (ver backend/acciones.js).
 */
@IgnoreExtraProperties
public class Cuota {

    public static final String PENDIENTE = "pendiente";
    public static final String PARCIAL = "parcial";
    public static final String PAGADO = "pagado";

    public static final String MODALIDAD_UNICO = "unico";
    public static final String MODALIDAD_ABONOS = "abonos";

    /** Clave en la base; no se guarda dentro de la cuota. */
    @Exclude
    public String id;

    public int numero;
    /** Mes de la cuota: "2026-11". */
    public String periodo;
    /** Fecha límite (milisegundos), hasta las 23:59:59 de Bogotá. */
    public long vence;
    public long monto;
    public String inquilinoId;
    /** Suma de los abonos aprobados. */
    public long pagado;
    public String estado;
    /** "unico" o "abonos"; vacío hasta el primer cobro. */
    public String modalidad;
    public Map<String, Abono> abonos = new HashMap<>();

    /** Constructor vacío requerido por Firebase. */
    public Cuota() {
    }

    @Exclude
    public boolean pagada() {
        return PAGADO.equals(estado);
    }
}
