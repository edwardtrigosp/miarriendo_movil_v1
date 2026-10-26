package com.example.miarriendo_v1;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.transition.TransitionManager;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Figma: Propietario / Cargando (Verificando datos → Creando ubicación → Publicando) y Registrado.
 * Cada estado corresponde a un trabajo real: validar la sesión, geocodificar la dirección y guardar.
 */
public class PublicandoActivity extends AppCompatActivity {

    static final String EXTRA_PROPIEDAD = "propiedad";
    static final String EXTRA_FOTOS = "fotos";

    /** Tiempo mínimo de cada estado para que se alcance a leer. */
    private static final long DURACION_MINIMA_MS = 900;
    private static final long ESPERA_REGISTRADO_MS = 1400;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService trabajo = Executors.newSingleThreadExecutor();

    private ViewGroup contentArea;
    private ImageView ivEmblem;
    private TextView tvCurrent, tvSubtitle;
    private View progressTrack, progressFill;
    private ValueAnimator animacion;

    private Propiedad propiedad;
    private ArrayList<Uri> fotos;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Pantallas.edgeToEdge(this);
        setContentView(R.layout.activity_publicando);
        Pantallas.aplicarInsets(findViewById(R.id.main));

        contentArea = findViewById(R.id.content_area);
        ivEmblem = findViewById(R.id.iv_emblem);
        tvCurrent = findViewById(R.id.tv_current);
        tvSubtitle = findViewById(R.id.tv_subtitle);
        progressTrack = findViewById(R.id.progress_track);
        progressFill = findViewById(R.id.progress_fill);

        propiedad = (Propiedad) getIntent().getSerializableExtra(EXTRA_PROPIEDAD, Propiedad.class);
        fotos = getIntent().getParcelableArrayListExtra(EXTRA_FOTOS, Uri.class);
        if (fotos == null) {
            fotos = new ArrayList<>();
        }

        // No se permite salir a mitad de la publicación.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
            }
        });

        progressTrack.post(this::verificarDatos);
    }

    // ---------- Estado 1: Verificando tus datos ----------

    private void verificarDatos() {
        mostrarEstado(R.drawable.ic_fact_check, R.string.publishing_verifying, 1f / 3f);
        long inicio = System.currentTimeMillis();

        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null || propiedad == null) {
            Toast.makeText(this, R.string.publishing_session, Toast.LENGTH_LONG).show();
            Intent login = new Intent(this, LoginActivity.class);
            login.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(login);
            return;
        }
        propiedad.propietarioId = usuario.getUid();
        despuesDe(inicio, this::crearUbicacion);
    }

    // ---------- Estado 2: Creando ubicación ----------

    private void crearUbicacion() {
        mostrarEstado(R.drawable.ic_location_on, R.string.publishing_location, 2f / 3f);
        long inicio = System.currentTimeMillis();

        if ((propiedad.lat != null && propiedad.lng != null) || !Geocoder.isPresent()) {
            // Ya tiene coordenadas (se usó "mi ubicación") o el teléfono no tiene geocodificador.
            despuesDe(inicio, this::publicarPropiedad);
            return;
        }
        String consulta = propiedad.direccion
                + (propiedad.barrio.isEmpty() ? "" : ", " + propiedad.barrio)
                + ", " + propiedad.ciudad + ", Colombia";
        new Geocoder(this, new Locale("es", "CO")).getFromLocationName(consulta, 1, new Geocoder.GeocodeListener() {
            @Override
            public void onGeocode(List<Address> resultados) {
                if (!resultados.isEmpty()) {
                    propiedad.lat = resultados.get(0).getLatitude();
                    propiedad.lng = resultados.get(0).getLongitude();
                }
                runOnUiThread(() -> despuesDe(inicio, PublicandoActivity.this::publicarPropiedad));
            }

            @Override
            public void onError(String mensaje) {
                // Sin coordenadas la propiedad se publica igual; no aparecerá en el mapa hasta editarla.
                runOnUiThread(() -> despuesDe(inicio, PublicandoActivity.this::publicarPropiedad));
            }
        });
    }

    // ---------- Estado 3: Publicando tu propiedad ----------

    private void publicarPropiedad() {
        mostrarEstado(R.drawable.ic_cloud_upload, R.string.publishing_saving, 0.9f);
        long inicio = System.currentTimeMillis();

        trabajo.execute(() -> {
            List<String> fotosBase64 = new ArrayList<>();
            try {
                for (int i = 0; i < fotos.size(); i++) {
                    fotosBase64.add(FotosCodec.aBase64(getContentResolver(), fotos.get(i), FotosCodec.LADO_FOTO));
                }
                if (!fotos.isEmpty()) {
                    propiedad.portada = FotosCodec.aBase64(getContentResolver(), fotos.get(0), FotosCodec.LADO_PORTADA);
                }
            } catch (Exception e) {
                runOnUiThread(() -> mostrarError(e.getMessage()));
                return;
            }
            runOnUiThread(() -> guardar(fotosBase64, inicio));
        });
    }

    private void guardar(List<String> fotosBase64, long inicio) {
        FirebaseDatabase db = FirebaseDatabase.getInstance();
        DatabaseReference ref = db.getReference("propiedades").push();
        propiedad.id = ref.getKey();
        propiedad.numFotos = fotosBase64.size();
        propiedad.creadoEn = System.currentTimeMillis();

        // Primero la propiedad (las reglas validan que sea del usuario) y luego sus fotos.
        // Si las fotos fallan se borra la propiedad para que "Reintentar" no la duplique.
        ref.setValue(propiedad)
                .continueWithTask(t -> {
                    if (!t.isSuccessful() || fotosBase64.isEmpty()) {
                        return t;
                    }
                    return db.getReference("fotos_propiedades").child(propiedad.id).setValue(fotosBase64)
                            .continueWithTask(tFotos -> {
                                if (!tFotos.isSuccessful()) {
                                    ref.removeValue();
                                }
                                return tFotos;
                            });
                })
                .addOnSuccessListener(v -> despuesDe(inicio, this::mostrarRegistrado))
                .addOnFailureListener(e -> mostrarError(e.getMessage()));
    }

    // ---------- Estado 4: ¡Registrado! ----------

    private void mostrarRegistrado() {
        TransitionManager.beginDelayedTransition(contentArea);
        ivEmblem.setImageResource(R.drawable.ic_task_alt);
        tvCurrent.setText(R.string.publishing_done);
        tvSubtitle.setText(R.string.publishing_done_subtitle);
        progressTrack.setVisibility(View.GONE);

        handler.postDelayed(() -> {
            Intent inicio = new Intent(this, InicioActivity.class);
            inicio.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(inicio);
        }, ESPERA_REGISTRADO_MS);
    }

    // ---------- Utilidades ----------

    /** Muestra un estado: emblema, título en grande y la barra avanzando. */
    private void mostrarEstado(int icono, int titulo, float progreso) {
        TransitionManager.beginDelayedTransition(contentArea);
        ivEmblem.setImageResource(icono);
        tvCurrent.setText(titulo);
        tvSubtitle.setText(R.string.publishing_wait);
        animarProgreso(progreso);
    }

    private void animarProgreso(float fraccion) {
        if (animacion != null) {
            animacion.cancel();
        }
        int desde = progressFill.getLayoutParams().width;
        int hasta = Math.round(progressTrack.getWidth() * fraccion);
        animacion = ValueAnimator.ofInt(desde, hasta);
        animacion.setDuration(DURACION_MINIMA_MS);
        animacion.setInterpolator(new DecelerateInterpolator());
        animacion.addUpdateListener(a -> {
            ViewGroup.LayoutParams lp = progressFill.getLayoutParams();
            lp.width = (int) a.getAnimatedValue();
            progressFill.setLayoutParams(lp);
        });
        animacion.start();
    }

    /** Ejecuta {@code siguiente} cuando haya pasado al menos DURACION_MINIMA_MS desde {@code inicio}. */
    private void despuesDe(long inicio, Runnable siguiente) {
        long restante = DURACION_MINIMA_MS - (System.currentTimeMillis() - inicio);
        handler.postDelayed(siguiente, Math.max(0, restante));
    }

    private void mostrarError(String mensaje) {
        if (isFinishing()) {
            return;
        }
        Dialogo.nuevo(this)
                .titulo(R.string.publishing_error_title)
                .mensaje(mensaje)
                .obligatorio()
                .cancelar(R.string.cancel, this::finish)
                .confirmar(R.string.publishing_retry, Dialogo.Tono.PRINCIPAL, this::verificarDatos)
                .mostrar();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        trabajo.shutdownNow();
        if (animacion != null) {
            animacion.cancel();
        }
    }
}
