package com.example.miarriendo_v1;

import android.content.Intent;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class registro_2 extends AppCompatActivity {

    private EditText etPassword, etConfirmPassword;
    private CheckBox cbTerms;
    private DatabaseReference mDatabase;
    private String name, lastName, email, phone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_registro2);

        mDatabase = FirebaseDatabase.getInstance().getReference();

        // Obtener datos del Intent anterior
        Intent intent = getIntent();
        name = intent.getStringExtra("name");
        lastName = intent.getStringExtra("lastName");
        email = intent.getStringExtra("email");
        phone = intent.getStringExtra("phone");

        etPassword = findViewById(R.id.et_password);
        etConfirmPassword = findViewById(R.id.et_confirm_password);
        cbTerms = findViewById(R.id.cb_terms);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        TextView tvLogin = findViewById(R.id.tv_login_2);
        tvLogin.setOnClickListener(v -> {
            Intent mainIntent = new Intent(registro_2.this, MainActivity.class);
            startActivity(mainIntent);
            finishAffinity();
        });

        findViewById(R.id.btn_back).setOnClickListener(v -> {
            finish();
        });

        findViewById(R.id.btn_register).setOnClickListener(v -> {
            String password = etPassword.getText().toString();
            String confirmPassword = etConfirmPassword.getText().toString();

            if (password.length() < 8) {
                Toast.makeText(this, "La contraseña debe tener al menos 8 caracteres", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!password.equals(confirmPassword)) {
                Toast.makeText(this, "Las contraseñas no coinciden", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!cbTerms.isChecked()) {
                Toast.makeText(this, "Debes aceptar los términos y condiciones", Toast.LENGTH_SHORT).show();
                return;
            }

            registerUser(password);
        });

        findViewById(R.id.menu_icon).setOnClickListener(v -> {
            Intent menuIntent = new Intent(registro_2.this, MenuMobilActivity.class);
            startActivity(menuIntent);
        });
    }

    private void registerUser(String password) {
        // Crear un mapa con los datos del usuario
        Map<String, Object> user = new HashMap<>();
        user.put("nombre", name);
        user.put("apellido", lastName);
        user.put("email", email);
        user.put("telefono", phone);
        user.put("password", password); // En una app real, nunca guardes passwords en texto plano

        // Generar una clave única para el usuario (o usar el email codificado como clave)
        String userId = mDatabase.child("usuarios").push().getKey();

        if (userId != null) {
            mDatabase.child("usuarios").child(userId).setValue(user)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(registro_2.this, "Registro exitoso", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(registro_2.this, MainActivity.class);
                    startActivity(intent);
                    finishAffinity();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(registro_2.this, "Error al registrar: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
        }
    }
}