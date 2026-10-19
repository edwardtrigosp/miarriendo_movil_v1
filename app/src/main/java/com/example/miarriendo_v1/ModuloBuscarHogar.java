package com.example.miarriendo_v1;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Módulo / Buscar hogar (fila) (482:2350): para quien ya arrienda, una sola fila que abre el mapa sin filtros.
 * La búsqueda queda al final porque lo primero es su pago.
 */
final class ModuloBuscarHogar implements ModuloInicio {

    private View vista;

    @Override
    public View crear(LayoutInflater inflater, ViewGroup padre) {
        vista = inflater.inflate(R.layout.modulo_buscar_hogar, padre, false);
        return vista;
    }

    @Override
    public void iniciar(ContextoInicio ctx) {
        View fila = vista.findViewById(R.id.fila);
        ((ImageView) fila.findViewById(R.id.iv_icon)).setImageResource(R.drawable.ic_map);
        ((TextView) fila.findViewById(R.id.tv_label)).setText(R.string.home_search_row);
        fila.setOnClickListener(v -> ctx.activity.startActivity(
                new Intent(ctx.activity, InquilinoMapaActivity.class)
                        .putExtra(FiltrosActivity.EXTRA_FILTROS, new FiltrosBusqueda())));
    }

    @Override
    public void detener() {
    }
}
