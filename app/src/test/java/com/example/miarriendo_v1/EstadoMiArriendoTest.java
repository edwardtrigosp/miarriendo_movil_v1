package com.example.miarriendo_v1;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;

/** Variantes de Módulo / Mi arriendo (446:3500) y el AnilloAbonos (430:2355), según ReglasPago. */
public class EstadoMiArriendoTest {

    private static final long DIA = 24L * 60 * 60 * 1000;
    private static final long AHORA = 1_000 * DIA;

    private static Cuota cuota(String estado, long pagado, long vence) {
        Cuota c = new Cuota();
        c.id = "c";
        c.monto = 450_000;
        c.estado = estado;
        c.pagado = pagado;
        c.vence = vence;
        return c;
    }

    /** Tres abonos de $150.000 con los estados dados. */
    private static Cuota porAbonos(long vence, String... estados) {
        Cuota c = cuota(Cuota.PARCIAL, 0, vence);
        c.modalidad = Cuota.MODALIDAD_ABONOS;
        for (int i = 0; i < estados.length; i++) {
            Abono a = new Abono();
            a.numero = i + 1;
            a.tipo = Abono.TIPO_ABONO;
            a.monto = 150_000;
            a.estado = estados[i];
            if (a.aprobado()) {
                c.pagado += a.monto;
            }
            c.abonos.put("a" + (i + 1), a);
        }
        return c;
    }

    @Test
    public void unaCuotaSinPagosEstaPorPagar() {
        assertEquals(ReglasPago.EstadoInicio.POR_PAGAR,
                ReglasPago.estadoInicio(cuota(Cuota.PENDIENTE, 0, AHORA + 5 * DIA), AHORA));
    }

    @Test
    public void conUnAbonoPagadoVaEnCursoYElSiguienteCobraSuAbono() {
        Cuota c = porAbonos(AHORA + 3 * DIA, Abono.APROBADO, Abono.PENDIENTE, Abono.PENDIENTE);
        assertEquals(ReglasPago.EstadoInicio.ABONO_EN_CURSO, ReglasPago.estadoInicio(c, AHORA));
        assertEquals(150_000, ReglasPago.montoSiguientePago(c));
        assertArrayEquals(new int[]{1, 3}, ReglasPago.progresoAbonos(c));
    }

    @Test
    public void unCobroEnNequiMandaAunqueEsteVencida() {
        Cuota c = porAbonos(AHORA - 2 * DIA, Abono.APROBADO, Abono.EN_PROCESO, Abono.PENDIENTE);
        assertEquals(ReglasPago.EstadoInicio.ESPERANDO_NEQUI, ReglasPago.estadoInicio(c, AHORA));
    }

    @Test
    public void pasadaLaFechaEsVencida() {
        Cuota c = porAbonos(AHORA - 2 * DIA, Abono.APROBADO, Abono.PENDIENTE, Abono.PENDIENTE);
        assertEquals(ReglasPago.EstadoInicio.VENCIDO, ReglasPago.estadoInicio(c, AHORA));
    }

    @Test
    public void pagadaEstaAlDiaYElAnilloSeCompletaAunqueSeHayaPagadoElSaldo() {
        Cuota c = porAbonos(AHORA + 3 * DIA, Abono.APROBADO, Abono.PENDIENTE, Abono.PENDIENTE);
        Abono saldo = new Abono();
        saldo.numero = 4;
        saldo.tipo = Abono.TIPO_SALDO;
        saldo.monto = 300_000;
        saldo.estado = Abono.APROBADO;
        c.abonos.put("a4", saldo);
        c.pagado = 450_000;
        c.estado = Cuota.PAGADO;
        assertEquals(ReglasPago.EstadoInicio.AL_DIA, ReglasPago.estadoInicio(c, AHORA));
        assertArrayEquals(new int[]{3, 3}, ReglasPago.progresoAbonos(c));
    }

    @Test
    public void sinAbonosElSiguientePagoEsTodoElSaldo() {
        assertEquals(450_000, ReglasPago.montoSiguientePago(cuota(Cuota.PENDIENTE, 0, AHORA - DIA)));
    }

    @Test
    public void laSiguienteCuotaEsLaQueVieneDespues() {
        Cuota noviembre = cuota(Cuota.PAGADO, 450_000, AHORA);
        Cuota enero = cuota(Cuota.PENDIENTE, 0, AHORA + 60 * DIA);
        Cuota diciembre = cuota(Cuota.PENDIENTE, 0, AHORA + 30 * DIA);
        assertEquals(diciembre, ReglasPago.siguienteCuota(Arrays.asList(enero, noviembre, diciembre), noviembre));
    }
}
