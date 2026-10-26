package com.example.miarriendo_v1;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.firebase.database.FirebaseDatabase;

import java.util.List;

/**
 * Módulo / Pendientes (482:2565): une Atención y Solicitudes recibidas. Hasta 3 filas por prioridad (pagos
 * vencidos → daños urgentes → solicitudes → daños que pueden esperar); la decisión se toma en el detalle.
 * Sin pendientes no está en el plan.
 */
final class ModuloPendientes implements ModuloInicio {

    private View vista;

    @Override
    public View crear(LayoutInflater inflater, ViewGroup padre) {
        vista = inflater.inflate(R.layout.modulo_pendientes, padre, false);
        return vista;
    }

    @Override
    public void iniciar(ContextoInicio ctx) {
        vista.findViewById(R.id.btn_see_all).setOnClickListener(v -> ctx.abrir(SolicitudesRecibidasActivity.class));
        actualizar(ctx);
    }

    @Override
    public void actualizar(ContextoInicio ctx) {
        List<PendientesPropietario.Pendiente> todos = ctx.datos.pendientes(System.currentTimeMillis());
        if (todos.isEmpty()) {
            ctx.ocultar(vista);
            return;
        }
        vista.setVisibility(View.VISIBLE);
        ((TextView) vista.findViewById(R.id.tv_count)).setText(String.valueOf(todos.size()));
        LinearLayout lista = vista.findViewById(R.id.lista);
        lista.removeAllViews();
        int visibles = Math.min(todos.size(), PendientesPropietario.MAXIMO);
        for (int i = 0; i < visibles; i++) {
            lista.addView(fila(ctx, lista, todos.get(i)));
        }
        // "Ver todo" lleva a las solicitudes: solo aparece si alguna quedó por fuera.
        boolean solicitudOculta = false;
        for (int i = visibles; i < todos.size(); i++) {
            solicitudOculta |= todos.get(i).tipo == PendientesPropietario.Pendiente.Tipo.SOLICITUD;
        }
        vista.findViewById(R.id.btn_see_all).setVisibility(solicitudOculta ? View.VISIBLE : View.GONE);
    }

    private static View fila(ContextoInicio ctx, LinearLayout padre, PendientesPropietario.Pendiente pendiente) {
        Context c = padre.getContext();
        View fila = LayoutInflater.from(c).inflate(R.layout.item_fila_pendiente, padre, false);
        ImageView icono = fila.findViewById(R.id.iv_icon);
        TextView chip = fila.findViewById(R.id.tv_status);
        TextView titulo = fila.findViewById(R.id.tv_title);
        TextView detalle = fila.findViewById(R.id.tv_detail);
        if (pendiente.tipo == PendientesPropietario.Pendiente.Tipo.PAGO_VENCIDO) {
            Propiedad p = pendiente.propiedad;
            String inquilino = nombre(p.inquilinoNombre);
            icono.setImageResource(R.drawable.ic_payments);
            StatusChipView.aplicar(chip, StatusChipView.Tipo.ERROR, c.getString(R.string.home_pending_overdue_chip));
            titulo.setText(inquilino == null ? c.getString(R.string.home_pending_overdue_title_generic)
                    : c.getString(R.string.home_pending_overdue_title, inquilino));
            detalle.setText(c.getString(R.string.home_pending_detail, p.nombreParaTarjeta(),
                    VistasPago.pesos(c, ReglasPago.saldo(pendiente.cuota))));
            fila.setOnClickListener(v -> ModuloRecaudo.abrirPagos(ctx, p));
        } else if (pendiente.tipo == PendientesPropietario.Pendiente.Tipo.REPORTE) {
            ReporteDano r = pendiente.reporte;
            icono.setImageResource(R.drawable.ic_build);
            StatusChipView.aplicar(chip, r.urgente ? StatusChipView.Tipo.ERROR : StatusChipView.Tipo.ADVERTENCIA,
                    c.getString(r.urgente ? R.string.home_pending_report_urgent : R.string.home_pending_report_chip));
            titulo.setText(r.descripcion);
            String cuando = Formato.haceCuanto(c, r.creadoEn);
            detalle.setText(c.getString(R.string.home_pending_detail, r.propiedadTitulo,
                    Character.toLowerCase(cuando.charAt(0)) + cuando.substring(1)));
            fila.setOnClickListener(v -> verReporte(ctx.activity, r));
        } else {
            SolicitudArriendo s = pendiente.solicitud;
            String inquilino = nombre(s.inquilinoNombre);
            icono.setImageResource(R.drawable.ic_person_add);
            StatusChipView.aplicar(chip, StatusChipView.Tipo.INFO, c.getString(R.string.home_pending_request_chip));
            titulo.setText(inquilino == null ? c.getString(R.string.home_pending_request_title_generic)
                    : c.getString(R.string.home_pending_request_title, inquilino));
            String cuando = Formato.haceCuanto(c, s.creadaEn);
            detalle.setText(c.getString(R.string.home_pending_detail, s.propiedadTitulo,
                    Character.toLowerCase(cuando.charAt(0)) + cuando.substring(1)));
            fila.setOnClickListener(v -> ctx.abrir(SolicitudesRecibidasActivity.class));
        }
        fila.setContentDescription(titulo.getText() + ". " + detalle.getText());
        return fila;
    }

    /** El nombre completo, como en Figma ("Laura Méndez no ha pagado"); null si no hay. */
    /** El detalle del daño reportado: qué, qué tan urgente, quién y la foto; desde aquí se marca como resuelto. */
    private static void verReporte(Activity c, ReporteDano r) {
        String quien = nombre(r.inquilinoNombre);
        String cuando = Formato.haceCuanto(c, r.creadoEn);
        String mensaje = r.descripcion + "\n\n" + c.getString(R.string.home_report_meta,
                c.getString(r.urgente ? R.string.report_urgent : R.string.report_can_wait),
                quien == null ? c.getString(R.string.home_report_tenant_generic) : quien,
                Character.toLowerCase(cuando.charAt(0)) + cuando.substring(1));
        Dialogo dialogo = Dialogo.nuevo(c)
                .titulo(c.getString(R.string.home_report_title, categoria(c, r.categoria), r.propiedadTitulo))
                .mensaje(mensaje)
                .cancelar(R.string.home_report_close)
                .confirmar(R.string.home_report_resolve, Dialogo.Tono.PRINCIPAL, () -> FirebaseDatabase.getInstance()
                        .getReference("reportes").child(r.id).child("estado").setValue(ReporteDano.ESTADO_RESUELTO));
        Bitmap foto = FotosCodec.desdeBase64(r.foto);
        if (foto != null) {
            ShapeableImageView imagen = new ShapeableImageView(c);
            imagen.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    c.getResources().getDimensionPixelSize(R.dimen.dialogo_foto_alto)));
            imagen.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imagen.setImageBitmap(foto);
            imagen.setShapeAppearanceModel(ShapeAppearanceModel.builder()
                    .setAllCornerSizes(c.getResources().getDimension(R.dimen.radius_sm)).build());
            imagen.setContentDescription(c.getString(R.string.report_photo));
            dialogo.contenido(imagen);
        }
        dialogo.mostrar();
    }

    static String categoria(Context c, String categoria) {
        if (ReporteDano.CATEGORIA_AGUA.equals(categoria)) {
            return c.getString(R.string.report_water);
        } else if (ReporteDano.CATEGORIA_ELECTRICIDAD.equals(categoria)) {
            return c.getString(R.string.report_electricity);
        } else if (ReporteDano.CATEGORIA_PUERTAS.equals(categoria)) {
            return c.getString(R.string.report_doors);
        } else if (ReporteDano.CATEGORIA_ELECTRODOMESTICOS.equals(categoria)) {
            return c.getString(R.string.report_appliances);
        }
        return c.getString(R.string.report_other);
    }

    private static String nombre(String nombre) {
        return nombre == null || nombre.trim().isEmpty() ? null : nombre.trim();
    }

    @Override
    public void detener() {
    }
}
