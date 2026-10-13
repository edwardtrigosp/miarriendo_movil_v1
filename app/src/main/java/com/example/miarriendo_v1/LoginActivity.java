package com.example.miarriendo_v1;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicatorSpec;
import com.google.android.material.progressindicator.IndeterminateDrawable;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;

import javax.crypto.Cipher;

/**
 * Figma: Onboarding / Login - Default (209:619), Error (209:682), Cargando (209:741),
 * Con huella (210:741) y Huella cambiada (209:793).
 */
public class LoginActivity extends AppCompatActivity {

    private static final String ESTADO_FORMULARIO = "formulario";

    private View modoContrasena, modoHuella, alerta;
    private TextInputLayout tilEmail, tilPassword;
    private TextInputEditText etEmail, etPassword;
    private MaterialButton btnLogin;
    private TextView tvForgot, tvBienvenida;
    private IndeterminateDrawable spinner;
    private BiometricSession huella;

    /** Con el formulario abierto desde "Usar contraseña", atrás vuelve al ingreso con huella. */
    private final OnBackPressedCallback volverAHuella = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            if (FirebaseAuth.getInstance().getCurrentUser() != null && huella.isEnabled()) {
                mostrarModoHuella();
            } else {
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Pantallas.edgeToEdge(this);
        setContentView(R.layout.activity_login);
        Pantallas.aplicarInsets(findViewById(R.id.main));

        huella = new BiometricSession(this);
        modoContrasena = findViewById(R.id.mode_password);
        modoHuella = findViewById(R.id.mode_biometric);
        alerta = findViewById(R.id.alert_login);
        tilEmail = findViewById(R.id.til_email);
        tilPassword = findViewById(R.id.til_password);
        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        btnLogin = findViewById(R.id.btn_login);
        tvForgot = findViewById(R.id.tv_forgot);
        tvBienvenida = findViewById(R.id.tv_welcome);
        spinner = crearSpinner();

        btnLogin.setOnClickListener(v -> iniciarSesion());
        tvForgot.setOnClickListener(v -> recuperarContrasena());
        findViewById(R.id.tv_create_account).setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
        findViewById(R.id.btn_use_fingerprint).setOnClickListener(v -> pedirHuella());
        findViewById(R.id.btn_use_password).setOnClickListener(v -> usarContrasena());
        etPassword.setOnEditorActionListener((v, accion, evento) -> {
            if (accion == EditorInfo.IME_ACTION_DONE) {
                iniciarSesion();
                return true;
            }
            return false;
        });
        limpiarErrorAlEscribir(etEmail, tilEmail);
        limpiarErrorAlEscribir(etPassword, tilPassword);
        getOnBackPressedDispatcher().addCallback(this, volverAHuella);

        elegirModo(savedInstanceState);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(ESTADO_FORMULARIO, modoContrasena.getVisibility() == View.VISIBLE);
    }

    /**
     * Con una sesión guardada y el ingreso con huella activado (Perfil → Ingreso con huella) muestra
     * la variante Con huella; si las huellas del teléfono cambiaron, la variante Huella cambiada.
     */
    private void elegirModo(Bundle savedInstanceState) {
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null || !huella.isEnabled()) {
            mostrarFormulario();
            return;
        }
        Cipher cipher;
        try {
            cipher = huella.isAvailable() ? huella.cipherForDecryption() : null;
        } catch (Exception e) {
            HuellaPrompt.error(this, e.getMessage());
            mostrarFormulario();
            etEmail.setText(usuario.getEmail());
            return;
        }
        if (cipher == null) {
            huellaCambiada(usuario);
            return;
        }
        if (savedInstanceState != null && savedInstanceState.getBoolean(ESTADO_FORMULARIO)) {
            usarContrasena();
            return;
        }
        mostrarModoHuella();
        if (savedInstanceState == null) {
            pedirHuella(usuario, cipher);
        }
    }

    // ---------- Variante Con huella ----------

    private void mostrarModoHuella() {
        volverAHuella.setEnabled(false);
        modoContrasena.setVisibility(View.GONE);
        modoHuella.setVisibility(View.VISIBLE);
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario != null) {
            cargarNombre(usuario.getUid());
        }
    }

    /** "Hola de nuevo, Edward" con el primer nombre del perfil; sin conexión queda "Hola de nuevo". */
    private void cargarNombre(String uid) {
        FirebaseDatabase.getInstance().getReference("usuarios").child(uid).get()
                .addOnSuccessListener(snapshot -> {
                    Usuario u = snapshot.getValue(Usuario.class);
                    String nombre = u == null || u.getNombre() == null ? "" : u.getNombre().trim();
                    if (!nombre.isEmpty()) {
                        tvBienvenida.setText(getString(R.string.login_welcome_back, nombre.split("\\s+")[0]));
                    }
                });
    }

    private void pedirHuella() {
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null) {
            mostrarFormulario();
            return;
        }
        Cipher cipher;
        try {
            cipher = huella.cipherForDecryption();
        } catch (Exception e) {
            HuellaPrompt.error(this, e.getMessage());
            return;
        }
        if (cipher == null) {
            huellaCambiada(usuario);
            return;
        }
        pedirHuella(usuario, cipher);
    }

    private void pedirHuella(FirebaseUser usuario, Cipher cipher) {
        HuellaPrompt.mostrar(this, R.string.bio_prompt_title, R.string.bio_prompt_subtitle, cipher,
                new HuellaPrompt.Resultado() {
                    @Override
                    public void exito(Cipher c) throws Exception {
                        if (usuario.getUid().equals(huella.read(c))) {
                            Pantallas.abrirInicio(LoginActivity.this);
                        } else {
                            // La huella guardada era de otra cuenta.
                            huella.disable();
                            FirebaseAuth.getInstance().signOut();
                            mostrarFormulario();
                            AlertView.mostrar(alerta, AlertView.Tipo.ERROR, getString(R.string.auth_session_expired));
                        }
                    }

                    @Override
                    public void cancelado() {
                        // Sigue en Con huella: "Usar huella" vuelve a abrir el diálogo.
                    }

                    @Override
                    public void usarContrasena() {
                        LoginActivity.this.usarContrasena();
                    }
                });
    }

    /** Variante Huella cambiada: la llave quedó invalidada, se pide la contraseña con el correo escrito. */
    private void huellaCambiada(FirebaseUser usuario) {
        huella.disable();
        mostrarFormulario();
        etEmail.setText(usuario.getEmail());
        AlertView.mostrar(alerta, AlertView.Tipo.ADVERTENCIA, getString(R.string.login_biometric_changed));
    }

    /** "Usar contraseña": formulario con el correo de la sesión guardada. */
    private void usarContrasena() {
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        mostrarFormulario();
        if (usuario != null) {
            etEmail.setText(usuario.getEmail());
            etPassword.requestFocus();
            volverAHuella.setEnabled(huella.isEnabled());
        }
    }

    // ---------- Variantes Default / Error / Cargando ----------

    private void mostrarFormulario() {
        volverAHuella.setEnabled(false);
        modoHuella.setVisibility(View.GONE);
        modoContrasena.setVisibility(View.VISIBLE);
        AlertView.ocultar(alerta);
        tilEmail.setError(null);
        tilPassword.setError(null);
    }

    private void iniciarSesion() {
        String email = texto(etEmail);
        String password = etPassword.getText() == null ? "" : etPassword.getText().toString();

        tilEmail.setError(null);
        tilPassword.setError(null);

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError(getString(R.string.error_email));
            return;
        }
        if (TextUtils.isEmpty(password)) {
            tilPassword.setError(getString(R.string.error_required));
            return;
        }

        FirebaseUser anterior = FirebaseAuth.getInstance().getCurrentUser();
        mostrarCargando(true);
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    // La huella guardada pertenece a la sesión anterior: si entró otra cuenta ya no sirve.
                    FirebaseUser nuevo = result.getUser();
                    if (anterior == null || nuevo == null || !anterior.getUid().equals(nuevo.getUid())) {
                        huella.disable();
                    }
                    Pantallas.abrirInicio(this);
                })
                .addOnFailureListener(e -> {
                    mostrarCargando(false);
                    tilPassword.setError(AuthErrors.mensaje(this, e));
                });
    }

    /** PrimaryButton Estado=Cargando: spinner + "Ingresando…", sin volver a enviar. */
    private void mostrarCargando(boolean cargando) {
        btnLogin.setClickable(!cargando);
        btnLogin.setText(cargando ? R.string.login_loading : R.string.login_button);
        btnLogin.setIcon(cargando ? spinner : null);
        tvForgot.setEnabled(!cargando);
    }

    private IndeterminateDrawable crearSpinner() {
        CircularProgressIndicatorSpec spec = new CircularProgressIndicatorSpec(this, null, 0,
                com.google.android.material.R.style.Widget_Material3_CircularProgressIndicator_ExtraSmall);
        spec.indicatorColors = new int[]{ContextCompat.getColor(this, R.color.surface_white)};
        spec.trackColor = Color.TRANSPARENT;
        return IndeterminateDrawable.createCircularDrawable(this, spec);
    }

    /** "¿Olvidaste tu contraseña?": envía el correo de Firebase y lo confirma con un Alert Info. */
    private void recuperarContrasena() {
        String email = texto(etEmail);
        tilEmail.setError(null);
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError(getString(R.string.login_reset_need_email));
            etEmail.requestFocus();
            return;
        }
        tvForgot.setEnabled(false);
        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                .addOnCompleteListener(t -> tvForgot.setEnabled(true))
                .addOnSuccessListener(r -> AlertView.mostrar(alerta, AlertView.Tipo.INFO,
                        getString(R.string.login_reset_sent, email)))
                .addOnFailureListener(e -> AlertView.mostrar(alerta, AlertView.Tipo.ERROR,
                        AuthErrors.mensaje(this, e)));
    }

    private static void limpiarErrorAlEscribir(TextInputEditText editText, TextInputLayout layout) {
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                layout.setError(null);
            }
        });
    }

    private static String texto(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }
}
