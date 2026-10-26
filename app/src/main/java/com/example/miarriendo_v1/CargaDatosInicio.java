package com.example.miarriendo_v1;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Escucha en Firebase las propiedades, solicitudes y cuotas del usuario y entrega un {@link DatosInicio}
 * cada vez que cambian. Inquilino: por inquilinoId; propietario: por propietarioId, más su cuenta bancaria y los
 * daños que le reportaron.
 * Las consultas ya están permitidas e indexadas en database.rules.json.
 */
final class CargaDatosInicio {

    interface Listo {
        void datos(DatosInicio datos);

        void error(Exception e);
    }

    private final String uid;
    private final String rol;
    private final boolean propietario;
    private final Query consultaPropiedades;
    private final Query consultaSolicitudes;
    private final DatabaseReference refCuenta;
    private final Query consultaReportes;
    private ValueEventListener escuchaReportes;
    private ValueEventListener escuchaPropiedades;
    private ValueEventListener escuchaSolicitudes;
    private ValueEventListener escuchaCuenta;
    /** Un listener de cuotas por propiedad; se rehacen cuando cambian las propiedades. */
    private final Map<Query, ValueEventListener> escuchasCuotas = new HashMap<>();

    private List<Propiedad> propiedades;
    private List<SolicitudArriendo> solicitudes;
    private Map<String, List<Cuota>> cuotas = new HashMap<>();
    private int cuotasPorLlegar;
    private Boolean tieneCuenta;
    private List<ReporteDano> reportes;

    CargaDatosInicio(String uid, String rol) {
        this.uid = uid;
        this.rol = rol;
        propietario = RegisterRoleActivity.ROL_PROPIETARIO.equals(rol);
        String campo = propietario ? "propietarioId" : "inquilinoId";
        FirebaseDatabase db = FirebaseDatabase.getInstance();
        consultaPropiedades = db.getReference("propiedades").orderByChild(campo).equalTo(uid);
        consultaSolicitudes = db.getReference("solicitudes_arriendo").orderByChild(campo).equalTo(uid);
        refCuenta = db.getReference("usuarios").child(uid).child("cuenta");
        consultaReportes = db.getReference("reportes").orderByChild("propietarioId").equalTo(uid);
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
                // Las más recientes primero (Mis propiedades).
                lista.sort((a, b) -> Long.compare(b.creadoEn, a.creadoEn));
                propiedades = lista;
                escucharCuotas(lista, listo);
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

        if (propietario) {
            // La cuenta se escucha en vivo: al registrarla, el Recaudo deja de mostrar el dinero retenido.
            escuchaCuenta = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    CuentaBancaria cuenta = snapshot.getValue(CuentaBancaria.class);
                    tieneCuenta = cuenta != null && cuenta.completa();
                    entregar(listo);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    tieneCuenta = false;
                    entregar(listo);
                }
            };
            refCuenta.addValueEventListener(escuchaCuenta);
            escuchaReportes = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    List<ReporteDano> lista = new ArrayList<>();
                    for (DataSnapshot hijo : snapshot.getChildren()) {
                        ReporteDano r = hijo.getValue(ReporteDano.class);
                        if (r != null) {
                            r.id = hijo.getKey();
                            lista.add(r);
                        }
                    }
                    reportes = lista;
                    entregar(listo);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    // Sin la regla de "reportes" publicada (o sin red): el inicio sigue sin reportes.
                    reportes = new ArrayList<>();
                    entregar(listo);
                }
            };
            consultaReportes.addValueEventListener(escuchaReportes);
        } else {
            tieneCuenta = false;
            reportes = new ArrayList<>();
        }
    }

    /**
     * Cuotas de cada propiedad arrendada. El inquilino solo puede leer las suyas (consulta por inquilinoId); el
     * propietario lee todas las de su propiedad y se queda con las del inquilino actual.
     */
    private void escucharCuotas(List<Propiedad> lista, Listo listo) {
        quitarEscuchasCuotas();
        cuotas = new HashMap<>();
        List<Propiedad> arrendadas = new ArrayList<>();
        for (Propiedad p : lista) {
            if (p.inquilinoId != null && !p.inquilinoId.isEmpty()) {
                arrendadas.add(p);
            }
        }
        cuotasPorLlegar = arrendadas.size();
        for (Propiedad p : arrendadas) {
            Query consulta = RepositorioPagos.cuotasDelInquilino(p.id, propietario ? p.inquilinoId : uid);
            boolean[] llego = {false};
            ValueEventListener escucha = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    cuotas.put(p.id, RepositorioPagos.leerCuotas(snapshot));
                    marcarLlegada(llego);
                    entregar(listo);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    // Sin permiso o sin red para una propiedad: el inicio sigue con las demás.
                    cuotas.remove(p.id);
                    marcarLlegada(llego);
                    entregar(listo);
                }
            };
            escuchasCuotas.put(consulta, escucha);
            consulta.addValueEventListener(escucha);
        }
    }

    private void marcarLlegada(boolean[] llego) {
        if (!llego[0]) {
            llego[0] = true;
            cuotasPorLlegar--;
        }
    }

    /** Solo entrega cuando ya llegó todo, para no armar el inicio con datos a medias. */
    private void entregar(Listo listo) {
        if (propiedades != null && solicitudes != null && tieneCuenta != null && reportes != null && cuotasPorLlegar == 0) {
            listo.datos(new DatosInicio(rol, propiedades, solicitudes, new HashMap<>(cuotas), tieneCuenta, reportes));
        }
    }

    private void quitarEscuchasCuotas() {
        for (Map.Entry<Query, ValueEventListener> e : escuchasCuotas.entrySet()) {
            e.getKey().removeEventListener(e.getValue());
        }
        escuchasCuotas.clear();
        cuotasPorLlegar = 0;
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
        if (escuchaCuenta != null) {
            refCuenta.removeEventListener(escuchaCuenta);
            escuchaCuenta = null;
        }
        if (escuchaReportes != null) {
            consultaReportes.removeEventListener(escuchaReportes);
            escuchaReportes = null;
        }
        quitarEscuchasCuotas();
        reportes = null;
        propiedades = null;
        solicitudes = null;
        tieneCuenta = null;
        cuotas = new HashMap<>();
    }
}
