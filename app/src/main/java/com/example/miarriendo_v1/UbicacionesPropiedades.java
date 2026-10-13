package com.example.miarriendo_v1;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Dónde pintar cada propiedad en el mapa: las coordenadas que el propietario guardó al publicar (ícono
 * my_location) o, si faltan, las que da el geocodificador del teléfono a partir de la dirección, el barrio y la
 * ciudad. Son ubicaciones orientativas (así lo dice el mapa), no exactas.
 */
final class UbicacionesPropiedades {

    interface Resultado {
        /** La posición, o null si no se pudo ubicar la propiedad. Siempre en el hilo principal. */
        void ubicada(@Nullable LatLng posicion);
    }

    // Colombia: evita que "Calle 20 #12-35" se resuelva en otro país.
    private static final double SUR = -4.3, OESTE = -79.1, NORTE = 13.5, ESTE = -66.8;

    // Solo se tocan desde el hilo principal.
    private static final Map<String, LatLng> ENCONTRADAS = new HashMap<>();
    private static final Set<String> SIN_RESULTADO = new HashSet<>();

    private UbicacionesPropiedades() {
    }

    static void resolver(Context context, Propiedad p, Resultado resultado) {
        LatLng guardada = guardada(p);
        if (guardada != null) {
            resultado.ubicada(guardada);
            return;
        }
        probar(context.getApplicationContext(), consultas(p), 0, resultado);
    }

    @Nullable
    private static LatLng guardada(Propiedad p) {
        if (p.lat == null || p.lng == null) {
            return null;
        }
        if (Math.abs(p.lat) < 1e-6 && Math.abs(p.lng) < 1e-6) {
            return null;
        }
        return new LatLng(p.lat, p.lng);
    }

    /** De la búsqueda más precisa a la más general. */
    private static List<String> consultas(Propiedad p) {
        List<String> lista = new ArrayList<>();
        agregar(lista, p.direccion, p.ciudad);
        agregar(lista, p.direccion, p.barrio, p.ciudad);
        agregar(lista, p.barrio, p.ciudad);
        agregar(lista, p.ciudad);
        return lista;
    }

    private static void agregar(List<String> lista, String... partes) {
        StringBuilder consulta = new StringBuilder();
        for (String parte : partes) {
            if (parte != null && !parte.trim().isEmpty()) {
                if (consulta.length() > 0) {
                    consulta.append(", ");
                }
                consulta.append(parte.trim());
            }
        }
        if (consulta.length() == 0) {
            return;
        }
        String texto = consulta.append(", Colombia").toString();
        if (!lista.contains(texto)) {
            lista.add(texto);
        }
    }

    private static void probar(Context context, List<String> consultas, int indice, Resultado resultado) {
        if (indice >= consultas.size()) {
            resultado.ubicada(null);
            return;
        }
        String consulta = consultas.get(indice);
        LatLng conocida = ENCONTRADAS.get(consulta);
        if (conocida != null) {
            resultado.ubicada(conocida);
            return;
        }
        if (SIN_RESULTADO.contains(consulta) || !Geocoder.isPresent()) {
            probar(context, consultas, indice + 1, resultado);
            return;
        }
        Handler principal = new Handler(Looper.getMainLooper());
        new Geocoder(context, new Locale("es", "CO")).getFromLocationName(consulta, 1, SUR, OESTE, NORTE, ESTE,
                new Geocoder.GeocodeListener() {
                    @Override
                    public void onGeocode(List<Address> direcciones) {
                        principal.post(() -> {
                            if (direcciones.isEmpty() || !direcciones.get(0).hasLatitude()
                                    || !direcciones.get(0).hasLongitude()) {
                                SIN_RESULTADO.add(consulta);
                                probar(context, consultas, indice + 1, resultado);
                                return;
                            }
                            Address a = direcciones.get(0);
                            LatLng posicion = new LatLng(a.getLatitude(), a.getLongitude());
                            ENCONTRADAS.put(consulta, posicion);
                            resultado.ubicada(posicion);
                        });
                    }

                    @Override
                    public void onError(String mensaje) {
                        // Puede ser un fallo pasajero de red: no se recuerda, solo se prueba la siguiente.
                        principal.post(() -> probar(context, consultas, indice + 1, resultado));
                    }
                });
    }
}
