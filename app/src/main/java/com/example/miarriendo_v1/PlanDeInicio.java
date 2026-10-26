package com.example.miarriendo_v1;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Qué módulos lleva el inicio unificado y en qué orden, según el rol y el estado del usuario.
 * Sigue el orden de la Presentación de Figma (531:3895, 19/10): Encabezado con sugerencias → Atajos → módulo
 * principal → el resto. Si un módulo no aplica no está en la lista: nunca se dibuja deshabilitado.
 * Atención y Solicitudes recibidas siguen unidas en Pendientes.
 */
final class PlanDeInicio {

    enum Modulo {
        ENCABEZADO,
        ATAJOS,
        TU_SOLICITUD,
        MI_ARRIENDO,
        RECAUDO,
        PENDIENTES,
        PRIMEROS_PASOS,
        EXPLORAR_MAPA,
        PARA_TI,
        DETALLE_ARRIENDO,
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
     * @param solicitudEnviada el inquilino tiene una solicitud que mostrar en "Tu solicitud".
     * @param hayPendientes    el propietario tiene un pago vencido o una solicitud por responder.
     */
    static List<Modulo> modulos(Perfil perfil, boolean solicitudEnviada, boolean hayPendientes) {
        List<Modulo> m = new ArrayList<>();
        switch (perfil) {
            case INQUILINO_SIN_ARRIENDO:
                m.add(Modulo.ENCABEZADO);
                if (solicitudEnviada) {
                    m.add(Modulo.TU_SOLICITUD);
                }
                // Sin Atajos: Explorar en mapa lleva al mapa y Para ti muestra los recién publicados.
                m.add(Modulo.EXPLORAR_MAPA);
                m.add(Modulo.PARA_TI);
                break;
            case INQUILINO_CON_ARRIENDO:
                m.add(Modulo.ENCABEZADO);
                m.add(Modulo.ATAJOS);
                m.add(Modulo.MI_ARRIENDO);
                m.add(Modulo.DETALLE_ARRIENDO);
                break;
            case PROPIETARIO:
                m.add(Modulo.ENCABEZADO);
                m.add(Modulo.ATAJOS);
                m.add(Modulo.RECAUDO);
                if (hayPendientes) {
                    m.add(Modulo.PENDIENTES);
                }
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
        return modulos(datos.perfil(), datos.tieneSolicitudEnviada(), datos.tienePendientes(System.currentTimeMillis()));
    }
}
