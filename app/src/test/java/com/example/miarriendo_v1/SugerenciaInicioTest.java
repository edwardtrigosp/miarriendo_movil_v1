package com.example.miarriendo_v1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reglas de sugerencias de Figma (475:4586): una a la vez, acción pendiente → novedad → educativa. */
public class SugerenciaInicioTest {

    private static final long DIA = 24L * 60 * 60 * 1000;
    private static final long AHORA = 1_000 * DIA;
    private static final String CELULAR = "3001234567";

    private static final String INQ = RegisterRoleActivity.ROL_INQUILINO;
    private static final String PROP = RegisterRoleActivity.ROL_PROPIETARIO;

    private final Set<String> ocultas = new HashSet<>();

    private static Propiedad propiedad(long inquilinoDesde) {
        Propiedad p = new Propiedad();
        p.id = "casa";
        p.inquilinoDesde = inquilinoDesde;
        return p;
    }

    /** Cuota con un abono aprobado {@code pagadoHace} y, si se pide, transferido {@code transferidoHace}. */
    private static Cuota cuotaConAbono(boolean pagada, long pagadoHace, long transferidoHace) {
        Cuota c = new Cuota();
        c.id = "c1";
        c.periodo = "2026-11";
        c.monto = 450_000;
        c.vence = AHORA + 5 * DIA;
        c.modalidad = Cuota.MODALIDAD_ABONOS;
        c.estado = pagada ? Cuota.PAGADO : Cuota.PARCIAL;
        c.pagado = pagada ? 450_000 : 150_000;
        Abono a = new Abono();
        a.numero = 1;
        a.tipo = Abono.TIPO_ABONO;
        a.monto = c.pagado;
        a.estado = Abono.APROBADO;
        a.pagadoEn = AHORA - pagadoHace;
        a.transferidoEn = transferidoHace < 0 ? 0 : AHORA - transferidoHace;
        c.abonos.put("a1", a);
        return c;
    }

    private static DatosInicio datos(String rol, Propiedad p, Cuota cuota) {
        Map<String, List<Cuota>> cuotas = new HashMap<>();
        if (cuota != null) {
            cuotas.put(p.id, Collections.singletonList(cuota));
        }
        return new DatosInicio(rol, Collections.singletonList(p), null, cuotas, true);
    }

    private SugerenciaInicio.Tipo tipo(DatosInicio datos, String telefono) {
        SugerenciaInicio s = SugerenciaInicio.elegir(datos, telefono, ocultas, AHORA);
        return s == null ? null : s.tipo;
    }

    @Test
    public void sinCelularNequiValidoLaAccionVaPrimero() {
        assertEquals(SugerenciaInicio.Tipo.CELULAR_NEQUI, tipo(datos(INQ, propiedad(AHORA - DIA), null), "12345"));
    }

    @Test
    public void conCelularLaPrimeraSemanaDaLaBienvenida() {
        assertEquals(SugerenciaInicio.Tipo.BIENVENIDA, tipo(datos(INQ, propiedad(AHORA - DIA), null), CELULAR));
        assertNull(tipo(datos(INQ, propiedad(AHORA - 8 * DIA), null), CELULAR));
    }

    @Test
    public void elComprobanteSoloSiMiArriendoNoLoOfreceYa() {
        assertEquals(SugerenciaInicio.Tipo.COMPROBANTE,
                tipo(datos(INQ, propiedad(0), cuotaConAbono(false, DIA, -1)), CELULAR));
        // Pagada: Mi arriendo ya muestra "Ver comprobante".
        assertNull(tipo(datos(INQ, propiedad(0), cuotaConAbono(true, DIA, -1)), CELULAR));
        // Hace más de 3 días ya no es novedad.
        assertNull(tipo(datos(INQ, propiedad(0), cuotaConAbono(false, 4 * DIA, -1)), CELULAR));
    }

    @Test
    public void loCerradoNoVuelveAAparecer() {
        ocultas.add(SugerenciaInicio.CLAVE_CELULAR_NEQUI);
        assertEquals(SugerenciaInicio.Tipo.BIENVENIDA, tipo(datos(INQ, propiedad(AHORA - DIA), null), null));
    }

    @Test
    public void laBienvenidaNoSeCierra() {
        SugerenciaInicio s = SugerenciaInicio.elegir(datos(INQ, propiedad(AHORA - DIA), null), CELULAR, ocultas, AHORA);
        assertNull(s.clave);
    }

    @Test
    public void elPropietarioVeLaTransferenciaDeLosUltimos3Dias() {
        SugerenciaInicio s = SugerenciaInicio.elegir(datos(PROP, propiedad(0), cuotaConAbono(true, 2 * DIA, DIA)),
                null, ocultas, AHORA);
        assertEquals(SugerenciaInicio.Tipo.TRANSFERENCIA, s.tipo);
        assertEquals("transferencia_c1", s.clave);
        assertNull(tipo(datos(PROP, propiedad(0), cuotaConAbono(true, 5 * DIA, 4 * DIA)), null));
        assertNull(tipo(datos(PROP, propiedad(0), cuotaConAbono(true, DIA, -1)), null));
    }

    @Test
    public void inquilinoSinArriendoVeAsiFuncionaHastaQueEnvieUnaSolicitud() {
        DatosInicio sinNada = new DatosInicio(INQ, null, null);
        assertEquals(SugerenciaInicio.Tipo.ASI_FUNCIONA, tipo(sinNada, null));
        DatosInicio conSolicitud = new DatosInicio(INQ, null, Collections.singletonList(new SolicitudArriendo()));
        assertNull(tipo(conSolicitud, null));
    }

    @Test
    public void elPropietarioNuevoNoTieneSugerencias() {
        assertNull(tipo(new DatosInicio(PROP, null, null), null));
    }
}
