package com.example.miarriendo_v1;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Qué módulos lleva el inicio unificado y en qué orden, según el rol y el estado del usuario.
 * Es el mapa de Figma en código: 00 · Inicio unificado · Mapa de módulos (448:4299).
 * Si un módulo no aplica no está en la lista: nunca se dibuja deshabilitado.
 */
final class PlanDeInicio {

    enum Modulo {
        ENCABEZADO,
        TU_SOLICITUD,
        MI_ARRIENDO,
        RECAUDO,
        SOLICITUDES_RECIBIDAS,
        PRIMEROS_PASOS,
        ATAJOS,
        RECORDATORIOS,
        ATENCION,
        PARA_TI,
        BUSCAR_HOGAR,
        MIS_PROPIEDADES
    }

    /** Columnas del mapa de módulos. "Ambos roles" queda fuera: hoy cada usuario tiene un solo rol. */
    enum Perfil {
        INQUILINO_SIN_ARRIENDO,
        INQUILINO_CON_ARRIENDO,
        PROPIETARIO,
        PROPIETARIO_NUEVO,
        /** Sin rol conocido: no hay inicio que mostrar. */
        DESCONOCIDO
    }

    private PlanDeInicio() {
    }

    /**
     * @param solicitudEnviada      el inquilino tiene una solicitud que mostrar en "Tu solicitud".
     * @param solicitudesPendientes el propietario tiene solicitudes por responder.
     */
    static List<Modulo> modulos(Perfil perfil, boolean solicitudEnviada, boolean solicitudesPendientes) {
        List<Modulo> m = new ArrayList<>();
        switch (perfil) {
            case INQUILINO_SIN_ARRIENDO:
                m.add(Modulo.ENCABEZADO);
                if (solicitudEnviada) {
                    m.add(Modulo.TU_SOLICITUD);
                }
                // Sin Buscar hogar: el buscador va en el Encabezado y los resultados en Para ti.
                m.add(Modulo.ATAJOS);
                m.add(Modulo.PARA_TI);
                break;
            case INQUILINO_CON_ARRIENDO:
                m.add(Modulo.ENCABEZADO);
                m.add(Modulo.MI_ARRIENDO);
                m.add(Modulo.ATAJOS);
                m.add(Modulo.RECORDATORIOS);
                m.add(Modulo.BUSCAR_HOGAR);
                break;
            case PROPIETARIO:
                m.add(Modulo.ENCABEZADO);
                m.add(Modulo.RECAUDO);
                if (solicitudesPendientes) {
                    m.add(Modulo.SOLICITUDES_RECIBIDAS);
                }
                m.add(Modulo.ATAJOS);
                m.add(Modulo.ATENCION);
                m.add(Modulo.MIS_PROPIEDADES);
                break;
            case PROPIETARIO_NUEVO:
                m.add(Modulo.ENCABEZADO);
                m.add(Modulo.PRIMEROS_PASOS);
                break;
            default:
                return Collections.emptyList();
        }
        return Collections.unmodifiableList(m);
    }

    static List<Modulo> modulos(DatosInicio datos) {
        return modulos(datos.perfil(), datos.tieneSolicitudEnviada(), datos.tieneSolicitudesPendientes());
    }
}
