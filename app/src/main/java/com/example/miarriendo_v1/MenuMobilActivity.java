package com.example.miarriendo_v1;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MenuMobilActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_menu_mobil);
        
        ImageView closeIcon = findViewById(R.id.close_icon);
        closeIcon.setOnClickListener(v -> finish());

        findViewById(R.id.btn_menu_login).setOnClickListener(v -> {
            Intent intent = new Intent(MenuMobilActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        findViewById(R.id.btn_menu_register).setOnClickListener(v -> {
            Intent intent = new Intent(MenuMobilActivity.this, registro_1.class);
            startActivity(intent);
            finish();
        });
    }
}