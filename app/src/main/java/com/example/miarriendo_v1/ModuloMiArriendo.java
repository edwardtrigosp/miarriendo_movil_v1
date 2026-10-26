package com.example.miarriendo_v1;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import java.util.List;

/**
 * Módulo / Mi arriendo (446:3500): la cuota del momento con una sola acción principal según su estado
 * (ReglasPago.EstadoInicio). Las acciones abren las mismas pantallas de pago que Mi arriendo. El arriendo se paga
 * completo: no hay abonos (un mes viejo que quedó a medias se termina pagando lo que falta).
 */
final class ModuloMiArriendo implements ModuloInicio {

    /** Paquete de la app de Nequi, para "Abrir Nequi para aprobar". */
    static final String PAQUETE_NEQUI = "com.nequi.MobileApp";

    /** Con esta anticipación (días) el chip pasa de la fecha a "Vence en N días", como el aviso de Mi arriendo. */
    private static final int DIAS_DE_AVISO = 5;

    private View vista;

    @Override
    public View crear(LayoutInflater inflater, ViewGroup padre) {
        vista = inflater.inflate(R.layout.modulo_mi_arriendo, padre, false);
        return vista;
    }

    @Override
    public void iniciar(ContextoInicio ctx) {
        vista.findViewById(R.id.titulo).setOnClickListener(v -> ctx.abrir(MiArriendoActivity.class));
        actualizar(ctx);
    }

    @Override
    public void actualizar(ContextoInicio ctx) {
        long ahora = System.currentTimeMillis();
        Propiedad propiedad = ctx.datos.propiedadArrendada();
        List<Cuota> cuotas = ctx.datos.cuotasDe(propiedad);
        Cuota cuota = ReglasPago.cuotaActiva(cuotas, ahora);
        if (cuota == null) {
            // Aceptada pero sin cuotas todavía (el servidor las crea al aceptar): no hay nada que pagar aún.
            ctx.ocultar(vista);
            return;
        }
        vista.setVisibility(View.VISIBLE);
        Context c = vista.getContext();
        String mes = VistasPago.nombreMes(c, cuota);
        String mesTitulo = mes.isEmpty() ? Formato.mes(c, cuota.vence) : mayuscula(mes);
        ((TextView) vista.findViewById(R.id.tv_title)).setText(c.getString(R.string.home_rental_title, mesTitulo));

        TextView etiqueta = vista.findViewById(R.id.tv_label);
        TextView monto = vista.findViewById(R.id.tv_amount);
        TextView detalle = vista.findViewById(R.id.tv_amount_detail);
        TextView chip = vista.findViewById(R.id.tv_status);
        TextView fecha = vista.findViewById(R.id.tv_date);
        View alerta = vista.findViewById(R.id.alert);
        MaterialButton primario = vista.findViewById(R.id.btn_primary);
        MaterialButton secundario = vista.findViewById(R.id.btn_secondary);
        AlertView.ocultar(alerta);
        secundario.setVisibility(View.GONE);

        String total = VistasPago.pesos(c, cuota.monto);
        String saldo = VistasPago.pesos(c, ReglasPago.saldo(cuota));
        String vence = Formato.diaMes(c, cuota.vence);
        switch (ReglasPago.estadoInicio(cuota, ahora)) {
            case ESPERANDO_NEQUI: {
                Abono abono = ReglasPago.enProceso(cuota);
                etiqueta.setText(R.string.home_rental_label_waiting);
                monto.setText(VistasPago.pesos(c, abono.monto));
                String concepto = VistasPago.conceptoDeAbono(c, cuota, abono);
                String celularFin = abono.intento == null ? null : abono.intento.celularFin;
                detalle.setText(celularFin == null ? concepto : c.getString(R.string.home_rental_detail_waiting, concepto, celularFin));
                StatusChipView.aplicar(chip, StatusChipView.Tipo.INFO, c.getString(R.string.home_rental_chip_waiting));
                fecha.setText(null);
                primario.setText(R.string.home_rental_open_nequi);
                primario.setOnClickListener(v -> abrirNequi(ctx, propiedad, cuota, abono));
                // Si el cobro no llegó, el estado y las opciones están en la pantalla de espera.
                secundario.setVisibility(View.VISIBLE);
                secundario.setText(R.string.my_rental_see_waiting);
                secundario.setOnClickListener(v -> ctx.activity.startActivity(esperaNequi(ctx, propiedad, cuota, abono)));
                break;
            }
            case AL_DIA: {
                etiqueta.setText(R.string.home_rental_label_up_to_date);
                monto.setText(VistasPago.pesos(c, 0));
                detalle.setText(c.getString(R.string.home_rental_detail_paid_on, total,
                        Formato.diaMes(c, ReglasPago.fechaDePago(cuota))));
                StatusChipView.aplicar(chip, StatusChipView.Tipo.EXITO, c.getString(R.string.home_rental_chip_paid, mesTitulo));
                Cuota siguiente = ReglasPago.siguienteCuota(cuotas, cuota);
                fecha.setText(siguiente == null ? null
                        : c.getString(R.string.home_rental_next_payment, Formato.diaMes(c, siguiente.vence)));
                primario.setText(c.getString(R.string.home_rental_see_receipt, mes));
                primario.setOnClickListener(v -> VistasPago.abrirCuota(c, propiedad.id, cuota));
                secundario.setVisibility(View.VISIBLE);
                secundario.setText(R.string.my_rental_see_history);
                secundario.setOnClickListener(v -> ctx.activity.startActivity(
                        new Intent(ctx.activity, HistorialInmuebleActivity.class)
                                .putExtra(VistasPago.EXTRA_PROPIEDAD_ID, propiedad.id)));
                break;
            }
            case VENCIDO: {
                etiqueta.setText(R.string.home_rental_label_overdue);
                monto.setText(saldo);
                detalle.setText(cuota.pagado > 0 ? c.getString(R.string.home_rental_detail_of, total)
                        : c.getString(R.string.home_rental_detail_month, mes));
                int dias = Math.max(1, -Formato.diasHasta(cuota.vence));
                StatusChipView.aplicar(chip, StatusChipView.Tipo.ERROR,
                        c.getResources().getQuantityString(R.plurals.home_rental_chip_overdue, dias, dias));
                fecha.setText(c.getString(R.string.home_rental_overdue_on, vence));
                AlertView.mostrar(alerta, AlertView.Tipo.ERROR, c.getString(R.string.home_rental_alert_overdue));
                primario.setText(R.string.home_rental_pay);
                primario.setOnClickListener(v -> pagar(ctx, propiedad, cuota));
                break;
            }
            case PARCIAL: {
                etiqueta.setText(R.string.home_rental_label_left);
                monto.setText(saldo);
                detalle.setText(c.getString(R.string.home_rental_detail_of, total));
                chipDeVencimiento(chip, cuota);
                fecha.setText(c.getString(R.string.home_rental_deadline, vence));
                primario.setText(c.getString(R.string.home_rental_pay_now, saldo));
                primario.setOnClickListener(v -> pagar(ctx, propiedad, cuota));
                break;
            }
            default: {
                etiqueta.setText(R.string.home_rental_label_to_pay);
                monto.setText(total);
                detalle.setText(c.getString(R.string.home_rental_detail_month, mes));
                chipDeVencimiento(chip, cuota);
                fecha.setText(c.getString(R.string.home_rental_due, vence));
                primario.setText(R.string.home_rental_pay);
                primario.setOnClickListener(v -> pagar(ctx, propiedad, cuota));
            }
        }
        fecha.setVisibility(fecha.getText() == null || fecha.getText().length() == 0 ? View.GONE : View.VISIBLE);
    }

    /** "Vence en 3 días" (advertencia) cerca de la fecha; antes, "No has pagado" (neutral). */
    private static void chipDeVencimiento(TextView chip, Cuota cuota) {
        Context c = chip.getContext();
        int dias = Formato.diasHasta(cuota.vence);
        if (dias == 0) {
            StatusChipView.aplicar(chip, StatusChipView.Tipo.ADVERTENCIA, c.getString(R.string.home_rental_chip_today));
        } else if (dias > 0 && dias <= DIAS_DE_AVISO) {
            StatusChipView.aplicar(chip, StatusChipView.Tipo.ADVERTENCIA,
                    c.getResources().getQuantityString(R.plurals.home_rental_chip_days, dias, dias));
        } else {
            StatusChipView.aplicar(chip, StatusChipView.Tipo.NEUTRAL, c.getString(R.string.home_rental_chip_unpaid));
        }
    }

    /** Pagar con Nequi lo que falta del mes (todo, salvo en un mes viejo que quedó a medias). */
    private static void pagar(ContextoInicio ctx, Propiedad propiedad, Cuota cuota) {
        ctx.activity.startActivity(VistasPago.intent(ctx.activity, PagarNequiActivity.class, propiedad.id, cuota.id));
    }

    /** Abre Nequi para aprobar el cobro; si no está instalada, la pantalla de espera del cobro. */
    private static void abrirNequi(ContextoInicio ctx, Propiedad propiedad, Cuota cuota, Abono abono) {
        Intent nequi = ctx.activity.getPackageManager().getLaunchIntentForPackage(PAQUETE_NEQUI);
        ctx.activity.startActivity(nequi != null ? nequi : esperaNequi(ctx, propiedad, cuota, abono));
    }

    private static Intent esperaNequi(ContextoInicio ctx, Propiedad propiedad, Cuota cuota, Abono abono) {
        return VistasPago.intent(ctx.activity, ConfirmarNequiActivity.class, propiedad.id, cuota.id)
                .putExtra(VistasPago.EXTRA_ABONO, abono.numero)
                .putExtra(VistasPago.EXTRA_CELULAR_FIN, abono.intento == null ? null : abono.intento.celularFin);
    }

    private static String mayuscula(String texto) {
        return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    @Override
    public void detener() {
    }
}
