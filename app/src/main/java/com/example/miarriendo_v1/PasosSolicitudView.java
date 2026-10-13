package com.example.miarriendo_v1;

import android.content.res.ColorStateList;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

/** Aplica un Estado del componente PasosSolicitud (441:3080) a una vista view_pasos_solicitud. */
final class PasosSolicitudView {

    enum Estado {
        EN_REVISION, ACEPTADA, NO_ACEPTADA;

        static Estado de(SolicitudArriendo s) {
            if (SolicitudArriendo.ESTADO_ACEPTADA.equals(s.estado)) {
                return ACEPTADA;
            }
            if (SolicitudArriendo.ESTADO_RECHAZADA.equals(s.estado)) {
                return NO_ACEPTADA;
            }
            return EN_REVISION;
        }
    }

    private PasosSolicitudView() {
    }

    static void aplicar(View vista, Estado estado) {
        hecho(vista.findViewById(R.id.paso1), vista.findViewById(R.id.tv_paso1));
        conector(vista.findViewById(R.id.conector1), R.color.brand_primary);
        TextView tvPaso2 = vista.findViewById(R.id.tv_paso2);
        TextView tvPaso3 = vista.findViewById(R.id.tv_paso3);
        ImageView paso2 = vista.findViewById(R.id.paso2);
        ImageView paso3 = vista.findViewById(R.id.paso3);
        if (estado == Estado.EN_REVISION) {
            circulo(paso2, R.drawable.bg_paso_actual, R.drawable.ic_more_horiz, R.color.brand_primary);
            texto(tvPaso2, R.string.step_in_review, R.color.text_primary);
            conector(vista.findViewById(R.id.conector2), R.color.surface_high);
            circulo(paso3, R.drawable.bg_circle_surface_high, 0, 0);
            texto(tvPaso3, R.string.step_answer, R.color.text_secondary);
            return;
        }
        hecho(paso2, tvPaso2);
        texto(tvPaso2, R.string.step_reviewed, R.color.text_primary);
        conector(vista.findViewById(R.id.conector2), R.color.brand_primary);
        if (estado == Estado.ACEPTADA) {
            circulo(paso3, R.drawable.bg_circle_brand_primary, R.drawable.ic_check, R.color.surface_white);
            paso3.setBackgroundTintList(color(paso3, R.color.status_on_success));
            texto(tvPaso3, R.string.your_request_status_accepted, R.color.text_primary);
        } else {
            circulo(paso3, R.drawable.bg_circle_brand_primary, R.drawable.ic_close, R.color.surface_white);
            paso3.setBackgroundTintList(color(paso3, R.color.state_error));
            texto(tvPaso3, R.string.your_request_status_rejected, R.color.state_error);
        }
    }

    private static ColorStateList color(View vista, int color) {
        return ColorStateList.valueOf(ContextCompat.getColor(vista.getContext(), color));
    }

    private static void hecho(ImageView circulo, TextView texto) {
        circulo(circulo, R.drawable.bg_circle_brand_primary, R.drawable.ic_check, R.color.surface_white);
        texto.setTextColor(ContextCompat.getColor(texto.getContext(), R.color.text_primary));
    }

    private static void circulo(ImageView vista, int fondo, int icono, int color) {
        vista.setBackgroundResource(fondo);
        vista.setBackgroundTintList(null);
        if (icono == 0) {
            vista.setImageDrawable(null);
            return;
        }
        vista.setImageResource(icono);
        vista.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(vista.getContext(), color)));
    }

    private static void texto(TextView vista, int texto, int color) {
        vista.setText(texto);
        vista.setTextColor(ContextCompat.getColor(vista.getContext(), color));
    }

    private static void conector(View vista, int color) {
        vista.setBackgroundColor(ContextCompat.getColor(vista.getContext(), color));
    }
}
