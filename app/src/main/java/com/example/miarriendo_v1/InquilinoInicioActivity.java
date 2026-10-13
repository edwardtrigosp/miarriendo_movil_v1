package com.example.miarriendo_v1;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.List;

/** Figma: Inquilino / Inicio · Explorar arriendos (315:6469). */
public class InquilinoInicioActivity extends AppCompatActivity {

    private TextView tvAvailable, tvEmpty;
    private TextInputEditText etSearch;
    private LinearLayout listProperties;
    private View loading;

    /** Filtros → Resultados con los filtros elegidos. */
    private final ActivityResultLauncher<Intent> elegirFiltros =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
                if (r.getResultCode() == RESULT_OK && r.getData() != null) {
                    abrirResultados((FiltrosBusqueda) r.getData().getSerializableExtra(FiltrosActivity.EXTRA_FILTROS));
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Pantallas.edgeToEdge(this);
        setContentView(R.layout.activity_inquilino_inicio);
        Pantallas.aplicarInsets(findViewById(R.id.main));

        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        tvAvailable = findViewById(R.id.tv_available);
        tvEmpty = findViewById(R.id.tv_empty);
        etSearch = findViewById(R.id.et_search);
        listProperties = findViewById(R.id.list_properties);
        loading = findViewById(R.id.loading);

        // Saludo, Avatar (perfil) y Mis solicitudes: igual que en el mapa.
        EncabezadoInquilino.configurar(this, usuario);
        findViewById(R.id.btn_map).setOnClickListener(v -> {
            FiltrosBusqueda filtros = new FiltrosBusqueda();
            filtros.ciudad = texto(etSearch);
            startActivity(new Intent(this, InquilinoMapaActivity.class).putExtra(FiltrosActivity.EXTRA_FILTROS, filtros));
        });
        findViewById(R.id.btn_filters).setOnClickListener(v -> {
            FiltrosBusqueda filtros = new FiltrosBusqueda();
            filtros.ciudad = texto(etSearch);
            elegirFiltros.launch(new Intent(this, FiltrosActivity.class).putExtra(FiltrosActivity.EXTRA_FILTROS, filtros));
        });
        etSearch.setOnEditorActionListener((v, accion, evento) -> {
            if (accion != EditorInfo.IME_ACTION_SEARCH) {
                return false;
            }
            FiltrosBusqueda filtros = new FiltrosBusqueda();
            filtros.ciudad = texto(etSearch);
            abrirResultados(filtros);
            return true;
        });
    }

    /** Si el usuario ya arrienda una propiedad, un aviso en el inicio lleva a Mi arriendo. */
    private void mostrarArriendoActivo(String uid) {
        View aviso = findViewById(R.id.banner_rental);
        com.google.firebase.database.FirebaseDatabase.getInstance().getReference("propiedades")
                .orderByChild("inquilinoId").equalTo(uid).get()
                .addOnSuccessListener(snapshot -> {
                    for (com.google.firebase.database.DataSnapshot hijo : snapshot.getChildren()) {
                        Propiedad p = hijo.getValue(Propiedad.class);
                        if (p != null) {
                            AlertView.mostrar(aviso, AlertView.Tipo.INFO, getString(R.string.my_rental_banner, p.tituloPublico()));
                            aviso.setOnClickListener(v -> startActivity(new Intent(this, MiArriendoActivity.class)));
                            return;
                        }
                    }
                    AlertView.ocultar(aviso);
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        FirebaseUser actual = FirebaseAuth.getInstance().getCurrentUser();
        if (actual != null) {
            mostrarArriendoActivo(actual.getUid());
        }
        // Se recarga al volver por si se publicó o se arrendó alguna propiedad.
        Catalogo.cargar(new Catalogo.Resultado() {
            @Override
            public void listo(List<Propiedad> disponibles) {
                mostrar(disponibles);
            }

            @Override
            public void error(Exception e) {
                loading.setVisibility(View.GONE);
                Toast.makeText(InquilinoInicioActivity.this,
                        getString(R.string.tenant_load_error, e.getMessage()), Toast.LENGTH_LONG).show();
            }
        });
    }

    /** La primera propiedad en PropertyCard y las demás en tarjeta compacta. */
    private void mostrar(List<Propiedad> disponibles) {
        loading.setVisibility(View.GONE);
        tvAvailable.setText(getResources().getQuantityString(R.plurals.tenant_available, disponibles.size(), disponibles.size()));
        tvEmpty.setVisibility(disponibles.isEmpty() ? View.VISIBLE : View.GONE);
        listProperties.removeAllViews();
        int entreGrupos = Math.round(20 * getResources().getDisplayMetrics().density);
        int entreTarjetas = getResources().getDimensionPixelSize(R.dimen.spacing_sm);
        for (int i = 0; i < disponibles.size(); i++) {
            Propiedad p = disponibles.get(i);
            View.OnClickListener abrir = v -> abrirDetalle(p);
            View tarjeta = i == 0
                    ? TarjetasPropiedad.grande(this, listProperties, p, abrir)
                    : TarjetasPropiedad.compacta(this, listProperties, p, abrir);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) tarjeta.getLayoutParams();
            lp.topMargin = i == 0 ? 0 : (i == 1 ? entreGrupos : entreTarjetas);
            listProperties.addView(tarjeta, lp);
        }
    }

    private void abrirDetalle(Propiedad p) {
        startActivity(new Intent(this, DetalleArriendoActivity.class)
                .putExtra(DetalleArriendoActivity.EXTRA_PROPIEDAD_ID, p.id));
    }

    private void abrirResultados(FiltrosBusqueda filtros) {
        startActivity(new Intent(this, ResultadosActivity.class).putExtra(FiltrosActivity.EXTRA_FILTROS, filtros));
    }

    private static String texto(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }
}
