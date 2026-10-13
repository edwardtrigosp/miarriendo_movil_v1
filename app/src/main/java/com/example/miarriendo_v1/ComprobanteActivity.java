package com.example.miarriendo_v1;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.FirebaseDatabase;

/**
 * Figma: Pagos / Comprobante de abono y Comprobante de pago. Lo que quedó registrado de un abono
 * aprobado: importe, concepto, fecha, método y referencia. Lo ven el inquilino y el propietario.
 */
public class ComprobanteActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Pantallas.edgeToEdge(this);
        setContentView(R.layout.activity_comprobante);
        Pantallas.aplicarInsets(findViewById(R.id.main));

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        String propiedadId = getIntent().getStringExtra(VistasPago.EXTRA_PROPIEDAD_ID);
        String cuotaId = getIntent().getStringExtra(VistasPago.EXTRA_CUOTA_ID);
        int numeroAbono = getIntent().getIntExtra(VistasPago.EXTRA_ABONO, 0);
        if (propiedadId == null || cuotaId == null || numeroAbono < 1) {
            finish();
            return;
        }

        FirebaseDatabase.getInstance().getReference("propiedades").child(propiedadId).get()
                .addOnSuccessListener(propiedadSnap -> {
                    Propiedad propiedad = propiedadSnap.getValue(Propiedad.class);
                    RepositorioPagos.cuota(propiedadId, cuotaId).get()
                            .addOnSuccessListener(cuotaSnap -> {
                                Cuota cuota = RepositorioPagos.leerCuota(cuotaSnap);
                                Abono abono = cuota == null ? null : cuota.abonos.get("a" + numeroAbono);
                                if (abono == null || !abono.aprobado()) {
                                    finish();
                                    return;
                                }
                                mostrar(propiedad, cuota, abono);
                            })
                            .addOnFailureListener(this::error);
                })
                .addOnFailureListener(this::error);
    }

    private void error(Exception e) {
        Toast.makeText(this, getString(R.string.pay_load_error, e.getMessage()), Toast.LENGTH_LONG).show();
        finish();
    }

    private void mostrar(Propiedad propiedad, Cuota cuota, Abono abono) {
        findViewById(R.id.loading).setVisibility(View.GONE);
        findViewById(R.id.content).setVisibility(View.VISIBLE);
        ((TextView) findViewById(R.id.tv_app_bar_title)).setText(Abono.TIPO_ABONO.equals(abono.tipo)
                ? R.string.receipt_title_installment : R.string.receipt_title_payment);

        VistasPago.tarjetaPago(findViewById(R.id.card_amount), getString(R.string.receipt_amount),
                VistasPago.pesos(this, abono.monto), StatusChipView.Tipo.EXITO, getString(R.string.pay_status_approved));
        ((TextView) findViewById(R.id.tv_property)).setText(propiedad == null ? "" : propiedad.tituloPublico());

        ViewGroup filas = VistasPago.filas(findViewById(R.id.card_detail));
        filas.removeAllViews();
        if (propiedad != null && propiedad.inquilinoNombre != null && !propiedad.inquilinoNombre.trim().isEmpty()) {
            VistasPago.dato(this, filas, getString(R.string.receipt_tenant), propiedad.inquilinoNombre.trim());
        }
        VistasPago.dato(this, filas, getString(R.string.receipt_period), VistasPago.periodo(this, cuota));
        VistasPago.dato(this, filas, getString(R.string.receipt_concept), VistasPago.conceptoDeAbono(this, cuota, abono));
        VistasPago.dato(this, filas, getString(R.string.receipt_date), VistasPago.fechaYHora(this, abono.pagadoEn));
        if (abono.medio != null && !abono.medio.isEmpty()) {
            VistasPago.dato(this, filas, getString(R.string.receipt_method), getString(R.string.receipt_method_value, abono.medio));
        }
        if (abono.comprobante != null && !abono.comprobante.isEmpty()) {
            VistasPago.dato(this, filas, getString(R.string.receipt_reference), abono.comprobante);
        }
    }
}
