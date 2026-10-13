package com.example.miarriendo_v1;

import android.content.res.ColorStateList;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

/** Aplica un Tipo del componente Alert (208:658) sobre un view_alert incluido en un layout. */
final class AlertView {

    enum Tipo {
        ADVERTENCIA(R.color.status_warning, R.color.status_on_warning, R.drawable.ic_warning),
        ERROR(R.color.status_error, R.color.status_on_error, R.drawable.ic_error),
        INFO(R.color.status_info, R.color.status_on_info, R.drawable.ic_info);

        final int fondo, contenido, icono;

        Tipo(int fondo, int contenido, int icono) {
            this.fondo = fondo;
            this.contenido = contenido;
            this.icono = icono;
        }
    }

    private AlertView() {
    }

    static void mostrar(View alerta, Tipo tipo, CharSequence mensaje) {
        int colorContenido = ContextCompat.getColor(alerta.getContext(), tipo.contenido);
        alerta.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(alerta.getContext(), tipo.fondo)));
        ImageView icono = alerta.findViewById(R.id.iv_alert_icon);
        icono.setImageResource(tipo.icono);
        icono.setImageTintList(ColorStateList.valueOf(colorContenido));
        TextView texto = alerta.findViewById(R.id.tv_alert_message);
        texto.setTextColor(colorContenido);
        texto.setText(mensaje);
        alerta.setVisibility(View.VISIBLE);
    }

    static void ocultar(View alerta) {
        alerta.setVisibility(View.GONE);
    }
}
