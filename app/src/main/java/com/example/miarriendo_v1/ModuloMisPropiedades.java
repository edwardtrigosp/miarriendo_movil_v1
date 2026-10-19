package com.example.miarriendo_v1;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Módulo / Mis propiedades (446:3608): OwnerPropertyCard por propiedad, las más recientes primero, y
 * "Agregar propiedad". El chip dice cómo va el pago del mes: Pagado, Vencido, Parcial, Pendiente o Disponible.
 */
final class ModuloMisPropiedades implements ModuloInicio {

    private View vista;

    @Override
    public View crear(LayoutInflater inflater, ViewGroup padre) {
        vista = inflater.inflate(R.layout.modulo_mis_propiedades, padre, false);
        return vista;
    }

    @Override
    public void iniciar(ContextoInicio ctx) {
        vista.findViewById(R.id.btn_add_property).setOnClickListener(v -> ctx.abrir(PublicarPropiedadActivity.class));
        actualizar(ctx);
    }

    @Override
    public void actualizar(ContextoInicio ctx) {
        Context c = vista.getContext();
        long ahora = System.currentTimeMillis();
        ((TextView) vista.findViewById(R.id.tv_count)).setText(String.valueOf(ctx.datos.propiedades.size()));
        LinearLayout lista = vista.findViewById(R.id.list_properties);
        lista.removeAllViews();
        int separacion = c.getResources().getDimensionPixelSize(R.dimen.spacing_sm);
        for (Propiedad p : ctx.datos.propiedades) {
            View tarjeta = TarjetasPropiedad.compacta(c, lista, p, v -> ctx.activity.startActivity(
                    new Intent(ctx.activity, PropiedadDetalleActivity.class)
                            .putExtra(PropiedadDetalleActivity.EXTRA_PROPIEDAD_ID, p.id)));
            // El propietario ve su nombre interno, no el título del anuncio.
            ((TextView) tarjeta.findViewById(R.id.tv_title)).setText(p.nombreParaTarjeta());
            mostrarEstado(tarjeta.findViewById(R.id.tv_status), p, ReglasPago.cuotaActiva(ctx.datos.cuotasDe(p), ahora), ahora);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) tarjeta.getLayoutParams();
            lp.topMargin = lista.getChildCount() == 0 ? 0 : separacion;
            lista.addView(tarjeta, lp);
        }
    }

    /** Con cuotas, el estado de la del momento; sin cuotas, el estado que anota el servidor en la propiedad. */
    private static void mostrarEstado(TextView chip, Propiedad p, Cuota cuota, long ahora) {
        Context c = chip.getContext();
        if (!p.tieneInquilino()) {
            StatusChipView.aplicar(chip, StatusChipView.Tipo.NEUTRAL, c.getString(R.string.owner_status_available));
            return;
        }
        if (cuota == null) {
            if (Propiedad.PAGO_ATRASADO.equals(p.estadoPago)) {
                StatusChipView.aplicar(chip, StatusChipView.Tipo.ERROR, c.getString(R.string.pay_status_overdue));
            } else if (Propiedad.PAGO_PENDIENTE.equals(p.estadoPago)) {
                StatusChipView.aplicar(chip, StatusChipView.Tipo.ADVERTENCIA, c.getString(R.string.owner_status_pending));
            } else {
                StatusChipView.aplicar(chip, StatusChipView.Tipo.PRIMARIO, c.getString(R.string.owner_status_up_to_date));
            }
            return;
        }
        switch (ReglasPago.estadoVisible(cuota, ahora)) {
            case PAGADO:
                StatusChipView.aplicar(chip, StatusChipView.Tipo.EXITO, c.getString(R.string.pay_status_paid));
                break;
            case VENCIDO:
                StatusChipView.aplicar(chip, StatusChipView.Tipo.ERROR, c.getString(R.string.pay_status_overdue));
                break;
            case PARCIAL:
                StatusChipView.aplicar(chip, StatusChipView.Tipo.ADVERTENCIA, c.getString(R.string.home_properties_partial));
                break;
            default:
                StatusChipView.aplicar(chip, StatusChipView.Tipo.ADVERTENCIA, c.getString(R.string.owner_status_pending));
        }
    }

    @Override
    public void detener() {
    }
}
