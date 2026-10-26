package com.example.miarriendo_v1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ReglasPagoTest {

    private static final long DIA = TimeUnit.DAYS.toMillis(1);
    private static final long AHORA = 1_000 * DIA;

    private static Cuota cuota(int numero, long vence, long monto, long pagado, String estado) {
        Cuota c = new Cuota();
        c.id = "c" + numero;
        c.numero = numero;
        c.vence = vence;
        c.monto = monto;
        c.pagado = pagado;
        c.estado = estado;
        return c;
    }

    private static Abono abono(Cuota cuota, int numero, long monto, String estado) {
        Abono a = new Abono();
        a.numero = numero;
        a.tipo = Abono.TIPO_ABONO;
        a.monto = monto;
        a.estado = estado;
        cuota.abonos.put("a" + numero, a);
        return a;
    }

    /** Cuota de 450.000 en tres abonos, con los estados dados. */
    private static Cuota tresAbonos(String e1, String e2, String e3, long pagado, String estado, long vence) {
        Cuota c = cuota(1, vence, 450_000, pagado, estado);
        c.modalidad = Cuota.MODALIDAD_ABONOS;
        abono(c, 1, 150_000, e1);
        abono(c, 2, 150_000, e2);
        abono(c, 3, 150_000, e3);
        return c;
    }

    @Test
    public void losAbonosSalenOrdenadosYConSuClave() {
        Cuota c = new Cuota();
        Abono tercero = new Abono();
        tercero.numero = 3;
        Abono primero = new Abono();
        primero.numero = 1;
        c.abonos.put("a3", tercero);
        c.abonos.put("a1", primero);

        List<Abono> lista = ReglasPago.abonos(c);

        assertEquals(2, lista.size());
        assertEquals("a1", lista.get(0).clave);
        assertEquals("a3", lista.get(1).clave);
    }

    @Test
    public void unaCuotaSinAbonosNoRompeNada() {
        Cuota c = cuota(1, AHORA + DIA, 100, 0, Cuota.PENDIENTE);
        c.abonos = null;
        assertTrue(ReglasPago.abonos(c).isEmpty());
        assertNull(ReglasPago.siguientePorPagar(c));
        assertNull(ReglasPago.enProceso(c));
        assertNull(ReglasPago.ultimoAprobado(c));
    }

    @Test
    public void elSiguienteAbonoEsElPrimeroPendienteORechazado() {
        Cuota c = tresAbonos(Abono.APROBADO, Abono.RECHAZADO, Abono.PENDIENTE, 150_000, Cuota.PARCIAL, AHORA + DIA);
        assertEquals(2, ReglasPago.siguientePorPagar(c).numero);

        c.abonos.get("a2").estado = Abono.APROBADO;
        assertEquals(3, ReglasPago.siguientePorPagar(c).numero);

        c.abonos.get("a3").estado = Abono.APROBADO;
        assertNull(ReglasPago.siguientePorPagar(c));
    }

    @Test
    public void unAbonoEnProcesoSeEncuentraAunqueNoSeaElPrimero() {
        Cuota c = tresAbonos(Abono.APROBADO, Abono.EN_PROCESO, Abono.PENDIENTE, 150_000, Cuota.PARCIAL, AHORA + DIA);
        assertEquals(2, ReglasPago.enProceso(c).numero);
    }

    @Test
    public void elUltimoAprobadoEsElDeFechaMasReciente() {
        Cuota c = tresAbonos(Abono.APROBADO, Abono.APROBADO, Abono.PENDIENTE, 300_000, Cuota.PARCIAL, AHORA + DIA);
        c.abonos.get("a1").pagadoEn = 10;
        c.abonos.get("a2").pagadoEn = 20;
        assertEquals(2, ReglasPago.ultimoAprobado(c).numero);
        assertEquals(2, ReglasPago.abonosAprobados(c));
    }

    @Test
    public void elSaldoNuncaEsNegativo() {
        assertEquals(300_000, ReglasPago.saldo(cuota(1, AHORA, 450_000, 150_000, Cuota.PARCIAL)));
        assertEquals(0, ReglasPago.saldo(cuota(1, AHORA, 450_000, 450_000, Cuota.PAGADO)));
        assertEquals(0, ReglasPago.saldo(cuota(1, AHORA, 450_000, 500_000, Cuota.PAGADO)));
    }

    @Test
    public void elEstadoVisibleDependeDelPagoYDeLaFechaLimite() {
        assertEquals(ReglasPago.EstadoVisible.PENDIENTE,
                ReglasPago.estadoVisible(cuota(1, AHORA + DIA, 450_000, 0, Cuota.PENDIENTE), AHORA));
        assertEquals(ReglasPago.EstadoVisible.PARCIAL,
                ReglasPago.estadoVisible(cuota(1, AHORA + DIA, 450_000, 150_000, Cuota.PARCIAL), AHORA));
        assertEquals(ReglasPago.EstadoVisible.PAGADO,
                ReglasPago.estadoVisible(cuota(1, AHORA + DIA, 450_000, 450_000, Cuota.PAGADO), AHORA));
        assertEquals(ReglasPago.EstadoVisible.VENCIDO,
                ReglasPago.estadoVisible(cuota(1, AHORA - 1, 450_000, 0, Cuota.PENDIENTE), AHORA));
        assertEquals(ReglasPago.EstadoVisible.VENCIDO,
                ReglasPago.estadoVisible(cuota(1, AHORA - 1, 450_000, 150_000, Cuota.PARCIAL), AHORA));
        assertEquals("una cuota pagada no se vence", ReglasPago.EstadoVisible.PAGADO,
                ReglasPago.estadoVisible(cuota(1, AHORA - DIA, 450_000, 450_000, Cuota.PAGADO), AHORA));
    }

    @Test
    public void laCuotaActivaEsLaPrimeraSinPagarSiEstaCerca() {
        List<Cuota> cuotas = new ArrayList<>(Arrays.asList(
                cuota(1, AHORA - 20 * DIA, 100, 100, Cuota.PAGADO),
                cuota(2, AHORA + 10 * DIA, 100, 0, Cuota.PENDIENTE),
                cuota(3, AHORA + 40 * DIA, 100, 0, Cuota.PENDIENTE)));
        assertEquals(2, ReglasPago.cuotaActiva(cuotas, AHORA).numero);
    }

    @Test
    public void despuesDePagarSeSigueMostrandoLaPagadaMientrasLaSiguienteEstaLejos() {
        List<Cuota> cuotas = new ArrayList<>(Arrays.asList(
                cuota(1, AHORA - 5 * DIA, 100, 100, Cuota.PAGADO),
                cuota(2, AHORA + 25 * DIA, 100, 0, Cuota.PENDIENTE),
                cuota(3, AHORA + 55 * DIA, 100, 0, Cuota.PENDIENTE)));
        // La 2 vence dentro de la ventana de 31 días: ya es la activa.
        assertEquals(2, ReglasPago.cuotaActiva(cuotas, AHORA).numero);

        cuotas.set(1, cuota(2, AHORA + 35 * DIA, 100, 0, Cuota.PENDIENTE));
        assertEquals("fuera de la ventana", 1, ReglasPago.cuotaActiva(cuotas, AHORA).numero);
    }

    @Test
    public void unArriendoSinNingunPagoMuestraLaPrimeraCuotaAunqueEsteLejos() {
        List<Cuota> cuotas = new ArrayList<>(Arrays.asList(
                cuota(1, AHORA + 60 * DIA, 100, 0, Cuota.PENDIENTE),
                cuota(2, AHORA + 90 * DIA, 100, 0, Cuota.PENDIENTE)));
        assertEquals(1, ReglasPago.cuotaActiva(cuotas, AHORA).numero);
    }

    @Test
    public void unaCuotaVencidaSigueSiendoLaActivaHastaQueSePague() {
        List<Cuota> cuotas = new ArrayList<>(Arrays.asList(
                cuota(1, AHORA - 3 * DIA, 100, 0, Cuota.PENDIENTE),
                cuota(2, AHORA + 27 * DIA, 100, 0, Cuota.PENDIENTE)));
        assertEquals(1, ReglasPago.cuotaActiva(cuotas, AHORA).numero);
    }

    @Test
    public void sinCuotasNoHayCuotaActiva() {
        assertNull(ReglasPago.cuotaActiva(new ArrayList<>(), AHORA));
    }

    @Test
    public void todasPagadasMuestraLaUltima() {
        List<Cuota> cuotas = new ArrayList<>(Arrays.asList(
                cuota(1, AHORA - 60 * DIA, 100, 100, Cuota.PAGADO),
                cuota(2, AHORA - 30 * DIA, 100, 100, Cuota.PAGADO)));
        assertEquals(2, ReglasPago.cuotaActiva(cuotas, AHORA).numero);
    }
}
