package com.example.miarriendo_v1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class PlanDeCobroTest {

    private static Cuota cuota(long monto, long pagado, String estado, String modalidad) {
        Cuota c = new Cuota();
        c.monto = monto;
        c.pagado = pagado;
        c.estado = estado;
        c.modalidad = modalidad;
        return c;
    }

    private static void abono(Cuota c, int numero, String tipo, long monto, String estado) {
        Abono a = new Abono();
        a.numero = numero;
        a.tipo = tipo;
        a.monto = monto;
        a.estado = estado;
        c.abonos.put("a" + numero, a);
    }

    @Test
    public void unPagoCobraTodoElMes() {
        PlanDeCobro plan = PlanDeCobro.de(cuota(450_000, 0, Cuota.PENDIENTE, null), Cuota.MODALIDAD_UNICO);
        assertEquals(PlanDeCobro.Tipo.UNICO, plan.tipo);
        assertEquals(450_000, plan.monto);
        assertEquals(0, plan.saldoDespues);
    }

    @Test
    public void elPrimerAbonoDeUnaCuotaNuevaEsUnTercio() {
        PlanDeCobro plan = PlanDeCobro.de(cuota(450_000, 0, Cuota.PENDIENTE, null), Cuota.MODALIDAD_ABONOS);
        assertEquals(PlanDeCobro.Tipo.ABONO, plan.tipo);
        assertEquals(1, plan.numero);
        assertEquals(3, plan.total);
        assertEquals(150_000, plan.monto);
        assertEquals(300_000, plan.saldoDespues);
    }

    @Test
    public void conMontoQueNoDivideElPrimerAbonoEsElPiso() {
        PlanDeCobro plan = PlanDeCobro.de(cuota(1_000_000, 0, Cuota.PENDIENTE, null), Cuota.MODALIDAD_ABONOS);
        assertEquals(333_333, plan.monto);
        assertEquals(666_667, plan.saldoDespues);
    }

    @Test
    public void despuesDeUnAbonoSigueElSiguienteDelPlan() {
        Cuota c = cuota(450_000, 150_000, Cuota.PARCIAL, Cuota.MODALIDAD_ABONOS);
        abono(c, 1, Abono.TIPO_ABONO, 150_000, Abono.APROBADO);
        abono(c, 2, Abono.TIPO_ABONO, 150_000, Abono.PENDIENTE);
        abono(c, 3, Abono.TIPO_ABONO, 150_000, Abono.PENDIENTE);

        PlanDeCobro plan = PlanDeCobro.de(c, null);

        assertEquals(PlanDeCobro.Tipo.ABONO, plan.tipo);
        assertEquals(2, plan.numero);
        assertEquals(3, plan.total);
        assertEquals(150_000, plan.monto);
        assertEquals(150_000, plan.saldoDespues);
    }

    @Test
    public void elSaldoCobraLoQueFaltaYSoloDespuesDeUnAbono() {
        Cuota conAbono = cuota(450_000, 150_000, Cuota.PARCIAL, Cuota.MODALIDAD_ABONOS);
        PlanDeCobro plan = PlanDeCobro.de(conAbono, VistasPago.MODALIDAD_SALDO);
        assertEquals(PlanDeCobro.Tipo.SALDO, plan.tipo);
        assertEquals(300_000, plan.monto);
        assertEquals(0, plan.saldoDespues);

        assertNull(PlanDeCobro.de(cuota(450_000, 0, Cuota.PENDIENTE, null), VistasPago.MODALIDAD_SALDO));
    }

    @Test
    public void unAbonoRechazadoSeReintentaConElMismoMonto() {
        Cuota c = cuota(450_000, 0, Cuota.PENDIENTE, Cuota.MODALIDAD_ABONOS);
        abono(c, 1, Abono.TIPO_ABONO, 150_000, Abono.RECHAZADO);
        abono(c, 2, Abono.TIPO_ABONO, 150_000, Abono.PENDIENTE);
        abono(c, 3, Abono.TIPO_ABONO, 150_000, Abono.PENDIENTE);

        PlanDeCobro plan = PlanDeCobro.de(c, null);

        assertEquals(1, plan.numero);
        assertEquals(150_000, plan.monto);
    }

    @Test
    public void antesDelPrimerPagoSePuedeCambiarDeAbonosAUnPago() {
        Cuota c = cuota(450_000, 0, Cuota.PENDIENTE, Cuota.MODALIDAD_ABONOS);
        abono(c, 1, Abono.TIPO_ABONO, 150_000, Abono.RECHAZADO);

        PlanDeCobro plan = PlanDeCobro.de(c, Cuota.MODALIDAD_UNICO);

        assertEquals(PlanDeCobro.Tipo.UNICO, plan.tipo);
        assertEquals(450_000, plan.monto);
    }

    @Test
    public void sinElegirFormaDePagoOConLaCuotaPagadaNoHayPlan() {
        assertNull(PlanDeCobro.de(cuota(450_000, 0, Cuota.PENDIENTE, null), null));
        assertNull(PlanDeCobro.de(cuota(450_000, 450_000, Cuota.PAGADO, Cuota.MODALIDAD_UNICO), null));
        assertNull(PlanDeCobro.de(null, null));
    }

    @Test
    public void unPagoUnicoRechazadoSeReintentaComoPagoUnico() {
        Cuota c = cuota(450_000, 0, Cuota.PENDIENTE, Cuota.MODALIDAD_UNICO);
        abono(c, 1, Abono.TIPO_UNICO, 450_000, Abono.RECHAZADO);

        PlanDeCobro plan = PlanDeCobro.de(c, null);

        assertEquals(PlanDeCobro.Tipo.UNICO, plan.tipo);
        assertEquals(450_000, plan.monto);
    }
}
