package com.example.miarriendo_v1;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Módulo / Para ti (446:3634), con el catálogo que antes cargaba InquilinoInicioActivity.
 * Sin solicitud: "Para ti" con la primera en PropertyCard (Default). Solicitud en revisión: "Sigue buscando".
 * Solicitud no aceptada: "Hogares parecidos", sin destacada. Solicitud aceptada: no se dibuja (05C · 3).
 */
final class ModuloParaTi implements ModuloInicio {

    private View vista;
    private boolean activo;

    @Override
    public View crear(LayoutInflater inflater, ViewGroup padre) {
        vista = inflater.inflate(R.layout.modulo_para_ti, padre, false);
        return vista;
    }

    @Override
    public void iniciar(ContextoInicio ctx) {
        activo = true;
        SolicitudArriendo solicitud = ctx.datos.solicitudParaMostrar(System.currentTimeMillis());
        PasosSolicitudView.Estado estado = solicitud == null ? null : PasosSolicitudView.Estado.de(solicitud);
        if (estado == PasosSolicitudView.Estado.ACEPTADA) {
            ctx.ocultar(vista);
            return;
        }
        TextView titulo = vista.findViewById(R.id.tv_heading);
        titulo.setText(estado == null ? R.string.tenant_for_you
                : estado == PasosSolicitudView.Estado.NO_ACEPTADA ? R.string.for_you_similar : R.string.for_you_keep_looking);
        ctx.catalogo(new Catalogo.Resultado() {
            @Override
            public void listo(List<Propiedad> disponibles) {
                if (!activo) {
                    return;
                }
                if (estado == PasosSolicitudView.Estado.NO_ACEPTADA) {
                    mostrar(ctx, parecidos(disponibles, solicitud), false);
                } else {
                    mostrar(ctx, disponibles, true);
                }
            }

            @Override
            public void error(Exception e) {
                if (!activo) {
                    return;
                }
                vista.findViewById(R.id.loading).setVisibility(View.GONE);
                Toast.makeText(ctx.activity, ctx.activity.getString(R.string.tenant_load_error, e.getMessage()),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Otras propiedades en un precio parecido; si no hay ninguna, todas menos la que no aceptaron. */
    private static List<Propiedad> parecidos(List<Propiedad> disponibles, SolicitudArriendo s) {
        List<Propiedad> otras = new ArrayList<>();
        for (Propiedad p : disponibles) {
            if (p.id == null || !p.id.equals(s.propiedadId)) {
                otras.add(p);
            }
        }
        List<Propiedad> parecidas = FiltrosBusqueda.parecidosA(s.precio).aplicar(otras);
        return parecidas.isEmpty() ? otras : parecidas;
    }

    /** Con destacada, la primera va en PropertyCard y las demás en tarjeta compacta (como el inicio anterior). */
    private void mostrar(ContextoInicio ctx, List<Propiedad> propiedades, boolean conDestacada) {
        vista.findViewById(R.id.loading).setVisibility(View.GONE);
        ((TextView) vista.findViewById(R.id.tv_available)).setText(vista.getResources()
                .getQuantityString(R.plurals.tenant_available, propiedades.size(), propiedades.size()));
        vista.findViewById(R.id.tv_empty).setVisibility(propiedades.isEmpty() ? View.VISIBLE : View.GONE);
        LinearLayout lista = vista.findViewById(R.id.list_properties);
        lista.removeAllViews();
        int entreGrupos = Math.round(20 * vista.getResources().getDisplayMetrics().density);
        int entreTarjetas = vista.getResources().getDimensionPixelSize(R.dimen.spacing_sm);
        for (int i = 0; i < propiedades.size(); i++) {
            Propiedad p = propiedades.get(i);
            View.OnClickListener abrir = v -> ctx.abrirDetalle(p);
            boolean destacada = conDestacada && i == 0;
            View tarjeta = destacada
                    ? TarjetasPropiedad.grande(ctx.activity, lista, p, abrir)
                    : TarjetasPropiedad.compacta(ctx.activity, lista, p, abrir);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) tarjeta.getLayoutParams();
            lp.topMargin = i == 0 ? 0 : (conDestacada && i == 1 ? entreGrupos : entreTarjetas);
            lista.addView(tarjeta, lp);
        }
    }

    /** La solicitud pudo cambiar de estado: se vuelve a elegir título y lista (el catálogo ya está en memoria). */
    @Override
    public void actualizar(ContextoInicio ctx) {
        vista.setVisibility(View.VISIBLE);
        iniciar(ctx);
    }

    @Override
    public void detener() {
        activo = false;
    }
}
