package com.example.miarriendo_v1;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

/**
 * Figma: Inquilino / Elegir forma de pago. Un pago de todo el mes o 3 abonos del mismo mes. La elección
 * se confirma con el primer cobro: hasta entonces se puede cambiar.
 */
public class ElegirFormaPagoActivity extends AppCompatActivity {

    private static final int NUM_ABONOS = 3;

    private String propiedadId, cuotaId;
    private boolean abonos;

    private MaterialButton btnContinue;
    private View opcionUnica, opcionAbonos;
    private ImageView radioUnico, radioAbonos;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Pantallas.edgeToEdge(this);
        setContentView(R.layout.activity_elegir_forma_pago);
        Pantallas.aplicarInsets(findViewById(R.id.main));

        ((TextView) findViewById(R.id.tv_app_bar_title)).setText(R.string.choose_title);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        propiedadId = getIntent().getStringExtra(VistasPago.EXTRA_PROPIEDAD_ID);
        cuotaId = getIntent().getStringExtra(VistasPago.EXTRA_CUOTA_ID);
        if (propiedadId == null || cuotaId == null) {
            finish();
            return;
        }
        btnContinue = findViewById(R.id.btn_continue);
        opcionUnica = findViewById(R.id.option_single);
        opcionAbonos = findViewById(R.id.option_installments);
        radioUnico = findViewById(R.id.iv_radio_single);
        radioAbonos = findViewById(R.id.iv_radio_installments);
        opcionUnica.setOnClickListener(v -> elegir(false));
        opcionAbonos.setOnClickListener(v -> elegir(true));
        btnContinue.setOnClickListener(v -> {
            startActivity(VistasPago.intent(this, PagarNequiActivity.class, propiedadId, cuotaId)
                    .putExtra(VistasPago.EXTRA_MODALIDAD, abonos ? Cuota.MODALIDAD_ABONOS : Cuota.MODALIDAD_UNICO));
            finish();
        });

        RepositorioPagos.cuota(propiedadId, cuotaId).get()
                .addOnSuccessListener(snapshot -> {
                    Cuota cuota = RepositorioPagos.leerCuota(snapshot);
                    if (cuota == null || cuota.pagada()) {
                        finish();
                        return;
                    }
                    mostrar(cuota);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, getString(R.string.pay_load_error, e.getMessage()), Toast.LENGTH_LONG).show();
                    finish();
                });
    }

    private void mostrar(Cuota cuota) {
        View tarjeta = findViewById(R.id.card_period);
        VistasPago.dato(this, VistasPago.filas(tarjeta), getString(R.string.my_rental_period), VistasPago.periodo(this, cuota));

        ((TextView) findViewById(R.id.tv_amount_single)).setText(VistasPago.pesosCop(this, cuota.monto));
        long[] montos = ReglasPago.montosDeAbonos(cuota.monto, NUM_ABONOS);
        ((TextView) findViewById(R.id.tv_amount_installments))
                .setText(getString(R.string.choose_each, VistasPago.pesosCop(this, montos[0])));
        // Si el monto no se divide exacto, el último abono ajusta la diferencia.
        ((TextView) findViewById(R.id.tv_desc_installments))
                .setText(montos[NUM_ABONOS - 1] == montos[0] ? R.string.choose_same_month : R.string.choose_last_adjusts);
        btnContinue.setEnabled(true);
    }

    private void elegir(boolean conAbonos) {
        abonos = conAbonos;
        opcionUnica.setBackgroundResource(conAbonos ? R.drawable.bg_option : R.drawable.bg_option_selected);
        opcionAbonos.setBackgroundResource(conAbonos ? R.drawable.bg_option_selected : R.drawable.bg_option);
        radioUnico.setImageResource(conAbonos ? R.drawable.ic_radio_unselected : R.drawable.ic_radio_selected);
        radioAbonos.setImageResource(conAbonos ? R.drawable.ic_radio_selected : R.drawable.ic_radio_unselected);
    }
}
