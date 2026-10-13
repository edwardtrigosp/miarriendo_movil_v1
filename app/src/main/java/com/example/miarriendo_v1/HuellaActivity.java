package com.example.miarriendo_v1;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import javax.crypto.Cipher;

/**
 * Figma: Propietario / Ingreso con huella — Desactivado (202:561), Activado (202:595) y
 * No disponible (204:619). La huella desbloquea la sesión de Firebase guardada en el teléfono.
 */
public class HuellaActivity extends AppCompatActivity {

    private BiometricSession biometricSession;
    private MaterialSwitch swBiometric;
    private TextView tvState;
    private View btnOpenSettings;
    private View tip;
    private final CompoundButton.OnCheckedChangeListener alCambiar = (b, activar) -> {
        if (activar) {
            activar();
        } else {
            biometricSession.disable();
            mostrarEstado();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Pantallas.edgeToEdge(this);
        setContentView(R.layout.activity_huella);
        Pantallas.aplicarInsets(findViewById(R.id.main));

        biometricSession = new BiometricSession(this);
        swBiometric = findViewById(R.id.sw_biometric);
        tvState = findViewById(R.id.tv_toggle_state);
        btnOpenSettings = findViewById(R.id.btn_open_settings);

        tip = findViewById(R.id.tip_biometric);
        ((TextView) tip.findViewById(R.id.tv_tip_title)).setText(R.string.bio_tip_title);
        ((TextView) tip.findViewById(R.id.tv_tip_text)).setText(R.string.bio_tip_text);
        tip.findViewById(R.id.btn_tip_close).setVisibility(View.GONE);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        btnOpenSettings.setOnClickListener(v -> abrirAjustesDeHuella());
        // Tocar cualquier parte de la fila también cambia el interruptor.
        findViewById(R.id.row_biometric).setOnClickListener(v -> {
            if (swBiometric.isEnabled()) swBiometric.toggle();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Se recalcula al volver de los ajustes del teléfono (por si registró una huella).
        mostrarEstado();
    }

    /** Pinta uno de los tres estados del diseño según el teléfono y lo guardado. */
    private void mostrarEstado() {
        boolean disponible = biometricSession.isAvailable();
        boolean activado = disponible && biometricSession.isEnabled();
        // TipCard solo en "Desactivado" (202:561); en Activado y No disponible no aparece.
        tip.setVisibility(disponible && !activado ? View.VISIBLE : View.GONE);

        swBiometric.setOnCheckedChangeListener(null);
        swBiometric.setChecked(activado);
        swBiometric.setEnabled(disponible);
        swBiometric.setOnCheckedChangeListener(alCambiar);

        if (!disponible) {
            boolean sinHuellas = biometricSession.needsEnrollment();
            tvState.setText(sinHuellas ? R.string.bio_state_not_enrolled : R.string.bio_state_no_hardware);
            btnOpenSettings.setVisibility(sinHuellas ? View.VISIBLE : View.GONE);
        } else {
            tvState.setText(activado ? R.string.bio_state_on : R.string.bio_state_off);
            btnOpenSettings.setVisibility(View.GONE);
        }
    }

    /** Confirma la huella y guarda la sesión cifrada con la llave del Keystore. */
    private void activar() {
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null) {
            mostrarEstado();
            return;
        }
        Cipher cipher;
        try {
            cipher = biometricSession.cipherForEncryption();
        } catch (Exception e) {
            HuellaPrompt.error(this, e.getMessage());
            mostrarEstado();
            return;
        }
        HuellaPrompt.mostrar(this, R.string.bio_prompt_enable_title, R.string.bio_prompt_enable_subtitle, cipher,
                new HuellaPrompt.Resultado() {
                    @Override
                    public void exito(Cipher c) throws Exception {
                        biometricSession.save(c, usuario.getUid());
                        Toast.makeText(HuellaActivity.this, R.string.bio_enabled, Toast.LENGTH_SHORT).show();
                        mostrarEstado();
                    }

                    @Override
                    public void cancelado() {
                        // Sin huella confirmada el interruptor vuelve a apagado.
                        mostrarEstado();
                    }
                });
    }

    private void abrirAjustesDeHuella() {
        Intent registrar = new Intent(Settings.ACTION_BIOMETRIC_ENROLL)
                .putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                        BiometricManager.Authenticators.BIOMETRIC_STRONG);
        try {
            startActivity(registrar);
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
        }
    }
}
