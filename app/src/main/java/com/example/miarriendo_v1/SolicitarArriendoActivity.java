package com.example.miarriendo_v1;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

/**
 * Figma: Inquilino / Solicitar arriendo (315:6896). Paso 1 "Tus datos": mensaje al propietario.
 * Paso 2 "Revisar": el mensaje tal como se enviará y el botón "Enviar solicitud".
 */
public class SolicitarArriendoActivity extends AppCompatActivity {

    private Propiedad propiedad;
    private String propietarioNombre;
    private String inquilinoNombre;
    private boolean revisando;

    private TextInputLayout tilMessage;
    private TextInputEditText etMessage;
    private TextView tvStepReview, tvReviewMessage, tvPrivacy;
    private View stepReviewBar, cardReview;
    private MaterialButton btnNext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Pantallas.edgeToEdge(this);
        setContentView(R.layout.activity_solicitar_arriendo);
        Pantallas.aplicarInsets(findViewById(R.id.main));

        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        String propiedadId = getIntent().getStringExtra(DetalleArriendoActivity.EXTRA_PROPIEDAD_ID);
        if (usuario == null || propiedadId == null) {
            finish();
            return;
        }

        ((TextView) findViewById(R.id.tv_app_bar_title)).setText(R.string.request_title);
        findViewById(R.id.btn_back).setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        tilMessage = findViewById(R.id.til_message);
        etMessage = findViewById(R.id.et_message);
        tvStepReview = findViewById(R.id.tv_step_review);
        tvReviewMessage = findViewById(R.id.tv_review_message);
        tvPrivacy = findViewById(R.id.tv_privacy);
        stepReviewBar = findViewById(R.id.step_review_bar);
        cardReview = findViewById(R.id.card_review);
        btnNext = findViewById(R.id.btn_next);
        btnNext.setEnabled(false);
        btnNext.setOnClickListener(v -> {
            if (revisando) {
                enviar(usuario);
            } else {
                revisar();
            }
        });
        // En "Revisar", atrás vuelve a "Tus datos" para editar el mensaje.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (revisando) {
                    mostrarPaso(false);
                } else {
                    finish();
                }
            }
        });

        cargarNombre(usuario);
        FirebaseDatabase.getInstance().getReference("propiedades").child(propiedadId).get()
                .addOnSuccessListener(s -> {
                    propiedad = s.getValue(Propiedad.class);
                    if (propiedad == null) {
                        Toast.makeText(this, R.string.detail_not_found, Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    propiedad.id = propiedadId;
                    TarjetasPropiedad.compacta(this, findViewById(R.id.card_property), propiedad);
                    if (TextUtils.isEmpty(etMessage.getText())) {
                        etMessage.setText(getString(R.string.request_message_default, propiedad.tituloPublico()));
                    }
                    cargarPropietario(propiedad.propietarioId);
                    btnNext.setEnabled(true);
                });
    }

    private void cargarNombre(FirebaseUser usuario) {
        inquilinoNombre = usuario.getEmail();
        FirebaseDatabase.getInstance().getReference("usuarios").child(usuario.getUid()).get()
                .addOnSuccessListener(s -> {
                    Usuario u = s.getValue(Usuario.class);
                    if (u != null && u.getNombre() != null && !u.getNombre().trim().isEmpty()) {
                        String apellido = u.getApellido() == null ? "" : " " + u.getApellido().trim();
                        inquilinoNombre = (u.getNombre().trim() + apellido).trim();
                    }
                });
    }

    /** "…compartirás los datos de tu cuenta y tu mensaje con Edward." */
    private void cargarPropietario(String uid) {
        if (uid == null) {
            return;
        }
        FirebaseDatabase.getInstance().getReference("usuarios").child(uid).child("nombre").get()
                .addOnSuccessListener(s -> {
                    String n = s.getValue(String.class);
                    if (n != null && !n.trim().isEmpty()) {
                        propietarioNombre = n.trim().split("\\s+")[0];
                        tvPrivacy.setText(getString(R.string.request_privacy, propietarioNombre));
                    }
                });
    }

    private void revisar() {
        String mensaje = mensaje();
        if (mensaje.isEmpty()) {
            tilMessage.setError(getString(R.string.request_message_required));
            return;
        }
        tilMessage.setError(null);
        tvReviewMessage.setText(mensaje);
        mostrarPaso(true);
    }

    /** Cambia el avance: "2. Revisar" en brand/primary y el segundo tramo lleno. */
    private void mostrarPaso(boolean revisar) {
        revisando = revisar;
        tilMessage.setVisibility(revisar ? View.GONE : View.VISIBLE);
        cardReview.setVisibility(revisar ? View.VISIBLE : View.GONE);
        stepReviewBar.setBackgroundResource(revisar ? R.drawable.bg_step_done : R.drawable.bg_step_pending);
        tvStepReview.setTextColor(ContextCompat.getColor(this, revisar ? R.color.brand_primary : R.color.text_placeholder));
        btnNext.setText(revisar ? R.string.request_send : R.string.request_review);
    }

    private void enviar(FirebaseUser usuario) {
        btnNext.setEnabled(false);
        btnNext.setText(R.string.request_sending);
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("solicitudes_arriendo").push();
        SolicitudArriendo s = new SolicitudArriendo();
        s.id = ref.getKey();
        s.propiedadId = propiedad.id;
        s.propietarioId = propiedad.propietarioId;
        s.propietarioNombre = propietarioNombre;
        s.inquilinoId = usuario.getUid();
        s.inquilinoNombre = inquilinoNombre;
        s.mensaje = mensaje();
        s.estado = SolicitudArriendo.ESTADO_PENDIENTE;
        s.propiedadTitulo = propiedad.tituloPublico();
        s.propiedadDireccion = propiedad.direccionCompleta();
        s.precio = propiedad.precio;
        s.creadaEn = System.currentTimeMillis();
        ref.setValue(s)
                .addOnSuccessListener(v -> {
                    startActivity(new Intent(this, SolicitudEnviadaActivity.class)
                            .putExtra(DetalleArriendoActivity.EXTRA_PROPIEDAD_ID, propiedad.id));
                    finish();
                })
                .addOnFailureListener(e -> {
                    btnNext.setEnabled(true);
                    btnNext.setText(R.string.request_send);
                    Toast.makeText(this, getString(R.string.request_error, e.getMessage()), Toast.LENGTH_LONG).show();
                });
    }

    private String mensaje() {
        return etMessage.getText() == null ? "" : etMessage.getText().toString().trim();
    }
}
