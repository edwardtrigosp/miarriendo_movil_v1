package com.example.miarriendo_v1;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.List;

/**
 * Módulo / Recaudo (446:3584): el dinero primero. Cuánto pagaron los inquilinos este mes, con barra de progreso y
 * desglose en el lenguaje de Pagos por inmueble. Retenido: Alert y "Registrar cuenta bancaria" junto al monto.
 * Sin propiedades arrendadas no se dibuja.
 */
final class ModuloRecaudo implements ModuloInicio {

    private View vista;

    @Override
    public View crear(LayoutInflater inflater, ViewGroup padre) {
        vista = inflater.inflate(R.layout.modulo_recaudo, padre, false);
        return vista;
    }

    @Override
    public void iniciar(ContextoInicio ctx) {
        vista.findViewById(R.id.btn_register_account).setOnClickListener(v -> ctx.abrir(CuentaBancariaActivity.class));
        actualizar(ctx);
    }

    @Override
    public void actualizar(ContextoInicio ctx) {
        RecaudoDelMes recaudo = ctx.datos.recaudo(System.currentTimeMillis());
        if (recaudo.vacio()) {
            ctx.ocultar(vista);
            return;
        }
        vista.setVisibility(View.VISIBLE);
        Context c = vista.getContext();
        String mes = VistasPago.nombreMes(c, recaudo.referencia);
        ((TextView) vista.findViewById(R.id.tv_title)).setText(c.getString(R.string.home_collection_title,
                mes.isEmpty() ? Formato.mes(c, recaudo.referencia.vence) : mes));

        RecaudoDelMes.Estado estado = recaudo.estado();
        boolean retenido = estado == RecaudoDelMes.Estado.RETENIDO;
        ((TextView) vista.findViewById(R.id.tv_label)).setText(retenido
                ? R.string.home_collection_label_paid : R.string.home_collection_label_received);
        ((TextView) vista.findViewById(R.id.tv_amount)).setText(VistasPago.pesos(c, recaudo.recibido));
        ((TextView) vista.findViewById(R.id.tv_amount_detail)).setText(c.getResources().getQuantityString(
                R.plurals.home_collection_detail, recaudo.inquilinos,
                VistasPago.pesos(c, recaudo.esperado), recaudo.inquilinosAlDia, recaudo.inquilinos));

        LinearProgressIndicator barra = vista.findViewById(R.id.barra);
        barra.setProgress(recaudo.progreso());
        barra.setIndicatorColor(ContextCompat.getColor(c,
                recaudo.progreso() >= 100 ? R.color.status_on_success : R.color.brand_primary));

        LinearLayout desglose = vista.findViewById(R.id.desglose);
        desglose.removeAllViews();
        fila(desglose, R.color.status_on_success, R.string.home_collection_transferred, recaudo.transferido);
        fila(desglose, R.color.brand_primary, R.string.home_collection_to_transfer, recaudo.porTransferir);
        fila(desglose, R.color.status_on_warning, R.string.home_collection_held, recaudo.retenido);
        fila(desglose, R.color.status_on_error, R.string.home_collection_pending, recaudo.pendiente());
        desglose.setVisibility(desglose.getChildCount() > 0 ? View.VISIBLE : View.GONE);

        View alerta = vista.findViewById(R.id.alert);
        if (retenido) {
            AlertView.mostrar(alerta, AlertView.Tipo.ADVERTENCIA,
                    c.getString(R.string.home_collection_alert_held, VistasPago.pesos(c, recaudo.retenido)));
        } else {
            AlertView.ocultar(alerta);
        }
        vista.findViewById(R.id.btn_register_account).setVisibility(retenido ? View.VISIBLE : View.GONE);

        View.OnClickListener verPagos = v -> verPagos(ctx);
        vista.findViewById(R.id.titulo).setOnClickListener(verPagos);
        vista.findViewById(R.id.btn_payments).setOnClickListener(verPagos);
    }

    /** Una fila de FilaDesglose; las que van en $0 no se dibujan. */
    private static void fila(LinearLayout padre, int color, int etiqueta, long valor) {
        if (valor <= 0) {
            return;
        }
        Context c = padre.getContext();
        View fila = LayoutInflater.from(c).inflate(R.layout.item_fila_desglose, padre, false);
        fila.findViewById(R.id.punto).setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(c, color)));
        ((TextView) fila.findViewById(R.id.tv_label)).setText(etiqueta);
        ((TextView) fila.findViewById(R.id.tv_value)).setText(VistasPago.pesos(c, valor));
        if (padre.getChildCount() > 0) {
            ((LinearLayout.LayoutParams) fila.getLayoutParams()).topMargin =
                    c.getResources().getDimensionPixelSize(R.dimen.spacing_sm);
        }
        padre.addView(fila);
    }

    /** Pagos por inmueble es de una propiedad: con varias arrendadas, primero se elige cuál. */
    private static void verPagos(ContextoInicio ctx) {
        List<Propiedad> arrendadas = new ArrayList<>();
        for (Propiedad p : ctx.datos.propiedades) {
            if (!ctx.datos.cuotasDe(p).isEmpty()) {
                arrendadas.add(p);
            }
        }
        if (arrendadas.isEmpty()) {
            return;
        }
        if (arrendadas.size() == 1) {
            abrirPagos(ctx, arrendadas.get(0));
            return;
        }
        CharSequence[] nombres = new CharSequence[arrendadas.size()];
        for (int i = 0; i < nombres.length; i++) {
            nombres[i] = arrendadas.get(i).nombreParaTarjeta();
        }
        new MaterialAlertDialogBuilder(ctx.activity)
                .setTitle(R.string.home_collection_choose)
                .setItems(nombres, (d, i) -> abrirPagos(ctx, arrendadas.get(i)))
                .show();
    }

    static void abrirPagos(ContextoInicio ctx, Propiedad p) {
        ctx.activity.startActivity(new Intent(ctx.activity, PagosInmuebleActivity.class)
                .putExtra(VistasPago.EXTRA_PROPIEDAD_ID, p.id));
    }

    @Override
    public void detener() {
    }
}
