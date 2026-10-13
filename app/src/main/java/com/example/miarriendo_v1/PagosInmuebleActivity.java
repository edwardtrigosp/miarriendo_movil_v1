package com.example.miarriendo_v1;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Figma: Propietario / Pagos por inmueble. El mes del momento del inmueble: si el inquilino pagó y qué pasó
 * con ese dinero (por transferir, transferido o retenido por falta de cuenta). Solo lee: las
 * transferencias las anota miarriendo en cada abono.
 */
public class PagosInmuebleActivity extends AppCompatActivity {

    private String propiedadId;
    private Propiedad propiedad;
    private List<Cuota> cuotas = new ArrayList<>();
    private boolean cuotasListas, cuentaLista;
    private CuentaBancaria cuenta;

    private View loading, content, alert, btnRegister;
    private TextView tvEmpty;

    private DatabaseReference refCuotas, refCuenta;
    private ValueEventListener escuchaCuotas, escuchaCuenta;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Pantallas.edgeToEdge(this);
        setContentView(R.layout.activity_pagos_inmueble);
        Pantallas.aplicarInsets(findViewById(R.id.main));

        ((TextView) findViewById(R.id.tv_app_bar_title)).setText(R.string.owner_pay_title);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        loading = findViewById(R.id.loading);
        content = findViewById(R.id.content);
        alert = findViewById(R.id.alert);
        btnRegister = findViewById(R.id.btn_register_account);
        tvEmpty = findViewById(R.id.tv_empty);
        btnRegister.setOnClickListener(v -> startActivity(new Intent(this, CuentaBancariaActivity.class)));

        propiedadId = getIntent().getStringExtra(VistasPago.EXTRA_PROPIEDAD_ID);
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (propiedadId == null || usuario == null) {
            finish();
            return;
        }
        configurarAcceso(R.id.item_receipt, R.drawable.ic_receipt_long, R.string.owner_pay_see_receipt);
        configurarAcceso(R.id.item_installments, R.drawable.ic_list, R.string.owner_pay_see_installments);
        configurarAcceso(R.id.item_history, R.drawable.ic_receipt_long, R.string.owner_pay_see_history);
        findViewById(R.id.item_history).setOnClickListener(v -> startActivity(
                new Intent(this, HistorialInmuebleActivity.class)
                        .putExtra(VistasPago.EXTRA_PROPIEDAD_ID, propiedadId)
                        .putExtra(VistasPago.EXTRA_PROPIETARIO, true)));

        FirebaseDatabase.getInstance().getReference("propiedades").child(propiedadId).get()
                .addOnSuccessListener(snapshot -> {
                    propiedad = snapshot.getValue(Propiedad.class);
                    if (propiedad == null || !propiedad.tieneInquilino()) {
                        mostrarVacio();
                        return;
                    }
                    ((TextView) findViewById(R.id.tv_context)).setText(getString(R.string.owner_pay_context,
                            propiedad.tituloPublico(), propiedad.inquilinoNombre.trim()));
                    escuchar(usuario.getUid());
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, getString(R.string.pay_load_error, e.getMessage()), Toast.LENGTH_LONG).show();
                    finish();
                });
    }

    private void configurarAcceso(int id, int icono, int texto) {
        View item = findViewById(id);
        ((ImageView) item.findViewById(R.id.iv_icon)).setImageResource(icono);
        ((TextView) item.findViewById(R.id.tv_label)).setText(texto);
    }

    private void escuchar(String uid) {
        refCuotas = RepositorioPagos.cuotas(propiedadId);
        escuchaCuotas = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                cuotas = RepositorioPagos.leerCuotas(snapshot);
                cuotasListas = true;
                mostrar();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(PagosInmuebleActivity.this,
                        getString(R.string.pay_load_error, error.getMessage()), Toast.LENGTH_LONG).show();
                cuotasListas = true;
                mostrar();
            }
        };
        refCuotas.addValueEventListener(escuchaCuotas);

        // La cuenta se escucha en vivo: al volver de registrarla, el aviso desaparece solo.
        refCuenta = FirebaseDatabase.getInstance().getReference("usuarios").child(uid).child("cuenta");
        escuchaCuenta = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                cuenta = snapshot.getValue(CuentaBancaria.class);
                cuentaLista = true;
                mostrar();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                cuenta = null;
                cuentaLista = true;
                mostrar();
            }
        };
        refCuenta.addValueEventListener(escuchaCuenta);
    }

    private void mostrarVacio() {
        loading.setVisibility(View.GONE);
        content.setVisibility(View.GONE);
        tvEmpty.setVisibility(View.VISIBLE);
    }

    private void mostrar() {
        if (!cuotasListas || !cuentaLista) {
            return;
        }
        long ahora = System.currentTimeMillis();
        Cuota cuota = ReglasPago.cuotaActiva(cuotas, ahora);
        if (cuota == null) {
            mostrarVacio();
            return;
        }
        loading.setVisibility(View.GONE);
        tvEmpty.setVisibility(View.GONE);
        content.setVisibility(View.VISIBLE);

        boolean tieneCuenta = cuenta != null && cuenta.completa();
        ReglasPago.EstadoTransferencia transferencia = ReglasPago.estadoTransferencia(cuota, tieneCuenta);
        mostrarTarjeta(cuota, transferencia, ahora);
        mostrarDetalle(cuota, transferencia, ahora);
        mostrarAviso(transferencia);
        mostrarAccesos(cuota);
    }

    private void mostrarTarjeta(Cuota cuota, ReglasPago.EstadoTransferencia transferencia, long ahora) {
        View tarjeta = findViewById(R.id.card_payment);
        switch (transferencia) {
            case TRANSFERIDO:
                VistasPago.tarjetaPago(tarjeta, getString(R.string.owner_pay_label_transferred),
                        VistasPago.pesos(this, cuota.pagado), StatusChipView.Tipo.EXITO, getString(R.string.owner_pay_chip_transferred));
                break;
            case POR_TRANSFERIR:
                VistasPago.tarjetaPago(tarjeta, getString(R.string.owner_pay_label_paid),
                        VistasPago.pesos(this, cuota.pagado), StatusChipView.Tipo.INFO, getString(R.string.owner_pay_chip_transfer));
                break;
            case SIN_CUENTA:
                VistasPago.tarjetaPago(tarjeta, getString(R.string.owner_pay_label_paid),
                        VistasPago.pesos(this, cuota.pagado), StatusChipView.Tipo.ADVERTENCIA, getString(R.string.owner_pay_chip_held));
                break;
            default:
                boolean vencida = ReglasPago.estadoVisible(cuota, ahora) == ReglasPago.EstadoVisible.VENCIDO;
                VistasPago.tarjetaPago(tarjeta, getString(R.string.owner_pay_label_monthly),
                        VistasPago.pesos(this, cuota.monto),
                        vencida ? StatusChipView.Tipo.ERROR : StatusChipView.Tipo.ADVERTENCIA,
                        getString(vencida ? R.string.pay_status_overdue : R.string.owner_pay_chip_unpaid));
        }
    }

    private void mostrarDetalle(Cuota cuota, ReglasPago.EstadoTransferencia transferencia, long ahora) {
        ViewGroup filas = VistasPago.filas(findViewById(R.id.card_detail));
        filas.removeAllViews();
        VistasPago.dato(this, filas, getString(R.string.owner_pay_row_period), VistasPago.periodo(this, cuota));
        if (!cuota.pagada()) {
            if (cuota.pagado > 0) {
                VistasPago.dato(this, filas, getString(R.string.owner_pay_row_pending), VistasPago.pesosCop(this, ReglasPago.saldo(cuota)));
            }
            boolean vencida = ahora > cuota.vence;
            VistasPago.dato(this, filas, getString(vencida ? R.string.owner_pay_row_overdue : R.string.owner_pay_row_due),
                    Formato.diaMes(this, cuota.vence));
        } else if (transferencia != ReglasPago.EstadoTransferencia.TRANSFERIDO) {
            VistasPago.dato(this, filas, getString(R.string.owner_pay_row_pending), VistasPago.pesosCop(this, 0));
        }
        switch (transferencia) {
            case POR_TRANSFERIR:
                VistasPago.dato(this, filas, getString(R.string.owner_pay_row_transfer), getString(R.string.owner_pay_row_transfer_next));
                break;
            case SIN_CUENTA:
                VistasPago.dato(this, filas, getString(R.string.owner_pay_row_transfer), getString(R.string.owner_pay_row_transfer_missing));
                break;
            case TRANSFERIDO:
                VistasPago.dato(this, filas, getString(R.string.owner_pay_row_transferred_on),
                        Formato.diaMes(this, ReglasPago.ultimaTransferencia(cuota)));
                if (cuenta != null && cuenta.banco != null) {
                    VistasPago.dato(this, filas, getString(R.string.owner_pay_row_account),
                            cuenta.banco + " " + CuentaBancaria.enmascararNumero(cuenta.numero).replace("•••• •••• ", "•••• "));
                }
                break;
            default:
                break;
        }
    }

    private void mostrarAviso(ReglasPago.EstadoTransferencia transferencia) {
        btnRegister.setVisibility(transferencia == ReglasPago.EstadoTransferencia.SIN_CUENTA ? View.VISIBLE : View.GONE);
        switch (transferencia) {
            case POR_TRANSFERIR:
                AlertView.mostrar(alert, AlertView.Tipo.INFO, getString(R.string.owner_pay_alert_transfer));
                break;
            case SIN_CUENTA:
                AlertView.mostrar(alert, AlertView.Tipo.ADVERTENCIA, getString(R.string.owner_pay_alert_no_account));
                break;
            default:
                AlertView.ocultar(alert);
        }
    }

    /** Con varios abonos se ve el seguimiento; con un solo pago aprobado, su comprobante. */
    private void mostrarAccesos(Cuota cuota) {
        Abono ultimo = ReglasPago.ultimoAprobado(cuota);
        boolean variosAbonos = cuota.abonos.size() > 1;
        boolean conComprobante = !variosAbonos && ultimo != null;

        View comprobante = findViewById(R.id.item_receipt);
        View abonos = findViewById(R.id.item_installments);
        comprobante.setVisibility(conComprobante ? View.VISIBLE : View.GONE);
        findViewById(R.id.divider_receipt).setVisibility(conComprobante ? View.VISIBLE : View.GONE);
        abonos.setVisibility(variosAbonos ? View.VISIBLE : View.GONE);
        findViewById(R.id.divider_installments).setVisibility(variosAbonos ? View.VISIBLE : View.GONE);

        comprobante.setOnClickListener(v -> startActivity(VistasPago.intent(this, ComprobanteActivity.class, propiedadId, cuota.id)
                .putExtra(VistasPago.EXTRA_ABONO, ultimo == null ? 1 : ultimo.numero)));
        abonos.setOnClickListener(v -> startActivity(
                VistasPago.intent(this, SeguimientoAbonosActivity.class, propiedadId, cuota.id)));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (refCuotas != null && escuchaCuotas != null) {
            refCuotas.removeEventListener(escuchaCuotas);
        }
        if (refCuenta != null && escuchaCuenta != null) {
            refCuenta.removeEventListener(escuchaCuenta);
        }
    }
}
