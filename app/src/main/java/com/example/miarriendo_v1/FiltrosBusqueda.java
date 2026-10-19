package com.example.miarriendo_v1;

import android.content.Context;

import java.io.Serializable;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Criterios de Inquilino / Filtros de búsqueda. Un campo vacío o en 0 no filtra. */
public class FiltrosBusqueda implements Serializable {

    /** Ciudad o barrio (coincidencia parcial, sin tildes ni mayúsculas). */
    public String ciudad = "";
    /** "Casa", "Apartamento"… como se guarda en Propiedad.tipo. */
    public String tipo = "";
    public long precioMin;
    public long precioMax;
    /** 0 = todas, 1, 2 o 3 (= 3 o más). */
    public int habitaciones;

    boolean cumple(Propiedad p) {
        if (!p.disponible()) {
            return false;
        }
        if (!ciudad.trim().isEmpty()) {
            String q = normalizar(ciudad);
            if (!normalizar(p.ciudad).contains(q) && !normalizar(p.barrio).contains(q)
                    && !normalizar(p.direccion).contains(q)) {
                return false;
            }
        }
        if (!tipo.isEmpty() && !tipo.equalsIgnoreCase(p.tipo)) {
            return false;
        }
        if (precioMin > 0 && p.precio < precioMin) {
            return false;
        }
        if (precioMax > 0 && p.precio > precioMax) {
            return false;
        }
        if (habitaciones >= 3) {
            return p.habitaciones >= 3;
        }
        return habitaciones == 0 || p.habitaciones == habitaciones;
    }

    List<Propiedad> aplicar(List<Propiedad> propiedades) {
        List<Propiedad> resultado = new ArrayList<>();
        for (Propiedad p : propiedades) {
            if (cumple(p)) {
                resultado.add(p);
            }
        }
        return resultado;
    }

    /** Un filtro aplicado: su texto de chip y cómo quitarlo de estos criterios. */
    static final class Activo {
        final String etiqueta;
        final Runnable quitar;

        Activo(String etiqueta, Runnable quitar) {
            this.etiqueta = etiqueta;
            this.quitar = quitar;
        }
    }

    /** Filtros aplicados, sin la ciudad (ya se ve en el buscador). El rango de precio cuenta como uno. */
    List<Activo> activos(Context context) {
        List<Activo> activos = new ArrayList<>();
        if (!tipo.isEmpty()) {
            activos.add(new Activo(tipo, () -> tipo = ""));
        }
        if (habitaciones > 0) {
            String n = habitaciones >= 3 ? context.getString(R.string.filters_bedrooms_three) : String.valueOf(habitaciones);
            activos.add(new Activo(context.getString(R.string.filter_chip_bedrooms, n), () -> habitaciones = 0));
        }
        Runnable quitarPrecio = () -> {
            precioMin = 0;
            precioMax = 0;
        };
        if (precioMin > 0 && precioMax > 0) {
            activos.add(new Activo(context.getString(R.string.filter_chip_price_range,
                    Propiedad.formatearMiles(precioMin), Propiedad.formatearMiles(precioMax)), quitarPrecio));
        } else if (precioMin > 0) {
            activos.add(new Activo(context.getString(R.string.filter_chip_price_from, Propiedad.formatearMiles(precioMin)), quitarPrecio));
        } else if (precioMax > 0) {
            activos.add(new Activo(context.getString(R.string.filter_chip_price_to, Propiedad.formatearMiles(precioMax)), quitarPrecio));
        }
        return activos;
    }

    /** Textos de los chips de "Filtros aplicados" (la ciudad ya se ve en el buscador). */
    List<String> etiquetas(Context context) {
        List<String> etiquetas = new ArrayList<>();
        for (Activo a : activos(context)) {
            etiquetas.add(a.etiqueta);
        }
        return etiquetas;
    }

    /** Quita todos los filtros, incluida la ciudad o barrio. */
    void quitarTodos() {
        ciudad = "";
        tipo = "";
        precioMin = 0;
        precioMax = 0;
        habitaciones = 0;
    }

    /** "Valledupar, Cesar" → "valledupar, cesar"; "Habitación" → "habitacion". */
    static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String sinTildes = Normalizer.normalize(texto.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT);
    }
}
