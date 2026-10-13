package com.example.miarriendo_v1;

import java.util.ArrayList;
import java.util.List;

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

    DatosInicio(String rol, List<Propiedad> propiedades, List<SolicitudArriendo> solicitudes) {
        this.rol = rol;
        this.propiedades = propiedades == null ? new ArrayList<>() : propiedades;
        this.solicitudes = solicitudes == null ? new ArrayList<>() : solicitudes;
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

    /** El inquilino tiene alguna solicitud enviada (cuál se muestra lo decide el módulo Tu solicitud). */
    boolean tieneSolicitudEnviada() {
        return esInquilino() && !solicitudes.isEmpty();
    }

    /** El propietario tiene al menos una solicitud por responder. */
    boolean tieneSolicitudesPendientes() {
        return esPropietario() && solicitudesPendientes() > 0;
    }

    int solicitudesPendientes() {
        int n = 0;
        for (SolicitudArriendo s : solicitudes) {
            if (s.pendiente()) {
                n++;
            }
        }
        return n;
    }
}
