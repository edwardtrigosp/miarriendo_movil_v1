package com.example.miarriendo_v1;

import static com.example.miarriendo_v1.PlanDeInicio.Modulo.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Las 4 columnas del mapa de módulos de Figma (448:4299). */
public class PlanDeInicioTest {

    private static final String INQ = RegisterRoleActivity.ROL_INQUILINO;
    private static final String PROP = RegisterRoleActivity.ROL_PROPIETARIO;

    private static SolicitudArriendo solicitud(String estado) {
        SolicitudArriendo s = new SolicitudArriendo();
        s.estado = estado;
        return s;
    }

    private static List<PlanDeInicio.Modulo> plan(String rol, int propiedades, SolicitudArriendo... solicitudes) {
        List<Propiedad> lista = new ArrayList<>();
        for (int i = 0; i < propiedades; i++) {
            lista.add(new Propiedad());
        }
        return PlanDeInicio.modulos(new DatosInicio(rol, lista, Arrays.asList(solicitudes)));
    }

    @Test
    public void inquilinoSinArriendoNiSolicitud() {
        assertEquals(Arrays.asList(ENCABEZADO, BUSCAR_HOGAR, ATAJOS, PARA_TI), plan(INQ, 0));
    }

    @Test
    public void inquilinoSinArriendoConSolicitudVeTuSolicitudEnSegundoLugar() {
        assertEquals(Arrays.asList(ENCABEZADO, TU_SOLICITUD, BUSCAR_HOGAR, ATAJOS, PARA_TI),
                plan(INQ, 0, solicitud(SolicitudArriendo.ESTADO_RECHAZADA)));
    }

    @Test
    public void inquilinoConArriendo() {
        assertEquals(Arrays.asList(ENCABEZADO, MI_ARRIENDO, ATAJOS, RECORDATORIOS, BUSCAR_HOGAR),
                plan(INQ, 1, solicitud(SolicitudArriendo.ESTADO_ACEPTADA)));
    }

    @Test
    public void propietarioSinSolicitudesPendientes() {
        assertEquals(Arrays.asList(ENCABEZADO, RECAUDO, ATAJOS, ATENCION, MIS_PROPIEDADES),
                plan(PROP, 2, solicitud(SolicitudArriendo.ESTADO_ACEPTADA)));
    }

    @Test
    public void propietarioConSolicitudPendienteVeSolicitudesRecibidasEnTercerLugar() {
        assertEquals(Arrays.asList(ENCABEZADO, RECAUDO, SOLICITUDES_RECIBIDAS, ATAJOS, ATENCION, MIS_PROPIEDADES),
                plan(PROP, 1, solicitud(SolicitudArriendo.ESTADO_PENDIENTE)));
    }

    @Test
    public void unaSolicitudSinEstadoCuentaComoPendiente() {
        assertTrue(plan(PROP, 1, solicitud(null)).contains(SOLICITUDES_RECIBIDAS));
    }

    @Test
    public void propietarioNuevoSoloVePrimerosPasos() {
        assertEquals(Arrays.asList(ENCABEZADO, PRIMEROS_PASOS), plan(PROP, 0, solicitud(SolicitudArriendo.ESTADO_PENDIENTE)));
    }

    @Test
    public void sinRolNoHayModulos() {
        assertEquals(Collections.emptyList(), plan(null, 3));
        assertEquals(Collections.emptyList(), plan("admin", 0));
    }

    @Test
    public void listasNulasSeTratanComoVacias() {
        DatosInicio datos = new DatosInicio(INQ, null, null);
        assertEquals(PlanDeInicio.Perfil.INQUILINO_SIN_ARRIENDO, datos.perfil());
        assertEquals(Arrays.asList(ENCABEZADO, BUSCAR_HOGAR, ATAJOS, PARA_TI), PlanDeInicio.modulos(datos));
    }

    @Test
    public void ningunModuloSeRepite() {
        for (PlanDeInicio.Perfil perfil : PlanDeInicio.Perfil.values()) {
            List<PlanDeInicio.Modulo> m = PlanDeInicio.modulos(perfil, true, true);
            assertEquals(perfil.name(), m.size(), new java.util.HashSet<>(m).size());
        }
    }
}
