package com.example.miarriendo_v1;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.List;

/**
 * Figma: Inquilino / Mi arriendo. Muestra la cuota del momento de la propiedad que arrienda el usuario:
 * su estado (por pagar, parcial, pagado o vencido), el aviso que corresponda y el siguiente paso.
 */
public class MiArriendoActivity extends AppCompatActivity {

    /** Se avisa del vencimiento con esta anticipación (días). */
    private static final int DIAS_DE_AVISO = 5;

    private String uid;
    private Propiedad propiedad;

    private View scroll, actions, loading, alert, cardPayment, cardDetail;
    private TextView tvEmpty;
    private MaterialButton btnPrimary, btnSecondary;

    private Query consultaCuotas;
    private ValueEventListener escuchaCuotas;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Pantallas.edgeToEdge(this);
        setContentView(R.layout.activity_mi_arriendo);
        Pantallas.aplicarInsets(findViewById(R.id.main));

        ((TextView) findViewById(R.id.tv_app_bar_title)).setText(R.string.my_rental_title);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        scroll = findViewById(R.id.scroll);
        actions = findViewById(R.id.actions);
        loading = findViewById(R.id.loading);
        alert = findViewById(R.id.alert);
        cardPayment = findViewById(R.id.card_payment);
        cardDetail = findViewById(R.id.card_detail);
        tvEmpty = findViewById(R.id.tv_empty);
        btnPrimary = findViewById(R.id.btn_primary);
        btnSecondary = findViewById(R.id.btn_secondary);

        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null) {
            finish();
            return;
        }
        uid = usuario.getUid();
        buscarPropiedad();
    }

    /** La propiedad que arrienda el usuario, si tiene una. */
    private void buscarPropiedad() {
        FirebaseDatabase.getInstance().getReference("propiedades")
                .orderByChild("inquilinoId").equalTo(uid).get()
                .addOnSuccessListener(snapshot -> {
                    for (DataSnapshot hijo : snapshot.getChildren()) {
                        Propiedad p = hijo.getValue(Propiedad.class);
                        if (p != null) {
                            p.id = hijo.getKey();
                            propiedad = p;
                            break;
                        }
                    }
                    if (propiedad == null) {
                        mostrarVacio(getString(R.string.my_rental_empty));
                        return;
                    }
                    View tarjeta = findViewById(R.id.card_property);
                    TarjetasPropiedad.compacta(this, tarjeta, propiedad);
                    tarjeta.findViewById(R.id.tv_status).setVisibility(View.GONE);
                    escucharCuotas();
                })
                .addOnFailureListener(e -> mostrarVacio(getString(R.string.pay_load_error, e.getMessage())));
    }

    private void escucharCuotas() {
        consultaCuotas = RepositorioPagos.cuotasDelInquilino(propiedad.id, uid);
        escuchaCuotas = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                mostrar(RepositorioPagos.leerCuotas(snapshot));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                mostrarVacio(getString(R.string.pay_load_error, error.getMessage()));
            }
        };
        consultaCuotas.addValueEventListener(escuchaCuotas);
    }

    private void mostrarVacio(String texto) {
        loading.setVisibility(View.GONE);
        scroll.setVisibility(View.GONE);
        actions.setVisibility(View.GONE);
        tvEmpty.setText(texto);
        tvEmpty.setVisibility(View.VISIBLE);
    }

    private void mostrar(List<Cuota> cuotas) {
        long ahora = System.currentTimeMillis();
        Cuota cuota = ReglasPago.cuotaActiva(cuotas, ahora);
        if (cuota == null) {
            mostrarVacio(getString(R.string.my_rental_no_installments));
            return;
        }
        loading.setVisibility(View.GONE);
        tvEmpty.setVisibility(View.GONE);
        scroll.setVisibility(View.VISIBLE);
        actions.setVisibility(View.VISIBLE);

        ReglasPago.EstadoVisible estado = ReglasPago.estadoVisible(cuota, ahora);
        Abono enProceso = ReglasPago.enProceso(cuota);
        boolean conSaldo = cuota.pagado > 0 && !cuota.pagada();

        mostrarAviso(cuota, estado, enProceso, ahora);
        mostrarTarjeta(cuota, estado, conSaldo, ahora);
        mostrarDetalle(cuota, estado);
        mostrarAcciones(cuota, estado, enProceso, conSaldo);
    }

    private void mostrarAviso(Cuota cuota, ReglasPago.EstadoVisible estado, Abono enProceso, long ahora) {
        if (enProceso != null) {
            AlertView.mostrar(alert, AlertView.Tipo.INFO, getString(R.string.my_rental_alert_waiting));
        } else if (estado == ReglasPago.EstadoVisible.VENCIDO) {
            AlertView.mostrar(alert, AlertView.Tipo.ERROR, getString(R.string.my_rental_alert_overdue));
        } else if (estado != ReglasPago.EstadoVisible.PAGADO) {
            int dias = Formato.diasHasta(cuota.vence);
            String mes = VistasPago.nombreMes(this, cuota);
            if (dias == 0) {
                AlertView.mostrar(alert, AlertView.Tipo.ADVERTENCIA, getString(R.string.my_rental_alert_today, mes));
            } else if (dias > 0 && dias <= DIAS_DE_AVISO) {
                AlertView.mostrar(alert, AlertView.Tipo.ADVERTENCIA,
                        getResources().getQuantityString(R.plurals.my_rental_alert_days, dias, dias, mes));
            } else {
                AlertView.ocultar(alert);
            }
        } else {
            AlertView.ocultar(alert);
        }
    }

    private void mostrarTarjeta(Cuota cuota, ReglasPago.EstadoVisible estado, boolean conSaldo, long ahora) {
        boolean muestraSaldo = conSaldo;
        VistasPago.tarjetaPago(cardPayment,
                getString(muestraSaldo ? R.string.pay_label_balance : R.string.pay_label_monthly),
                VistasPago.pesos(this, muestraSaldo ? ReglasPago.saldo(cuota) : cuota.monto),
                StatusChipView.Tipo.ADVERTENCIA, "");
        VistasPago.chipDeCuota(this, cardPayment.findViewById(R.id.tv_pc_status), cuota, ahora);

        cardPayment.setOnClickListener(null);
        cardPayment.setClickable(false);
    }

    private void mostrarDetalle(Cuota cuota, ReglasPago.EstadoVisible estado) {
        android.view.ViewGroup filas = VistasPago.filas(cardDetail);
        filas.removeAllViews();
        String periodo = VistasPago.periodo(this, cuota);
        String vence = Formato.diaMes(this, cuota.vence);
        switch (estado) {
            case PAGADO:
                Abono ultimo = ReglasPago.ultimoAprobado(cuota);
                VistasPago.dato(this, filas, getString(R.string.my_rental_period), periodo);
                VistasPago.dato(this, filas, getString(R.string.my_rental_paid_on),
                        Formato.diaMes(this, ultimo == null || ultimo.pagadoEn == 0 ? cuota.vence : ultimo.pagadoEn));
                break;
            case PARCIAL:
                VistasPago.dato(this, filas, getString(R.string.my_rental_paid), VistasPago.pesosCop(this, cuota.pagado));
                VistasPago.dato(this, filas, getString(R.string.my_rental_due), vence);
                break;
            case VENCIDO:
                VistasPago.dato(this, filas, getString(R.string.my_rental_period), periodo);
                if (cuota.pagado > 0) {
                    VistasPago.dato(this, filas, getString(R.string.my_rental_paid), VistasPago.pesosCop(this, cuota.pagado));
                }
                VistasPago.dato(this, filas, getString(R.string.my_rental_overdue_on), vence);
                break;
            default:
                VistasPago.dato(this, filas, getString(R.string.my_rental_period), periodo);
                VistasPago.dato(this, filas, getString(R.string.my_rental_due), vence);
        }
    }

    private void mostrarAcciones(Cuota cuota, ReglasPago.EstadoVisible estado, Abono enProceso, boolean conSaldo) {
        btnSecondary.setVisibility(View.GONE);
        if (enProceso != null) {
            // Un cobro esperando la aprobación en Nequi: no se puede iniciar otro.
            btnPrimary.setText(R.string.my_rental_see_waiting);
            btnPrimary.setOnClickListener(v -> startActivity(
                    VistasPago.intent(this, ConfirmarNequiActivity.class, propiedad.id, cuota.id)
                            .putExtra(VistasPago.EXTRA_ABONO, enProceso.numero)
                            .putExtra(VistasPago.EXTRA_CELULAR_FIN, enProceso.intento == null ? null : enProceso.intento.celularFin)));
            return;
        }
        if (estado == ReglasPago.EstadoVisible.PAGADO) {
            btnPrimary.setText(R.string.my_rental_see_receipt);
            btnPrimary.setOnClickListener(v -> VistasPago.abrirCuota(this, propiedad.id, cuota));
            btnSecondary.setVisibility(View.VISIBLE);
            btnSecondary.setText(R.string.my_rental_see_history);
            btnSecondary.setOnClickListener(v -> startActivity(
                    new Intent(this, HistorialInmuebleActivity.class).putExtra(VistasPago.EXTRA_PROPIEDAD_ID, propiedad.id)));
            return;
        }
        // Se paga lo que falta del mes: todo, o el saldo de un mes viejo que quedó a medias en abonos.
        btnPrimary.setText(conSaldo ? R.string.my_rental_pay_balance
                : estado == ReglasPago.EstadoVisible.VENCIDO ? R.string.my_rental_pay_now : R.string.my_rental_pay);
        btnPrimary.setOnClickListener(v -> startActivity(
                VistasPago.intent(this, PagarNequiActivity.class, propiedad.id, cuota.id)));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (consultaCuotas != null && escuchaCuotas != null) {
            consultaCuotas.removeEventListener(escuchaCuotas);
        }
    }
}
