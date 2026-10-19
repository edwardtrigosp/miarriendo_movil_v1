package com.example.miarriendo_v1;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * La sugerencia del encabezado, según las Reglas de sugerencias de Figma (475:4586): una sola a la vez, por
 * prioridad acción pendiente → novedad → educativa, y nunca la que repite lo que ya muestra un módulo.
 * Las que se cierran quedan en usuarios/{uid}/sugerencias_ocultas con su {@link #clave}. Java puro.
 * "Activa recordatorios de pago" queda fuera hasta que la app tenga recordatorios.
 */
final class SugerenciaInicio {

    enum Tipo {
        /** Inquilino sin arriendo que aún no envía ninguna solicitud. */
        ASI_FUNCIONA,
        /** Inquilino con arriendo sin un celular Nequi válido en el perfil. */
        CELULAR_NEQUI,
        /** Inquilino con arriendo: abono aprobado hace poco mientras Mi arriendo aún no ofrece el comprobante. */
        COMPROBANTE,
        /** Inquilino con arriendo durante su primera semana. No se cierra: se va sola. */
        BIENVENIDA,
        /** Propietario: miarriendo le transfirió un pago hace poco. */
        TRANSFERENCIA
    }

    static final String CLAVE_ASI_FUNCIONA = "asi_funciona";
    static final String CLAVE_CELULAR_NEQUI = "celular_nequi";

    static final long DIAS_BIENVENIDA_MS = TimeUnit.DAYS.toMillis(7);
    static final long DIAS_NOVEDAD_MS = TimeUnit.DAYS.toMillis(3);

    final Tipo tipo;
    /** Clave en sugerencias_ocultas; null si la sugerencia no se puede cerrar. */
    final String clave;
    final String propiedadId;
    final Cuota cuota;
    final Abono abono;

    private SugerenciaInicio(Tipo tipo, String clave, String propiedadId, Cuota cuota, Abono abono) {
        this.tipo = tipo;
        this.clave = clave;
        this.propiedadId = propiedadId;
        this.cuota = cuota;
        this.abono = abono;
    }

    /**
     * @param telefono el teléfono del perfil (usuarios/{uid}/telefono).
     * @param ocultas  las claves que el usuario ya cerró.
     * @return la sugerencia que toca o null si ninguna aplica (la franja no se dibuja).
     */
    static SugerenciaInicio elegir(DatosInicio datos, String telefono, Set<String> ocultas, long ahora) {
        switch (datos.perfil()) {
            case INQUILINO_SIN_ARRIENDO:
                if (datos.solicitudes.isEmpty() && !ocultas.contains(CLAVE_ASI_FUNCIONA)) {
                    return new SugerenciaInicio(Tipo.ASI_FUNCIONA, CLAVE_ASI_FUNCIONA, null, null, null);
                }
                return null;
            case INQUILINO_CON_ARRIENDO:
                return paraInquilinoConArriendo(datos, telefono, ocultas, ahora);
            case PROPIETARIO:
                return transferenciaReciente(datos, ocultas, ahora);
            default:
                return null;
        }
    }

    private static SugerenciaInicio paraInquilinoConArriendo(DatosInicio datos, String telefono, Set<String> ocultas,
                                                             long ahora) {
        Propiedad propiedad = datos.propiedadArrendada();
        // Acción pendiente
        if (CelularNequi.normalizar(telefono) == null && !ocultas.contains(CLAVE_CELULAR_NEQUI)) {
            return new SugerenciaInicio(Tipo.CELULAR_NEQUI, CLAVE_CELULAR_NEQUI, null, null, null);
        }
        // Novedades: el comprobante solo si Mi arriendo no lo ofrece ya (lo hace cuando la cuota está pagada).
        Cuota cuota = ReglasPago.cuotaActiva(datos.cuotasDe(propiedad), ahora);
        if (cuota != null && !cuota.pagada()) {
            Abono ultimo = ReglasPago.ultimoAprobado(cuota);
            if (ultimo != null && ultimo.pagadoEn > 0 && ahora - ultimo.pagadoEn < DIAS_NOVEDAD_MS) {
                String clave = "comprobante_" + cuota.id + "_" + ultimo.numero;
                if (!ocultas.contains(clave)) {
                    return new SugerenciaInicio(Tipo.COMPROBANTE, clave, propiedad.id, cuota, ultimo);
                }
            }
        }
        if (propiedad != null && propiedad.inquilinoDesde > 0 && ahora - propiedad.inquilinoDesde < DIAS_BIENVENIDA_MS) {
            return new SugerenciaInicio(Tipo.BIENVENIDA, null, propiedad.id, null, null);
        }
        return null;
    }

    /** La transferencia más reciente de los últimos 3 días, si el propietario no la cerró. */
    private static SugerenciaInicio transferenciaReciente(DatosInicio datos, Set<String> ocultas, long ahora) {
        SugerenciaInicio elegida = null;
        long masReciente = 0;
        for (Propiedad p : datos.propiedades) {
            List<Cuota> cuotas = datos.cuotasDe(p);
            for (Cuota cuota : cuotas) {
                long transferida = ReglasPago.ultimaTransferencia(cuota);
                String clave = "transferencia_" + cuota.id;
                if (transferida > masReciente && ahora - transferida < DIAS_NOVEDAD_MS && !ocultas.contains(clave)) {
                    masReciente = transferida;
                    elegida = new SugerenciaInicio(Tipo.TRANSFERENCIA, clave, p.id, cuota, null);
                }
            }
        }
        return elegida;
    }
}
