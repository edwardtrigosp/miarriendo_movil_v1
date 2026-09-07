package com.example.miarriendo_v1;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class PropiedadesActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_propiedades);
        
        findViewById(R.id.menu_icon).setOnClickListener(v -> {
            Intent intent = new Intent(PropiedadesActivity.this, MenuMobilActivity.class);
            startActivity(intent);
        });
    }
}