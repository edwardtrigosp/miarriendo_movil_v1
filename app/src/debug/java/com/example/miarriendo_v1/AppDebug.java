package com.example.miarriendo_v1;

import android.app.Application;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

/**
 * Solo compilaciones debug. Con USE_FIREBASE_EMULATORS la app habla con los emuladores de Firebase del PC
 * (host según EMULATORS_HOST: 10.0.2.2 es el PC desde el emulador de Android) en vez de con el proyecto real. Así se pueden probar los
 * flujos de pago con datos de mentira. Se activa con -PemuladoresFirebase=true al compilar.
 */
public class AppDebug extends Application {

    private static final int PUERTO_AUTH = 9199;
    private static final int PUERTO_BASE_DE_DATOS = 9401;

    @Override
    public void onCreate() {
        super.onCreate();
        if (BuildConfig.USE_FIREBASE_EMULATORS) {
            FirebaseAuth.getInstance().useEmulator(BuildConfig.EMULATORS_HOST, PUERTO_AUTH);
            FirebaseDatabase.getInstance().useEmulator(BuildConfig.EMULATORS_HOST, PUERTO_BASE_DE_DATOS);
        }
    }
}
