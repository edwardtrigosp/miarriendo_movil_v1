package com.example.miarriendo_v1;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Figma: Calendario de visita (535:2619). Mes con flechas, semana de lunes a domingo y los días del mes:
 * los pasados se ven atenuados y no se pueden tocar, hoy lleva un borde brand/primary y el día elegido
 * va relleno. Solo deja elegir desde hoy hasta {@link #DIAS_MAXIMOS} días después.
 */
public class CalendarioVisita extends LinearLayout {

    interface AlElegir {
        void dia(LocalDate dia);
    }

    static final int DIAS_MAXIMOS = 60;
    private static final Locale ES = new Locale("es", "CO");

    private final TextView tvMes;
    private final ImageButton btnAnterior, btnSiguiente;
    private final LinearLayout semanas;
    private final LocalDate hoy = LocalDate.now();
    private YearMonth mes = YearMonth.from(hoy);
    private LocalDate elegido;
    private AlElegir alElegir;

    public CalendarioVisita(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.view_calendario_visita, this, true);
        tvMes = findViewById(R.id.tv_mes);
        btnAnterior = findViewById(R.id.btn_mes_anterior);
        btnSiguiente = findViewById(R.id.btn_mes_siguiente);
        semanas = findViewById(R.id.semanas);
        btnAnterior.setOnClickListener(v -> cambiarMes(-1));
        btnSiguiente.setOnClickListener(v -> cambiarMes(1));
        pintar();
    }

    void alElegir(AlElegir listener) {
        alElegir = listener;
    }

    @Nullable
    LocalDate elegido() {
        return elegido;
    }

    private LocalDate ultimoDia() {
        return hoy.plusDays(DIAS_MAXIMOS);
    }

    private void cambiarMes(int delta) {
        mes = mes.plusMonths(delta);
        pintar();
    }

    private boolean elegible(LocalDate dia) {
        return !dia.isBefore(hoy) && !dia.isAfter(ultimoDia());
    }

    private void pintar() {
        String nombre = mes.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, ES);
        tvMes.setText(Character.toUpperCase(nombre.charAt(0)) + nombre.substring(1) + " " + mes.getYear());
        boolean hayAnterior = mes.isAfter(YearMonth.from(hoy));
        boolean haySiguiente = mes.isBefore(YearMonth.from(ultimoDia()));
        flecha(btnAnterior, hayAnterior);
        flecha(btnSiguiente, haySiguiente);

        semanas.removeAllViews();
        int vacios = mes.atDay(1).getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue();
        int dias = mes.lengthOfMonth();
        LinearLayout semana = null;
        for (int celda = 0; celda < vacios + dias; celda++) {
            if (celda % 7 == 0) {
                semana = new LinearLayout(getContext());
                semana.setOrientation(HORIZONTAL);
                semana.setWeightSum(7);
                semanas.addView(semana, new LayoutParams(LayoutParams.MATCH_PARENT, alto()));
            }
            semana.addView(celda < vacios ? new View(getContext()) : dia(mes.atDay(celda - vacios + 1)),
                    new LayoutParams(0, LayoutParams.MATCH_PARENT, 1));
        }
    }

    /** La flecha sin mes al que ir se ve en text/secondary y no responde, como la de octubre en Figma. */
    private void flecha(ImageButton boton, boolean activa) {
        boton.setEnabled(activa);
        boton.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(getContext(),
                activa ? R.color.text_primary : R.color.text_secondary)));
        boton.setAlpha(activa ? 1f : 0.35f);
    }

    private View dia(LocalDate fecha) {
        TextView t = (TextView) LayoutInflater.from(getContext()).inflate(R.layout.item_dia_calendario, null, false);
        t.setText(String.valueOf(fecha.getDayOfMonth()));
        boolean elegible = elegible(fecha);
        boolean esElegido = fecha.equals(elegido);
        int lado = getResources().getDimensionPixelSize(R.dimen.calendario_dia);
        if (esElegido) {
            t.setBackground(circulo(R.color.brand_primary, 0));
            t.setTextAppearance(R.style.TextAppearance_Miarriendo_BodySmall_Medium);
            t.setTextColor(ContextCompat.getColor(getContext(), R.color.surface_white));
        } else if (fecha.equals(hoy)) {
            t.setBackground(circulo(0, R.color.brand_primary));
            t.setTextColor(ContextCompat.getColor(getContext(), R.color.brand_primary));
        } else if (!elegible) {
            t.setAlpha(0.35f);
            t.setTextColor(ContextCompat.getColor(getContext(), R.color.text_secondary));
        }
        t.setMinimumWidth(lado);
        t.setEnabled(elegible);
        t.setSelected(esElegido);
        t.setContentDescription(fecha.getDayOfMonth() + " de "
                + fecha.getMonth().getDisplayName(TextStyle.FULL, ES));
        if (elegible) {
            t.setOnClickListener(v -> {
                elegido = fecha;
                pintar();
                if (alElegir != null) {
                    alElegir.dia(fecha);
                }
            });
        }
        // Centrado en su columna, con el círculo de 34 de Figma.
        LinearLayout celda = new LinearLayout(getContext());
        celda.setGravity(Gravity.CENTER);
        celda.addView(t, new LayoutParams(lado, lado));
        return celda;
    }

    private GradientDrawable circulo(int relleno, int borde) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        if (relleno != 0) {
            d.setColor(ContextCompat.getColor(getContext(), relleno));
        }
        if (borde != 0) {
            d.setStroke(Math.round(getResources().getDisplayMetrics().density), ContextCompat.getColor(getContext(), borde));
        }
        return d;
    }

    private int alto() {
        return getResources().getDimensionPixelSize(R.dimen.calendario_fila);
    }
}
