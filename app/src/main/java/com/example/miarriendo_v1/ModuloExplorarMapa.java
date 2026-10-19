package com.example.miarriendo_v1;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * Módulo / Explorar en mapa (compacto) (481:2342): mapa en miniatura y un botón que abre el mapa sin filtros.
 * Reemplaza al buscador del encabezado y a los atajos del inquilino sin arriendo (497:4649).
 */
final class ModuloExplorarMapa implements ModuloInicio {

    private View vista;

    @Override
    public View crear(LayoutInflater inflater, ViewGroup padre) {
        vista = inflater.inflate(R.layout.modulo_explorar_mapa, padre, false);
        return vista;
    }

    @Override
    public void iniciar(ContextoInicio ctx) {
        vista.findViewById(R.id.btn_explorar_mapa).setOnClickListener(v -> ctx.activity.startActivity(
                new Intent(ctx.activity, InquilinoMapaActivity.class)
                        .putExtra(FiltrosActivity.EXTRA_FILTROS, new FiltrosBusqueda())));
    }

    @Override
    public void detener() {
    }
}
