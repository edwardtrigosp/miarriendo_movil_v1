package com.example.miarriendo_v1;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Módulo / Atajos (446:3523), justo debajo del encabezado. Cada rol tiene sus atajos por función:
 * inquilino con arriendo (Pagar, Mis pagos, Contrato y Reportar) y propietario (Publicar, Solicitudes con badge
 * y Pagos). "Reportes" del propietario no se dibuja: sus reportes ya llegan a Pendientes.
 */
final class ModuloAtajos implements ModuloInicio {

    private View vista;
    private TextView badgeSolicitudes;

    @Override
    public View crear(LayoutInflater inflater, ViewGroup padre) {
        vista = inflater.inflate(R.layout.modulo_atajos, padre, false);
        return vista;
    }

    @Override
    public void iniciar(ContextoInicio ctx) {
        LinearLayout atajos = vista.findViewById(R.id.atajos);
        atajos.removeAllViews();
        badgeSolicitudes = null;
        switch (ctx.datos.perfil()) {
            case INQUILINO_CON_ARRIENDO:
                Propiedad propiedad = ctx.datos.propiedadArrendada();
                // Siempre disponible: Mi arriendo muestra lo que toca pagar o que ya está al día.
                agregar(atajos, R.drawable.ic_send, R.string.shortcut_pay, v -> ctx.abrir(MiArriendoActivity.class));
                agregar(atajos, R.drawable.ic_receipt_long, R.string.shortcut_my_payments, v -> ctx.activity.startActivity(
                        new Intent(ctx.activity, HistorialInmuebleActivity.class)
                                .putExtra(VistasPago.EXTRA_PROPIEDAD_ID, propiedad.id)));
                agregar(atajos, R.drawable.ic_description, R.string.shortcut_contract, v -> ctx.activity.startActivity(
                        new Intent(ctx.activity, ContratoActivity.class)
                                .putExtra(ContratoActivity.EXTRA_PROPIEDAD_ID, propiedad.id)));
                agregar(atajos, R.drawable.ic_build, R.string.shortcut_report, v -> ctx.activity.startActivity(
                        new Intent(ctx.activity, ReportarDanoActivity.class)
                                .putExtra(ReportarDanoActivity.EXTRA_PROPIEDAD_ID, propiedad.id)));
                break;
            case PROPIETARIO:
                agregar(atajos, R.drawable.ic_add_home, R.string.shortcut_publish, v -> ctx.abrir(PublicarPropiedadActivity.class));
                View solicitudes = agregar(atajos, R.drawable.ic_inbox, R.string.shortcut_requests,
                        v -> ctx.abrir(SolicitudesRecibidasActivity.class));
                badgeSolicitudes = solicitudes.findViewById(R.id.tv_badge);
                if (ModuloRecaudo.hayArrendadas(ctx)) {
                    agregar(atajos, R.drawable.ic_payments, R.string.shortcut_payments, v -> ModuloRecaudo.verPagos(ctx));
                }
                break;
            default:
                ctx.ocultar(vista);
                return;
        }
        actualizar(ctx);
    }

    /** Badge con las solicitudes recibidas que siguen sin respuesta. */
    @Override
    public void actualizar(ContextoInicio ctx) {
        if (badgeSolicitudes == null) {
            return;
        }
        int pendientes = 0;
        for (SolicitudArriendo s : ctx.datos.solicitudes) {
            if (s.pendiente()) {
                pendientes++;
            }
        }
        badgeSolicitudes.setVisibility(pendientes > 0 ? View.VISIBLE : View.GONE);
        badgeSolicitudes.setText(String.valueOf(pendientes));
    }

    private static View agregar(LinearLayout padre, int icono, int etiqueta, View.OnClickListener alTocar) {
        View atajo = LayoutInflater.from(padre.getContext()).inflate(R.layout.item_atajo, padre, false);
        ((ImageView) atajo.findViewById(R.id.iv_icon)).setImageResource(icono);
        TextView texto = atajo.findViewById(R.id.tv_label);
        texto.setText(etiqueta);
        atajo.setContentDescription(texto.getText());
        atajo.setOnClickListener(alTocar);
        padre.addView(atajo);
        return atajo;
    }

    @Override
    public void detener() {
        badgeSolicitudes = null;
    }
}
