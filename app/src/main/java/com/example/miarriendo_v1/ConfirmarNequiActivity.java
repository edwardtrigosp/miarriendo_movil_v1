package com.example.miarriendo_v1;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONObject;

/**
 * Figma: Inquilino / Confirmar en Nequi - Pendiente. Espera a que el inquilino apruebe el cobro en su app
 * Nequi. El resultado llega solo: el servidor lo escribe en la base cuando Wompi avisa, y mientras la
 * pantalla está abierta también se le pregunta al servidor cada pocos segundos por si el aviso se demora.
 */
public class ConfirmarNequiActivity extends AppCompatActivity {

    private static final long INTERVALO_CONSULTA_MS = 8_000;

    private String propiedadId, cuotaId;
    private int numeroAbono;

    private MaterialButton btnCheck;
    private DatabaseReference refCuota;
    private ValueEventListener escucha;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable consultaPeriodica = new Runnable() {
        @Override
        public void run() {
            consultar(false);
            handler.postDelayed(this, INTERVALO_CONSULTA_MS);
        }
    };

    private boolean consultando;
    private boolean terminado;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Pantallas.edgeToEdge(this);
        setContentView(R.layout.activity_confirmar_nequi);
        Pantallas.aplicarInsets(findViewById(R.id.main));

        ((TextView) findViewById(R.id.tv_app_bar_title)).setText(R.string.waiting_title);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        propiedadId = getIntent().getStringExtra(VistasPago.EXTRA_PROPIEDAD_ID);
        cuotaId = getIntent().getStringExtra(VistasPago.EXTRA_CUOTA_ID);
        numeroAbono = getIntent().getIntExtra(VistasPago.EXTRA_ABONO, 0);
        if (propiedadId == null || cuotaId == null || numeroAbono < 1) {
            finish();
            return;
        }

        String celularFin = getIntent().getStringExtra(VistasPago.EXTRA_CELULAR_FIN);
        ((TextView) findViewById(R.id.tv_text)).setText(celularFin == null || celularFin.isEmpty()
                ? getString(R.string.waiting_text_no_phone) : getString(R.string.waiting_text, celularFin));
        AlertView.mostrar(findViewById(R.id.alert), AlertView.Tipo.INFO, getString(R.string.waiting_notice));

        btnCheck = findViewById(R.id.btn_check);
        btnCheck.setOnClickListener(v -> consultar(true));
        findViewById(R.id.btn_back_rental).setOnClickListener(v -> volverAMiArriendo());

        refCuota = RepositorioPagos.cuota(propiedadId, cuotaId);
        escucha = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                alCambiarLaCuota(RepositorioPagos.leerCuota(snapshot));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ConfirmarNequiActivity.this,
                        getString(R.string.pay_load_error, error.getMessage()), Toast.LENGTH_LONG).show();
            }
        };
        refCuota.addValueEventListener(escucha);
    }

    @Override
    protected void onStart() {
        super.onStart();
        handler.postDelayed(consultaPeriodica, INTERVALO_CONSULTA_MS);
    }

    @Override
    protected void onStop() {
        super.onStop();
        handler.removeCallbacks(consultaPeriodica);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(consultaPeriodica);
        if (refCuota != null && escucha != null) {
            refCuota.removeEventListener(escucha);
        }
    }

    private void alCambiarLaCuota(Cuota cuota) {
        if (cuota == null) {
            return;
        }
        Abono abono = cuota.abonos.get("a" + numeroAbono);
        if (abono == null) {
            return;
        }
        abono.clave = "a" + numeroAbono;

        View tarjeta = findViewById(R.id.card_detail);
        TextView titulo = tarjeta.findViewById(R.id.tv_card_title);
        titulo.setVisibility(View.VISIBLE);
        titulo.setText(VistasPago.conceptoDeAbono(this, cuota, abono));
        ViewGroup filas = VistasPago.filas(tarjeta);
        filas.removeAllViews();
        VistasPago.dato(this, filas, getString(R.string.waiting_to_approve), VistasPago.pesosCop(this, abono.monto));
        VistasPago.dato(this, filas, getString(R.string.my_rental_period), VistasPago.periodo(this, cuota));

        if (abono.aprobado() || Abono.RECHAZADO.equals(abono.estado)) {
            irAlResultado();
        }
    }

    /** "Consultar estado": le pregunta al servidor, que a su vez le pregunta a Wompi. */
    private void consultar(boolean manual) {
        if (consultando || terminado) {
            return;
        }
        consultando = true;
        if (manual) {
            btnCheck.setEnabled(false);
            btnCheck.setText(R.string.waiting_checking);
        }
        String ruta = "/api/pagos/" + propiedadId + "/" + cuotaId + "/abonos/" + numeroAbono + "/estado";
        ApiBackend.get(this, ruta, new ApiBackend.Respuesta() {
            @Override
            public void ok(JSONObject datos) {
                consultando = false;
                if (isDestroyed()) {
                    return;
                }
                restaurarBoton();
                String estado = datos.optString("estado");
                if (Abono.APROBADO.equals(estado) || Abono.RECHAZADO.equals(estado)) {
                    irAlResultado();
                } else if (manual) {
                    Toast.makeText(ConfirmarNequiActivity.this, R.string.waiting_still, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void error(int estado, String mensaje) {
                consultando = false;
                if (isDestroyed()) {
                    return;
                }
                restaurarBoton();
                if (manual) {
                    Toast.makeText(ConfirmarNequiActivity.this, mensaje, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void restaurarBoton() {
        btnCheck.setEnabled(true);
        btnCheck.setText(R.string.waiting_check);
    }

    private void irAlResultado() {
        if (terminado) {
            return;
        }
        terminado = true;
        startActivity(VistasPago.intent(this, ResultadoPagoActivity.class, propiedadId, cuotaId)
                .putExtra(VistasPago.EXTRA_ABONO, numeroAbono));
        finish();
    }

    private void volverAMiArriendo() {
        startActivity(new Intent(this, MiArriendoActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }
}
