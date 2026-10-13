package com.example.miarriendo_v1;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.List;

/**
 * Inicio unificado por módulos: reemplazará a InquilinoInicioActivity y PropietarioInicioActivity.
 * Figma: 00 · Inicio unificado · Mapa de módulos (448:4299); ejemplos en 05B, 05C y 02B.
 * Solo se abre con el interruptor BuildConfig.INICIO_UNIFICADO; los módulos llegan en las fases 1 a 3.
 */
public class InicioActivity extends AppCompatActivity {

    private LinearLayout contenedor;
    private View loading;
    private String uid;
    private CargaDatosInicio carga;
    private final List<ModuloInicio> activos = new ArrayList<>();
    private List<PlanDeInicio.Modulo> planActual;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Pantallas.edgeToEdge(this);
        setContentView(R.layout.activity_inicio);
        Pantallas.aplicarInsets(findViewById(R.id.main));
        contenedor = findViewById(R.id.contenedor_modulos);
        loading = findViewById(R.id.loading);

        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        uid = usuario.getUid();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (uid == null) {
            return;
        }
        if (carga != null) {
            iniciarCarga();
            return;
        }
        FirebaseDatabase.getInstance().getReference("usuarios").child(uid).child("rol").get()
                .addOnSuccessListener(snapshot -> {
                    carga = new CargaDatosInicio(uid, snapshot.getValue(String.class));
                    if (!isFinishing()) {
                        iniciarCarga();
                    }
                })
                .addOnFailureListener(this::mostrarError);
    }

    private void iniciarCarga() {
        carga.iniciar(new CargaDatosInicio.Listo() {
            @Override
            public void datos(DatosInicio datos) {
                armar(datos);
            }

            @Override
            public void error(Exception e) {
                mostrarError(e);
            }
        });
    }

    /** Rehace la lista de módulos solo si el plan cambió (por ejemplo, el inquilino empezó a arrendar). */
    private void armar(DatosInicio datos) {
        loading.setVisibility(View.GONE);
        List<PlanDeInicio.Modulo> plan = PlanDeInicio.modulos(datos);
        if (plan.equals(planActual)) {
            return;
        }
        planActual = plan;
        detenerModulos();
        contenedor.removeAllViews();
        ContextoInicio ctx = new ContextoInicio(this, uid, datos);
        for (PlanDeInicio.Modulo tipo : plan) {
            ModuloInicio modulo = crearModulo(tipo);
            if (modulo == null) {
                continue;
            }
            contenedor.addView(modulo.crear(getLayoutInflater(), contenedor));
            modulo.iniciar(ctx);
            activos.add(modulo);
        }
    }

    /** Fábrica de módulos. Los que aún no existen devuelven null y no se dibujan. */
    private ModuloInicio crearModulo(PlanDeInicio.Modulo tipo) {
        return null;
    }

    private void mostrarError(Exception e) {
        loading.setVisibility(View.GONE);
        Toast.makeText(this, getString(R.string.tenant_load_error, e.getMessage()), Toast.LENGTH_LONG).show();
    }

    private void detenerModulos() {
        for (ModuloInicio m : activos) {
            m.detener();
        }
        activos.clear();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (carga != null) {
            carga.detener();
        }
        detenerModulos();
        planActual = null;
    }
}
