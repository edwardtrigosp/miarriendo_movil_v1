package com.example.miarriendo_v1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Módulo / Pendientes (482:2565): pagos vencidos primero y luego solicitudes nuevas. */
public class PendientesPropietarioTest {

    private static final long DIA = 24L * 60 * 60 * 1000;
    private static final long AHORA = 1_000 * DIA;

    private final List<Propiedad> propiedades = new ArrayList<>();
    private final Map<String, List<Cuota>> cuotas = new HashMap<>();

    private void propiedadConCuota(String id, long vence, String estado) {
        Propiedad p = new Propiedad();
        p.id = id;
        propiedades.add(p);
        Cuota c = new Cuota();
        c.id = id + "-c";
        c.monto = 450_000;
        c.vence = vence;
        c.estado = estado;
        cuotas.put(id, Collections.singletonList(c));
    }

    private static SolicitudArriendo solicitud(String id, String estado, long creadaEn) {
        SolicitudArriendo s = new SolicitudArriendo();
        s.id = id;
        s.estado = estado;
        s.creadaEn = creadaEn;
        return s;
    }

    private List<PendientesPropietario.Pendiente> todos(SolicitudArriendo... solicitudes) {
        return PendientesPropietario.todos(propiedades, cuotas, Arrays.asList(solicitudes), AHORA);
    }

    @Test
    public void vencidosPrimeroDelMasAntiguoYLuegoSolicitudesDeLaMasNueva() {
        propiedadConCuota("reciente", AHORA - DIA, Cuota.PENDIENTE);
        propiedadConCuota("antigua", AHORA - 10 * DIA, Cuota.PARCIAL);
        List<PendientesPropietario.Pendiente> p = todos(
                solicitud("vieja", SolicitudArriendo.ESTADO_PENDIENTE, AHORA - 5 * DIA),
                solicitud("nueva", SolicitudArriendo.ESTADO_PENDIENTE, AHORA - DIA));
        assertEquals(4, p.size());
        assertEquals("antigua", p.get(0).propiedad.id);
        assertEquals("reciente", p.get(1).propiedad.id);
        assertEquals("nueva", p.get(2).solicitud.id);
        assertEquals("vieja", p.get(3).solicitud.id);
    }

    @Test
    public void noSonPendientesLasCuotasAlDiaNiLasSolicitudesRespondidas() {
        propiedadConCuota("pagada", AHORA - DIA, Cuota.PAGADO);
        propiedadConCuota("por_vencer", AHORA + 3 * DIA, Cuota.PENDIENTE);
        assertTrue(todos(solicitud("r", SolicitudArriendo.ESTADO_RECHAZADA, AHORA),
                solicitud("a", SolicitudArriendo.ESTADO_ACEPTADA, AHORA)).isEmpty());
    }

    @Test
    public void unaPropiedadSinCuotasNoEsPendiente() {
        Propiedad libre = new Propiedad();
        libre.id = "libre";
        propiedades.add(libre);
        assertTrue(todos().isEmpty());
    }
}
