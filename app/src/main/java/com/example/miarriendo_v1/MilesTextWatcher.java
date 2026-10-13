package com.example.miarriendo_v1;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

/** Da formato de miles a un campo numérico mientras se escribe: "1350000" → "1.350.000". */
final class MilesTextWatcher implements TextWatcher {

    private final EditText editText;
    private boolean formateando;

    MilesTextWatcher(EditText editText) {
        this.editText = editText;
    }

    /** Valor sin separadores; 0 si el campo está vacío. */
    static long valor(CharSequence texto) {
        String digitos = texto == null ? "" : texto.toString().replaceAll("\\D", "");
        if (digitos.isEmpty()) {
            return 0;
        }
        try {
            return Long.parseLong(digitos);
        } catch (NumberFormatException e) {
            return Long.MAX_VALUE;
        }
    }

    @Override
    public void afterTextChanged(Editable s) {
        if (formateando) {
            return;
        }
        String digitos = s.toString().replaceAll("\\D", "");
        String formateado = digitos.isEmpty() ? "" : Propiedad.formatearMiles(valor(digitos));
        if (formateado.equals(s.toString())) {
            return;
        }
        formateando = true;
        editText.setText(formateado);
        editText.setSelection(formateado.length());
        formateando = false;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }
}
