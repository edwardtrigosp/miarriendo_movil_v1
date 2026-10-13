package com.example.miarriendo_v1;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Escucha en Firebase las propiedades y solicitudes del usuario y entrega un {@link DatosInicio}
 * cada vez que cambian. Inquilino: por inquilinoId; propietario: por propietarioId.
 * Las consultas ya están permitidas e indexadas en database.rules.json.
 */
final class CargaDatosInicio {

    interface Listo {
        void datos(DatosInicio datos);

        void error(Exception e);
    }

    private final String rol;
    private final Query consultaPropiedades;
    private final Query consultaSolicitudes;
    private ValueEventListener escuchaPropiedades;
    private ValueEventListener escuchaSolicitudes;
    private List<Propiedad> propiedades;
    private List<SolicitudArriendo> solicitudes;

    CargaDatosInicio(String uid, String rol) {
        this.rol = rol;
        String campo = RegisterRoleActivity.ROL_PROPIETARIO.equals(rol) ? "propietarioId" : "inquilinoId";
        FirebaseDatabase db = FirebaseDatabase.getInstance();
        consultaPropiedades = db.getReference("propiedades").orderByChild(campo).equalTo(uid);
        consultaSolicitudes = db.getReference("solicitudes_arriendo").orderByChild(campo).equalTo(uid);
    }

    void iniciar(Listo listo) {
        detener();
        escuchaPropiedades = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Propiedad> lista = new ArrayList<>();
                for (DataSnapshot hijo : snapshot.getChildren()) {
                    Propiedad p = hijo.getValue(Propiedad.class);
                    if (p != null) {
                        p.id = hijo.getKey();
                        lista.add(p);
                    }
                }
                propiedades = lista;
                entregar(listo);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                listo.error(error.toException());
            }
        };
        escuchaSolicitudes = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<SolicitudArriendo> lista = new ArrayList<>();
                for (DataSnapshot hijo : snapshot.getChildren()) {
                    SolicitudArriendo s = hijo.getValue(SolicitudArriendo.class);
                    if (s != null) {
                        s.id = hijo.getKey();
                        lista.add(s);
                    }
                }
                solicitudes = lista;
                entregar(listo);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                listo.error(error.toException());
            }
        };
        consultaPropiedades.addValueEventListener(escuchaPropiedades);
        consultaSolicitudes.addValueEventListener(escuchaSolicitudes);
    }

    /** Solo entrega cuando ya llegaron las dos consultas, para no armar el inicio con datos a medias. */
    private void entregar(Listo listo) {
        if (propiedades != null && solicitudes != null) {
            listo.datos(new DatosInicio(rol, propiedades, solicitudes));
        }
    }

    void detener() {
        if (escuchaPropiedades != null) {
            consultaPropiedades.removeEventListener(escuchaPropiedades);
            escuchaPropiedades = null;
        }
        if (escuchaSolicitudes != null) {
            consultaSolicitudes.removeEventListener(escuchaSolicitudes);
            escuchaSolicitudes = null;
        }
        propiedades = null;
        solicitudes = null;
    }
}
