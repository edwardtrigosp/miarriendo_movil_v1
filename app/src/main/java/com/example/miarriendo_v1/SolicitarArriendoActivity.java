package com.example.miarriendo_v1;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Figma: Inquilino / Solicitar arriendo, una sola pantalla (547:2421). El inquilino elige el día de la visita,
 * cuántas personas vivirían y si tienen mascotas, y puede dejar un mensaje. Un solo botón: "Enviar solicitud",
 * activo cuando hay un día elegido. Al enviarla vuelve al inicio, donde "Tu solicitud" muestra cómo va.
 */
public class SolicitarArriendoActivity extends AppCompatActivity {

    static final int PERSONAS_MIN = 1;
    static final int PERSONAS_MAX = 10;

    private Propiedad propiedad;
    private String propietarioNombre;
    private String inquilinoNombre;
    private int personas = PERSONAS_MIN;

    private CalendarioVisita calendario;
    private TextView tvPersonas;
    private ChipGroup cgMascotas;
    private TextInputEditText etMessage;
    private TextView tvPrivacy;
    private MaterialButton btnSend;

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
        calendario = findViewById(R.id.calendario);
        tvPersonas = findViewById(R.id.tv_personas);
        cgMascotas = findViewById(R.id.cg_mascotas);
        etMessage = findViewById(R.id.et_message);
        tvPrivacy = findViewById(R.id.tv_privacy);
        btnSend = findViewById(R.id.btn_send);

        findViewById(R.id.btn_menos).setOnClickListener(v -> cambiarPersonas(-1));
        findViewById(R.id.btn_mas).setOnClickListener(v -> cambiarPersonas(1));
        cambiarPersonas(0);
        calendario.alElegir(dia -> actualizarBoton());
        btnSend.setOnClickListener(v -> enviar(usuario));
        actualizarBoton();

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
                    cargarPropietario(propiedad.propietarioId);
                    actualizarBoton();
                });
    }

    private void cambiarPersonas(int delta) {
        personas = Math.max(PERSONAS_MIN, Math.min(PERSONAS_MAX, personas + delta));
        tvPersonas.setText(String.valueOf(personas));
        findViewById(R.id.btn_menos).setEnabled(personas > PERSONAS_MIN);
        findViewById(R.id.btn_menos).setAlpha(personas > PERSONAS_MIN ? 1f : 0.35f);
        findViewById(R.id.btn_mas).setEnabled(personas < PERSONAS_MAX);
        findViewById(R.id.btn_mas).setAlpha(personas < PERSONAS_MAX ? 1f : 0.35f);
    }

    /** "Enviar solicitud" se activa con la propiedad cargada y un día elegido. */
    private void actualizarBoton() {
        btnSend.setEnabled(propiedad != null && calendario.elegido() != null);
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

    /** "Mensaje para Edward (opcional)" y "…compartirás los datos de tu cuenta y de esta solicitud con Edward." */
    private void cargarPropietario(String uid) {
        if (uid == null) {
            return;
        }
        FirebaseDatabase.getInstance().getReference("usuarios").child(uid).child("nombre").get()
                .addOnSuccessListener(s -> {
                    String n = s.getValue(String.class);
                    if (n != null && !n.trim().isEmpty()) {
                        propietarioNombre = n.trim().split("\\s+")[0];
                        ((TextInputLayout) findViewById(R.id.til_message))
                                .setHint(getString(R.string.request_message_optional, propietarioNombre));
                        tvPrivacy.setText(getString(R.string.request_privacy, propietarioNombre));
                    }
                });
    }

    private void enviar(FirebaseUser usuario) {
        LocalDate dia = calendario.elegido();
        if (propiedad == null || dia == null) {
            return;
        }
        btnSend.setEnabled(false);
        btnSend.setText(R.string.request_sending);
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("solicitudes_arriendo").push();
        SolicitudArriendo s = new SolicitudArriendo();
        s.id = ref.getKey();
        s.propiedadId = propiedad.id;
        s.propietarioId = propiedad.propietarioId;
        s.propietarioNombre = propietarioNombre;
        s.inquilinoId = usuario.getUid();
        s.inquilinoNombre = inquilinoNombre;
        s.mensaje = etMessage.getText() == null ? "" : etMessage.getText().toString().trim();
        s.visitaEn = dia.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        s.personas = personas;
        s.mascotas = cgMascotas.getCheckedChipId() == R.id.chip_con_mascotas;
        s.estado = SolicitudArriendo.ESTADO_PENDIENTE;
        s.propiedadTitulo = propiedad.tituloPublico();
        s.propiedadDireccion = propiedad.direccionCompleta();
        s.precio = propiedad.precio;
        s.creadaEn = System.currentTimeMillis();
        ref.setValue(s)
                .addOnSuccessListener(v -> {
                    Toast.makeText(this, propietarioNombre == null ? getString(R.string.request_sent_generic)
                            : getString(R.string.request_sent, propietarioNombre), Toast.LENGTH_LONG).show();
                    // El inicio muestra "Tu solicitud" con el estado en vivo.
                    startActivity(new Intent(this, InicioActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                    finish();
                })
                .addOnFailureListener(e -> {
                    actualizarBoton();
                    btnSend.setText(R.string.request_send);
                    Toast.makeText(this, getString(R.string.request_error, e.getMessage()), Toast.LENGTH_LONG).show();
                });
    }
}
