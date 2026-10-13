package com.example.miarriendo_v1;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import androidx.annotation.Nullable;
import androidx.biometric.BiometricManager;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Guarda la sesión cifrada con una llave del Android Keystore que solo se puede usar
 * después de que el usuario pone su huella (BIOMETRIC_STRONG).
 */
public class BiometricSession {

    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "miarriendo_biometric_key";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String PREFS = "biometric_session";
    private static final String PREF_DATA = "data";
    private static final String PREF_IV = "iv";

    private final SharedPreferences prefs;
    private final BiometricManager biometricManager;

    public BiometricSession(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        biometricManager = BiometricManager.from(context);
    }

    /** El dispositivo tiene sensor y al menos una huella registrada. */
    public boolean isAvailable() {
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                == BiometricManager.BIOMETRIC_SUCCESS;
    }

    /** Tiene lector de huella pero todavía no hay ninguna huella registrada en el teléfono. */
    public boolean needsEnrollment() {
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED;
    }

    public boolean isEnabled() {
        return prefs.contains(PREF_DATA) && prefs.contains(PREF_IV);
    }

    public void disable() {
        prefs.edit().clear().apply();
        try {
            KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
            keyStore.load(null);
            keyStore.deleteEntry(KEY_ALIAS);
        } catch (Exception ignored) {
            // Si la llave no existe no hay nada que borrar.
        }
    }

    /** Cipher para cifrar (activar huella). Debe pasarse a BiometricPrompt. */
    public Cipher cipherForEncryption() throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        try {
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        } catch (KeyPermanentlyInvalidatedException e) {
            // Las huellas cambiaron: se regenera la llave.
            disable();
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        }
        return cipher;
    }

    /**
     * Cipher para descifrar (ingresar con huella). Devuelve null si la llave quedó invalidada
     * porque el usuario agregó o borró huellas; en ese caso hay que volver a usar contraseña.
     */
    @Nullable
    public Cipher cipherForDecryption() throws Exception {
        byte[] iv = Base64.decode(prefs.getString(PREF_IV, ""), Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        try {
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
            return cipher;
        } catch (KeyPermanentlyInvalidatedException e) {
            disable();
            return null;
        }
    }

    /** Llamar con el cipher ya autenticado por BiometricPrompt. */
    public void save(Cipher authenticatedCipher, String sessionValue) throws Exception {
        byte[] encrypted = authenticatedCipher.doFinal(sessionValue.getBytes(StandardCharsets.UTF_8));
        prefs.edit()
                .putString(PREF_DATA, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString(PREF_IV, Base64.encodeToString(authenticatedCipher.getIV(), Base64.NO_WRAP))
                .apply();
    }

    /** Llamar con el cipher ya autenticado por BiometricPrompt. */
    public String read(Cipher authenticatedCipher) throws Exception {
        byte[] encrypted = Base64.decode(prefs.getString(PREF_DATA, ""), Base64.NO_WRAP);
        return new String(authenticatedCipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        }

        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                // 0 segundos = cada uso de la llave exige poner la huella.
                .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                .setInvalidatedByBiometricEnrollment(true)
                .build());
        return generator.generateKey();
    }
}
