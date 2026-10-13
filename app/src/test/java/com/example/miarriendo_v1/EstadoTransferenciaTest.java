package com.example.miarriendo_v1;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class EstadoTransferenciaTest {

    private static Cuota cuota(Abono... abonos) {
        Cuota c = new Cuota();
        c.monto = 450_000;
        for (Abono a : abonos) {
            c.abonos.put("a" + a.numero, a);
        }
        return c;
    }

    private static Abono abono(int numero, String estado, long transferidoEn) {
        Abono a = new Abono();
        a.numero = numero;
        a.tipo = Abono.TIPO_ABONO;
        a.monto = 150_000;
        a.estado = estado;
        a.transferidoEn = transferidoEn;
        return a;
    }

    @Test
    public void sinAbonosAprobadosNoHayNadaQueTransferir() {
        Cuota c = cuota(abono(1, Abono.PENDIENTE, 0), abono(2, Abono.EN_PROCESO, 0), abono(3, Abono.RECHAZADO, 0));
        assertEquals(ReglasPago.EstadoTransferencia.SIN_PAGOS, ReglasPago.estadoTransferencia(c, true));
        assertEquals(ReglasPago.EstadoTransferencia.SIN_PAGOS, ReglasPago.estadoTransferencia(new Cuota(), false));
    }

    @Test
    public void conDineroPagadoYSinCuentaElPropietarioDebeRegistrarla() {
        Cuota c = cuota(abono(1, Abono.APROBADO, 0), abono(2, Abono.PENDIENTE, 0));
        assertEquals(ReglasPago.EstadoTransferencia.SIN_CUENTA, ReglasPago.estadoTransferencia(c, false));
    }

    @Test
    public void conCuentaLoPagadoQuedaPorTransferir() {
        Cuota c = cuota(abono(1, Abono.APROBADO, 0), abono(2, Abono.PENDIENTE, 0));
        assertEquals(ReglasPago.EstadoTransferencia.POR_TRANSFERIR, ReglasPago.estadoTransferencia(c, true));
    }

    @Test
    public void unAbonoTransferidoYOtroNoSiguePorTransferir() {
        Cuota c = cuota(abono(1, Abono.APROBADO, 1000), abono(2, Abono.APROBADO, 0));
        assertEquals(ReglasPago.EstadoTransferencia.POR_TRANSFERIR, ReglasPago.estadoTransferencia(c, true));
    }

    @Test
    public void cuandoTodoLoAprobadoSeTransfirioEstaTransferido() {
        Cuota c = cuota(abono(1, Abono.APROBADO, 1000), abono(2, Abono.APROBADO, 2000), abono(3, Abono.PENDIENTE, 0));
        assertEquals(ReglasPago.EstadoTransferencia.TRANSFERIDO, ReglasPago.estadoTransferencia(c, true));
        // Transferido es transferido aunque después el propietario borre su cuenta.
        assertEquals(ReglasPago.EstadoTransferencia.TRANSFERIDO, ReglasPago.estadoTransferencia(c, false));
        assertEquals(2000, ReglasPago.ultimaTransferencia(c));
    }

    @Test
    public void sinTransferenciasLaUltimaFechaEsCero() {
        assertEquals(0, ReglasPago.ultimaTransferencia(cuota(abono(1, Abono.APROBADO, 0))));
    }
}
