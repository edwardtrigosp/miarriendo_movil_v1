package com.example.miarriendo_v1;

import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

/** Lo que el inicio unificado le pasa a cada módulo. */
final class ContextoInicio {

    final AppCompatActivity activity;
    final String uid;
    final DatosInicio datos;

    ContextoInicio(AppCompatActivity activity, String uid, DatosInicio datos) {
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
}
