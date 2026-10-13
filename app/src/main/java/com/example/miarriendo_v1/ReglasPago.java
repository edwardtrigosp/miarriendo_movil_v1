package com.example.miarriendo_v1;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Lo que se sabe de una cuota a partir de sus datos: qué se muestra, qué abono toca pagar y cuál es la
 * cuota del momento. No toca Android ni Firebase, así que se prueba con JUnit. El servidor es quien
 * manda: estas reglas solo deciden qué enseñar y qué botón ofrecer.
 */
final class ReglasPago {

    /** Mientras la próxima cuota sin pagar venza dentro de este plazo, es la que se muestra. */
    static final int VENTANA_CUOTA_ACTIVA_DIAS = 31;

    enum EstadoVisible {
        /** "No has pagado" */
        PENDIENTE,
        /** "Parcial · 1 de 3" */
        PARCIAL,
        PAGADO,
        /** Pasó la fecha límite sin pagar del todo. */
        VENCIDO
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

    /** Se puede pagar el saldo de una vez: ya hubo un abono, faltan otros y no hay un cobro en curso. */
    static boolean puedePagarSaldo(Cuota cuota) {
        return Cuota.MODALIDAD_ABONOS.equals(cuota.modalidad)
                && cuota.pagado > 0
                && saldo(cuota) > 0
                && enProceso(cuota) == null;
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

    /** Reparte {@code monto} en {@code cantidad} abonos; el último absorbe lo que sobre (igual que el servidor). */
    static long[] montosDeAbonos(long monto, int cantidad) {
        long base = monto / cantidad;
        long[] montos = new long[cantidad];
        for (int i = 0; i < cantidad - 1; i++) {
            montos[i] = base;
        }
        montos[cantidad - 1] = monto - base * (cantidad - 1);
        return montos;
    }
}
