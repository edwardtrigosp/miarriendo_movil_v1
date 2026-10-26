package com.example.miarriendo_v1;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import android.os.Handler;
import android.os.Looper;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.Locale;

/**
 * Figma: Inquilino / Arriendo pagado y Pago no completado ("Abono aprobado" solo para meses viejos en abonos). Lo que se muestra sale de lo
 * que quedó guardado del abono, no de lo que se intentó: así el saldo siempre es el real.
 */
public class ResultadoPagoActivity extends AppCompatActivity {

    private String propiedadId, cuotaId;
    private int numeroAbono;

    /** Si el resultado no llega a la base de datos en este tiempo, se vuelve a la espera de Nequi. */
    private static final long ESPERA_MAX_MS = 8000;

    private MaterialButton btnPrimary, btnSecondary;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private DatabaseReference refCuota;
    private ValueEventListener escucha;
    private boolean mostrado;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Pantallas.edgeToEdge(this);
        setContentView(R.layout.activity_resultado_pago);
        Pantallas.aplicarInsets(findViewById(R.id.main));

        findViewById(R.id.btn_back).setOnClickListener(v -> irAMiArriendo());
        // Atrás vuelve a Mi arriendo: la pantalla anterior (esperar a Nequi) ya no tiene sentido.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                irAMiArriendo();
            }
        });
        propiedadId = getIntent().getStringExtra(VistasPago.EXTRA_PROPIEDAD_ID);
        cuotaId = getIntent().getStringExtra(VistasPago.EXTRA_CUOTA_ID);
        numeroAbono = getIntent().getIntExtra(VistasPago.EXTRA_ABONO, 0);
        if (propiedadId == null || cuotaId == null || numeroAbono < 1) {
            finish();
            return;
        }
        btnPrimary = findViewById(R.id.btn_primary);
        btnSecondary = findViewById(R.id.btn_secondary);

        // Un get() puede devolver la copia local aún sin el resultado (el servidor ya lo sabe, la app todavía no):
        // se escucha la cuota hasta que traiga el abono resuelto.
        refCuota = RepositorioPagos.cuota(propiedadId, cuotaId);
        escucha = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Cuota cuota = RepositorioPagos.leerCuota(snapshot);
                Abono abono = cuota == null ? null : cuota.abonos.get("a" + numeroAbono);
                if (mostrado || abono == null || !(abono.aprobado() || Abono.RECHAZADO.equals(abono.estado))) {
                    return;
                }
                mostrado = true;
                handler.removeCallbacksAndMessages(null);
                abono.clave = "a" + numeroAbono;
                if (abono.aprobado()) {
                    mostrarAprobado(cuota, abono);
                } else {
                    mostrarRechazado(cuota, abono);
                }
                findViewById(R.id.content).setVisibility(View.VISIBLE);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ResultadoPagoActivity.this,
                        getString(R.string.pay_load_error, error.getMessage()), Toast.LENGTH_LONG).show();
                finish();
            }
        };
        refCuota.addValueEventListener(escucha);
        handler.postDelayed(() -> {
            if (!mostrado) {
                // Todavía sin resultado: se vuelve a esperar la aprobación.
                startActivity(VistasPago.intent(this, ConfirmarNequiActivity.class, propiedadId, cuotaId)
                        .putExtra(VistasPago.EXTRA_ABONO, numeroAbono));
                finish();
            }
        }, ESPERA_MAX_MS);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (refCuota != null && escucha != null) {
            refCuota.removeEventListener(escucha);
        }
    }

    private void emblema(int fondo, int icono, int color) {
        ImageView emblema = findViewById(R.id.iv_emblem);
        emblema.setBackgroundResource(fondo);
        emblema.setImageResource(icono);
        ImageViewCompat.setImageTintList(emblema,
                android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, color)));
    }

    private void mostrarAprobado(Cuota cuota, Abono abono) {
        emblema(R.drawable.bg_circle_success, R.drawable.ic_check, R.color.status_on_success);
        String periodo = VistasPago.periodo(this, cuota);
        TextView texto = findViewById(R.id.tv_text);
        TextView chip = findViewById(R.id.tv_status);
        View detalle = findViewById(R.id.card_detail);
        TextView tituloDetalle = detalle.findViewById(R.id.tv_card_title);
        ViewGroup filas = VistasPago.filas(detalle);
        filas.removeAllViews();

        if (cuota.pagada()) {
            ((TextView) findViewById(R.id.tv_app_bar_title)).setText(R.string.result_paid_title);
            texto.setText(R.string.result_paid_single);
            StatusChipView.aplicar(chip, StatusChipView.Tipo.EXITO, getString(R.string.pay_status_paid));
            tituloDetalle.setVisibility(View.VISIBLE);
            tituloDetalle.setText(periodo);
            VistasPago.dato(this, filas, getString(R.string.result_paid_total), VistasPago.pesosCop(this, cuota.pagado));
            VistasPago.dato(this, filas, getString(R.string.result_balance), VistasPago.pesosCop(this, ReglasPago.saldo(cuota)));
            btnPrimary.setText(R.string.result_see_rental);
            btnPrimary.setOnClickListener(v -> irAMiArriendo());
            btnSecondary.setVisibility(View.VISIBLE);
            btnSecondary.setText(R.string.my_rental_see_receipt);
            btnSecondary.setOnClickListener(v -> startActivity(
                    VistasPago.intent(this, ComprobanteActivity.class, propiedadId, cuotaId)
                            .putExtra(VistasPago.EXTRA_ABONO, abono.numero)));
        } else {
            ((TextView) findViewById(R.id.tv_app_bar_title)).setText(R.string.result_installment_title);
            texto.setText(getString(R.string.result_installment_text, VistasPago.pesos(this, abono.monto),
                    periodo.toLowerCase(new Locale("es", "CO"))));
            VistasPago.chipDeCuota(this, chip, cuota, System.currentTimeMillis());
            VistasPago.dato(this, filas, getString(R.string.result_balance_left), VistasPago.pesosCop(this, ReglasPago.saldo(cuota)));
            btnPrimary.setText(R.string.my_rental_pay_balance);
            btnPrimary.setOnClickListener(v -> {
                startActivity(VistasPago.intent(this, PagarNequiActivity.class, propiedadId, cuotaId));
                finish();
            });
            btnSecondary.setVisibility(View.VISIBLE);
            btnSecondary.setText(R.string.result_see_rental);
            btnSecondary.setOnClickListener(v -> irAMiArriendo());
        }
    }

    private void mostrarRechazado(Cuota cuota, Abono abono) {
        emblema(R.drawable.bg_circle_error, R.drawable.ic_cancel, R.color.status_on_error);
        ((TextView) findViewById(R.id.tv_app_bar_title)).setText(R.string.result_failed_title);
        findViewById(R.id.tv_status).setVisibility(View.GONE);
        findViewById(R.id.card_detail).setVisibility(View.GONE);

        String monto = VistasPago.pesos(this, abono.monto);
        ((TextView) findViewById(R.id.tv_text)).setText(Abono.TIPO_ABONO.equals(abono.tipo)
                ? getString(R.string.result_failed_installment, abono.numero, monto)
                : getString(R.string.result_failed_single, monto));
        TextView motivo = findViewById(R.id.tv_reason);
        if (abono.motivo != null && !abono.motivo.isEmpty()) {
            motivo.setVisibility(View.VISIBLE);
            motivo.setText(getString(R.string.result_failed_reason, abono.motivo));
        }

        View tarjeta = findViewById(R.id.card_payment);
        tarjeta.setVisibility(View.VISIBLE);
        VistasPago.tarjetaPago(tarjeta, getString(R.string.result_failed_balance, VistasPago.periodo(this, cuota)),
                VistasPago.pesos(this, ReglasPago.saldo(cuota)), StatusChipView.Tipo.ERROR, "");
        VistasPago.chipDeCuota(this, tarjeta.findViewById(R.id.tv_pc_status), cuota, System.currentTimeMillis());

        btnPrimary.setText(R.string.result_retry);
        btnPrimary.setOnClickListener(v -> {
            startActivity(VistasPago.intent(this, PagarNequiActivity.class, propiedadId, cuotaId));
            finish();
        });
        btnSecondary.setVisibility(View.VISIBLE);
        btnSecondary.setText(R.string.waiting_back);
        btnSecondary.setOnClickListener(v -> irAMiArriendo());
    }

    private void irAMiArriendo() {
        startActivity(new Intent(this, MiArriendoActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }
}
