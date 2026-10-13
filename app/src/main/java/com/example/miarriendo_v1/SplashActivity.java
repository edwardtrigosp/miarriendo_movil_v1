package com.example.miarriendo_v1;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Figma: Onboarding / Splash. Muestra el logo y, si ya hay sesión, abre el inicio del usuario;
 * si no, pasa al login.
 */
public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DELAY_MS = 1500;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable abrirLogin = () -> {
        Runnable irAlLogin = () -> {
            startActivity(new Intent(SplashActivity.this, LoginActivity.class));
            finish();
        };
        // Con el ingreso con huella activado, la sesión guardada se desbloquea en el login.
        if (new BiometricSession(this).isEnabled()) {
            irAlLogin.run();
        } else {
            Pantallas.abrirInicio(this, irAlLogin);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Pantallas.edgeToEdge(this);
        setContentView(R.layout.activity_splash);
    }

    @Override
    protected void onStart() {
        super.onStart();
        handler.postDelayed(abrirLogin, SPLASH_DELAY_MS);
    }

    @Override
    protected void onStop() {
        super.onStop();
        handler.removeCallbacks(abrirLogin);
    }
}
