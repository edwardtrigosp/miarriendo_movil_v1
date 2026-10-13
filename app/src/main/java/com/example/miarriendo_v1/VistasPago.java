package com.example.miarriendo_v1;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

/** Piezas de pantalla y textos que comparten las vistas de pagos. */
final class VistasPago {

    // Claves de los extras entre pantallas de pago
    static final String EXTRA_PROPIEDAD_ID = "propiedad_id";
    static final String EXTRA_CUOTA_ID = "cuota_id";
    /** Número de abono (entero); 0 si no aplica. */
    static final String EXTRA_ABONO = "abono";
    /** "unico", "abonos" o "saldo"; vacío si se sigue con el plan ya elegido. */
    static final String EXTRA_MODALIDAD = "modalidad";
    static final String EXTRA_CELULAR_FIN = "celular_fin";
    /** La pantalla la abre el propietario (ve todos los pagos del inmueble). */
    static final String EXTRA_PROPIETARIO = "propietario";

    /** Valor de {@link #EXTRA_MODALIDAD} para pagar el saldo de una vez. */
    static final String MODALIDAD_SALDO = "saldo";

    private VistasPago() {
    }

    static String pesos(Context context, long valor) {
        return context.getString(R.string.pay_money, Propiedad.formatearMiles(valor));
    }

    static String pesosCop(Context context, long valor) {
        return context.getString(R.string.pay_money_cop, Propiedad.formatearMiles(valor));
    }

    /** Agrega una fila "Etiqueta ... Valor" a las filas de una tarjeta de detalle. */
    static void dato(Context context, ViewGroup filas, CharSequence etiqueta, CharSequence valor) {
        View fila = LayoutInflater.from(context).inflate(R.layout.item_dato, filas, false);
        ((TextView) fila.findViewById(R.id.tv_label)).setText(etiqueta);
        ((TextView) fila.findViewById(R.id.tv_value)).setText(valor);
        if (filas.getChildCount() > 0) {
            ((LinearLayout.LayoutParams) fila.getLayoutParams()).topMargin =
                    context.getResources().getDimensionPixelSize(R.dimen.spacing_sm);
        }
        filas.addView(fila);
    }

    /** Filas de una tarjeta view_card_detalle ya incluida. */
    static ViewGroup filas(View tarjeta) {
        return tarjeta.findViewById(R.id.card_rows);
    }

    static void tarjetaPago(View tarjeta, CharSequence etiqueta, CharSequence monto, StatusChipView.Tipo tipo, CharSequence chip) {
        ((TextView) tarjeta.findViewById(R.id.tv_pc_label)).setText(etiqueta);
        ((TextView) tarjeta.findViewById(R.id.tv_pc_amount)).setText(monto);
        StatusChipView.aplicar(tarjeta.findViewById(R.id.tv_pc_status), tipo, chip);
    }

    /** "No has pagado", "Parcial · 1 de 3", "Pagado" o "Vencido". */
    static void chipDeCuota(Context context, TextView chip, Cuota cuota, long ahora) {
        switch (ReglasPago.estadoVisible(cuota, ahora)) {
            case PAGADO:
                StatusChipView.aplicar(chip, StatusChipView.Tipo.EXITO, context.getString(R.string.pay_status_paid));
                break;
            case VENCIDO:
                StatusChipView.aplicar(chip, StatusChipView.Tipo.ERROR, context.getString(R.string.pay_status_overdue));
                break;
            case PARCIAL:
                StatusChipView.aplicar(chip, StatusChipView.Tipo.ADVERTENCIA, context.getString(
                        R.string.pay_status_partial, ReglasPago.abonosAprobados(cuota), Math.max(1, cuota.abonos.size())));
                break;
            default:
                StatusChipView.aplicar(chip, StatusChipView.Tipo.ADVERTENCIA, context.getString(R.string.pay_status_unpaid));
        }
    }

    /** Aprobado, Pendiente de aprobación, No aprobado o Pendiente. */
    static void chipDeAbono(Context context, TextView chip, Abono abono) {
        if (abono.aprobado()) {
            StatusChipView.aplicar(chip, StatusChipView.Tipo.EXITO, context.getString(R.string.pay_status_approved));
        } else if (abono.enProceso()) {
            StatusChipView.aplicar(chip, StatusChipView.Tipo.INFO, context.getString(R.string.pay_status_waiting));
        } else if (Abono.RECHAZADO.equals(abono.estado)) {
            StatusChipView.aplicar(chip, StatusChipView.Tipo.ERROR, context.getString(R.string.pay_status_rejected));
        } else {
            StatusChipView.aplicar(chip, StatusChipView.Tipo.NEUTRAL, context.getString(R.string.pay_status_pending));
        }
    }

    /** "Pago único", "Abono 2" o "Saldo". */
    static String nombreDeAbono(Context context, Abono abono) {
        if (Abono.TIPO_UNICO.equals(abono.tipo)) {
            return context.getString(R.string.pay_concept_single);
        }
        if (Abono.TIPO_SALDO.equals(abono.tipo)) {
            return context.getString(R.string.pay_concept_balance);
        }
        return context.getString(R.string.pay_concept_installment, abono.numero);
    }

    /** "Pago único", "Abono 1 de 3" o "Saldo". */
    static String conceptoDeAbono(Context context, Cuota cuota, Abono abono) {
        if (Abono.TIPO_ABONO.equals(abono.tipo)) {
            return context.getString(R.string.pay_concept_installment_of, abono.numero, Math.max(1, cuota.abonos.size()));
        }
        return nombreDeAbono(context, abono);
    }

    /** Mes de la cuota en minúscula ("noviembre"), a partir de su periodo y no de la zona horaria del teléfono. */
    static String nombreMes(Context context, Cuota cuota) {
        String[] meses = context.getResources().getStringArray(R.array.months);
        int mes = mesDe(cuota);
        return mes >= 1 && mes <= 12 ? meses[mes - 1] : "";
    }

    /** "Noviembre de 2026" */
    static String periodo(Context context, Cuota cuota) {
        String mes = nombreMes(context, cuota);
        if (mes.isEmpty()) {
            return Formato.mesAnio(context, cuota.vence);
        }
        int anio = Integer.parseInt(cuota.periodo.substring(0, 4));
        String texto = context.getString(R.string.date_month_year, mes, anio);
        return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    private static int mesDe(Cuota cuota) {
        if (cuota.periodo == null || !cuota.periodo.matches("\\d{4}-\\d{2}")) {
            return 0;
        }
        return Integer.parseInt(cuota.periodo.substring(5, 7));
    }

    /** "2 nov 2026 · 9:41" */
    static String fechaYHora(Context context, long fecha) {
        String hora = DateFormat.getTimeInstance(DateFormat.SHORT, new Locale("es", "CO")).format(new Date(fecha));
        return context.getString(R.string.pay_date_time, Formato.fechaCorta(context, fecha), hora);
    }

    /** Con varios abonos se abre el seguimiento; con un solo pago, su comprobante. */
    static void abrirCuota(Context context, String propiedadId, Cuota cuota) {
        Abono ultimo = ReglasPago.ultimoAprobado(cuota);
        if (cuota.abonos.size() > 1 || ultimo == null) {
            context.startActivity(intent(context, SeguimientoAbonosActivity.class, propiedadId, cuota.id));
        } else {
            context.startActivity(intent(context, ComprobanteActivity.class, propiedadId, cuota.id)
                    .putExtra(EXTRA_ABONO, ultimo.numero));
        }
    }

    /** Abre una pantalla de pagos con los ids de propiedad y cuota. */
    static Intent intent(Context context, Class<?> pantalla, String propiedadId, String cuotaId) {
        return new Intent(context, pantalla)
                .putExtra(EXTRA_PROPIEDAD_ID, propiedadId)
                .putExtra(EXTRA_CUOTA_ID, cuotaId);
    }
}
