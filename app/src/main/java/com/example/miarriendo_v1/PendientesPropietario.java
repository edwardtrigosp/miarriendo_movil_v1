package com.example.miarriendo_v1;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Lo que el propietario tiene que atender, para Módulo / Pendientes (482:2565), por prioridad: pagos vencidos
 * (el más antiguo arriba), daños urgentes, solicitudes por responder y daños que pueden esperar (los más nuevos
 * arriba). Java puro: se prueba con JUnit.
 */
final class PendientesPropietario {

    /** Filas que caben en el módulo; el resto se ve con "Ver todo". */
    static final int MAXIMO = 3;

    static final class Pendiente {

        enum Tipo {
            PAGO_VENCIDO,
            SOLICITUD,
            REPORTE
        }

        final Tipo tipo;
        final Propiedad propiedad;
        final Cuota cuota;
        final SolicitudArriendo solicitud;
        final ReporteDano reporte;

        private Pendiente(Tipo tipo, Propiedad propiedad, Cuota cuota, SolicitudArriendo solicitud, ReporteDano reporte) {
            this.tipo = tipo;
            this.propiedad = propiedad;
            this.cuota = cuota;
            this.solicitud = solicitud;
            this.reporte = reporte;
        }
    }

    private PendientesPropietario() {
    }

    static List<Pendiente> todos(List<Propiedad> propiedades, Map<String, List<Cuota>> cuotas,
                                 List<SolicitudArriendo> solicitudes, long ahora) {
        return todos(propiedades, cuotas, solicitudes, Collections.emptyList(), ahora);
    }

    static List<Pendiente> todos(List<Propiedad> propiedades, Map<String, List<Cuota>> cuotas,
                                 List<SolicitudArriendo> solicitudes, List<ReporteDano> reportes, long ahora) {
        List<Pendiente> vencidos = new ArrayList<>();
        for (Propiedad p : propiedades) {
            List<Cuota> deLaPropiedad = cuotas.get(p.id);
            Cuota cuota = deLaPropiedad == null ? null : ReglasPago.cuotaActiva(deLaPropiedad, ahora);
            if (cuota != null && ReglasPago.estadoVisible(cuota, ahora) == ReglasPago.EstadoVisible.VENCIDO) {
                vencidos.add(new Pendiente(Pendiente.Tipo.PAGO_VENCIDO, p, cuota, null, null));
            }
        }
        vencidos.sort((a, b) -> Long.compare(a.cuota.vence, b.cuota.vence));

        List<Pendiente> nuevas = new ArrayList<>();
        for (SolicitudArriendo s : solicitudes) {
            if (s.pendiente()) {
                nuevas.add(new Pendiente(Pendiente.Tipo.SOLICITUD, null, null, s, null));
            }
        }
        nuevas.sort((a, b) -> Long.compare(b.solicitud.creadaEn, a.solicitud.creadaEn));

        List<Pendiente> urgentes = new ArrayList<>(), pueden = new ArrayList<>();
        for (ReporteDano r : reportes) {
            if (r.abierto()) {
                (r.urgente ? urgentes : pueden).add(new Pendiente(Pendiente.Tipo.REPORTE, null, null, null, r));
            }
        }
        urgentes.sort((a, b) -> Long.compare(b.reporte.creadoEn, a.reporte.creadoEn));
        pueden.sort((a, b) -> Long.compare(b.reporte.creadoEn, a.reporte.creadoEn));

        List<Pendiente> todos = new ArrayList<>(vencidos);
        todos.addAll(urgentes);
        todos.addAll(nuevas);
        todos.addAll(pueden);
        return todos;
    }
}
