package com.example.miarriendo_v1;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Las sugerencias del encabezado (Figma: Sugerencia 430:2335, tarjetas en carrusel como en Nu). Son tareas
 * recomendadas fijas: no se cierran, desaparecen cuando la persona hace la acción. Cada una aplica a un momento
 * y nunca repite lo que ya muestra la pantalla (botón principal, atajos u otro módulo).
 * Las que se completan al abrirlas (comprobante, transferencia, así funciona) quedan en
 * usuarios/{uid}/sugerencias_ocultas con su {@link #clave}. Java puro.
 * "Activa recordatorios" queda fuera hasta que la app tenga recordatorios.
 */
final class SugerenciaInicio {

    enum Tipo {
        /** Inquilino sin arriendo que aún no envía ninguna solicitud. */
        ASI_FUNCIONA,
        /** Inquilino con arriendo sin un celular Nequi válido en el perfil. */
        CELULAR_NEQUI,
        /** Inquilino con arriendo: abono aprobado hace poco mientras Mi arriendo aún no ofrece el comprobante. */
        COMPROBANTE,
        /** Propietario: miarriendo le transfirió un pago hace poco. */
        TRANSFERENCIA,
        /** El teléfono tiene huella y la persona aún no la usa para entrar. */
        HUELLA
    }

    static final String CLAVE_ASI_FUNCIONA = "asi_funciona";

    /** Tarjetas que caben en el carrusel sin que se vuelva una lista. */
    static final int MAXIMO = 3;
    static final long DIAS_NOVEDAD_MS = TimeUnit.DAYS.toMillis(3);

    final Tipo tipo;
    /** Clave en sugerencias_ocultas cuando se completa al abrirla; null si se completa haciendo la acción. */
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

    private static SugerenciaInicio de(Tipo tipo) {
        return new SugerenciaInicio(tipo, null, null, null, null);
    }

    /**
     * @param telefono        el teléfono del perfil (usuarios/{uid}/telefono).
     * @param completadas     las claves de las que ya se abrieron.
     * @param huellaPendiente el teléfono tiene huella registrada y el ingreso con huella está apagado.
     * @return las sugerencias en orden (novedad → acción pendiente → educativa), como máximo {@link #MAXIMO};
     * vacía si ninguna aplica (el carrusel no se dibuja).
     */
    static List<SugerenciaInicio> elegir(DatosInicio datos, String telefono, Set<String> completadas,
                                         boolean huellaPendiente, long ahora) {
        List<SugerenciaInicio> s = new ArrayList<>();
        switch (datos.perfil()) {
            case INQUILINO_SIN_ARRIENDO:
                if (datos.solicitudes.isEmpty() && !completadas.contains(CLAVE_ASI_FUNCIONA)) {
                    s.add(new SugerenciaInicio(Tipo.ASI_FUNCIONA, CLAVE_ASI_FUNCIONA, null, null, null));
                }
                break;
            case INQUILINO_CON_ARRIENDO:
                SugerenciaInicio comprobante = comprobanteReciente(datos, completadas, ahora);
                if (comprobante != null) {
                    s.add(comprobante);
                }
                if (CelularNequi.normalizar(telefono) == null) {
                    s.add(de(Tipo.CELULAR_NEQUI));
                }
                break;
            case PROPIETARIO:
                SugerenciaInicio transferencia = transferenciaReciente(datos, completadas, ahora);
                if (transferencia != null) {
                    s.add(transferencia);
                }
                break;
            case PROPIETARIO_NUEVO:
                break;
            default:
                return Collections.emptyList();
        }
        if (huellaPendiente) {
            s.add(de(Tipo.HUELLA));
        }
        return s.size() > MAXIMO ? s.subList(0, MAXIMO) : s;
    }

    /** El comprobante del último abono aprobado, solo si Mi arriendo no lo ofrece ya (lo hace con la cuota pagada). */
    private static SugerenciaInicio comprobanteReciente(DatosInicio datos, Set<String> completadas, long ahora) {
        Propiedad propiedad = datos.propiedadArrendada();
        Cuota cuota = ReglasPago.cuotaActiva(datos.cuotasDe(propiedad), ahora);
        if (cuota == null || cuota.pagada()) {
            return null;
        }
        Abono ultimo = ReglasPago.ultimoAprobado(cuota);
        if (ultimo == null || ultimo.pagadoEn <= 0 || ahora - ultimo.pagadoEn >= DIAS_NOVEDAD_MS) {
            return null;
        }
        String clave = "comprobante_" + cuota.id + "_" + ultimo.numero;
        return completadas.contains(clave) ? null : new SugerenciaInicio(Tipo.COMPROBANTE, clave, propiedad.id, cuota, ultimo);
    }

    /** La transferencia más reciente de los últimos 3 días, si el propietario aún no la revisó. */
    private static SugerenciaInicio transferenciaReciente(DatosInicio datos, Set<String> completadas, long ahora) {
        SugerenciaInicio elegida = null;
        long masReciente = 0;
        for (Propiedad p : datos.propiedades) {
            for (Cuota cuota : datos.cuotasDe(p)) {
                long transferida = ReglasPago.ultimaTransferencia(cuota);
                String clave = "transferencia_" + cuota.id;
                if (transferida > masReciente && ahora - transferida < DIAS_NOVEDAD_MS && !completadas.contains(clave)) {
                    masReciente = transferida;
                    elegida = new SugerenciaInicio(Tipo.TRANSFERENCIA, clave, p.id, cuota, null);
                }
            }
        }
        return elegida;
    }
}
