package com.example.miarriendo_v1;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * Módulo / Tu solicitud (446:3671): la solicitud de DatosInicio.solicitudParaMostrar con sus pasos.
 * En revisión → Ver mi solicitud; Aceptada → Ver mi arriendo; No aceptada → Ver hogares similares.
 */
final class ModuloTuSolicitud implements ModuloInicio {

    private View vista;

    @Override
    public View crear(LayoutInflater inflater, ViewGroup padre) {
        vista = inflater.inflate(R.layout.modulo_tu_solicitud, padre, false);
        return vista;
    }

    @Override
    public void iniciar(ContextoInicio ctx) {
        actualizar(ctx);
    }

    @Override
    public void actualizar(ContextoInicio ctx) {
        SolicitudArriendo s = ctx.datos.solicitudParaMostrar(System.currentTimeMillis());
        if (s == null) {
            ctx.ocultar(vista);
            return;
        }
        vista.setVisibility(View.VISIBLE);
        Context c = vista.getContext();
        String propietario = s.propietarioNombre == null || s.propietarioNombre.trim().isEmpty()
                ? null : s.propietarioNombre.trim().split("\\s+")[0];
        String precio = c.getString(R.string.owner_price_month, Propiedad.formatearMiles(s.precio));
        String direccion = s.propiedadDireccion == null ? "" : s.propiedadDireccion;

        ((TextView) vista.findViewById(R.id.tv_title)).setText(s.propiedadTitulo);
        ((TextView) vista.findViewById(R.id.tv_detail)).setText(propietario == null
                ? c.getString(R.string.your_request_detail_no_owner, direccion, precio)
                : c.getString(R.string.your_request_detail, direccion, precio, propietario));

        PasosSolicitudView.Estado estado = PasosSolicitudView.Estado.de(s);
        PasosSolicitudView.aplicar(vista.findViewById(R.id.pasos), estado);
        TextView chip = vista.findViewById(R.id.tv_status);
        TextView mensaje = vista.findViewById(R.id.tv_message);
        TextView secundario = vista.findViewById(R.id.btn_secondary);
        TextView primario = vista.findViewById(R.id.btn_primary);
        secundario.setVisibility(View.GONE);
        primario.setVisibility(View.GONE);
        switch (estado) {
            case ACEPTADA:
                StatusChipView.aplicar(chip, StatusChipView.Tipo.EXITO, c.getString(R.string.your_request_status_accepted));
                mensaje.setText(propietario == null ? c.getString(R.string.your_request_accepted_text_generic)
                        : c.getString(R.string.your_request_accepted_text, propietario));
                primario.setText(R.string.your_request_see_rental);
                primario.setVisibility(View.VISIBLE);
                primario.setOnClickListener(v -> ctx.abrir(MiArriendoActivity.class));
                break;
            case NO_ACEPTADA:
                StatusChipView.aplicar(chip, StatusChipView.Tipo.ERROR, c.getString(R.string.your_request_status_rejected));
                mensaje.setText(propietario == null ? c.getString(R.string.your_request_rejected_text_generic)
                        : c.getString(R.string.your_request_rejected_text, propietario, s.propiedadTitulo));
                primario.setText(R.string.your_request_see_similar);
                primario.setVisibility(View.VISIBLE);
                primario.setOnClickListener(v -> ctx.abrirResultados(FiltrosBusqueda.parecidosA(s.precio)));
                break;
            default:
                StatusChipView.aplicar(chip, StatusChipView.Tipo.INFO, c.getString(R.string.your_request_status_review));
                mensaje.setText(propietario == null ? c.getString(R.string.your_request_review_text_generic)
                        : c.getString(R.string.your_request_review_text, propietario));
                secundario.setVisibility(View.VISIBLE);
                secundario.setOnClickListener(v -> ctx.abrir(MisSolicitudesActivity.class));
        }
    }

    @Override
    public void detener() {
    }
}
