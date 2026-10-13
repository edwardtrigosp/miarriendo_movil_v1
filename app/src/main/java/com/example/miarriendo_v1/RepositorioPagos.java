package com.example.miarriendo_v1;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Dónde están las cuotas en la base de datos y cómo se leen. */
final class RepositorioPagos {

    private RepositorioPagos() {
    }

    static DatabaseReference cuotas(String propiedadId) {
        return FirebaseDatabase.getInstance().getReference("pagos").child(propiedadId);
    }

    static DatabaseReference cuota(String propiedadId, String cuotaId) {
        return cuotas(propiedadId).child(cuotaId);
    }

    /**
     * Las cuotas de un inquilino en una propiedad. La consulta por inquilinoId es la que permite
     * conservar el historial después de que termine el arriendo (ver database.rules.json).
     */
    static Query cuotasDelInquilino(String propiedadId, String uid) {
        return cuotas(propiedadId).orderByChild("inquilinoId").equalTo(uid);
    }

    /** Cuotas por número de cuota, con su id puesto. */
    static List<Cuota> leerCuotas(DataSnapshot snapshot) {
        List<Cuota> lista = new ArrayList<>();
        if (snapshot != null) {
            for (DataSnapshot hijo : snapshot.getChildren()) {
                Cuota cuota = leerCuota(hijo);
                if (cuota != null) {
                    lista.add(cuota);
                }
            }
        }
        lista.sort(Comparator.comparingInt(c -> c.numero));
        return lista;
    }

    static Cuota leerCuota(DataSnapshot snapshot) {
        Cuota cuota = snapshot == null ? null : snapshot.getValue(Cuota.class);
        if (cuota != null) {
            cuota.id = snapshot.getKey();
        }
        return cuota;
    }
}
