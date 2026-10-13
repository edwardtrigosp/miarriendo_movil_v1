package com.example.miarriendo_v1;

import android.content.Context;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

/** Textos derivados de datos: iniciales, fechas en español y tiempo relativo. */
final class Formato {

    private Formato() {
    }

    /** "Juan Pérez" → "JP"; "Edward" → "E". */
    static String iniciales(String nombreCompleto) {
        if (nombreCompleto == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String parte : nombreCompleto.trim().split("\\s+")) {
            if (!parte.isEmpty() && sb.length() < 2) {
                sb.append(Character.toUpperCase(parte.charAt(0)));
            }
        }
        return sb.toString();
    }

    /** "Septiembre" */
    static String mes(Context context, long fecha) {
        String nombre = context.getResources().getStringArray(R.array.months)[calendario(fecha).get(Calendar.MONTH)];
        return Character.toUpperCase(nombre.charAt(0)) + nombre.substring(1);
    }

    /** "5 de octubre" */
    static String diaMes(Context context, long fecha) {
        Calendar c = calendario(fecha);
        return context.getString(R.string.date_day_month, c.get(Calendar.DAY_OF_MONTH),
                context.getResources().getStringArray(R.array.months)[c.get(Calendar.MONTH)]);
    }

    /** "3 sep" */
    static String diaMesCorto(Context context, long fecha) {
        Calendar c = calendario(fecha);
        return context.getString(R.string.date_day_month_short, c.get(Calendar.DAY_OF_MONTH),
                context.getResources().getStringArray(R.array.months_short)[c.get(Calendar.MONTH)]);
    }

    /** "5 oct 2026" */
    static String fechaCorta(Context context, long fecha) {
        Calendar c = calendario(fecha);
        return context.getString(R.string.date_day_month_year, c.get(Calendar.DAY_OF_MONTH),
                context.getResources().getStringArray(R.array.months_short)[c.get(Calendar.MONTH)], c.get(Calendar.YEAR));
    }

    /** "marzo de 2026" */
    static String mesAnio(Context context, long fecha) {
        Calendar c = calendario(fecha);
        return context.getString(R.string.date_month_year,
                context.getResources().getStringArray(R.array.months)[c.get(Calendar.MONTH)], c.get(Calendar.YEAR));
    }

    /** Días de calendario desde hoy hasta la fecha: 0 hoy, negativo si ya pasó. */
    static int diasHasta(long fecha) {
        long hoy = inicioDelDia(System.currentTimeMillis());
        return (int) TimeUnit.MILLISECONDS.toDays(inicioDelDia(fecha) - hoy);
    }

    /** "Hace 2 días", "Hace 3 semanas"… */
    static String haceCuanto(Context context, long fecha) {
        long ms = Math.max(0, System.currentTimeMillis() - fecha);
        long minutos = TimeUnit.MILLISECONDS.toMinutes(ms);
        if (minutos < 1) return context.getString(R.string.time_now);
        if (minutos < 60) return plural(context, R.plurals.time_minutes, minutos);
        long horas = TimeUnit.MILLISECONDS.toHours(ms);
        if (horas < 24) return plural(context, R.plurals.time_hours, horas);
        int dias = -diasHasta(fecha);
        if (dias <= 1) return context.getString(R.string.time_yesterday);
        if (dias < 7) return plural(context, R.plurals.time_days, dias);
        if (dias < 30) return plural(context, R.plurals.time_weeks, dias / 7);
        return plural(context, R.plurals.time_months, dias / 30);
    }

    private static String plural(Context context, int id, long n) {
        return context.getResources().getQuantityString(id, (int) n, (int) n);
    }

    private static Calendar calendario(long fecha) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(fecha);
        return c;
    }

    private static long inicioDelDia(long fecha) {
        Calendar c = calendario(fecha);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }
}
