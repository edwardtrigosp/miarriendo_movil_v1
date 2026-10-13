package com.example.miarriendo_v1;

import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import javax.crypto.Cipher;

/** Muestra el diálogo de huella del sistema ligado a un Cipher de {@link BiometricSession}. */
final class HuellaPrompt {

    interface Resultado {
        /** La huella fue reconocida; el cipher ya está autenticado. */
        void exito(Cipher cipher) throws Exception;

        /** El usuario cerró el diálogo o hubo un error. */
        void cancelado();

        /** El usuario tocó "Usar contraseña" en el diálogo. Por defecto equivale a cancelar. */
        default void usarContrasena() {
            cancelado();
        }
    }

    private HuellaPrompt() {
    }

    static void mostrar(FragmentActivity activity, int titulo, int subtitulo, Cipher cipher, Resultado resultado) {
        BiometricPrompt prompt = new BiometricPrompt(activity, ContextCompat.getMainExecutor(activity),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        BiometricPrompt.CryptoObject crypto = result.getCryptoObject();
                        if (crypto == null || crypto.getCipher() == null) {
                            resultado.cancelado();
                            return;
                        }
                        try {
                            resultado.exito(crypto.getCipher());
                        } catch (Exception e) {
                            error(activity, e.getMessage());
                            resultado.cancelado();
                        }
                    }

                    @Override
                    public void onAuthenticationError(int codigo, @NonNull CharSequence mensaje) {
                        if (codigo != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                                && codigo != BiometricPrompt.ERROR_USER_CANCELED
                                && codigo != BiometricPrompt.ERROR_CANCELED) {
                            error(activity, mensaje.toString());
                        }
                        if (codigo == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                            resultado.usarContrasena();
                        } else {
                            resultado.cancelado();
                        }
                    }
                    // onAuthenticationFailed (huella no reconocida) lo maneja el propio diálogo del sistema.
                });

        prompt.authenticate(new BiometricPrompt.PromptInfo.Builder()
                        .setTitle(activity.getString(titulo))
                        .setSubtitle(activity.getString(subtitulo))
                        .setNegativeButtonText(activity.getString(R.string.bio_prompt_cancel))
                        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                        .build(),
                new BiometricPrompt.CryptoObject(cipher));
    }

    static void error(FragmentActivity activity, String mensaje) {
        Toast.makeText(activity, activity.getString(R.string.bio_error, mensaje), Toast.LENGTH_LONG).show();
    }
}
