package com.example.miarriendo_v1;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Módulo / Atajos (446:3523). Por ahora la variante Rol=Inquilino sin arriendo (446:3522): Mapa, Filtros,
 * Solicitudes (con badge de pendientes) y Mi perfil. "Ayuda" no se dibuja hasta que exista esa pantalla.
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
        if (!ctx.datos.esInquilino()) {
            // Las variantes de inquilino con arriendo y de propietario llegan en las fases 2 y 3.
            ctx.ocultar(vista);
            return;
        }
        agregar(atajos, R.drawable.ic_map, R.string.shortcut_map, v -> ctx.activity.startActivity(
                new Intent(ctx.activity, InquilinoMapaActivity.class)
                        .putExtra(FiltrosActivity.EXTRA_FILTROS, new FiltrosBusqueda())));
        agregar(atajos, R.drawable.ic_tune, R.string.shortcut_filters, v -> ctx.elegirFiltros(new FiltrosBusqueda()));
        View solicitudes = agregar(atajos, R.drawable.ic_fact_check, R.string.shortcut_requests,
                v -> ctx.abrir(MisSolicitudesActivity.class));
        badgeSolicitudes = solicitudes.findViewById(R.id.tv_badge);
        agregar(atajos, R.drawable.ic_person, R.string.shortcut_profile, v -> ctx.activity.startActivity(
                new Intent(ctx.activity, PerfilActivity.class).putExtra(PerfilActivity.EXTRA_INQUILINO, true)));
        actualizar(ctx);
    }

    /** Badge con las solicitudes enviadas que siguen pendientes. */
    @Override
    public void actualizar(ContextoInicio ctx) {
        if (badgeSolicitudes == null) {
            return;
        }
        int pendientes = ctx.datos.solicitudesPendientes();
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
