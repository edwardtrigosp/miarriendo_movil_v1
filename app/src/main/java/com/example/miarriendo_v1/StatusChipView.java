package com.example.miarriendo_v1;

import android.content.res.ColorStateList;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

/** Aplica un Tipo del componente StatusChip (189:455) a un TextView con style Widget.Miarriendo.StatusChip. */
final class StatusChipView {

    enum Tipo {
        PRIMARIO(R.color.status_primary, R.color.status_on_primary),
        EXITO(R.color.status_success, R.color.status_on_success),
        ADVERTENCIA(R.color.status_warning, R.color.status_on_warning),
        ERROR(R.color.status_error, R.color.status_on_error),
        INFO(R.color.status_info, R.color.status_on_info),
        NEUTRAL(R.color.status_neutral, R.color.status_on_neutral);

        final int fondo, contenido;

        Tipo(int fondo, int contenido) {
            this.fondo = fondo;
            this.contenido = contenido;
        }
    }

    private StatusChipView() {
    }

    static void aplicar(TextView chip, Tipo tipo, CharSequence texto) {
        chip.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(chip.getContext(), tipo.fondo)));
        chip.setTextColor(ContextCompat.getColor(chip.getContext(), tipo.contenido));
        chip.setText(texto);
    }
}
