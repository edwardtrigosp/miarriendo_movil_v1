package com.example.miarriendo_v1;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONObject;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Propietario / Solicitudes recibidas. Aceptar activa el arriendo desde el servidor (crea el inquilino y
 * las cuotas); rechazar solo cambia el estado de la solicitud, que las reglas de la base permiten.
 */
public class SolicitudesRecibidasActivity extends AppCompatActivity {

    private LinearLayout listRequests;
    private TextView tvEmpty;
    private View loading;

    private Query consulta;
    private ValueEventListener escucha;

    private List<SolicitudArriendo> pendientes = new ArrayList<>();
    /** Solicitudes con una respuesta en camino: sus botones quedan apagados hasta que termine. */
    private final Set<String> respondiendo = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Pantallas.edgeToEdge(this);
        setContentView(R.layout.activity_solicitudes_recibidas);
        Pantallas.aplicarInsets(findViewById(R.id.main));

        ((TextView) findViewById(R.id.tv_app_bar_title)).setText(R.string.received_title);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        listRequests = findViewById(R.id.list_requests);
        tvEmpty = findViewById(R.id.tv_empty);
        loading = findViewById(R.id.loading);

        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null) {
            finish();
            return;
        }
        consulta = FirebaseDatabase.getInstance().getReference("solicitudes_arriendo")
                .orderByChild("propietarioId").equalTo(usuario.getUid());
        escucha = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<SolicitudArriendo> lista = new ArrayList<>();
                for (DataSnapshot hijo : snapshot.getChildren()) {
                    SolicitudArriendo s = hijo.getValue(SolicitudArriendo.class);
                    if (s != null && s.pendiente()) {
                        if (s.id == null) {
                            s.id = hijo.getKey();
                        }
                        lista.add(s);
                    }
                }
                lista.sort((a, b) -> Long.compare(b.creadaEn, a.creadaEn));
                pendientes = lista;
                mostrar();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                pendientes = new ArrayList<>();
                mostrar();
                Toast.makeText(SolicitudesRecibidasActivity.this,
                        getString(R.string.received_load_error, error.getMessage()), Toast.LENGTH_LONG).show();
            }
        };
        consulta.addValueEventListener(escucha);
    }

    private void mostrar() {
        loading.setVisibility(View.GONE);
        tvEmpty.setVisibility(pendientes.isEmpty() ? View.VISIBLE : View.GONE);
        listRequests.removeAllViews();
        int separacion = getResources().getDimensionPixelSize(R.dimen.spacing_md);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < pendientes.size(); i++) {
            View tarjeta = inflater.inflate(R.layout.item_solicitud_recibida, listRequests, false);
            llenar(tarjeta, pendientes.get(i));
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) tarjeta.getLayoutParams();
            lp.topMargin = i == 0 ? 0 : separacion;
            listRequests.addView(tarjeta, lp);
        }
    }

    /** "Visita el miércoles 22 de octubre · 3 personas · sin mascotas"; null en solicitudes de antes. */
    private String visita(SolicitudArriendo s) {
        if (s.visitaEn <= 0) {
            return null;
        }
        String dia = Instant.ofEpochMilli(s.visitaEn).atZone(ZoneId.systemDefault()).toLocalDate()
                .format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", new Locale("es", "CO")));
        String personas = getResources().getQuantityString(R.plurals.request_people_count, Math.max(1, s.personas),
                Math.max(1, s.personas));
        return getString(R.string.request_visit_summary, dia, personas,
                getString(s.mascotas ? R.string.request_with_pets : R.string.request_without_pets));
    }

    private void llenar(View tarjeta, SolicitudArriendo s) {
        ((TextView) tarjeta.findViewById(R.id.tv_date)).setText(Formato.haceCuanto(this, s.creadaEn));
        ((TextView) tarjeta.findViewById(R.id.tv_initials)).setText(Formato.iniciales(s.inquilinoNombre));
        ((TextView) tarjeta.findViewById(R.id.tv_tenant_name)).setText(s.inquilinoNombre);
        TextView mensaje = tarjeta.findViewById(R.id.tv_message);
        mensaje.setText(s.mensaje);
        mensaje.setVisibility(s.mensaje == null || s.mensaje.trim().isEmpty() ? View.GONE : View.VISIBLE);
        String visita = visita(s);
        TextView tvVisita = tarjeta.findViewById(R.id.tv_visit);
        tvVisita.setText(visita);
        tvVisita.setVisibility(visita == null ? View.GONE : View.VISIBLE);
        ((TextView) tarjeta.findViewById(R.id.tv_property_name)).setText(s.propiedadTitulo);
        ((TextView) tarjeta.findViewById(R.id.tv_property_address)).setText(s.propiedadDireccion);
        ((TextView) tarjeta.findViewById(R.id.tv_price))
                .setText(getString(R.string.owner_price_month, Propiedad.formatearMiles(s.precio)));

        boolean libre = !respondiendo.contains(s.id);
        View aceptar = tarjeta.findViewById(R.id.btn_accept);
        View rechazar = tarjeta.findViewById(R.id.btn_reject);
        aceptar.setEnabled(libre);
        rechazar.setEnabled(libre);
        aceptar.setOnClickListener(v -> confirmarAceptar(s));
        rechazar.setOnClickListener(v -> confirmarRechazo(s));
    }

    private String nombreDe(SolicitudArriendo s) {
        return s.inquilinoNombre == null || s.inquilinoNombre.trim().isEmpty() ? getString(R.string.detail_tenant) : s.inquilinoNombre.trim();
    }

    private void confirmarAceptar(SolicitudArriendo s) {
        Dialogo.nuevo(this)
                .titulo(getString(R.string.received_accept_title, nombreDe(s)))
                .mensaje(R.string.received_accept_message)
                .cancelar(R.string.common_cancel)
                .confirmar(R.string.received_accept, Dialogo.Tono.PRINCIPAL, () -> aceptar(s))
                .mostrar();
    }

    private void aceptar(SolicitudArriendo s) {
        respondiendo.add(s.id);
        mostrar();
        ApiBackend.post(this, "/api/solicitudes/" + s.id + "/aceptar", new JSONObject(), new ApiBackend.Respuesta() {
            @Override
            public void ok(JSONObject datos) {
                respondiendo.remove(s.id);
                if (!isDestroyed()) {
                    Toast.makeText(SolicitudesRecibidasActivity.this, R.string.received_accepted_done, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void error(int estado, String mensaje) {
                respondiendo.remove(s.id);
                if (!isDestroyed()) {
                    mostrar();
                    Toast.makeText(SolicitudesRecibidasActivity.this, mensaje, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void confirmarRechazo(SolicitudArriendo s) {
        Dialogo.nuevo(this)
                .titulo(R.string.received_reject_title)
                .mensaje(getString(R.string.received_reject_message, nombreDe(s)))
                .cancelar(R.string.common_cancel)
                .confirmar(R.string.received_reject, Dialogo.Tono.DESTRUCTIVO, () -> rechazar(s))
                .mostrar();
    }

    private void rechazar(SolicitudArriendo s) {
        respondiendo.add(s.id);
        mostrar();
        FirebaseDatabase.getInstance().getReference("solicitudes_arriendo").child(s.id).child("estado")
                .setValue(SolicitudArriendo.ESTADO_RECHAZADA)
                .addOnSuccessListener(v -> {
                    respondiendo.remove(s.id);
                    if (!isDestroyed()) {
                        Toast.makeText(this, R.string.received_rejected_done, Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    respondiendo.remove(s.id);
                    if (!isDestroyed()) {
                        mostrar();
                        Toast.makeText(this, getString(R.string.received_load_error, e.getMessage()), Toast.LENGTH_LONG).show();
                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (consulta != null && escucha != null) {
            consulta.removeEventListener(escucha);
        }
    }
}
