package com.example.miarriendo_v1;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashSet;
import java.util.Set;

/**
 * Módulo / Encabezado simple (481:2320): Avatar → Perfil, notificaciones → solicitudes y una sugerencia en franja
 * solo si hay disparador (SugerenciaInicio). El saludo y el subtítulo solo van en Inquilino sin arriendo (497:4613):
 * en los demás perfiles el primer módulo (Mi arriendo, Recaudo o Primeros pasos) ya hace de título.
 * Los íconos de ocultar montos y de ayuda de Figma no se dibujan porque la app aún no tiene esas funciones.
 */
final class ModuloEncabezado implements ModuloInicio {

    private View vista;
    private boolean activo;
    /** Del perfil (usuarios/{uid}): se lee una vez y se reutiliza cuando cambian los datos. */
    private boolean perfilLeido;
    private String telefono;
    private final Set<String> ocultas = new HashSet<>();

    @Override
    public View crear(LayoutInflater inflater, ViewGroup padre) {
        vista = inflater.inflate(R.layout.modulo_encabezado, padre, false);
        return vista;
    }

    @Override
    public void iniciar(ContextoInicio ctx) {
        activo = true;
        TextView avatar = vista.findViewById(R.id.tv_avatar);
        TextView saludo = vista.findViewById(R.id.tv_greeting);
        boolean inquilino = ctx.datos.esInquilino();
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        saludo.setText(R.string.home_greeting_generic);
        avatar.setText(Formato.iniciales(usuario == null ? "" : usuario.getEmail()));
        avatar.setOnClickListener(v -> ctx.activity.startActivity(
                new Intent(ctx.activity, PerfilActivity.class).putExtra(PerfilActivity.EXTRA_INQUILINO, inquilino)));
        View notificaciones = vista.findViewById(R.id.btn_notifications);
        if (inquilino) {
            notificaciones.setOnClickListener(v -> ctx.abrir(MisSolicitudesActivity.class));
        } else {
            notificaciones.setOnClickListener(v -> ctx.abrir(SolicitudesRecibidasActivity.class));
        }

        boolean sinArriendo = ctx.datos.perfil() == PlanDeInicio.Perfil.INQUILINO_SIN_ARRIENDO;
        saludo.setVisibility(sinArriendo ? View.VISIBLE : View.GONE);
        TextView subtitulo = vista.findViewById(R.id.tv_subtitle);
        subtitulo.setText(R.string.home_subtitle_search);
        subtitulo.setVisibility(sinArriendo ? View.VISIBLE : View.GONE);
        cargarPerfil(ctx, saludo, avatar);
    }

    /** Cambiaron cuotas o solicitudes: la sugerencia puede aparecer o irse (comprobante, transferencia). */
    @Override
    public void actualizar(ContextoInicio ctx) {
        if (perfilLeido) {
            mostrarSugerencia(ctx);
        }
    }

    /** "Hola, María", las iniciales del Avatar y, en la misma lectura, el teléfono y las sugerencias cerradas. */
    private void cargarPerfil(ContextoInicio ctx, TextView saludo, TextView avatar) {
        FirebaseDatabase.getInstance().getReference("usuarios").child(ctx.uid).get()
                .addOnSuccessListener(snapshot -> {
                    if (!activo) {
                        return;
                    }
                    Usuario u = snapshot.getValue(Usuario.class);
                    String nombre = u == null || u.getNombre() == null ? "" : u.getNombre().trim();
                    if (!nombre.isEmpty()) {
                        String primerNombre = nombre.split("\\s+")[0];
                        saludo.setText(saludo.getContext().getString(R.string.home_greeting, primerNombre));
                        String apellido = u.getApellido() == null ? "" : u.getApellido().trim();
                        avatar.setText(Formato.iniciales((primerNombre + " " + apellido).trim()));
                    }
                    telefono = u == null ? null : u.getTelefono();
                    ocultas.clear();
                    for (DataSnapshot oculta : snapshot.child("sugerencias_ocultas").getChildren()) {
                        ocultas.add(oculta.getKey());
                    }
                    perfilLeido = true;
                    mostrarSugerencia(ctx);
                });
    }

    /** Sugerencia (430:2335) en franja. Las que se cierran se guardan para no volver a mostrarlas. */
    private void mostrarSugerencia(ContextoInicio ctx) {
        ViewGroup orientacion = vista.findViewById(R.id.orientacion);
        orientacion.removeAllViews();
        SugerenciaInicio s = SugerenciaInicio.elegir(ctx.datos, telefono, ocultas, System.currentTimeMillis());
        if (s == null) {
            orientacion.setVisibility(View.GONE);
            return;
        }
        View franja = LayoutInflater.from(orientacion.getContext()).inflate(R.layout.item_sugerencia, orientacion, false);
        ImageView icono = franja.findViewById(R.id.iv_icon);
        TextView texto = franja.findViewById(R.id.tv_text);
        switch (s.tipo) {
            case ASI_FUNCIONA:
                icono.setImageResource(R.drawable.ic_info);
                texto.setText(R.string.home_tip_how_it_works);
                break;
            case CELULAR_NEQUI:
                icono.setImageResource(R.drawable.ic_smartphone);
                texto.setText(R.string.home_tip_nequi);
                franja.setOnClickListener(v -> pedirCelular(ctx));
                break;
            case COMPROBANTE:
                icono.setImageResource(R.drawable.ic_receipt_long);
                texto.setText(ctx.activity.getString(R.string.home_tip_receipt, VistasPago.nombreMes(ctx.activity, s.cuota)));
                franja.setOnClickListener(v -> {
                    // "Lo abre": desaparece igual que si la cerrara.
                    ocultar(ctx, s.clave);
                    ctx.activity.startActivity(VistasPago.intent(ctx.activity, ComprobanteActivity.class, s.propiedadId, s.cuota.id)
                            .putExtra(VistasPago.EXTRA_ABONO, s.abono.numero));
                });
                break;
            case BIENVENIDA:
                icono.setImageResource(R.drawable.ic_celebration);
                texto.setText(R.string.home_tip_welcome);
                break;
            case TRANSFERENCIA:
                icono.setImageResource(R.drawable.ic_account_balance);
                texto.setText(ctx.activity.getString(R.string.home_tip_transfer, VistasPago.nombreMes(ctx.activity, s.cuota)));
                franja.setOnClickListener(v -> ctx.activity.startActivity(
                        new Intent(ctx.activity, PagosInmuebleActivity.class).putExtra(VistasPago.EXTRA_PROPIEDAD_ID, s.propiedadId)));
                break;
        }
        View cerrar = franja.findViewById(R.id.btn_close);
        if (s.clave == null) {
            cerrar.setVisibility(View.GONE);
        } else {
            cerrar.setOnClickListener(v -> ocultar(ctx, s.clave));
        }
        orientacion.addView(franja);
        orientacion.setVisibility(View.VISIBLE);
    }

    private void ocultar(ContextoInicio ctx, String clave) {
        ocultas.add(clave);
        FirebaseDatabase.getInstance().getReference("usuarios").child(ctx.uid)
                .child("sugerencias_ocultas").child(clave).setValue(true);
        mostrarSugerencia(ctx);
    }

    /** "Guarda tu celular Nequi": se pide aquí mismo y se guarda en el perfil; PagarNequi lo usa de ahí. */
    private void pedirCelular(ContextoInicio ctx) {
        View contenido = LayoutInflater.from(ctx.activity).inflate(R.layout.dialog_celular_nequi, null, false);
        TextInputLayout campo = contenido.findViewById(R.id.til_phone);
        TextInputEditText entrada = contenido.findViewById(R.id.et_phone);
        AlertDialog dialogo = new MaterialAlertDialogBuilder(ctx.activity)
                .setTitle(R.string.home_nequi_dialog_title)
                .setMessage(R.string.home_nequi_dialog_text)
                .setView(contenido)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.home_nequi_dialog_save, null)
                .show();
        dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String celular = CelularNequi.normalizar(entrada.getText() == null ? null : entrada.getText().toString());
            if (celular == null) {
                campo.setError(ctx.activity.getString(R.string.nequi_phone_error));
                return;
            }
            FirebaseDatabase.getInstance().getReference("usuarios").child(ctx.uid).child("telefono").setValue(celular)
                    .addOnSuccessListener(x -> {
                        telefono = celular;
                        Toast.makeText(ctx.activity, R.string.home_nequi_saved, Toast.LENGTH_SHORT).show();
                        if (activo) {
                            mostrarSugerencia(ctx);
                        }
                    });
            dialogo.dismiss();
        });
    }

    @Override
    public void detener() {
        activo = false;
    }
}
