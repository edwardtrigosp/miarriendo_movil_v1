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

    /** Textos de los chips de "Filtros aplicados" (la ciudad ya se ve en el buscador). */
    List<String> etiquetas(Context context) {
        List<String> etiquetas = new ArrayList<>();
        if (!tipo.isEmpty()) {
            etiquetas.add(tipo);
        }
        if (habitaciones > 0) {
            String n = habitaciones >= 3 ? context.getString(R.string.filters_bedrooms_three) : String.valueOf(habitaciones);
            etiquetas.add(context.getString(R.string.filter_chip_bedrooms, n));
        }
        if (precioMin > 0 && precioMax > 0) {
            etiquetas.add(context.getString(R.string.filter_chip_price_range,
                    Propiedad.formatearMiles(precioMin), Propiedad.formatearMiles(precioMax)));
        } else if (precioMin > 0) {
            etiquetas.add(context.getString(R.string.filter_chip_price_from, Propiedad.formatearMiles(precioMin)));
        } else if (precioMax > 0) {
            etiquetas.add(context.getString(R.string.filter_chip_price_to, Propiedad.formatearMiles(precioMax)));
        }
        return etiquetas;
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
