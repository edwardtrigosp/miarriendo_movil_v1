package com.example.miarriendo_v1;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;


import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Módulo / Encabezado (446:3435) como en la Presentación (531:3895): fondo lila, Avatar → Perfil, ayuda →
 * cómo funciona miarriendo, notificaciones → solicitudes, y debajo las sugerencias en tarjetas (SugerenciaInicio).
 * Sin saludo: el primer módulo (Mi arriendo, Recaudo, Explorar en mapa o Primeros pasos) ya hace de título.
 * El ojo para ocultar montos no se dibuja hasta que la app tenga esa función.
 */
final class ModuloEncabezado implements ModuloInicio {

    private View vista;
    private boolean activo;
    /** Del perfil (usuarios/{uid}): se lee una vez y se reutiliza cuando cambian los datos. */
    private boolean perfilLeido;
    private String telefono;
    private final Set<String> completadas = new HashSet<>();

    @Override
    public View crear(LayoutInflater inflater, ViewGroup padre) {
        vista = inflater.inflate(R.layout.modulo_encabezado, padre, false);
        return vista;
    }

    @Override
    public void iniciar(ContextoInicio ctx) {
        activo = true;
        TextView avatar = vista.findViewById(R.id.tv_avatar);
        boolean inquilino = ctx.datos.esInquilino();
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        // Al volver al inicio ya tiene las iniciales del nombre: no se cambian por las del correo (parpadeo).
        if (avatar.length() == 0) {
            avatar.setText(Formato.iniciales(usuario == null ? "" : usuario.getEmail()));
        }
        avatar.setOnClickListener(v -> ctx.activity.startActivity(
                new Intent(ctx.activity, PerfilActivity.class).putExtra(PerfilActivity.EXTRA_INQUILINO, inquilino)));
        vista.findViewById(R.id.btn_help).setOnClickListener(v -> mostrarComoFunciona(ctx));
        View notificaciones = vista.findViewById(R.id.btn_notifications);
        if (inquilino) {
            notificaciones.setOnClickListener(v -> ctx.abrir(MisSolicitudesActivity.class));
        } else {
            notificaciones.setOnClickListener(v -> ctx.abrir(SolicitudesRecibidasActivity.class));
        }
        cargarPerfil(ctx, avatar);
    }

    /** Cambiaron cuotas o solicitudes: una sugerencia puede aparecer o irse (comprobante, transferencia). */
    @Override
    public void actualizar(ContextoInicio ctx) {
        if (perfilLeido) {
            mostrarSugerencias(ctx);
        }
    }

    /** Las iniciales del Avatar y, en la misma lectura, el teléfono y las sugerencias ya completadas. */
    private void cargarPerfil(ContextoInicio ctx, TextView avatar) {
        FirebaseDatabase.getInstance().getReference("usuarios").child(ctx.uid).get()
                .addOnSuccessListener(snapshot -> {
                    if (!activo) {
                        return;
                    }
                    Usuario u = snapshot.getValue(Usuario.class);
                    String nombre = u == null || u.getNombre() == null ? "" : u.getNombre().trim();
                    if (!nombre.isEmpty()) {
                        String apellido = u.getApellido() == null ? "" : u.getApellido().trim();
                        avatar.setText(Formato.iniciales((nombre.split("\\s+")[0] + " " + apellido).trim()));
                    }
                    telefono = u == null ? null : u.getTelefono();
                    completadas.clear();
                    for (DataSnapshot hecha : snapshot.child("sugerencias_ocultas").getChildren()) {
                        completadas.add(hecha.getKey());
                    }
                    perfilLeido = true;
                    mostrarSugerencias(ctx);
                });
    }

    /** Sugerencias (430:2335) en carrusel. No se cierran: se van cuando la persona hace lo que proponen. */
    private void mostrarSugerencias(ContextoInicio ctx) {
        View carrusel = vista.findViewById(R.id.carrusel_sugerencias);
        ViewGroup fila = vista.findViewById(R.id.sugerencias);
        fila.removeAllViews();
        BiometricSession huella = new BiometricSession(ctx.activity);
        boolean huellaPendiente = huella.isAvailable() && !huella.isEnabled();
        List<SugerenciaInicio> sugerencias = SugerenciaInicio.elegir(ctx.datos, telefono, completadas, huellaPendiente,
                System.currentTimeMillis());
        for (SugerenciaInicio s : sugerencias) {
            fila.addView(tarjeta(ctx, fila, s));
        }
        carrusel.setVisibility(sugerencias.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private View tarjeta(ContextoInicio ctx, ViewGroup fila, SugerenciaInicio s) {
        View tarjeta = LayoutInflater.from(fila.getContext()).inflate(R.layout.item_sugerencia, fila, false);
        ImageView icono = tarjeta.findViewById(R.id.iv_icon);
        TextView texto = tarjeta.findViewById(R.id.tv_text);
        switch (s.tipo) {
            case ASI_FUNCIONA:
                icono.setImageResource(R.drawable.ic_lightbulb);
                texto.setText(R.string.home_tip_how_it_works);
                tarjeta.setOnClickListener(v -> {
                    completar(ctx, s.clave);
                    mostrarComoFunciona(ctx);
                });
                break;
            case CELULAR_NEQUI:
                icono.setImageResource(R.drawable.ic_smartphone);
                texto.setText(R.string.home_tip_nequi);
                tarjeta.setOnClickListener(v -> pedirCelular(ctx));
                break;
            case COMPROBANTE:
                icono.setImageResource(R.drawable.ic_receipt_long);
                texto.setText(ctx.activity.getString(R.string.home_tip_receipt, VistasPago.nombreMes(ctx.activity, s.cuota)));
                tarjeta.setOnClickListener(v -> {
                    completar(ctx, s.clave);
                    ctx.activity.startActivity(VistasPago.intent(ctx.activity, ComprobanteActivity.class, s.propiedadId, s.cuota.id)
                            .putExtra(VistasPago.EXTRA_ABONO, s.abono.numero));
                });
                break;
            case TRANSFERENCIA:
                icono.setImageResource(R.drawable.ic_account_balance);
                texto.setText(R.string.home_tip_transfer);
                tarjeta.setOnClickListener(v -> {
                    completar(ctx, s.clave);
                    ctx.activity.startActivity(new Intent(ctx.activity, PagosInmuebleActivity.class)
                            .putExtra(VistasPago.EXTRA_PROPIEDAD_ID, s.propiedadId));
                });
                break;
            case HUELLA:
                icono.setImageResource(R.drawable.ic_fingerprint);
                texto.setText(R.string.home_tip_fingerprint);
                tarjeta.setOnClickListener(v -> ctx.abrir(HuellaActivity.class));
                break;
        }
        tarjeta.setContentDescription(texto.getText());
        return tarjeta;
    }

    /** La persona ya hizo lo que proponía la sugerencia: no vuelve a salir. */
    private void completar(ContextoInicio ctx, String clave) {
        completadas.add(clave);
        FirebaseDatabase.getInstance().getReference("usuarios").child(ctx.uid)
                .child("sugerencias_ocultas").child(clave).setValue(true);
        mostrarSugerencias(ctx);
    }

    /** Ayuda (?) y "Mira cómo funciona": los pasos de miarriendo según el rol. */
    private static void mostrarComoFunciona(ContextoInicio ctx) {
        Dialogo.nuevo(ctx.activity)
                .titulo(R.string.home_help_title)
                .mensaje(ctx.datos.esInquilino() ? R.string.home_help_tenant : R.string.home_help_owner)
                .confirmar(R.string.home_help_ok, Dialogo.Tono.PRINCIPAL, null)
                .mostrar();
    }

    /** "Guarda tu celular Nequi": se pide aquí mismo y se guarda en el perfil; PagarNequi lo usa de ahí. */
    private void pedirCelular(ContextoInicio ctx) {
        View contenido = LayoutInflater.from(ctx.activity).inflate(R.layout.dialog_celular_nequi, null, false);
        TextInputLayout campo = contenido.findViewById(R.id.til_phone);
        TextInputEditText entrada = contenido.findViewById(R.id.et_phone);
        Dialogo.nuevo(ctx.activity)
                .titulo(R.string.home_nequi_dialog_title)
                .mensaje(R.string.home_nequi_dialog_text)
                .contenido(contenido)
                .cancelar(R.string.cancel)
                .confirmarSi(R.string.home_nequi_dialog_save, Dialogo.Tono.PRINCIPAL, () -> guardarCelular(ctx, campo, entrada))
                .mostrar();
    }

    /** Devuelve false si el número no es válido, para que el diálogo siga abierto con el error. */
    private boolean guardarCelular(ContextoInicio ctx, TextInputLayout campo, TextInputEditText entrada) {
        String celular = CelularNequi.normalizar(entrada.getText() == null ? null : entrada.getText().toString());
        if (celular == null) {
            campo.setError(ctx.activity.getString(R.string.nequi_phone_error));
            return false;
        }
        FirebaseDatabase.getInstance().getReference("usuarios").child(ctx.uid).child("telefono").setValue(celular)
                .addOnSuccessListener(x -> {
                    telefono = celular;
                    Toast.makeText(ctx.activity, R.string.home_nequi_saved, Toast.LENGTH_SHORT).show();
                    if (activo) {
                        mostrarSugerencias(ctx);
                    }
                });
        return true;
    }

    @Override
    public void detener() {
        activo = false;
    }
}
