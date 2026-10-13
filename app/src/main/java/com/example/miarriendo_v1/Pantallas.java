package com.example.miarriendo_v1;

import android.content.Intent;
import android.graphics.Color;
import android.view.View;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;

/** Utilidades compartidas por las pantallas. */
final class Pantallas {

    private Pantallas() {
    }

    /**
     * Activa edge-to-edge con iconos oscuros en las barras del sistema (el diseño es claro),
     * aunque el teléfono esté en modo oscuro.
     */
    static void edgeToEdge(ComponentActivity activity) {
        EdgeToEdge.enable(activity,
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT));
    }

    /** Deja el contenido fuera de la barra de estado y de navegación (edge-to-edge). */
    static void aplicarInsets(View root) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }

    /** Destino después de iniciar sesión o crear la cuenta, según el rol guardado en Firebase. */
    static void abrirInicio(ComponentActivity activity) {
        abrirInicio(activity, () -> Toast.makeText(activity, R.string.home_pending, Toast.LENGTH_LONG).show());
    }

    /**
     * Abre el inicio que corresponde al rol del usuario con sesión iniciada.
     * Si no hay sesión o no se pudo leer su rol, ejecuta {@code sinInicio}.
     */
    static void abrirInicio(ComponentActivity activity, Runnable sinInicio) {
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null) {
            sinInicio.run();
            return;
        }
        FirebaseDatabase.getInstance().getReference("usuarios").child(usuario.getUid()).child("rol")
                .get()
                .addOnSuccessListener(snapshot -> {
                    String rol = snapshot.getValue(String.class);
                    Class<?> destino = RegisterRoleActivity.ROL_PROPIETARIO.equals(rol) ? PropietarioInicioActivity.class
                            : RegisterRoleActivity.ROL_INQUILINO.equals(rol) ? InquilinoInicioActivity.class : null;
                    if (destino == null) {
                        sinInicio.run();
                        return;
                    }
                    Intent inicio = new Intent(activity, destino);
                    inicio.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    activity.startActivity(inicio);
                })
                .addOnFailureListener(e -> sinInicio.run());
    }
}
