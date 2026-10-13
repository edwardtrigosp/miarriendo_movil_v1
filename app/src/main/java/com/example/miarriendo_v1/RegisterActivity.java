package com.example.miarriendo_v1;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/** Figma: Onboarding / Registro paso 1 — datos personales y contraseña. */
public class RegisterActivity extends AppCompatActivity {

    static final String EXTRA_NAME = "name";
    static final String EXTRA_PHONE = "phone";
    static final String EXTRA_EMAIL = "email";
    static final String EXTRA_PASSWORD = "password";

    private static final int MIN_PASSWORD_LENGTH = 8;

    private TextInputLayout tilName, tilEmail, tilPassword, tilPasswordConfirm;
    private TextInputEditText etName, etPhone, etEmail, etPassword, etPasswordConfirm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Pantallas.edgeToEdge(this);
        setContentView(R.layout.activity_register);
        Pantallas.aplicarInsets(findViewById(R.id.main));

        tilName = findViewById(R.id.til_name);
        tilEmail = findViewById(R.id.til_email);
        tilPassword = findViewById(R.id.til_password);
        tilPasswordConfirm = findViewById(R.id.til_password_confirm);
        etName = findViewById(R.id.et_name);
        etPhone = findViewById(R.id.et_phone);
        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        etPasswordConfirm = findViewById(R.id.et_password_confirm);

        findViewById(R.id.btn_continue).setOnClickListener(v -> continuar());
        // Se llega aquí desde el login, así que "Inicia sesión" vuelve a él.
        findViewById(R.id.tv_login).setOnClickListener(v -> finish());
    }

    private void continuar() {
        String name = texto(etName);
        String phone = texto(etPhone);
        String email = texto(etEmail);
        String password = etPassword.getText() == null ? "" : etPassword.getText().toString();
        String confirm = etPasswordConfirm.getText() == null ? "" : etPasswordConfirm.getText().toString();

        tilName.setError(null);
        tilEmail.setError(null);
        tilPassword.setError(null);
        tilPasswordConfirm.setError(null);

        if (TextUtils.isEmpty(name)) {
            tilName.setError(getString(R.string.error_required));
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError(getString(R.string.error_email));
            return;
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            tilPassword.setError(getString(R.string.error_password_length));
            return;
        }
        if (!password.equals(confirm)) {
            tilPasswordConfirm.setError(getString(R.string.error_password_match));
            return;
        }

        Intent intent = new Intent(this, RegisterRoleActivity.class);
        intent.putExtra(EXTRA_NAME, name);
        intent.putExtra(EXTRA_PHONE, phone);
        intent.putExtra(EXTRA_EMAIL, email);
        intent.putExtra(EXTRA_PASSWORD, password);
        startActivity(intent);
    }

    private static String texto(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }
}
