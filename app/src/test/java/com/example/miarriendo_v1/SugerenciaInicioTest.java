package com.example.miarriendo_v1;

import static com.example.miarriendo_v1.SugerenciaInicio.Tipo.*;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Sugerencias del encabezado: tareas fijas que se van al hacerlas y nunca repiten lo que ya está en pantalla. */
public class SugerenciaInicioTest {

    private static final long DIA = 24L * 60 * 60 * 1000;
    private static final long AHORA = 1_000 * DIA;
    private static final String CELULAR = "3001234567";

    private static final String INQ = RegisterRoleActivity.ROL_INQUILINO;
    private static final String PROP = RegisterRoleActivity.ROL_PROPIETARIO;

    private final Set<String> completadas = new HashSet<>();

    private static Propiedad propiedad() {
        Propiedad p = new Propiedad();
        p.id = "casa";
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

    private static DatosInicio datos(String rol, Cuota cuota) {
        Propiedad p = propiedad();
        Map<String, List<Cuota>> cuotas = new HashMap<>();
        if (cuota != null) {
            cuotas.put(p.id, Collections.singletonList(cuota));
        }
        return new DatosInicio(rol, Collections.singletonList(p), null, cuotas, true);
    }

    private List<SugerenciaInicio.Tipo> tipos(DatosInicio datos, String telefono, boolean huella) {
        List<SugerenciaInicio.Tipo> tipos = new ArrayList<>();
        for (SugerenciaInicio s : SugerenciaInicio.elegir(datos, telefono, completadas, huella, AHORA)) {
            tipos.add(s.tipo);
        }
        return tipos;
    }

    @Test
    public void inquilinoConArriendoSinCelularNiHuella() {
        assertEquals(Arrays.asList(CELULAR_NEQUI, HUELLA), tipos(datos(INQ, null), "12345", true));
    }

    @Test
    public void cadaTareaSeVaCuandoSeHace() {
        assertEquals(Collections.singletonList(HUELLA), tipos(datos(INQ, null), CELULAR, true));
        assertEquals(Collections.emptyList(), tipos(datos(INQ, null), CELULAR, false));
    }

    @Test
    public void elComprobanteVaPrimeroYSoloSiMiArriendoNoLoOfreceYa() {
        assertEquals(Arrays.asList(COMPROBANTE, CELULAR_NEQUI), tipos(datos(INQ, cuotaConAbono(false, DIA, -1)), null, false));
        // Pagada: Mi arriendo ya muestra "Ver comprobante".
        assertEquals(Collections.emptyList(), tipos(datos(INQ, cuotaConAbono(true, DIA, -1)), CELULAR, false));
        // Hace más de 3 días ya no es novedad.
        assertEquals(Collections.emptyList(), tipos(datos(INQ, cuotaConAbono(false, 4 * DIA, -1)), CELULAR, false));
    }

    @Test
    public void elComprobanteAbiertoNoVuelve() {
        completadas.add("comprobante_c1_1");
        assertEquals(Collections.emptyList(), tipos(datos(INQ, cuotaConAbono(false, DIA, -1)), CELULAR, false));
    }

    @Test
    public void elPropietarioVeLaTransferenciaDeLosUltimos3Dias() {
        List<SugerenciaInicio> s = SugerenciaInicio.elegir(datos(PROP, cuotaConAbono(true, 2 * DIA, DIA)),
                null, completadas, false, AHORA);
        assertEquals(TRANSFERENCIA, s.get(0).tipo);
        assertEquals("transferencia_c1", s.get(0).clave);
        assertEquals(Collections.emptyList(), tipos(datos(PROP, cuotaConAbono(true, 5 * DIA, 4 * DIA)), null, false));
        assertEquals(Collections.emptyList(), tipos(datos(PROP, cuotaConAbono(true, DIA, -1)), null, false));
    }

    @Test
    public void inquilinoSinArriendoVeAsiFuncionaHastaQueEnvieUnaSolicitudOLoAbra() {
        assertEquals(Collections.singletonList(ASI_FUNCIONA), tipos(new DatosInicio(INQ, null, null), null, false));
        DatosInicio conSolicitud = new DatosInicio(INQ, null, Collections.singletonList(new SolicitudArriendo()));
        assertEquals(Collections.emptyList(), tipos(conSolicitud, null, false));
        completadas.add(SugerenciaInicio.CLAVE_ASI_FUNCIONA);
        assertEquals(Collections.emptyList(), tipos(new DatosInicio(INQ, null, null), null, false));
    }

    @Test
    public void elPropietarioNuevoSoloVeLaHuella() {
        assertEquals(Collections.emptyList(), tipos(new DatosInicio(PROP, null, null), null, false));
        assertEquals(Collections.singletonList(HUELLA), tipos(new DatosInicio(PROP, null, null), null, true));
    }

    @Test
    public void nuncaMasDeTres() {
        List<SugerenciaInicio> s = SugerenciaInicio.elegir(datos(INQ, cuotaConAbono(false, DIA, -1)), null, completadas,
                true, AHORA);
        assertEquals(SugerenciaInicio.MAXIMO, s.size());
    }

    @Test
    public void sinRolNoHaySugerencias() {
        assertEquals(Collections.emptyList(), tipos(new DatosInicio(null, null, null), null, true));
    }
}
