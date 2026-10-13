package com.example.miarriendo_v1;

import android.content.Intent;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;

/**
 * Encabezado de las vistas del inquilino (Marca 321:1146 / Perfil y notificaciones 342:2904): saludo
 * "Hola Edward!", Avatar que abre el perfil y botón de Mis solicitudes. La vista debe traer los ids
 * tv_greeting, tv_avatar y btn_my_requests.
 */
final class EncabezadoInquilino {

    private EncabezadoInquilino() {
    }

    static void configurar(AppCompatActivity activity, FirebaseUser usuario) {
        TextView saludo = activity.findViewById(R.id.tv_greeting);
        TextView avatar = activity.findViewById(R.id.tv_avatar);
        saludo.setText(R.string.tenant_greeting_generic);
        avatar.setText(Formato.iniciales(usuario.getEmail()));
        avatar.setOnClickListener(v -> activity.startActivity(new Intent(activity, PerfilActivity.class)
                .putExtra(PerfilActivity.EXTRA_INQUILINO, true)));
        activity.findViewById(R.id.btn_my_requests).setOnClickListener(v ->
                activity.startActivity(new Intent(activity, MisSolicitudesActivity.class)));
        cargarNombre(activity, saludo, avatar, usuario.getUid());
    }

    /** "Hola Edward!" con el primer nombre del perfil y sus iniciales en el Avatar. */
    private static void cargarNombre(AppCompatActivity activity, TextView saludo, TextView avatar, String uid) {
        FirebaseDatabase.getInstance().getReference("usuarios").child(uid).get()
                .addOnSuccessListener(snapshot -> {
                    Usuario u = snapshot.getValue(Usuario.class);
                    String nombre = u == null || u.getNombre() == null ? "" : u.getNombre().trim();
                    if (nombre.isEmpty()) {
                        return;
                    }
                    String primerNombre = nombre.split("\\s+")[0];
                    saludo.setText(activity.getString(R.string.tenant_greeting, primerNombre));
                    String apellido = u.getApellido() == null ? "" : u.getApellido().trim();
                    avatar.setText(Formato.iniciales((primerNombre + " " + apellido).trim()));
                });
    }
}
