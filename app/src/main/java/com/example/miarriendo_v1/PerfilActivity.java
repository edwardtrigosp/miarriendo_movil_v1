package com.example.miarriendo_v1;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.FirebaseDatabase;

/**
 * Figma: Propietario / Perfil (193:387) y confirmación de cierre de sesión (193:514).
 * Con {@link #EXTRA_INQUILINO}: Inquilino / Perfil y configuración (319:5709), sin estadísticas.
 */
public class PerfilActivity extends AppCompatActivity {

    static final String EXTRA_INQUILINO = "inquilino";

    /** Opacidad del fondo detrás del diálogo (Figma: surface/scrim al 32%). */
    private static final float OPACIDAD_SCRIM = 0.32f;

    private TextView tvInitials, tvName, tvEmail, tvRole;
    private TextView tvCountProperties, tvCountTenants, tvCountRequests;
    private boolean inquilino;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Pantallas.edgeToEdge(this);
        inquilino = getIntent().getBooleanExtra(EXTRA_INQUILINO, false);
        setContentView(inquilino ? R.layout.activity_configuracion : R.layout.activity_perfil);
        Pantallas.aplicarInsets(findViewById(R.id.main));

        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null) {
            irAlLogin();
            return;
        }

        tvInitials = findViewById(R.id.tv_initials);
        tvName = findViewById(R.id.tv_name);
        tvEmail = findViewById(R.id.tv_email);
        tvRole = findViewById(R.id.tv_role);
        tvCountProperties = findViewById(R.id.tv_count_properties);
        tvCountTenants = findViewById(R.id.tv_count_tenants);
        tvCountRequests = findViewById(R.id.tv_count_requests);

        tvEmail.setText(usuario.getEmail());
        tvRole.setText(inquilino ? R.string.profile_role_tenant : R.string.profile_role_owner);
        if (inquilino) {
            ((TextView) findViewById(R.id.tv_app_bar_title)).setText(R.string.profile_settings_title);
        }

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        configurarItem(R.id.item_personal_data, R.drawable.ic_person, R.string.profile_personal_data);
        configurarItem(R.id.item_security, R.drawable.ic_lock, R.string.profile_security);
        configurarItem(R.id.item_biometric, R.drawable.ic_fingerprint, R.string.profile_biometric);
        findViewById(R.id.item_biometric).setOnClickListener(v ->
                startActivity(new Intent(this, HuellaActivity.class)));
        configurarItem(R.id.item_bank_account, R.drawable.ic_account_balance, R.string.profile_bank_account);
        findViewById(R.id.item_bank_account).setOnClickListener(v ->
                startActivity(new Intent(this, CuentaBancariaActivity.class)));
        configurarItem(R.id.item_notifications, R.drawable.ic_notifications, R.string.profile_notifications);
        configurarItem(R.id.item_help, R.drawable.ic_help, R.string.profile_help);
        configurarItem(R.id.item_terms, R.drawable.ic_description, R.string.profile_terms);
        findViewById(R.id.btn_logout).setOnClickListener(v -> confirmarCierreSesion());

        cargarPerfil(usuario.getUid());
        if (!inquilino) {
            cargarEstadisticas(usuario.getUid());
        }
    }

    /** Llena un ListItem incluido con su ícono y texto. Las pantallas de destino aún no existen. */
    private void configurarItem(int id, int icono, int texto) {
        View item = findViewById(id);
        ((ImageView) item.findViewById(R.id.iv_icon)).setImageResource(icono);
        ((TextView) item.findViewById(R.id.tv_label)).setText(texto);
        item.setOnClickListener(v -> Toast.makeText(this, R.string.soon, Toast.LENGTH_SHORT).show());
    }

    private void cargarPerfil(String uid) {
        FirebaseDatabase.getInstance().getReference("usuarios").child(uid).get()
                .addOnSuccessListener(snapshot -> {
                    Usuario u = snapshot.getValue(Usuario.class);
                    if (u == null) {
                        return;
                    }
                    String nombre = valor(u.getNombre());
                    String apellido = valor(u.getApellido());
                    tvName.setText((nombre + " " + apellido).trim());
                    tvInitials.setText(iniciales(nombre, apellido));
                    if (!valor(u.getEmail()).isEmpty()) {
                        tvEmail.setText(u.getEmail());
                    }
                    tvRole.setText(RegisterRoleActivity.ROL_INQUILINO.equals(u.getRol())
                            ? R.string.profile_role_tenant : R.string.profile_role_owner);
                });
    }

    /** Propiedades publicadas e inquilinos asignados. Las solicitudes aún no existen en la base. */
    private void cargarEstadisticas(String uid) {
        FirebaseDatabase.getInstance().getReference("propiedades")
                .orderByChild("propietarioId").equalTo(uid).get()
                .addOnSuccessListener(snapshot -> {
                    int propiedades = 0, inquilinos = 0;
                    for (DataSnapshot hijo : snapshot.getChildren()) {
                        Propiedad p = hijo.getValue(Propiedad.class);
                        if (p == null) {
                            continue;
                        }
                        propiedades++;
                        if (p.inquilinoNombre != null && !p.inquilinoNombre.isEmpty()) {
                            inquilinos++;
                        }
                    }
                    tvCountProperties.setText(String.valueOf(propiedades));
                    tvCountTenants.setText(String.valueOf(inquilinos));
                });
        tvCountRequests.setText("0");
    }

    private void confirmarCierreSesion() {
        Dialog dialogo = new Dialog(this);
        dialogo.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialogo.setContentView(R.layout.dialog_confirmar_salida);
        Window ventana = dialogo.getWindow();
        if (ventana != null) {
            int margen = getResources().getDimensionPixelSize(R.dimen.spacing_lg);
            ventana.setBackgroundDrawable(new InsetDrawable(new ColorDrawable(Color.TRANSPARENT), margen));
            ventana.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            ventana.setDimAmount(OPACIDAD_SCRIM);
        }
        TextView mensaje = dialogo.findViewById(R.id.tv_dialog_message);
        if (inquilino) {
            mensaje.setText(R.string.logout_message_tenant);
        } else if (new BiometricSession(this).isEnabled()) {
            mensaje.setText(R.string.logout_message_biometric);
        }
        dialogo.findViewById(R.id.btn_dialog_cancel).setOnClickListener(v -> dialogo.dismiss());
        dialogo.findViewById(R.id.btn_dialog_confirm).setOnClickListener(v -> {
            dialogo.dismiss();
            cerrarSesion();
        });
        dialogo.show();
    }

    /**
     * Con el ingreso con huella activado se conserva la sesión cifrada: el login muestra
     * "Hola de nuevo" y se vuelve a entrar con la huella. "Cambiar de cuenta" es la salida completa.
     */
    private void cerrarSesion() {
        if (!new BiometricSession(this).isEnabled()) {
            FirebaseAuth.getInstance().signOut();
        }
        irAlLogin();
    }

    private void irAlLogin() {
        Intent login = new Intent(this, LoginActivity.class);
        login.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(login);
        finish();
    }

    /** "Edward", "Trigos" → "ET" */
    private static String iniciales(String nombre, String apellido) {
        StringBuilder sb = new StringBuilder();
        if (!nombre.isEmpty()) sb.append(Character.toUpperCase(nombre.charAt(0)));
        if (!apellido.isEmpty()) sb.append(Character.toUpperCase(apellido.charAt(0)));
        return sb.toString();
    }

    private static String valor(String s) {
        return s == null ? "" : s.trim();
    }
}
