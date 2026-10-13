package com.example.miarriendo_v1;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.List;

/**
 * Figma: Pagos / Seguimiento de 3 abonos. El mes con su avance, cada abono con su estado y el comprobante
 * de los aprobados. Lo ven el inquilino y el propietario del inmueble.
 */
public class SeguimientoAbonosActivity extends AppCompatActivity {

    private String propiedadId, cuotaId;
    private DatabaseReference refCuota;
    private ValueEventListener escucha;
    private View loading, content;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Pantallas.edgeToEdge(this);
        setContentView(R.layout.activity_seguimiento_abonos);
        Pantallas.aplicarInsets(findViewById(R.id.main));

        ((TextView) findViewById(R.id.tv_app_bar_title)).setText(R.string.tracking_title);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        loading = findViewById(R.id.loading);
        content = findViewById(R.id.content);
        propiedadId = getIntent().getStringExtra(VistasPago.EXTRA_PROPIEDAD_ID);
        cuotaId = getIntent().getStringExtra(VistasPago.EXTRA_CUOTA_ID);
        if (propiedadId == null || cuotaId == null) {
            finish();
            return;
        }

        FirebaseDatabase.getInstance().getReference("propiedades").child(propiedadId).get()
                .addOnSuccessListener(snapshot -> {
                    Propiedad p = snapshot.getValue(Propiedad.class);
                    if (p != null) {
                        ((TextView) findViewById(R.id.tv_property)).setText(p.tituloPublico());
                    }
                });

        refCuota = RepositorioPagos.cuota(propiedadId, cuotaId);
        escucha = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Cuota cuota = RepositorioPagos.leerCuota(snapshot);
                if (cuota == null) {
                    finish();
                    return;
                }
                mostrar(cuota);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(SeguimientoAbonosActivity.this,
                        getString(R.string.pay_load_error, error.getMessage()), Toast.LENGTH_LONG).show();
                finish();
            }
        };
        refCuota.addValueEventListener(escucha);
    }

    private void mostrar(Cuota cuota) {
        loading.setVisibility(View.GONE);
        content.setVisibility(View.VISIBLE);
        long ahora = System.currentTimeMillis();
        List<Abono> abonos = ReglasPago.abonos(cuota);

        ((TextView) findViewById(R.id.tv_period)).setText(VistasPago.periodo(this, cuota));
        VistasPago.chipDeCuota(this, findViewById(R.id.tv_status), cuota, ahora);

        ViewGroup filas = findViewById(R.id.rows);
        filas.removeAllViews();
        VistasPago.dato(this, filas, getString(R.string.tracking_total), VistasPago.pesosCop(this, cuota.monto));
        VistasPago.dato(this, filas, getString(R.string.tracking_paid), VistasPago.pesosCop(this, cuota.pagado));
        VistasPago.dato(this, filas, getString(R.string.tracking_balance), VistasPago.pesosCop(this, ReglasPago.saldo(cuota)));

        // Un tramo por abono: lleno si ya está aprobado.
        LinearLayout progreso = findViewById(R.id.progress);
        progreso.removeAllViews();
        int separacion = getResources().getDimensionPixelSize(R.dimen.spacing_xs);
        for (int i = 0; i < abonos.size(); i++) {
            View tramo = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            lp.setMarginStart(i == 0 ? 0 : separacion);
            tramo.setLayoutParams(lp);
            tramo.setBackgroundResource(abonos.get(i).aprobado() ? R.drawable.bg_step_done : R.drawable.bg_step_pending);
            progreso.addView(tramo);
        }

        LinearLayout lista = findViewById(R.id.list_installments);
        lista.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Abono abono : abonos) {
            View tarjeta = inflater.inflate(R.layout.item_abono, lista, false);
            ((TextView) tarjeta.findViewById(R.id.tv_name)).setText(VistasPago.nombreDeAbono(this, abono));
            ((TextView) tarjeta.findViewById(R.id.tv_amount)).setText(VistasPago.pesosCop(this, abono.monto));
            VistasPago.chipDeAbono(this, tarjeta.findViewById(R.id.tv_status), abono);
            View comprobante = tarjeta.findViewById(R.id.tv_receipt);
            if (abono.aprobado()) {
                comprobante.setVisibility(View.VISIBLE);
                comprobante.setOnClickListener(v -> startActivity(
                        VistasPago.intent(this, ComprobanteActivity.class, propiedadId, cuotaId)
                                .putExtra(VistasPago.EXTRA_ABONO, abono.numero)));
            }
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) tarjeta.getLayoutParams();
            lp.topMargin = lista.getChildCount() == 0 ? 0 : getResources().getDimensionPixelSize(R.dimen.spacing_sm);
            lista.addView(tarjeta, lp);
        }

        TextView limite = findViewById(R.id.tv_deadline);
        limite.setVisibility(cuota.pagada() ? View.GONE : View.VISIBLE);
        limite.setText(getString(R.string.tracking_deadline, Formato.diaMes(this, cuota.vence)));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (refCuota != null && escucha != null) {
            refCuota.removeEventListener(escucha);
        }
    }
}
