package com.example.miarriendo_v1;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.FirebaseDatabase;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Figma: Inquilino / Mi contrato (586:3132), desde el atajo "Contrato". Muestra las condiciones que el propietario
 * registró al publicar (duración, canon, día de pago y cláusulas) y las fechas del contrato desde que lo aceptó.
 * "Descargar en PDF" arma un resumen de lo registrado en miarriendo (no el contrato firmado) y lo comparte.
 * El depósito no se muestra: está pendiente la revisión frente a la Ley 820.
 */
public class ContratoActivity extends AppCompatActivity {

    static final String EXTRA_PROPIEDAD_ID = "propiedad_id";
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", new Locale("es", "CO"));

    private Propiedad propiedad;
    private String propietarioNombre;
    /** Las filas de "Condiciones del contrato" (sin el estado), para pintarlas y para el PDF. */
    private final List<String[]> condiciones = new ArrayList<>();
    private String estado;
    private MaterialButton btnPdf;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Pantallas.edgeToEdge(this);
        setContentView(R.layout.activity_contrato);
        Pantallas.aplicarInsets(findViewById(R.id.main));
        ((TextView) findViewById(R.id.tv_app_bar_title)).setText(R.string.contract_title);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        btnPdf = findViewById(R.id.btn_pdf);
        btnPdf.setOnClickListener(v -> compartirPdf());

        String propiedadId = getIntent().getStringExtra(EXTRA_PROPIEDAD_ID);
        if (propiedadId == null) {
            finish();
            return;
        }
        FirebaseDatabase.getInstance().getReference("propiedades").child(propiedadId).get()
                .addOnSuccessListener(s -> {
                    propiedad = s.getValue(Propiedad.class);
                    if (propiedad == null) {
                        Toast.makeText(this, R.string.detail_not_found, Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    propiedad.id = propiedadId;
                    cargarPropietario();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, getString(R.string.tenant_load_error, e.getMessage()), Toast.LENGTH_LONG).show();
                    finish();
                });
    }

    /** El nombre del propietario (usuarios/{uid}/nombre lo puede leer cualquiera con sesión). */
    private void cargarPropietario() {
        if (propiedad.propietarioId == null) {
            mostrar();
            return;
        }
        FirebaseDatabase.getInstance().getReference("usuarios").child(propiedad.propietarioId).child("nombre").get()
                .addOnSuccessListener(s -> {
                    String n = s.getValue(String.class);
                    propietarioNombre = n == null || n.trim().isEmpty() ? null : n.trim();
                    mostrar();
                })
                .addOnFailureListener(e -> mostrar());
    }

    private void mostrar() {
        findViewById(R.id.loading).setVisibility(View.GONE);
        findViewById(R.id.content).setVisibility(View.VISIBLE);
        View tarjeta = findViewById(R.id.card_property);
        TarjetasPropiedad.compacta(this, tarjeta, propiedad);
        // El estado del pago no va aquí: el contrato tiene el suyo en sus condiciones.
        tarjeta.findViewById(R.id.tv_status).setVisibility(View.GONE);

        ContratoArriendo contrato = ContratoArriendo.de(propiedad, ZoneId.systemDefault());
        LocalDate hoy = LocalDate.now();
        boolean vigente = contrato.vigente(hoy);
        boolean porEmpezar = contrato.inicio != null && hoy.isBefore(contrato.inicio);
        estado = getString(vigente ? R.string.contract_status_active
                : porEmpezar ? R.string.contract_status_upcoming : R.string.contract_status_ended);

        condiciones.clear();
        if (propietarioNombre != null) {
            condiciones.add(new String[]{getString(R.string.contract_owner), propietarioNombre});
        }
        if (propiedad.inquilinoNombre != null && !propiedad.inquilinoNombre.trim().isEmpty()) {
            condiciones.add(new String[]{getString(R.string.contract_tenant), propiedad.inquilinoNombre.trim()});
        }
        if (contrato.inicio != null) {
            condiciones.add(new String[]{getString(R.string.contract_start), contrato.inicio.format(FECHA)});
        }
        if (contrato.fin != null) {
            condiciones.add(new String[]{getString(R.string.contract_end), contrato.fin.format(FECHA)});
        }
        if (propiedad.duracionMeses > 0) {
            condiciones.add(new String[]{getString(R.string.contract_duration),
                    getResources().getQuantityString(R.plurals.contract_months, propiedad.duracionMeses, propiedad.duracionMeses)});
        }
        condiciones.add(new String[]{getString(R.string.contract_rent), VistasPago.pesos(this, propiedad.precio)});
        if (propiedad.diaDePago > 0) {
            condiciones.add(new String[]{getString(R.string.contract_pay_day), getString(R.string.contract_pay_day_value, propiedad.diaDePago)});
        }

        ViewGroup filas = VistasPago.filas(findViewById(R.id.card_detail));
        filas.removeAllViews();
        filaEstado(filas, vigente ? StatusChipView.Tipo.EXITO : porEmpezar ? StatusChipView.Tipo.INFO : StatusChipView.Tipo.NEUTRAL);
        for (String[] c : condiciones) {
            VistasPago.dato(this, filas, c[0], c[1]);
        }

        String clausulas = clausulas();
        findViewById(R.id.bloque_clausulas).setVisibility(clausulas == null ? View.GONE : View.VISIBLE);
        ((TextView) findViewById(R.id.tv_clauses)).setText(clausulas);
        ((TextView) findViewById(R.id.tv_note)).setText(propietarioNombre == null ? getString(R.string.contract_note_generic)
                : getString(R.string.contract_note, propietarioNombre.split("\\s+")[0]));
        btnPdf.setEnabled(true);
    }

    /** "Estado ........ [Vigente]": la fila Dato con chip de la Tarjeta de detalle. */
    private void filaEstado(ViewGroup filas, StatusChipView.Tipo tipo) {
        View fila = LayoutInflater.from(this).inflate(R.layout.item_dato, filas, false);
        ((TextView) fila.findViewById(R.id.tv_label)).setText(R.string.contract_status);
        LinearLayout contenedor = (LinearLayout) fila;
        View valor = fila.findViewById(R.id.tv_value);
        valor.setVisibility(View.GONE);
        View espacio = new View(this);
        contenedor.addView(espacio, new LinearLayout.LayoutParams(0, 0, 1));
        TextView chip = (TextView) LayoutInflater.from(this).inflate(R.layout.view_status_chip, contenedor, false);
        StatusChipView.aplicar(chip, tipo, estado);
        contenedor.addView(chip);
        filas.addView(fila);
    }

    private String clausulas() {
        return propiedad.clausulas == null || propiedad.clausulas.trim().isEmpty() ? null : propiedad.clausulas.trim();
    }

    private void compartirPdf() {
        try {
            File archivo = ResumenContratoPdf.crear(this, propiedad, estado, condiciones, clausulas());
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".archivos", archivo);
            Intent enviar = new Intent(Intent.ACTION_SEND)
                    .setType("application/pdf")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.contract_pdf_subject, propiedad.tituloPublico()))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(enviar, getString(R.string.contract_download_pdf)));
        } catch (IOException e) {
            Toast.makeText(this, getString(R.string.contract_pdf_error, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }
}
