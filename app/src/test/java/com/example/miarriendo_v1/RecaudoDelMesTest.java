package com.example.miarriendo_v1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Módulo / Recaudo (446:3584): Mes en curso, Retenido y Al día. */
public class RecaudoDelMesTest {

    private static final long DIA = 24L * 60 * 60 * 1000;
    private static final long AHORA = 1_000 * DIA;

    private static Cuota cuota(String id, long monto, long vence) {
        Cuota c = new Cuota();
        c.id = id;
        c.monto = monto;
        c.vence = vence;
        c.estado = Cuota.PENDIENTE;
        return c;
    }

    private static Cuota pagada(String id, long monto, long transferidoEn) {
        Cuota c = cuota(id, monto, AHORA + 5 * DIA);
        c.estado = Cuota.PAGADO;
        c.pagado = monto;
        Abono a = new Abono();
        a.numero = 1;
        a.tipo = Abono.TIPO_UNICO;
        a.monto = monto;
        a.estado = Abono.APROBADO;
        a.pagadoEn = AHORA - DIA;
        a.transferidoEn = transferidoEn;
        c.abonos.put("a1", a);
        return c;
    }

    private static RecaudoDelMes recaudo(boolean tieneCuenta, Cuota... activas) {
        List<List<Cuota>> porPropiedad = new ArrayList<>();
        for (Cuota c : activas) {
            porPropiedad.add(Collections.singletonList(c));
        }
        return RecaudoDelMes.calcular(porPropiedad, tieneCuenta, AHORA);
    }

    @Test
    public void mesEnCursoConUnInquilinoPagadoYOtroPendiente() {
        RecaudoDelMes r = recaudo(true, pagada("a", 450_000, AHORA - DIA), cuota("b", 450_000, AHORA + 5 * DIA));
        assertEquals(RecaudoDelMes.Estado.MES_EN_CURSO, r.estado());
        assertEquals(900_000, r.esperado);
        assertEquals(450_000, r.recibido);
        assertEquals(450_000, r.transferido);
        assertEquals(450_000, r.pendiente());
        assertEquals(1, r.inquilinosAlDia);
        assertEquals(2, r.inquilinos);
        assertEquals(50, r.progreso());
    }

    @Test
    public void sinCuentaElDineroPagadoQuedaRetenido() {
        RecaudoDelMes r = recaudo(false, pagada("a", 450_000, 0), cuota("b", 450_000, AHORA + 5 * DIA));
        assertEquals(RecaudoDelMes.Estado.RETENIDO, r.estado());
        assertEquals(450_000, r.retenido);
        assertEquals(0, r.porTransferir);
    }

    @Test
    public void todoPagadoEsAlDiaAunqueFalteTransferir() {
        RecaudoDelMes r = recaudo(true, pagada("a", 450_000, AHORA - DIA), pagada("b", 450_000, 0));
        assertEquals(RecaudoDelMes.Estado.AL_DIA, r.estado());
        assertEquals(450_000, r.transferido);
        assertEquals(450_000, r.porTransferir);
        assertEquals(100, r.progreso());
    }

    @Test
    public void elMesLoDaLaCuotaQueVencePrimero() {
        Cuota tarde = cuota("tarde", 1, AHORA + 20 * DIA);
        Cuota pronto = cuota("pronto", 1, AHORA + 2 * DIA);
        assertEquals("pronto", recaudo(true, tarde, pronto).referencia.id);
    }

    @Test
    public void sinPropiedadesArrendadasNoHayRecaudo() {
        assertTrue(RecaudoDelMes.calcular(new ArrayList<>(), true, AHORA).vacio());
        assertTrue(RecaudoDelMes.calcular(Arrays.asList(new ArrayList<>()), true, AHORA).vacio());
    }
}
