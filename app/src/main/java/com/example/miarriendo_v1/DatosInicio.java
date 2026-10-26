package com.example.miarriendo_v1;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Datos base del inicio unificado, leídos una vez y compartidos por los módulos para que no repitan
 * la misma consulta. Es Java puro: la carga desde Firebase está en {@link CargaDatosInicio}.
 */
final class DatosInicio {

    final String rol;
    /** Inquilino: propiedades donde es el inquilino actual. Propietario: sus propiedades. */
    final List<Propiedad> propiedades;
    /** Inquilino: solicitudes que envió. Propietario: solicitudes que recibió. */
    final List<SolicitudArriendo> solicitudes;
    /** Cuotas por id de propiedad: las del inquilino actual (inquilino: las suyas). */
    final Map<String, List<Cuota>> cuotas;
    /** El propietario registró una cuenta bancaria completa para recibir las transferencias. */
    final boolean tieneCuenta;
    /** Propietario: daños que le reportaron sus inquilinos. Vacía para el inquilino. */
    final List<ReporteDano> reportes;

    DatosInicio(String rol, List<Propiedad> propiedades, List<SolicitudArriendo> solicitudes) {
        this(rol, propiedades, solicitudes, null, false);
    }

    DatosInicio(String rol, List<Propiedad> propiedades, List<SolicitudArriendo> solicitudes,
                Map<String, List<Cuota>> cuotas, boolean tieneCuenta) {
        this(rol, propiedades, solicitudes, cuotas, tieneCuenta, null);
    }

    DatosInicio(String rol, List<Propiedad> propiedades, List<SolicitudArriendo> solicitudes,
                Map<String, List<Cuota>> cuotas, boolean tieneCuenta, List<ReporteDano> reportes) {
        this.rol = rol;
        this.propiedades = propiedades == null ? new ArrayList<>() : propiedades;
        this.solicitudes = solicitudes == null ? new ArrayList<>() : solicitudes;
        this.cuotas = cuotas == null ? new HashMap<>() : cuotas;
        this.tieneCuenta = tieneCuenta;
        this.reportes = reportes == null ? new ArrayList<>() : reportes;
    }

    /** Las cuotas de una propiedad; vacía si no tiene. */
    List<Cuota> cuotasDe(Propiedad p) {
        List<Cuota> lista = p == null ? null : cuotas.get(p.id);
        return lista == null ? Collections.emptyList() : lista;
    }

    /** Inquilino con arriendo: la propiedad que arrienda. Null si no tiene. */
    Propiedad propiedadArrendada() {
        return esInquilino() && !propiedades.isEmpty() ? propiedades.get(0) : null;
    }

    boolean esInquilino() {
        return RegisterRoleActivity.ROL_INQUILINO.equals(rol);
    }

    boolean esPropietario() {
        return RegisterRoleActivity.ROL_PROPIETARIO.equals(rol);
    }

    PlanDeInicio.Perfil perfil() {
        if (esInquilino()) {
            return propiedades.isEmpty() ? PlanDeInicio.Perfil.INQUILINO_SIN_ARRIENDO
                    : PlanDeInicio.Perfil.INQUILINO_CON_ARRIENDO;
        }
        if (esPropietario()) {
            return propiedades.isEmpty() ? PlanDeInicio.Perfil.PROPIETARIO_NUEVO
                    : PlanDeInicio.Perfil.PROPIETARIO;
        }
        return PlanDeInicio.Perfil.DESCONOCIDO;
    }

    /** Una solicitud ya respondida se sigue mostrando en el inicio durante este tiempo desde que se envió. */
    static final long VIGENCIA_RESPONDIDA_MS = 30L * 24 * 60 * 60 * 1000;

    /** El inquilino tiene una solicitud que mostrar en "Tu solicitud". */
    boolean tieneSolicitudEnviada() {
        return solicitudParaMostrar(System.currentTimeMillis()) != null;
    }

    /**
     * La solicitud del módulo "Tu solicitud": la pendiente más reciente; si no hay, la última respondida
     * enviada hace menos de {@link #VIGENCIA_RESPONDIDA_MS} (no se guarda cuándo se respondió). Null si ninguna.
     */
    SolicitudArriendo solicitudParaMostrar(long ahora) {
        if (!esInquilino()) {
            return null;
        }
        SolicitudArriendo pendiente = null, respondida = null;
        for (SolicitudArriendo s : solicitudes) {
            if (s.pendiente()) {
                if (pendiente == null || s.creadaEn > pendiente.creadaEn) {
                    pendiente = s;
                }
            } else if (ahora - s.creadaEn < VIGENCIA_RESPONDIDA_MS
                    && (respondida == null || s.creadaEn > respondida.creadaEn)) {
                respondida = s;
            }
        }
        return pendiente != null ? pendiente : respondida;
    }

    /** El propietario tiene algo que atender: un pago vencido, una solicitud por responder o un daño reportado. */
    boolean tienePendientes(long ahora) {
        return esPropietario() && !pendientes(ahora).isEmpty();
    }

    List<PendientesPropietario.Pendiente> pendientes(long ahora) {
        return PendientesPropietario.todos(propiedades, cuotas, solicitudes, reportes, ahora);
    }

    RecaudoDelMes recaudo(long ahora) {
        return RecaudoDelMes.calcular(cuotas.values(), tieneCuenta, ahora);
    }
}
