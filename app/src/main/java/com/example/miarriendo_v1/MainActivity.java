package com.example.miarriendo_v1;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class MainActivity extends AppCompatActivity {

    private DatabaseReference mDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Inicializar Firebase
        FirebaseApp.initializeApp(this);
        
        // Inicializar Firebase Realtime Database
        mDatabase = FirebaseDatabase.getInstance().getReference();
        
        // Operación de prueba: Escribir un mensaje de bienvenida
        mDatabase.child("test_connection").setValue("¡Conexión exitosa desde miarriendo_v1!")
            .addOnSuccessListener(aVoid -> Toast.makeText(MainActivity.this, "Conectado a Firebase", Toast.LENGTH_SHORT).show())
            .addOnFailureListener(e -> Toast.makeText(MainActivity.this, "Error de conexión: " + e.getMessage(), Toast.LENGTH_LONG).show());

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        TextView tvCreateAccount = findViewById(R.id.tv_create_account);
        tvCreateAccount.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, registro_1.class);
            startActivity(intent);
        });

        findViewById(R.id.menu_icon).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, MenuMobilActivity.class);
            startActivity(intent);
        });
    }
}