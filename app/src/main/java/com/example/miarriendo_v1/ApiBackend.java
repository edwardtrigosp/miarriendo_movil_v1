package com.example.miarriendo_v1;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cliente del servidor de pagos (carpeta backend/). Cada petición lleva el ID token de Firebase del
 * usuario; el servidor decide qué puede hacer con él. La respuesta llega siempre en el hilo principal.
 */
final class ApiBackend {

    interface Respuesta {
        void ok(JSONObject datos);

        /** {@code estado} es el código HTTP, o 0 si no hubo conexión. */
        void error(int estado, String mensaje);
    }

    private static final int TIEMPO_CONEXION_MS = 10_000;
    /** Crear un cobro en Wompi puede tardar unos segundos. */
    private static final int TIEMPO_LECTURA_MS = 40_000;

    private static final ExecutorService HILO = Executors.newFixedThreadPool(2);
    private static final Handler PRINCIPAL = new Handler(Looper.getMainLooper());

    private ApiBackend() {
    }

    static void get(Context context, String ruta, Respuesta respuesta) {
        llamar(context.getApplicationContext(), "GET", ruta, null, respuesta);
    }

    static void post(Context context, String ruta, JSONObject cuerpo, Respuesta respuesta) {
        llamar(context.getApplicationContext(), "POST", ruta, cuerpo, respuesta);
    }

    private static void llamar(Context context, String metodo, String ruta, JSONObject cuerpo, Respuesta respuesta) {
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null) {
            devolverError(respuesta, 401, context.getString(R.string.api_error_session));
            return;
        }
        usuario.getIdToken(false)
                .addOnSuccessListener(token -> HILO.execute(() -> ejecutar(context, metodo, ruta, cuerpo, token.getToken(), respuesta)))
                .addOnFailureListener(e -> devolverError(respuesta, 401, context.getString(R.string.api_error_session)));
    }

    private static void ejecutar(Context context, String metodo, String ruta, JSONObject cuerpo, String token, Respuesta respuesta) {
        HttpURLConnection conexion = null;
        int estado = 0;
        try {
            conexion = (HttpURLConnection) new URL(BuildConfig.BACKEND_URL.replaceAll("/+$", "") + ruta).openConnection();
            conexion.setRequestMethod(metodo);
            conexion.setConnectTimeout(TIEMPO_CONEXION_MS);
            conexion.setReadTimeout(TIEMPO_LECTURA_MS);
            conexion.setRequestProperty("Authorization", "Bearer " + token);
            conexion.setRequestProperty("Accept", "application/json");
            if (cuerpo != null) {
                conexion.setDoOutput(true);
                conexion.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (OutputStream salida = conexion.getOutputStream()) {
                    salida.write(cuerpo.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
            estado = conexion.getResponseCode();
            String texto = leer(estado >= 400 ? conexion.getErrorStream() : conexion.getInputStream());
            JSONObject json = texto.isEmpty() ? new JSONObject() : new JSONObject(texto);
            if (estado >= 200 && estado < 300) {
                PRINCIPAL.post(() -> respuesta.ok(json));
            } else {
                devolverError(respuesta, estado, json.optString("error", context.getString(R.string.api_error_generic)));
            }
        } catch (JSONException e) {
            devolverError(respuesta, estado, context.getString(R.string.api_error_generic));
        } catch (IOException e) {
            devolverError(respuesta, 0, context.getString(R.string.api_error_connection));
        } finally {
            if (conexion != null) {
                conexion.disconnect();
            }
        }
    }

    private static String leer(InputStream entrada) throws IOException {
        if (entrada == null) {
            return "";
        }
        try (InputStream in = entrada; ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) != -1) {
                salida.write(buffer, 0, n);
            }
            return salida.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static void devolverError(Respuesta respuesta, int estado, String mensaje) {
        PRINCIPAL.post(() -> respuesta.error(estado, mensaje));
    }
}
