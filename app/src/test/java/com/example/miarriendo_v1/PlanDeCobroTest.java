package com.example.miarriendo_v1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** Desde el 20/10/2026 el arriendo se paga completo: el mes entero o lo que falta de un mes viejo en abonos. */
public class PlanDeCobroTest {

    private static Cuota cuota(long monto, long pagado, String estado) {
        Cuota c = new Cuota();
        c.monto = monto;
        c.pagado = pagado;
        c.estado = estado;
        return c;
    }

    @Test
    public void sinNadaPagadoSeCobraTodoElMes() {
        PlanDeCobro plan = PlanDeCobro.de(cuota(450_000, 0, Cuota.PENDIENTE));
        assertEquals(PlanDeCobro.Tipo.UNICO, plan.tipo);
        assertEquals(450_000, plan.monto);
        assertEquals(Cuota.MODALIDAD_UNICO, plan.modalidad());
    }

    @Test
    public void unMesViejoAMediasSeTerminaConElSaldo() {
        Cuota c = cuota(450_000, 150_000, Cuota.PARCIAL);
        c.modalidad = Cuota.MODALIDAD_ABONOS;
        PlanDeCobro plan = PlanDeCobro.de(c);
        assertEquals(PlanDeCobro.Tipo.SALDO, plan.tipo);
        assertEquals(300_000, plan.monto);
        assertEquals(VistasPago.MODALIDAD_SALDO, plan.modalidad());
    }

    @Test
    public void unaCuotaPagadaNoTieneNadaQueCobrar() {
        assertNull(PlanDeCobro.de(cuota(450_000, 450_000, Cuota.PAGADO)));
        assertNull(PlanDeCobro.de(null));
    }
}
