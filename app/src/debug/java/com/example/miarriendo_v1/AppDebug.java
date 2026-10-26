package com.example.miarriendo_v1;

import android.app.Activity;
import android.app.Application;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

/**
 * Solo compilaciones debug. Con USE_FIREBASE_EMULATORS la app habla con los emuladores de Firebase del PC
 * (host según EMULATORS_HOST: 10.0.2.2 es el PC desde el emulador de Android) en vez de con el proyecto real. Así se pueden probar los
 * flujos de pago con datos de mentira. Se activa con -PemuladoresFirebase=true al compilar.
 * Con ESCALA_TEXTO distinto de 1 (-PescalaTexto=2.0) agranda el texto de cada pantalla de la app, para revisar
 * la accesibilidad con letra grande sin tocar los ajustes del teléfono.
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
        if (BuildConfig.ESCALA_TEXTO != 1f) {
            registerActivityLifecycleCallbacks(new EscalaTexto());
        }
    }

    /** Antes de crear cada pantalla, multiplica la escala de fuente por ESCALA_TEXTO. */
    private static final class EscalaTexto implements ActivityLifecycleCallbacks {
        @Override
        public void onActivityPreCreated(@NonNull Activity activity, @Nullable Bundle estado) {
            Resources recursos = activity.getResources();
            Configuration config = new Configuration(recursos.getConfiguration());
            config.fontScale = BuildConfig.ESCALA_TEXTO;
            recursos.updateConfiguration(config, recursos.getDisplayMetrics());
        }

        @Override public void onActivityCreated(@NonNull Activity a, @Nullable Bundle b) { }
        @Override public void onActivityStarted(@NonNull Activity a) { }
        @Override public void onActivityResumed(@NonNull Activity a) { }
        @Override public void onActivityPaused(@NonNull Activity a) { }
        @Override public void onActivityStopped(@NonNull Activity a) { }
        @Override public void onActivitySaveInstanceState(@NonNull Activity a, @NonNull Bundle b) { }
        @Override public void onActivityDestroyed(@NonNull Activity a) { }
    }
}
