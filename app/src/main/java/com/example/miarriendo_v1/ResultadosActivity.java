package com.example.miarriendo_v1;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

/** Figma: Inquilino / Resultados con filtros (315:6696). */
public class ResultadosActivity extends AppCompatActivity {

    private FiltrosBusqueda filtros;
    private List<Propiedad> catalogo = new ArrayList<>();

    private TextInputEditText etSearch;
    private ChipGroup cgApplied;
    private TextView tvCount, tvEnd, tvEmpty;
    private LinearLayout listResults;
    private View loading;

    private final ActivityResultLauncher<Intent> editarFiltros =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
                if (r.getResultCode() == RESULT_OK && r.getData() != null) {
                    filtros = (FiltrosBusqueda) r.getData().getSerializableExtra(FiltrosActivity.EXTRA_FILTROS);
                    etSearch.setText(filtros.ciudad);
                    mostrar();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Pantallas.edgeToEdge(this);
        setContentView(R.layout.activity_resultados);
        Pantallas.aplicarInsets(findViewById(R.id.main));

        ((TextView) findViewById(R.id.tv_app_bar_title)).setText(R.string.results_title);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        etSearch = findViewById(R.id.et_search);
        cgApplied = findViewById(R.id.cg_applied);
        tvCount = findViewById(R.id.tv_count);
        tvEnd = findViewById(R.id.tv_end);
        tvEmpty = findViewById(R.id.tv_empty);
        listResults = findViewById(R.id.list_results);
        loading = findViewById(R.id.loading);

        filtros = (FiltrosBusqueda) getIntent().getSerializableExtra(FiltrosActivity.EXTRA_FILTROS);
        if (filtros == null) {
            filtros = new FiltrosBusqueda();
        }
        etSearch.setText(filtros.ciudad);
        etSearch.setOnEditorActionListener((v, accion, evento) -> {
            if (accion != EditorInfo.IME_ACTION_SEARCH) {
                return false;
            }
            filtros.ciudad = etSearch.getText() == null ? "" : etSearch.getText().toString().trim();
            mostrar();
            return true;
        });
        findViewById(R.id.btn_filters).setOnClickListener(v -> abrirFiltros());

        Catalogo.cargar(new Catalogo.Resultado() {
            @Override
            public void listo(List<Propiedad> disponibles) {
                catalogo = disponibles;
                loading.setVisibility(View.GONE);
                mostrar();
            }

            @Override
            public void error(Exception e) {
                loading.setVisibility(View.GONE);
                Toast.makeText(ResultadosActivity.this,
                        getString(R.string.tenant_load_error, e.getMessage()), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void abrirFiltros() {
        editarFiltros.launch(new Intent(this, FiltrosActivity.class).putExtra(FiltrosActivity.EXTRA_FILTROS, filtros));
    }

    private void mostrar() {
        // Filtros aplicados: tocarlos abre los filtros para cambiarlos.
        cgApplied.removeAllViews();
        List<String> etiquetas = filtros.etiquetas(this);
        cgApplied.setVisibility(etiquetas.isEmpty() ? View.GONE : View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (String etiqueta : etiquetas) {
            Chip chip = (Chip) inflater.inflate(R.layout.item_filtro_aplicado, cgApplied, false);
            chip.setText(etiqueta);
            chip.setOnClickListener(v -> abrirFiltros());
            cgApplied.addView(chip);
        }

        List<Propiedad> resultados = filtros.aplicar(catalogo);
        tvCount.setText(getResources().getQuantityString(R.plurals.results_count, resultados.size(), resultados.size()));
        tvEmpty.setVisibility(resultados.isEmpty() ? View.VISIBLE : View.GONE);
        tvEnd.setVisibility(resultados.isEmpty() ? View.GONE : View.VISIBLE);
        listResults.removeAllViews();
        int separacion = Math.round(20 * getResources().getDisplayMetrics().density);
        for (int i = 0; i < resultados.size(); i++) {
            Propiedad p = resultados.get(i);
            View tarjeta = TarjetasPropiedad.grande(this, listResults, p, v ->
                    startActivity(new Intent(this, DetalleArriendoActivity.class)
                            .putExtra(DetalleArriendoActivity.EXTRA_PROPIEDAD_ID, p.id)));
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) tarjeta.getLayoutParams();
            lp.topMargin = i == 0 ? 0 : separacion;
            listResults.addView(tarjeta, lp);
        }
    }
}
