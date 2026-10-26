package com.example.miarriendo_v1;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.List;

/**
 * Inicio único por módulos para inquilinos y propietarios (reemplazó a los inicios por rol).
 * Figma: página Presentación (531:3325), filas 2 a 4: inquilino sin arriendo (531:3446), con arriendo (531:3895)
 * y propietario (531:4102). El orden de los módulos lo decide PlanDeInicio.
 */
public class InicioActivity extends AppCompatActivity {

    private LinearLayout contenedor;
    private View loading;
    private ObjectAnimator brilloCarga;
    private String uid;
    private CargaDatosInicio carga;
    private final List<ModuloInicio> activos = new ArrayList<>();
    private List<PlanDeInicio.Modulo> planActual;
    /** Los módulos quitaron sus listeners en onStop y esperan volver a iniciarse. */
    private boolean modulosDetenidos;

    /** Catálogo compartido por los módulos; se vuelve a pedir cada vez que la pantalla regresa. */
    private List<Propiedad> catalogo;
    private final List<Catalogo.Resultado> esperandoCatalogo = new ArrayList<>();
    private boolean cargandoCatalogo;

    /** Filtros → Resultados con los filtros elegidos. */
    private final ActivityResultLauncher<Intent> elegirFiltros =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
                if (r.getResultCode() == RESULT_OK && r.getData() != null) {
                    startActivity(new Intent(this, ResultadosActivity.class).putExtra(FiltrosActivity.EXTRA_FILTROS,
                            r.getData().getSerializableExtra(FiltrosActivity.EXTRA_FILTROS)));
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Pantallas.edgeToEdge(this);
        setContentView(R.layout.activity_inicio);
        Pantallas.aplicarInsets(findViewById(R.id.main));
        contenedor = findViewById(R.id.contenedor_modulos);
        loading = findViewById(R.id.loading);
        brilloCarga = ObjectAnimator.ofFloat(loading, View.ALPHA, 1f, 0.55f).setDuration(900);
        brilloCarga.setRepeatMode(ValueAnimator.REVERSE);
        brilloCarga.setRepeatCount(ValueAnimator.INFINITE);
        brilloCarga.start();

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
        ocultarCarga();
        List<PlanDeInicio.Modulo> plan = PlanDeInicio.modulos(datos);
        ContextoInicio ctx = new ContextoInicio(this, uid, datos);
        if (plan.equals(planActual)) {
            if (modulosDetenidos) {
                // Se vuelve de otra pantalla con el mismo plan: se reutilizan las vistas en vez de rehacerlas,
                // así el inicio no parpadea.
                modulosDetenidos = false;
                for (int i = 0; i < activos.size(); i++) {
                    contenedor.getChildAt(i).setVisibility(View.VISIBLE);
                    activos.get(i).iniciar(ctx);
                }
            } else {
                for (ModuloInicio m : activos) {
                    m.actualizar(ctx);
                }
            }
            return;
        }
        planActual = plan;
        modulosDetenidos = false;
        detenerModulos();
        contenedor.removeAllViews();
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

    /** Fábrica de módulos. Un módulo sin implementación devuelve null y no se dibuja. */
    private ModuloInicio crearModulo(PlanDeInicio.Modulo tipo) {
        switch (tipo) {
            case ENCABEZADO:
                return new ModuloEncabezado();
            case ATAJOS:
                return new ModuloAtajos();
            case TU_SOLICITUD:
                return new ModuloTuSolicitud();
            case EXPLORAR_MAPA:
                return new ModuloExplorarMapa();
            case PARA_TI:
                return new ModuloParaTi();
            case MI_ARRIENDO:
                return new ModuloMiArriendo();
            case DETALLE_ARRIENDO:
                return new ModuloDetalleArriendo();
            case RECAUDO:
                return new ModuloRecaudo();
            case PENDIENTES:
                return new ModuloPendientes();
            case MIS_PROPIEDADES:
                return new ModuloMisPropiedades();
            case PRIMEROS_PASOS:
                return new ModuloPrimerosPasos();
            default:
                return null;
        }
    }

    void elegirFiltros(FiltrosBusqueda filtros) {
        elegirFiltros.launch(new Intent(this, FiltrosActivity.class).putExtra(FiltrosActivity.EXTRA_FILTROS, filtros));
    }

    void catalogo(Catalogo.Resultado resultado) {
        if (catalogo != null) {
            resultado.listo(catalogo);
            return;
        }
        esperandoCatalogo.add(resultado);
        if (cargandoCatalogo) {
            return;
        }
        cargandoCatalogo = true;
        Catalogo.cargar(new Catalogo.Resultado() {
            @Override
            public void listo(List<Propiedad> disponibles) {
                cargandoCatalogo = false;
                catalogo = disponibles;
                for (Catalogo.Resultado r : new ArrayList<>(esperandoCatalogo)) {
                    r.listo(disponibles);
                }
                esperandoCatalogo.clear();
            }

            @Override
            public void error(Exception e) {
                cargandoCatalogo = false;
                for (Catalogo.Resultado r : new ArrayList<>(esperandoCatalogo)) {
                    r.error(e);
                }
                esperandoCatalogo.clear();
            }
        });
    }

    /** Quita el esqueleto de carga (Figma 624:8011) con un fundido corto cuando ya están los módulos. */
    private void ocultarCarga() {
        if (loading.getVisibility() == View.GONE) {
            return;
        }
        brilloCarga.cancel();
        loading.animate().alpha(0f).setDuration(150).withEndAction(() -> loading.setVisibility(View.GONE));
    }

    private void mostrarError(Exception e) {
        ocultarCarga();
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
        // Las vistas se conservan; al volver, armar() las reinicia si el plan no cambió.
        for (ModuloInicio m : activos) {
            m.detener();
        }
        modulosDetenidos = true;
        // Al volver se recarga, por si se publicó o se arrendó alguna propiedad.
        catalogo = null;
        esperandoCatalogo.clear();
    }
}
