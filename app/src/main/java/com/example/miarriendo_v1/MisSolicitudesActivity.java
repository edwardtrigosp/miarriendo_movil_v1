package com.example.miarriendo_v1;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
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

import java.util.ArrayList;
import java.util.List;

/** Figma: Inquilino / Mis solicitudes · Pendiente (315:7097). Se actualiza cuando el propietario responde. */
public class MisSolicitudesActivity extends AppCompatActivity {

    private LinearLayout listRequests;
    private View loading, summary;
    private TextView tvCount, tvPending, tvEmpty;

    private Query consulta;
    private ValueEventListener escucha;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Pantallas.edgeToEdge(this);
        setContentView(R.layout.activity_mis_solicitudes);
        Pantallas.aplicarInsets(findViewById(R.id.main));

        ((TextView) findViewById(R.id.tv_app_bar_title)).setText(R.string.my_requests_title);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        listRequests = findViewById(R.id.list_requests);
        loading = findViewById(R.id.loading);
        summary = findViewById(R.id.summary);
        tvCount = findViewById(R.id.tv_count);
        tvPending = findViewById(R.id.tv_pending);
        tvEmpty = findViewById(R.id.tv_empty);

        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null) {
            finish();
            return;
        }
        consulta = FirebaseDatabase.getInstance().getReference("solicitudes_arriendo")
                .orderByChild("inquilinoId").equalTo(usuario.getUid());
        escucha = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<SolicitudArriendo> solicitudes = new ArrayList<>();
                for (DataSnapshot hijo : snapshot.getChildren()) {
                    SolicitudArriendo s = hijo.getValue(SolicitudArriendo.class);
                    if (s != null) {
                        solicitudes.add(s);
                    }
                }
                solicitudes.sort((a, b) -> Long.compare(b.creadaEn, a.creadaEn));
                mostrar(solicitudes);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                mostrar(new ArrayList<>());
                Toast.makeText(MisSolicitudesActivity.this,
                        getString(R.string.tenant_load_error, error.getMessage()), Toast.LENGTH_LONG).show();
            }
        };
        consulta.addValueEventListener(escucha);
    }

    private void mostrar(List<SolicitudArriendo> solicitudes) {
        loading.setVisibility(View.GONE);
        int pendientes = 0;
        for (SolicitudArriendo s : solicitudes) {
            if (s.pendiente()) pendientes++;
        }
        summary.setVisibility(solicitudes.isEmpty() ? View.GONE : View.VISIBLE);
        tvEmpty.setVisibility(solicitudes.isEmpty() ? View.VISIBLE : View.GONE);
        tvCount.setText(getResources().getQuantityString(R.plurals.my_requests_count, solicitudes.size(), solicitudes.size()));
        tvPending.setText(getResources().getQuantityString(R.plurals.my_requests_pending, pendientes, pendientes));

        listRequests.removeAllViews();
        int margen = getResources().getDimensionPixelSize(R.dimen.spacing_lg);
        int entreTarjetas = getResources().getDimensionPixelSize(R.dimen.spacing_md);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < solicitudes.size(); i++) {
            View tarjeta = inflater.inflate(R.layout.item_solicitud_arriendo, listRequests, false);
            llenar(tarjeta, solicitudes.get(i));
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) tarjeta.getLayoutParams();
            lp.topMargin = i == 0 ? margen : entreTarjetas;
            listRequests.addView(tarjeta, lp);
        }
    }

    private void llenar(View tarjeta, SolicitudArriendo s) {
        ((TextView) tarjeta.findViewById(R.id.tv_date)).setText(Formato.fechaCorta(this, s.creadaEn));
        ((TextView) tarjeta.findViewById(R.id.tv_initials)).setText(Formato.iniciales(s.inquilinoNombre));
        ((TextView) tarjeta.findViewById(R.id.tv_tenant_name)).setText(s.inquilinoNombre);
        ((TextView) tarjeta.findViewById(R.id.tv_message)).setText(s.mensaje);
        ((TextView) tarjeta.findViewById(R.id.tv_property_name)).setText(s.propiedadTitulo);
        ((TextView) tarjeta.findViewById(R.id.tv_property_address)).setText(s.propiedadDireccion);
        ((TextView) tarjeta.findViewById(R.id.tv_price))
                .setText(getString(R.string.owner_price_month, Propiedad.formatearMiles(s.precio)));
        ((TextView) tarjeta.findViewById(R.id.tv_status)).setText(estado(s));

        ImageView foto = tarjeta.findViewById(R.id.iv_property);
        if (s.propiedadId != null) {
            FirebaseDatabase.getInstance().getReference("propiedades").child(s.propiedadId).child("portada").get()
                    .addOnSuccessListener(snap -> {
                        Bitmap portada = FotosCodec.desdeBase64(snap.getValue(String.class));
                        if (portada != null) foto.setImageBitmap(portada);
                    });
        }
        tarjeta.findViewById(R.id.btn_view).setOnClickListener(v ->
                startActivity(new Intent(this, DetalleArriendoActivity.class)
                        .putExtra(DetalleArriendoActivity.EXTRA_PROPIEDAD_ID, s.propiedadId)));
    }

    /** "En espera de la respuesta de Edward." según el estado. */
    private String estado(SolicitudArriendo s) {
        boolean conNombre = s.propietarioNombre != null && !s.propietarioNombre.isEmpty();
        if (SolicitudArriendo.ESTADO_ACEPTADA.equals(s.estado)) {
            return getString(R.string.my_requests_accepted,
                    conNombre ? s.propietarioNombre : getString(R.string.tenant_detail_owner_generic));
        }
        if (SolicitudArriendo.ESTADO_RECHAZADA.equals(s.estado)) {
            return getString(R.string.my_requests_rejected);
        }
        return conNombre ? getString(R.string.my_requests_waiting, s.propietarioNombre)
                : getString(R.string.my_requests_waiting_generic);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (consulta != null && escucha != null) {
            consulta.removeEventListener(escucha);
        }
    }
}
