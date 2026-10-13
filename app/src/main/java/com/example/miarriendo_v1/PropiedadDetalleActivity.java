package com.example.miarriendo_v1;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Figma: Propietario / Propiedad - Detalle (217:766). Datos de la propiedad, inquilino,
 * pagos ("pagos/{id}") y solicitudes ("solicitudes/{id}").
 */
public class PropiedadDetalleActivity extends AppCompatActivity {

    static final String EXTRA_PROPIEDAD_ID = "propiedad_id";

    private static final int MAX_FOTOS = 10;

    private String propiedadId;
    private TextView btnChangePhotos;
    private final ExecutorService trabajo = Executors.newSingleThreadExecutor();

    /** Fotos nuevas desde la galería: reemplazan las de la propiedad (la primera es la portada). */
    private final ActivityResultLauncher<PickVisualMediaRequest> elegirFotos =
            registerForActivityResult(new ActivityResultContracts.PickMultipleVisualMedia(MAX_FOTOS), uris -> {
                if (!uris.isEmpty()) {
                    guardarFotos(uris);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Pantallas.edgeToEdge(this);
        setContentView(R.layout.activity_propiedad_detalle);
        Pantallas.aplicarInsets(findViewById(R.id.main));

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        btnChangePhotos = findViewById(R.id.btn_change_photos);
        btnChangePhotos.setOnClickListener(v -> elegirFotos.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build()));

        propiedadId = getIntent().getStringExtra(EXTRA_PROPIEDAD_ID);
        if (propiedadId == null) {
            noEncontrada();
            return;
        }
        DatabaseReference db = FirebaseDatabase.getInstance().getReference();
        db.child("propiedades").child(propiedadId).get()
                .addOnSuccessListener(snapshot -> {
                    Propiedad p = snapshot.getValue(Propiedad.class);
                    if (p == null) {
                        noEncontrada();
                    } else {
                        mostrarPropiedad(p);
                    }
                })
                .addOnFailureListener(e -> noEncontrada());
        // Foto completa para la portada; mientras llega se ve la portada pequeña.
        db.child("fotos_propiedades").child(propiedadId).child("0").get()
                .addOnSuccessListener(snapshot -> mostrarPortada(snapshot.getValue(String.class)));
        db.child("pagos").child(propiedadId).get()
                .addOnSuccessListener(this::mostrarPagos)
                .addOnFailureListener(e -> mostrarPagos(null));
        db.child("solicitudes").child(propiedadId).get()
                .addOnSuccessListener(this::mostrarSolicitudes)
                .addOnFailureListener(e -> mostrarSolicitudes(null));
    }

    private void noEncontrada() {
        Toast.makeText(this, R.string.detail_not_found, Toast.LENGTH_SHORT).show();
        finish();
    }

    // ---------- Property Info + Tenant Section ----------

    private void mostrarPropiedad(Propiedad p) {
        ((TextView) findViewById(R.id.tv_title)).setText(p.nombreParaTarjeta());
        ((TextView) findViewById(R.id.tv_address)).setText(p.direccionCompleta());
        ((TextView) findViewById(R.id.tv_price))
                .setText(getString(R.string.owner_price_month, Propiedad.formatearMiles(p.precio)));
        if (((ImageView) findViewById(R.id.iv_cover)).getScaleType() != ImageView.ScaleType.CENTER_CROP) {
            mostrarPortada(p.portada);
        }

        boolean tieneInquilino = p.tieneInquilino();
        View verPagos = findViewById(R.id.btn_payments_detail);
        verPagos.setVisibility(tieneInquilino ? View.VISIBLE : View.GONE);
        verPagos.setOnClickListener(v -> startActivity(new android.content.Intent(this, PagosInmuebleActivity.class)
                .putExtra(VistasPago.EXTRA_PROPIEDAD_ID, propiedadId)));
        findViewById(R.id.card_tenant).setVisibility(tieneInquilino ? View.VISIBLE : View.GONE);
        findViewById(R.id.tv_no_tenant).setVisibility(tieneInquilino ? View.GONE : View.VISIBLE);
        if (tieneInquilino) {
            ((TextView) findViewById(R.id.tv_tenant_initials)).setText(Formato.iniciales(p.inquilinoNombre));
            ((TextView) findViewById(R.id.tv_tenant_name)).setText(p.inquilinoNombre.trim());
            TextView desde = findViewById(R.id.tv_tenant_since);
            desde.setVisibility(p.inquilinoDesde > 0 ? View.VISIBLE : View.GONE);
            if (p.inquilinoDesde > 0) {
                desde.setText(getString(R.string.detail_tenant_since, Formato.mesAnio(this, p.inquilinoDesde)));
            }
        }
    }

    private void mostrarPortada(String base64) {
        Bitmap foto = FotosCodec.desdeBase64(base64);
        if (foto != null) {
            ImageView cover = findViewById(R.id.iv_cover);
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            cover.setImageBitmap(foto);
        }
    }

    /** Reduce las fotos en segundo plano y las guarda junto con la portada pequeña. */
    private void guardarFotos(List<Uri> uris) {
        btnChangePhotos.setEnabled(false);
        btnChangePhotos.setText(R.string.detail_saving_photos);
        trabajo.execute(() -> {
            try {
                List<String> fotos = new ArrayList<>();
                for (Uri uri : uris) {
                    fotos.add(FotosCodec.aBase64(getContentResolver(), uri, FotosCodec.LADO_FOTO));
                }
                String portada = FotosCodec.aBase64(getContentResolver(), uris.get(0), FotosCodec.LADO_PORTADA);
                runOnUiThread(() -> subirFotos(fotos, portada));
            } catch (Exception e) {
                runOnUiThread(() -> errorFotos(e.getMessage()));
            }
        });
    }

    private void subirFotos(List<String> fotos, String portada) {
        Map<String, Object> cambios = new HashMap<>();
        cambios.put("fotos_propiedades/" + propiedadId, fotos);
        cambios.put("propiedades/" + propiedadId + "/portada", portada);
        cambios.put("propiedades/" + propiedadId + "/numFotos", fotos.size());
        FirebaseDatabase.getInstance().getReference().updateChildren(cambios)
                .addOnSuccessListener(v -> {
                    mostrarPortada(fotos.get(0));
                    btnChangePhotos.setEnabled(true);
                    btnChangePhotos.setText(R.string.detail_change_photos);
                    Toast.makeText(this, R.string.detail_photos_saved, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> errorFotos(e.getMessage()));
    }

    private void errorFotos(String mensaje) {
        btnChangePhotos.setEnabled(true);
        btnChangePhotos.setText(R.string.detail_change_photos);
        Toast.makeText(this, getString(R.string.detail_photos_error, mensaje), Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        trabajo.shutdown();
    }

    // ---------- Payments Section ----------

    /** Próximo pago = la cuota sin pagar que vence primero (por su saldo); historial = pagadas, de la más reciente. */
    private void mostrarPagos(DataSnapshot snapshot) {
        Cuota proxima = null;
        List<Cuota> pagadas = new ArrayList<>();
        for (Cuota cuota : RepositorioPagos.leerCuotas(snapshot)) {
            if (cuota.pagada()) {
                pagadas.add(cuota);
            } else if (proxima == null || cuota.vence < proxima.vence) {
                proxima = cuota;
            }
        }
        pagadas.sort((a, b) -> Long.compare(ReglasPago.fechaDePago(b), ReglasPago.fechaDePago(a)));

        View tarjeta = findViewById(R.id.card_next_payment);
        tarjeta.setVisibility(proxima != null ? View.VISIBLE : View.GONE);
        if (proxima != null) {
            ((TextView) findViewById(R.id.tv_next_amount))
                    .setText(getString(R.string.detail_money, Propiedad.formatearMiles(ReglasPago.saldo(proxima))));
            ((TextView) findViewById(R.id.tv_next_due))
                    .setText(getString(R.string.detail_due, Formato.diaMes(this, proxima.vence)));
            mostrarVencimiento(findViewById(R.id.tv_next_status), proxima.vence);
        }

        LinearLayout lista = findViewById(R.id.list_payments);
        lista.removeAllViews();
        lista.setVisibility(pagadas.isEmpty() ? View.GONE : View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < pagadas.size(); i++) {
            if (i > 0) {
                lista.addView(divisor());
            }
            Cuota cuota = pagadas.get(i);
            Abono ultimo = ReglasPago.ultimoAprobado(cuota);
            View fila = inflater.inflate(R.layout.item_pago, lista, false);
            ((TextView) fila.findViewById(R.id.tv_month)).setText(Formato.mes(this, cuota.vence));
            String fecha = Formato.diaMesCorto(this, ReglasPago.fechaDePago(cuota));
            ((TextView) fila.findViewById(R.id.tv_detail)).setText(ultimo == null || ultimo.medio == null || ultimo.medio.isEmpty()
                    ? getString(R.string.detail_paid_on_no_method, fecha)
                    : getString(R.string.detail_paid_on, fecha, ultimo.medio));
            ((TextView) fila.findViewById(R.id.tv_amount))
                    .setText(getString(R.string.detail_money, Propiedad.formatearMiles(cuota.monto)));
            fila.setOnClickListener(v -> VistasPago.abrirCuota(this, propiedadId, cuota));
            lista.addView(fila);
        }

        findViewById(R.id.tv_no_payments).setVisibility(proxima == null && pagadas.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** "En 6 días" (Info), "Vence hoy" (Advertencia) o "Atrasado 3 días" (Error). */
    private void mostrarVencimiento(TextView chip, long vence) {
        int dias = Formato.diasHasta(vence);
        if (dias > 0) {
            StatusChipView.aplicar(chip, StatusChipView.Tipo.INFO,
                    getResources().getQuantityString(R.plurals.detail_due_in_days, dias, dias));
        } else if (dias == 0) {
            StatusChipView.aplicar(chip, StatusChipView.Tipo.ADVERTENCIA, getString(R.string.detail_due_today));
        } else {
            StatusChipView.aplicar(chip, StatusChipView.Tipo.ERROR,
                    getResources().getQuantityString(R.plurals.detail_overdue_days, -dias, -dias));
        }
    }

    /** Línea de 1dp border/card entre filas de la PaymentList. */
    private View divisor() {
        View linea = new View(this);
        linea.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                Math.max(1, Math.round(getResources().getDisplayMetrics().density))));
        linea.setBackgroundColor(ContextCompat.getColor(this, R.color.border_card));
        return linea;
    }

    // ---------- Requests Section ----------

    private void mostrarSolicitudes(DataSnapshot snapshot) {
        List<Solicitud> solicitudes = new ArrayList<>();
        if (snapshot != null) {
            for (DataSnapshot hijo : snapshot.getChildren()) {
                Solicitud s = hijo.getValue(Solicitud.class);
                if (s != null) solicitudes.add(s);
            }
        }
        solicitudes.sort((a, b) -> Long.compare(b.creadaEn, a.creadaEn));

        LinearLayout lista = findViewById(R.id.list_requests);
        lista.removeAllViews();
        int separacion = getResources().getDimensionPixelSize(R.dimen.spacing_sm);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Solicitud s : solicitudes) {
            View fila = inflater.inflate(R.layout.item_solicitud, lista, false);
            ((TextView) fila.findViewById(R.id.tv_title)).setText(s.titulo);
            ((TextView) fila.findViewById(R.id.tv_time)).setText(Formato.haceCuanto(this, s.creadaEn));
            mostrarEstado(fila.findViewById(R.id.tv_status), s.estado);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) fila.getLayoutParams();
            lp.topMargin = separacion;
            lista.addView(fila, lp);
        }
        findViewById(R.id.tv_no_requests).setVisibility(solicitudes.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** Nueva (Advertencia), En proceso (Info) o Resuelta (Éxito). */
    private void mostrarEstado(TextView chip, String estado) {
        if (Solicitud.ESTADO_RESUELTA.equals(estado)) {
            StatusChipView.aplicar(chip, StatusChipView.Tipo.EXITO, getString(R.string.request_resolved));
        } else if (Solicitud.ESTADO_EN_PROCESO.equals(estado)) {
            StatusChipView.aplicar(chip, StatusChipView.Tipo.INFO, getString(R.string.request_in_progress));
        } else {
            StatusChipView.aplicar(chip, StatusChipView.Tipo.ADVERTENCIA, getString(R.string.request_new));
        }
    }
}
