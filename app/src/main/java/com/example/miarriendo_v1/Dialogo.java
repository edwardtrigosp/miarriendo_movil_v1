package com.example.miarriendo_v1;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.InsetDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.StringRes;

/**
 * Diálogo de miarriendo (Figma: Dialog 627:2695) en lugar de los diálogos nativos de Material.
 * Informativo: solo {@link #confirmar}. Confirmar: botón principal morado. Destructivo: botón con borde rojo.
 * Cancelar va siempre a la izquierda. Admite una vista propia (campo, foto) o una lista de opciones.
 */
final class Dialogo {

    enum Tono {
        PRINCIPAL,
        DESTRUCTIVO
    }

    /** Acción de un botón; devuelve false para dejar el diálogo abierto (por ejemplo, si un campo no es válido). */
    interface Accion {
        boolean ejecutar();
    }

    interface AlElegir {
        void elegido(int posicion);
    }

    private static final float OPACIDAD_SCRIM = 0.32f;

    private final Activity activity;
    private final Dialog dialogo;
    private final View raiz;

    private Dialogo(Activity activity) {
        this.activity = activity;
        dialogo = new Dialog(activity);
        dialogo.requestWindowFeature(Window.FEATURE_NO_TITLE);
        raiz = LayoutInflater.from(activity).inflate(R.layout.dialog_miarriendo, null, false);
        dialogo.setContentView(raiz);
        Window ventana = dialogo.getWindow();
        if (ventana != null) {
            int margen = activity.getResources().getDimensionPixelSize(R.dimen.spacing_lg);
            ventana.setBackgroundDrawable(new InsetDrawable(new ColorDrawable(Color.TRANSPARENT), margen));
            ventana.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            ventana.setDimAmount(OPACIDAD_SCRIM);
        }
    }

    static Dialogo nuevo(Activity activity) {
        return new Dialogo(activity);
    }

    Dialogo titulo(@StringRes int texto) {
        return titulo(activity.getString(texto));
    }

    Dialogo titulo(CharSequence texto) {
        ((TextView) raiz.findViewById(R.id.tv_dialog_title)).setText(texto);
        return this;
    }

    Dialogo mensaje(@StringRes int texto) {
        return mensaje(activity.getString(texto));
    }

    Dialogo mensaje(CharSequence texto) {
        TextView mensaje = raiz.findViewById(R.id.tv_dialog_message);
        mensaje.setText(texto);
        mensaje.setVisibility(View.VISIBLE);
        return this;
    }

    /** Campo, foto u otra vista debajo del mensaje. */
    Dialogo contenido(View vista) {
        FrameLayout contenedor = raiz.findViewById(R.id.dialog_content);
        contenedor.addView(vista);
        contenedor.setVisibility(View.VISIBLE);
        return this;
    }

    /** Opciones como filas (ListItem); al tocar una se cierra el diálogo. */
    Dialogo lista(CharSequence[] opciones, AlElegir alElegir) {
        LinearLayout lista = raiz.findViewById(R.id.dialog_list);
        for (int i = 0; i < opciones.length; i++) {
            View fila = LayoutInflater.from(activity).inflate(R.layout.item_dialogo_opcion, lista, false);
            ((TextView) fila.findViewById(R.id.tv_option)).setText(opciones[i]);
            int posicion = i;
            fila.setOnClickListener(v -> {
                dialogo.dismiss();
                alElegir.elegido(posicion);
            });
            lista.addView(fila);
        }
        lista.setVisibility(View.VISIBLE);
        return this;
    }

    Dialogo cancelar(@StringRes int texto, Runnable alCancelar) {
        TextView boton = raiz.findViewById(R.id.btn_dialog_cancel);
        boton.setText(texto);
        boton.setVisibility(View.VISIBLE);
        boton.setOnClickListener(v -> {
            dialogo.dismiss();
            if (alCancelar != null) {
                alCancelar.run();
            }
        });
        return this;
    }

    Dialogo cancelar(@StringRes int texto) {
        return cancelar(texto, null);
    }

    /** Confirmar que solo cierra si la acción devuelve true. */
    Dialogo confirmarSi(@StringRes int texto, Tono tono, Accion accion) {
        TextView boton = raiz.findViewById(tono == Tono.DESTRUCTIVO
                ? R.id.btn_dialog_confirm_destructive : R.id.btn_dialog_confirm);
        boton.setText(texto);
        boton.setVisibility(View.VISIBLE);
        boton.setOnClickListener(v -> {
            if (accion == null || accion.ejecutar()) {
                dialogo.dismiss();
            }
        });
        return this;
    }

    /** Confirmar que siempre cierra el diálogo. */
    Dialogo confirmar(@StringRes int texto, Tono tono, Runnable accion) {
        return confirmarSi(texto, tono, () -> {
            if (accion != null) {
                accion.run();
            }
            return true;
        });
    }

    /** Sin tocar fuera ni el botón atrás (por ejemplo, un error que obliga a decidir). */
    Dialogo obligatorio() {
        dialogo.setCancelable(false);
        return this;
    }

    Dialog mostrar() {
        boolean hayCancelar = raiz.findViewById(R.id.btn_dialog_cancel).getVisibility() == View.VISIBLE;
        boolean hayConfirmar = raiz.findViewById(R.id.btn_dialog_confirm).getVisibility() == View.VISIBLE
                || raiz.findViewById(R.id.btn_dialog_confirm_destructive).getVisibility() == View.VISIBLE;
        raiz.findViewById(R.id.dialog_actions_gap).setVisibility(hayCancelar && hayConfirmar ? View.VISIBLE : View.GONE);
        raiz.findViewById(R.id.dialog_actions).setVisibility(hayCancelar || hayConfirmar ? View.VISIBLE : View.GONE);
        dialogo.show();
        return dialogo;
    }
}
