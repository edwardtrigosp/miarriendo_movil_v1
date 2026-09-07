package com.example.miarriendo_v1;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class registro_1 extends AppCompatActivity {

    private EditText etName, etLastName, etEmail, etPhone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_registro1);

        etName = findViewById(R.id.et_name);
        etLastName = findViewById(R.id.et_last_name);
        etEmail = findViewById(R.id.et_email);
        etPhone = findViewById(R.id.et_phone);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        TextView tvLogin = findViewById(R.id.tv_login);
        tvLogin.setOnClickListener(v -> {
            Intent intent = new Intent(registro_1.this, MainActivity.class);
            startActivity(intent);
            finish();
        });

        findViewById(R.id.btn_next).setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String lastName = etLastName.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String phone = etPhone.getText().toString().trim();

            if (name.isEmpty() || lastName.isEmpty() || email.isEmpty()) {
                Toast.makeText(this, "Por favor completa los campos obligatorios", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(registro_1.this, registro_2.class);
            intent.putExtra("name", name);
            intent.putExtra("lastName", lastName);
            intent.putExtra("email", email);
            intent.putExtra("phone", phone);
            startActivity(intent);
        });

        findViewById(R.id.menu_icon).setOnClickListener(v -> {
            Intent intent = new Intent(registro_1.this, MenuMobilActivity.class);
            startActivity(intent);
        });
    }
}