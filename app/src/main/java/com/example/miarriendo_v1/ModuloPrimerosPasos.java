package com.example.miarriendo_v1;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.progressindicator.LinearProgressIndicator;

/**
 * Módulo / Primeros pasos (446:3618): el propietario sin propiedades ve los 3 pasos para empezar a cobrar y cómo le
 * llega el dinero. Un paso hecho cambia su ícono por task_alt en verde; ningún paso se deshabilita.
 */
final class ModuloPrimerosPasos implements ModuloInicio {

    private static final int PASOS = 3;

    private View vista;

    @Override
    public View crear(LayoutInflater inflater, ViewGroup padre) {
        vista = inflater.inflate(R.layout.modulo_primeros_pasos, padre, false);
        return vista;
    }

    @Override
    public void iniciar(ContextoInicio ctx) {
        vista.findViewById(R.id.btn_publish).setOnClickListener(v -> ctx.abrir(PublicarPropiedadActivity.class));
        View tip = vista.findViewById(R.id.tip);
        ((TextView) tip.findViewById(R.id.tv_tip_title)).setText(R.string.home_first_steps_tip_title);
        ((TextView) tip.findViewById(R.id.tv_tip_text)).setText(R.string.home_first_steps_tip_text);
        tip.findViewById(R.id.btn_tip_close).setVisibility(View.GONE);
        actualizar(ctx);
    }

    @Override
    public void actualizar(ContextoInicio ctx) {
        boolean publico = !ctx.datos.propiedades.isEmpty();
        boolean acepto = false;
        for (Propiedad p : ctx.datos.propiedades) {
            acepto |= p.tieneInquilino();
        }
        int hechos = 0;
        hechos += paso(R.id.paso_publicar, R.drawable.ic_add_home, R.string.home_first_steps_publish, publico,
                v -> ctx.abrir(PublicarPropiedadActivity.class));
        hechos += paso(R.id.paso_aceptar, R.drawable.ic_how_to_reg, R.string.home_first_steps_accept, acepto,
                v -> ctx.abrir(SolicitudesRecibidasActivity.class));
        hechos += paso(R.id.paso_cuenta, R.drawable.ic_account_balance, R.string.home_first_steps_account, ctx.datos.tieneCuenta,
                v -> ctx.abrir(CuentaBancariaActivity.class));
        ((TextView) vista.findViewById(R.id.tv_progress)).setText(
                vista.getContext().getString(R.string.home_first_steps_progress, hechos));
        ((LinearProgressIndicator) vista.findViewById(R.id.barra)).setProgress(hechos * 100 / PASOS);
    }

    /** @return 1 si el paso está hecho. */
    private int paso(int id, int icono, int texto, boolean hecho, View.OnClickListener alTocar) {
        Context c = vista.getContext();
        View fila = vista.findViewById(id);
        ImageView iv = fila.findViewById(R.id.iv_icon);
        iv.setImageResource(hecho ? R.drawable.ic_task_alt : icono);
        iv.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(c,
                hecho ? R.color.status_on_success : R.color.brand_on_container)));
        TextView etiqueta = fila.findViewById(R.id.tv_label);
        etiqueta.setText(texto);
        fila.setContentDescription(hecho
                ? etiqueta.getText() + ". " + c.getString(R.string.home_first_steps_done) : etiqueta.getText());
        fila.setOnClickListener(alTocar);
        return hecho ? 1 : 0;
    }

    @Override
    public void detener() {
    }
}
