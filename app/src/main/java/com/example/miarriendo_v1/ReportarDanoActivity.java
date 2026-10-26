package com.example.miarriendo_v1;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.io.IOException;

/**
 * Figma: Inquilino / Reportar un daño (587:2131), desde el atajo "Reportar". El inquilino elige qué se dañó y
 * qué tan urgente es, cuenta qué pasó y puede adjuntar una foto. Un solo botón, activo con categoría y
 * descripción. El reporte queda en reportes/{id} y el propietario lo ve en Pendientes de su inicio.
 */
public class ReportarDanoActivity extends AppCompatActivity {

    static final String EXTRA_PROPIEDAD_ID = "propiedad_id";
    /** Lado mayor de la foto: basta para ver el daño y deja el reporte en ~150 KB. */
    private static final int LADO_FOTO = 1024;

    private Propiedad propiedad;
    private String propietarioNombre;
    private String inquilinoNombre;
    private String foto;

    private ChipGroup cgCategoria, cgUrgencia;
    private TextInputEditText etDescripcion;
    private ImageView ivFoto;
    private MaterialButton btnSend;

    private final ActivityResultLauncher<PickVisualMediaRequest> elegirFoto =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), this::usarFoto);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Pantallas.edgeToEdge(this);
        setContentView(R.layout.activity_reportar_dano);
        Pantallas.aplicarInsets(findViewById(R.id.main));

        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        String propiedadId = getIntent().getStringExtra(EXTRA_PROPIEDAD_ID);
        if (usuario == null || propiedadId == null) {
            finish();
            return;
        }
        ((TextView) findViewById(R.id.tv_app_bar_title)).setText(R.string.report_title);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        cgCategoria = findViewById(R.id.cg_categoria);
        cgUrgencia = findViewById(R.id.cg_urgencia);
        etDescripcion = findViewById(R.id.et_descripcion);
        ivFoto = findViewById(R.id.iv_foto);
        btnSend = findViewById(R.id.btn_send);

        cgCategoria.setOnCheckedStateChangeListener((g, ids) -> actualizarBoton());
        etDescripcion.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                actualizarBoton();
            }
        });
        ivFoto.setOnClickListener(v -> elegirFoto.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build()));
        findViewById(R.id.btn_quitar_foto).setOnClickListener(v -> usarFoto(null));
        btnSend.setOnClickListener(v -> enviar(usuario));

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

    private void usarFoto(Uri uri) {
        if (uri == null) {
            foto = null;
            ivFoto.setImageResource(R.drawable.ic_add_a_photo);
            ivFoto.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int relleno = Math.round(36 * getResources().getDisplayMetrics().density);
            ivFoto.setPadding(relleno, relleno, relleno, relleno);
            ivFoto.setImageTintList(getColorStateList(R.color.brand_primary));
            findViewById(R.id.btn_quitar_foto).setVisibility(View.GONE);
            return;
        }
        try {
            foto = FotosCodec.aBase64(getContentResolver(), uri, LADO_FOTO);
            ivFoto.setImageTintList(null);
            ivFoto.setPadding(0, 0, 0, 0);
            ivFoto.setScaleType(ImageView.ScaleType.CENTER_CROP);
            ivFoto.setClipToOutline(true);
            ivFoto.setImageBitmap(FotosCodec.desdeBase64(foto));
            findViewById(R.id.btn_quitar_foto).setVisibility(View.VISIBLE);
        } catch (IOException e) {
            Toast.makeText(this, getString(R.string.report_photo_error, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private String descripcion() {
        return etDescripcion.getText() == null ? "" : etDescripcion.getText().toString().trim();
    }

    /** "Enviar reporte" se activa con la propiedad cargada, una categoría y algo escrito. */
    private void actualizarBoton() {
        btnSend.setEnabled(propiedad != null && categoria() != null && !descripcion().isEmpty());
    }

    private String categoria() {
        int id = cgCategoria.getCheckedChipId();
        if (id == R.id.chip_agua) {
            return ReporteDano.CATEGORIA_AGUA;
        } else if (id == R.id.chip_electricidad) {
            return ReporteDano.CATEGORIA_ELECTRICIDAD;
        } else if (id == R.id.chip_puertas) {
            return ReporteDano.CATEGORIA_PUERTAS;
        } else if (id == R.id.chip_electrodomesticos) {
            return ReporteDano.CATEGORIA_ELECTRODOMESTICOS;
        } else if (id == R.id.chip_otro) {
            return ReporteDano.CATEGORIA_OTRO;
        }
        return null;
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

    /** "Cuéntale a Edward qué pasó" y "Edward verá tu reporte en su inicio". */
    private void cargarPropietario(String uid) {
        if (uid == null) {
            return;
        }
        FirebaseDatabase.getInstance().getReference("usuarios").child(uid).child("nombre").get()
                .addOnSuccessListener(s -> {
                    String n = s.getValue(String.class);
                    if (n != null && !n.trim().isEmpty()) {
                        propietarioNombre = n.trim().split("\\s+")[0];
                        ((TextInputLayout) findViewById(R.id.til_descripcion))
                                .setHint(getString(R.string.report_description, propietarioNombre));
                        ((TextView) findViewById(R.id.tv_privacy)).setText(getString(R.string.report_privacy, propietarioNombre));
                    }
                });
    }

    private void enviar(FirebaseUser usuario) {
        if (propiedad == null || categoria() == null || descripcion().isEmpty()) {
            return;
        }
        btnSend.setEnabled(false);
        btnSend.setText(R.string.request_sending);
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("reportes").push();
        ReporteDano r = new ReporteDano();
        r.id = ref.getKey();
        r.propiedadId = propiedad.id;
        r.propiedadTitulo = propiedad.tituloPublico();
        r.propietarioId = propiedad.propietarioId;
        r.inquilinoId = usuario.getUid();
        r.inquilinoNombre = inquilinoNombre;
        r.categoria = categoria();
        r.urgente = cgUrgencia.getCheckedChipId() == R.id.chip_urgente;
        r.descripcion = descripcion();
        r.foto = foto;
        r.estado = ReporteDano.ESTADO_ABIERTO;
        r.creadoEn = System.currentTimeMillis();
        ref.setValue(r)
                .addOnSuccessListener(v -> {
                    Toast.makeText(this, propietarioNombre == null ? getString(R.string.report_sent_generic)
                            : getString(R.string.report_sent, propietarioNombre), Toast.LENGTH_LONG).show();
                    startActivity(new Intent(this, InicioActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                    finish();
                })
                .addOnFailureListener(e -> {
                    btnSend.setText(R.string.report_send);
                    actualizarBoton();
                    Toast.makeText(this, getString(R.string.report_error, e.getMessage()), Toast.LENGTH_LONG).show();
                });
    }
}
