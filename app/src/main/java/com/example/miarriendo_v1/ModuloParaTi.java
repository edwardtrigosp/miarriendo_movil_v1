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
 * Módulo / Para ti (compacta) (481:2361), con el catálogo de propiedades disponibles.
 * Hasta {@link #MAXIMO} tarjetas compactas, sin la tarjeta grande con foto. El título según el estado:
 * sin solicitud "Recién publicados"; en revisión "Sigue buscando"; no aceptada "Hogares parecidos".
 * Solicitud aceptada: no se dibuja (05C · 3).
 */
final class ModuloParaTi implements ModuloInicio {

    /** Máximo de tarjetas del módulo; el resto se explora desde el mapa. */
    private static final int MAXIMO = 3;

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
        titulo.setText(estado == null ? R.string.for_you_recent
                : estado == PasosSolicitudView.Estado.NO_ACEPTADA ? R.string.for_you_similar : R.string.for_you_keep_looking);
        ctx.catalogo(new Catalogo.Resultado() {
            @Override
            public void listo(List<Propiedad> disponibles) {
                if (!activo) {
                    return;
                }
                mostrar(ctx, estado == PasosSolicitudView.Estado.NO_ACEPTADA
                        ? parecidos(disponibles, solicitud) : disponibles);
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

    /** "N disponibles" cuenta todas; en pantalla van las {@link #MAXIMO} primeras, ya ordenadas por recientes. */
    private void mostrar(ContextoInicio ctx, List<Propiedad> propiedades) {
        vista.findViewById(R.id.loading).setVisibility(View.GONE);
        ((TextView) vista.findViewById(R.id.tv_available)).setText(vista.getResources()
                .getQuantityString(R.plurals.tenant_available, propiedades.size(), propiedades.size()));
        vista.findViewById(R.id.tv_empty).setVisibility(propiedades.isEmpty() ? View.VISIBLE : View.GONE);
        LinearLayout lista = vista.findViewById(R.id.list_properties);
        lista.removeAllViews();
        int entreTarjetas = vista.getResources().getDimensionPixelSize(R.dimen.spacing_sm);
        for (int i = 0; i < Math.min(propiedades.size(), MAXIMO); i++) {
            Propiedad p = propiedades.get(i);
            View tarjeta = TarjetasPropiedad.compacta(ctx.activity, lista, p, v -> ctx.abrirDetalle(p));
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) tarjeta.getLayoutParams();
            lp.topMargin = i == 0 ? 0 : entreTarjetas;
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
