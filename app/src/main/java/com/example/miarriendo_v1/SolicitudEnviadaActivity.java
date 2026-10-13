package com.example.miarriendo_v1;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.FirebaseDatabase;

/** Figma: Inquilino / Solicitud enviada (315:7044). */
public class SolicitudEnviadaActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Pantallas.edgeToEdge(this);
        setContentView(R.layout.activity_solicitud_enviada);
        Pantallas.aplicarInsets(findViewById(R.id.main));

        ((TextView) findViewById(R.id.tv_app_bar_title)).setText(R.string.sent_title);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_my_requests).setOnClickListener(v -> {
            startActivity(new Intent(this, MisSolicitudesActivity.class));
            finish();
        });
        findViewById(R.id.btn_keep_exploring).setOnClickListener(v -> {
            Intent inicio = new Intent(this, InquilinoInicioActivity.class);
            inicio.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(inicio);
            finish();
        });

        String propiedadId = getIntent().getStringExtra(DetalleArriendoActivity.EXTRA_PROPIEDAD_ID);
        if (propiedadId != null) {
            FirebaseDatabase.getInstance().getReference("propiedades").child(propiedadId).get()
                    .addOnSuccessListener(s -> {
                        Propiedad p = s.getValue(Propiedad.class);
                        if (p != null) {
                            p.id = propiedadId;
                            TarjetasPropiedad.compacta(this, findViewById(R.id.card_property), p);
                        }
                    });
        }
    }
}
