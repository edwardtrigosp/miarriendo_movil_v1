package com.example.miarriendo_v1;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Módulo / Recordatorios y detalle (531:3931), debajo de Mi arriendo: "Detalles de mi arriendo" abre la ficha
 * del inmueble que arrienda (dirección, fotos y condiciones). Los recordatorios llegan cuando la app los tenga.
 */
final class ModuloDetalleArriendo implements ModuloInicio {

    private View vista;

    @Override
    public View crear(LayoutInflater inflater, ViewGroup padre) {
        vista = inflater.inflate(R.layout.modulo_detalle_arriendo, padre, false);
        return vista;
    }

    @Override
    public void iniciar(ContextoInicio ctx) {
        Propiedad propiedad = ctx.datos.propiedadArrendada();
        if (propiedad == null) {
            ctx.ocultar(vista);
            return;
        }
        View fila = vista.findViewById(R.id.fila);
        ((ImageView) fila.findViewById(R.id.iv_icon)).setImageResource(R.drawable.ic_description);
        ((TextView) fila.findViewById(R.id.tv_label)).setText(R.string.home_rental_details);
        fila.setOnClickListener(v -> ctx.abrirDetalle(propiedad));
    }

    @Override
    public void detener() {
    }
}
