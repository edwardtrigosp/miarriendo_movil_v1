package com.example.miarriendo_v1;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.TypefaceSpan;
import android.text.style.UnderlineSpan;
import android.view.View;
import android.widget.CheckBox;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

/** Figma: Onboarding / Registro paso 2 — "¿Cómo la usarás?" y términos. Crea la cuenta. */
public class RegisterRoleActivity extends AppCompatActivity {

    static final String ROL_INQUILINO = "inquilino";
    static final String ROL_PROPIETARIO = "propietario";

    private RadioGroup rgRole;
    private CheckBox cbTerms;
    private View btnCreateAccount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Pantallas.edgeToEdge(this);
        setContentView(R.layout.activity_register_role);
        Pantallas.aplicarInsets(findViewById(R.id.main));

        rgRole = findViewById(R.id.rg_role);
        cbTerms = findViewById(R.id.cb_terms);
        btnCreateAccount = findViewById(R.id.btn_create_account);

        cbTerms.setText(textoTerminos());
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        btnCreateAccount.setOnClickListener(v -> crearCuenta());
    }

    /** "Acepto los " + "términos y condiciones de uso" en morado, medium y subrayado. */
    private CharSequence textoTerminos() {
        String prefijo = getString(R.string.terms_prefix);
        String enlace = getString(R.string.terms_link);
        SpannableStringBuilder texto = new SpannableStringBuilder(prefijo).append(enlace);
        int inicio = prefijo.length();
        int fin = texto.length();
        texto.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this, R.color.brand_primary)),
                inicio, fin, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        texto.setSpan(new TypefaceSpan(Typeface.create("sans-serif-medium", Typeface.NORMAL)),
                inicio, fin, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        texto.setSpan(new UnderlineSpan(), inicio, fin, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return texto;
    }

    private void crearCuenta() {
        if (!cbTerms.isChecked()) {
            Toast.makeText(this, R.string.error_terms, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent datos = getIntent();
        String nombreCompleto = valor(datos.getStringExtra(RegisterActivity.EXTRA_NAME));
        String telefono = valor(datos.getStringExtra(RegisterActivity.EXTRA_PHONE));
        String email = valor(datos.getStringExtra(RegisterActivity.EXTRA_EMAIL));
        String password = valor(datos.getStringExtra(RegisterActivity.EXTRA_PASSWORD));
        String rol = rgRole.getCheckedRadioButtonId() == R.id.rb_owner ? ROL_PROPIETARIO : ROL_INQUILINO;

        // Se separa el nombre completo en nombre y apellido para el modelo Usuario.
        int espacio = nombreCompleto.indexOf(' ');
        String nombre = espacio > 0 ? nombreCompleto.substring(0, espacio) : nombreCompleto;
        String apellido = espacio > 0 ? nombreCompleto.substring(espacio + 1).trim() : "";

        btnCreateAccount.setEnabled(false);

        // Firebase Auth guarda la contraseña de forma segura; en la base solo va el perfil.
        FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    String uid = result.getUser().getUid();
                    Usuario usuario = new Usuario(uid, nombre, apellido, telefono, email);
                    usuario.setRol(rol);

                    FirebaseDatabase.getInstance().getReference("usuarios").child(uid).setValue(usuario)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this, R.string.register_success, Toast.LENGTH_SHORT).show();
                                btnCreateAccount.setEnabled(true);
                                Pantallas.abrirInicio(this);
                            })
                            .addOnFailureListener(e -> {
                                btnCreateAccount.setEnabled(true);
                                Toast.makeText(this, getString(R.string.register_error, e.getMessage()),
                                        Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(e -> {
                    btnCreateAccount.setEnabled(true);
                    Toast.makeText(this, AuthErrors.mensaje(this, e), Toast.LENGTH_LONG).show();
                });
    }

    private static String valor(String s) {
        return s == null ? "" : s;
    }
}
