package com.example.miarriendo_v1;

import android.content.Intent;
import android.view.View;

/** Lo que el inicio unificado le pasa a cada módulo. */
final class ContextoInicio {

    final InicioActivity activity;
    final String uid;
    final DatosInicio datos;

    ContextoInicio(InicioActivity activity, String uid, DatosInicio datos) {
        this.activity = activity;
        this.uid = uid;
        this.datos = datos;
    }

    String rol() {
        return datos.rol;
    }

    /** Un módulo que no aplica no se dibuja: nada de secciones vacías ni botones deshabilitados. */
    void ocultar(View vistaDelModulo) {
        vistaDelModulo.setVisibility(View.GONE);
    }

    void abrir(Class<?> pantalla) {
        activity.startActivity(new Intent(activity, pantalla));
    }

    void abrirResultados(FiltrosBusqueda filtros) {
        activity.startActivity(new Intent(activity, ResultadosActivity.class)
                .putExtra(FiltrosActivity.EXTRA_FILTROS, filtros));
    }

    void abrirDetalle(Propiedad p) {
        activity.startActivity(new Intent(activity, DetalleArriendoActivity.class)
                .putExtra(DetalleArriendoActivity.EXTRA_PROPIEDAD_ID, p.id));
    }

    /** Filtros → Resultados con los filtros elegidos. */
    void elegirFiltros(FiltrosBusqueda filtros) {
        activity.elegirFiltros(filtros);
    }

    /** El catálogo de propiedades disponibles, cargado una sola vez para todos los módulos. */
    void catalogo(Catalogo.Resultado resultado) {
        activity.catalogo(resultado);
    }
}
