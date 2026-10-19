package com.example.miarriendo_v1;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lo que el propietario tiene que atender, para Módulo / Pendientes (482:2565): primero los pagos vencidos
 * (el más antiguo arriba) y luego las solicitudes por responder (la más nueva arriba). Mantenimiento queda
 * fuera hasta que la app guarde esas solicitudes. Java puro: se prueba con JUnit.
 */
final class PendientesPropietario {

    /** Filas que caben en el módulo; el resto se ve con "Ver todo". */
    static final int MAXIMO = 3;

    static final class Pendiente {

        enum Tipo {
            PAGO_VENCIDO,
            SOLICITUD
        }

        final Tipo tipo;
        final Propiedad propiedad;
        final Cuota cuota;
        final SolicitudArriendo solicitud;

        private Pendiente(Tipo tipo, Propiedad propiedad, Cuota cuota, SolicitudArriendo solicitud) {
            this.tipo = tipo;
            this.propiedad = propiedad;
            this.cuota = cuota;
            this.solicitud = solicitud;
        }
    }

    private PendientesPropietario() {
    }

    static List<Pendiente> todos(List<Propiedad> propiedades, Map<String, List<Cuota>> cuotas,
                                 List<SolicitudArriendo> solicitudes, long ahora) {
        List<Pendiente> vencidos = new ArrayList<>();
        for (Propiedad p : propiedades) {
            List<Cuota> deLaPropiedad = cuotas.get(p.id);
            Cuota cuota = deLaPropiedad == null ? null : ReglasPago.cuotaActiva(deLaPropiedad, ahora);
            if (cuota != null && ReglasPago.estadoVisible(cuota, ahora) == ReglasPago.EstadoVisible.VENCIDO) {
                vencidos.add(new Pendiente(Pendiente.Tipo.PAGO_VENCIDO, p, cuota, null));
            }
        }
        vencidos.sort((a, b) -> Long.compare(a.cuota.vence, b.cuota.vence));

        List<Pendiente> nuevas = new ArrayList<>();
        for (SolicitudArriendo s : solicitudes) {
            if (s.pendiente()) {
                nuevas.add(new Pendiente(Pendiente.Tipo.SOLICITUD, null, null, s));
            }
        }
        nuevas.sort((a, b) -> Long.compare(b.solicitud.creadaEn, a.solicitud.creadaEn));

        List<Pendiente> todos = new ArrayList<>(vencidos);
        todos.addAll(nuevas);
        return todos;
    }
}
