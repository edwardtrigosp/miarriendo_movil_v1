package com.example.miarriendo_v1;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * Un módulo del inicio unificado (Figma: Components · "Módulo / ...").
 * Cada módulo carga sus datos, pinta su vista y se oculta solo si no aplica; si falla, no tumba la pantalla.
 */
interface ModuloInicio {

    View crear(LayoutInflater inflater, ViewGroup padre);

    /** Registra los listeners de Firebase (onStart). */
    void iniciar(ContextoInicio ctx);

    /** Cambiaron los datos base pero no el plan: el módulo se vuelve a pintar si los usa. */
    default void actualizar(ContextoInicio ctx) {
    }

    /** Quita los listeners (onStop). */
    void detener();
}
