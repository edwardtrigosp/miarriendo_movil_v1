package com.example.miarriendo_v1;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.util.List;

/**
 * Módulo / Mi arriendo (446:3500): la cuota del momento con una sola acción principal según su estado
 * (ReglasPago.EstadoInicio). Las acciones abren las mismas pantallas de pago que Mi arriendo.
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
        TextView mensaje = vista.findViewById(R.id.tv_message);
        View alerta = vista.findViewById(R.id.alert);
        MaterialButton primario = vista.findViewById(R.id.btn_primary);
        MaterialButton secundario = vista.findViewById(R.id.btn_secondary);
        mensaje.setVisibility(View.GONE);
        AlertView.ocultar(alerta);
        secundario.setVisibility(View.GONE);
        mostrarAnillo(cuota);

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
                int abonos = ReglasPago.abonosAprobados(cuota);
                detalle.setText(abonos > 1
                        ? c.getString(R.string.home_rental_detail_paid_installments, total, abonos)
                        : c.getString(R.string.home_rental_detail_paid_single, total));
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
                primario.setText(c.getString(R.string.home_rental_pay_now, VistasPago.pesos(c, ReglasPago.montoSiguientePago(cuota))));
                primario.setOnClickListener(v -> pagarSiguiente(ctx, propiedad, cuota));
                mostrarPagarSaldo(ctx, secundario, propiedad, cuota);
                break;
            }
            case ABONO_EN_CURSO: {
                etiqueta.setText(R.string.home_rental_label_left);
                monto.setText(saldo);
                detalle.setText(c.getString(R.string.home_rental_detail_of, total));
                chipDeVencimiento(chip, cuota);
                fecha.setText(c.getString(R.string.home_rental_deadline, vence));
                Abono siguiente = ReglasPago.siguientePorPagar(cuota);
                int[] progreso = ReglasPago.progresoAbonos(cuota);
                primario.setText(siguiente != null && ReglasPago.porAbonos(cuota)
                        ? c.getString(R.string.home_rental_pay_installment, siguiente.numero, progreso[1],
                        VistasPago.pesos(c, siguiente.monto))
                        : c.getString(R.string.home_rental_pay_now, saldo));
                primario.setOnClickListener(v -> pagarSiguiente(ctx, propiedad, cuota));
                mostrarPagarSaldo(ctx, secundario, propiedad, cuota);
                break;
            }
            default: {
                etiqueta.setText(R.string.home_rental_label_to_pay);
                monto.setText(total);
                detalle.setText(c.getString(R.string.home_rental_detail_month, mes));
                chipDeVencimiento(chip, cuota);
                fecha.setText(c.getString(R.string.home_rental_due, vence));
                long abono = ReglasPago.montosDeAbonos(cuota.monto, ReglasPago.ABONOS_POR_CUOTA)[0];
                mensaje.setText(c.getString(R.string.home_rental_how_to_pay, VistasPago.pesos(c, abono)));
                mensaje.setVisibility(View.VISIBLE);
                primario.setText(R.string.home_rental_pay);
                primario.setOnClickListener(v -> ctx.activity.startActivity(
                        VistasPago.intent(ctx.activity, ElegirFormaPagoActivity.class, propiedad.id, cuota.id)));
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

    /** AnilloAbonos (430:2355): solo con abonos; verde cuando el mes está completo. */
    private void mostrarAnillo(Cuota cuota) {
        View anillo = vista.findViewById(R.id.anillo);
        if (!ReglasPago.porAbonos(cuota)) {
            anillo.setVisibility(View.GONE);
            return;
        }
        int[] progreso = ReglasPago.progresoAbonos(cuota);
        Context c = vista.getContext();
        CircularProgressIndicator indicador = vista.findViewById(R.id.anillo_progreso);
        indicador.setMax(progreso[1]);
        indicador.setProgress(progreso[0]);
        boolean completo = progreso[0] == progreso[1];
        indicador.setIndicatorColor(ContextCompat.getColor(c, completo ? R.color.status_on_success : R.color.brand_primary));
        ((TextView) vista.findViewById(R.id.tv_anillo)).setText(c.getString(R.string.home_rental_ring, progreso[0], progreso[1]));
        anillo.setContentDescription(c.getString(R.string.home_rental_ring_description, progreso[0], progreso[1]));
        anillo.setVisibility(View.VISIBLE);
    }

    /** Siguiente abono si va por abonos; si nunca ha pagado, elegir forma de pago. */
    private static void pagarSiguiente(ContextoInicio ctx, Propiedad propiedad, Cuota cuota) {
        Abono siguiente = ReglasPago.siguientePorPagar(cuota);
        if (cuota.pagado == 0 && siguiente == null) {
            ctx.activity.startActivity(VistasPago.intent(ctx.activity, ElegirFormaPagoActivity.class, propiedad.id, cuota.id));
            return;
        }
        ctx.activity.startActivity(VistasPago.intent(ctx.activity, PagarNequiActivity.class, propiedad.id, cuota.id)
                .putExtra(VistasPago.EXTRA_ABONO, siguiente == null ? 0 : siguiente.numero));
    }

    private static void mostrarPagarSaldo(ContextoInicio ctx, MaterialButton boton, Propiedad propiedad, Cuota cuota) {
        if (!ReglasPago.puedePagarSaldo(cuota)) {
            return;
        }
        boton.setVisibility(View.VISIBLE);
        boton.setText(R.string.my_rental_pay_balance);
        boton.setOnClickListener(v -> ctx.activity.startActivity(
                VistasPago.intent(ctx.activity, PagarNequiActivity.class, propiedad.id, cuota.id)
                        .putExtra(VistasPago.EXTRA_MODALIDAD, VistasPago.MODALIDAD_SALDO)));
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
