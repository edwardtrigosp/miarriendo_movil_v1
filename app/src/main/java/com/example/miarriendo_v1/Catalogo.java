package com.example.miarriendo_v1;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.List;

/** Propiedades disponibles para los inquilinos, de la más reciente a la más antigua. */
final class Catalogo {

    interface Resultado {
        void listo(List<Propiedad> disponibles);

        void error(Exception e);
    }

    private Catalogo() {
    }

    static void cargar(Resultado resultado) {
        FirebaseDatabase.getInstance().getReference("propiedades").get()
                .addOnSuccessListener(snapshot -> resultado.listo(disponibles(snapshot)))
                .addOnFailureListener(resultado::error);
    }

    private static List<Propiedad> disponibles(DataSnapshot snapshot) {
        List<Propiedad> lista = new ArrayList<>();
        for (DataSnapshot hijo : snapshot.getChildren()) {
            Propiedad p = hijo.getValue(Propiedad.class);
            if (p != null && p.disponible()) {
                if (p.id == null) {
                    p.id = hijo.getKey();
                }
                lista.add(p);
            }
        }
        lista.sort((a, b) -> Long.compare(b.creadoEn, a.creadoEn));
        return lista;
    }
}
