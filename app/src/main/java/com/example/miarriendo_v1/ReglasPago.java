package com.example.miarriendo_v1;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Lo que se sabe de una cuota a partir de sus datos: qué se muestra, si hay un cobro en curso y cuál es la
 * cuota del momento. Cada cobro de la cuota es un {@link Abono}: desde el 20/10/2026 hay uno solo por mes
 * (pago único); los meses viejos pueden tener varios abonos y un saldo. No toca Android ni Firebase, así que se prueba con JUnit. El servidor es quien
 * manda: estas reglas solo deciden qué enseñar y qué botón ofrecer.
 */
final class ReglasPago {

    /** Mientras la próxima cuota sin pagar venza dentro de este plazo, es la que se muestra. */
    static final int VENTANA_CUOTA_ACTIVA_DIAS = 31;

    enum EstadoVisible {
        /** "No has pagado" */
        PENDIENTE,
        /** Un mes viejo que quedó a medias en abonos. */
        PARCIAL,
        PAGADO,
        /** Pasó la fecha límite sin pagar del todo. */
        VENCIDO
    }

    /** Variante de Módulo / Mi arriendo (446:3500) en el inicio. */
    enum EstadoInicio {
        POR_PAGAR,
        /** Un mes viejo que quedó a medias en abonos: se paga lo que falta. */
        PARCIAL,
        VENCIDO,
        ESPERANDO_NEQUI,
        AL_DIA
    }

    /** Qué pasó con el dinero que el inquilino ya pagó, visto desde el propietario. */
    enum EstadoTransferencia {
        /** Todavía no hay nada pagado. */
        SIN_PAGOS,
        /** Hay dinero pagado pero el propietario no ha registrado dónde recibirlo. */
        SIN_CUENTA,
        /** miarriendo lo recibió y lo transferirá en el próximo lote. */
        POR_TRANSFERIR,
        /** Todo lo pagado ya se transfirió al propietario. */
        TRANSFERIDO
    }

    private ReglasPago() {
    }

    /**
     * Estado de la transferencia al propietario de lo pagado en la cuota. Un abono se da por transferido
     * cuando miarriendo anota la fecha en transferidoEn; la app solo lo lee.
     */
    static EstadoTransferencia estadoTransferencia(Cuota cuota, boolean propietarioTieneCuenta) {
        int aprobados = 0;
        int transferidos = 0;
        for (Abono abono : abonos(cuota)) {
            if (abono.aprobado()) {
                aprobados++;
                if (abono.transferidoEn > 0) {
                    transferidos++;
                }
            }
        }
        if (aprobados == 0) {
            return EstadoTransferencia.SIN_PAGOS;
        }
        if (transferidos == aprobados) {
            return EstadoTransferencia.TRANSFERIDO;
        }
        return propietarioTieneCuenta ? EstadoTransferencia.POR_TRANSFERIR : EstadoTransferencia.SIN_CUENTA;
    }

    /** Fecha de la transferencia más reciente de la cuota; 0 si no hay ninguna. */
    static long ultimaTransferencia(Cuota cuota) {
        long ultima = 0;
        for (Abono abono : abonos(cuota)) {
            ultima = Math.max(ultima, abono.transferidoEn);
        }
        return ultima;
    }

    /** Abonos de la cuota por número, con su clave ya puesta. */
    static List<Abono> abonos(Cuota cuota) {
        List<Abono> lista = new ArrayList<>();
        if (cuota == null || cuota.abonos == null) {
            return lista;
        }
        for (Map.Entry<String, Abono> entrada : cuota.abonos.entrySet()) {
            Abono abono = entrada.getValue();
            if (abono != null) {
                abono.clave = entrada.getKey();
                lista.add(abono);
            }
        }
        lista.sort(Comparator.comparingInt(a -> a.numero));
        return lista;
    }

    /** El primer abono que falta por pagar (pendiente o rechazado); null si no hay. */
    static Abono siguientePorPagar(Cuota cuota) {
        for (Abono abono : abonos(cuota)) {
            if (Abono.PENDIENTE.equals(abono.estado) || Abono.RECHAZADO.equals(abono.estado)) {
                return abono;
            }
        }
        return null;
    }

    /** El abono con un cobro esperando la aprobación en Nequi; null si no hay. */
    static Abono enProceso(Cuota cuota) {
        for (Abono abono : abonos(cuota)) {
            if (abono.enProceso()) {
                return abono;
            }
        }
        return null;
    }

    /** El último abono aprobado (el más reciente por fecha de pago); null si no hay. */
    static Abono ultimoAprobado(Cuota cuota) {
        Abono ultimo = null;
        for (Abono abono : abonos(cuota)) {
            if (abono.aprobado() && (ultimo == null || abono.pagadoEn >= ultimo.pagadoEn)) {
                ultimo = abono;
            }
        }
        return ultimo;
    }

    /** Cuándo se completó el pago de la cuota: el último abono aprobado; si no hay, su vencimiento. */
    static long fechaDePago(Cuota cuota) {
        Abono ultimo = ultimoAprobado(cuota);
        return ultimo == null || ultimo.pagadoEn == 0 ? cuota.vence : ultimo.pagadoEn;
    }

    static long saldo(Cuota cuota) {
        return Math.max(0, cuota.monto - cuota.pagado);
    }

    static int abonosAprobados(Cuota cuota) {
        int n = 0;
        for (Abono abono : abonos(cuota)) {
            if (abono.aprobado()) {
                n++;
            }
        }
        return n;
    }

    static EstadoVisible estadoVisible(Cuota cuota, long ahora) {
        if (cuota.pagada()) {
            return EstadoVisible.PAGADO;
        }
        if (ahora > cuota.vence) {
            return EstadoVisible.VENCIDO;
        }
        return cuota.pagado > 0 ? EstadoVisible.PARCIAL : EstadoVisible.PENDIENTE;
    }

    /** Un cobro esperando Nequi manda sobre todo lo demás: mientras exista no se puede iniciar otro. */
    static EstadoInicio estadoInicio(Cuota cuota, long ahora) {
        if (enProceso(cuota) != null) {
            return EstadoInicio.ESPERANDO_NEQUI;
        }
        switch (estadoVisible(cuota, ahora)) {
            case PAGADO:
                return EstadoInicio.AL_DIA;
            case VENCIDO:
                return EstadoInicio.VENCIDO;
            case PARCIAL:
                return EstadoInicio.PARCIAL;
            default:
                return EstadoInicio.POR_PAGAR;
        }
    }

    /** La cuota que sigue a la activa (para "Próximo pago: 5 de diciembre"); null si no hay. */
    static Cuota siguienteCuota(List<Cuota> cuotas, Cuota activa) {
        Cuota siguiente = null;
        for (Cuota cuota : cuotas) {
            if (cuota.vence > activa.vence && (siguiente == null || cuota.vence < siguiente.vence)) {
                siguiente = cuota;
            }
        }
        return siguiente;
    }

    /**
     * La cuota que ve el inquilino en "Mi arriendo": la primera sin pagar si ya está cerca; si no, la
     * última pagada, para no mostrar "No has pagado" de un mes lejano justo después de pagar.
     */
    static Cuota cuotaActiva(List<Cuota> cuotas, long ahora) {
        Cuota primeraSinPagar = null;
        Cuota ultimaPagada = null;
        for (Cuota cuota : cuotas) {
            if (cuota.pagada()) {
                if (ultimaPagada == null || cuota.vence > ultimaPagada.vence) {
                    ultimaPagada = cuota;
                }
            } else if (primeraSinPagar == null || cuota.vence < primeraSinPagar.vence) {
                primeraSinPagar = cuota;
            }
        }
        if (primeraSinPagar == null) {
            return ultimaPagada;
        }
        long limite = ahora + TimeUnit.DAYS.toMillis(VENTANA_CUOTA_ACTIVA_DIAS);
        if (ultimaPagada != null && primeraSinPagar.vence > limite) {
            return ultimaPagada;
        }
        return primeraSinPagar;
    }
}
