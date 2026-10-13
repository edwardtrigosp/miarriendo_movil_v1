package com.example.miarriendo_v1;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Figma: Inquilino / Filtros de búsqueda (315:6599). Devuelve los filtros elegidos en
 * {@link #EXTRA_FILTROS}; el botón principal muestra cuántas propiedades cumplen.
 */
public class FiltrosActivity extends AppCompatActivity {

    static final String EXTRA_FILTROS = "filtros";

    private MaterialAutoCompleteTextView etCity;
    private TextInputEditText etPriceMin, etPriceMax;
    private ChipGroup cgType, cgBedrooms;
    private MaterialButton btnApply;
    private List<Propiedad> catalogo = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Pantallas.edgeToEdge(this);
        setContentView(R.layout.activity_filtros);
        Pantallas.aplicarInsets(findViewById(R.id.main));

        ((TextView) findViewById(R.id.tv_app_bar_title)).setText(R.string.tenant_filters);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        etCity = findViewById(R.id.et_city);
        etPriceMin = findViewById(R.id.et_price_min);
        etPriceMax = findViewById(R.id.et_price_max);
        cgType = findViewById(R.id.cg_type);
        cgBedrooms = findViewById(R.id.cg_bedrooms);
        btnApply = findViewById(R.id.btn_apply);

        etPriceMin.addTextChangedListener(new MilesTextWatcher(etPriceMin));
        etPriceMax.addTextChangedListener(new MilesTextWatcher(etPriceMax));
        TextWatcher recontar = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                actualizarConteo();
            }
        };
        etPriceMin.addTextChangedListener(recontar);
        etPriceMax.addTextChangedListener(recontar);
        etCity.setOnItemClickListener((parent, view, position, id) -> actualizarConteo());
        cgType.setOnCheckedStateChangeListener((g, ids) -> actualizarConteo());
        cgBedrooms.setOnCheckedStateChangeListener((g, ids) -> actualizarConteo());

        FiltrosBusqueda inicial = (FiltrosBusqueda) getIntent().getSerializableExtra(EXTRA_FILTROS);
        mostrarFiltros(inicial != null ? inicial : new FiltrosBusqueda());

        btnApply.setOnClickListener(v -> {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_FILTROS, leerFiltros()));
            finish();
        });
        findViewById(R.id.btn_reset).setOnClickListener(v -> mostrarFiltros(new FiltrosBusqueda()));

        actualizarConteo();
        Catalogo.cargar(new Catalogo.Resultado() {
            @Override
            public void listo(List<Propiedad> disponibles) {
                catalogo = disponibles;
                llenarCiudades();
                actualizarConteo();
            }

            @Override
            public void error(Exception e) {
                // Sin catálogo el botón dice "Ver 0 propiedades"; los filtros igual se pueden aplicar.
            }
        });
    }

    /** "Todas las ciudades" y las ciudades de las propiedades disponibles. */
    private void llenarCiudades() {
        TreeSet<String> ciudades = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Propiedad p : catalogo) {
            if (p.ciudad != null && !p.ciudad.trim().isEmpty()) {
                ciudades.add(p.ciudad.trim());
            }
        }
        List<String> opciones = new ArrayList<>();
        opciones.add(getString(R.string.filters_all_cities));
        opciones.addAll(ciudades);
        etCity.setAdapter(new ArrayAdapter<>(this, R.layout.item_dropdown, opciones));
    }

    private void mostrarFiltros(FiltrosBusqueda f) {
        etCity.setText(f.ciudad.isEmpty() ? getString(R.string.filters_all_cities) : f.ciudad, false);
        etPriceMin.setText(f.precioMin > 0 ? Propiedad.formatearMiles(f.precioMin) : "");
        etPriceMax.setText(f.precioMax > 0 ? Propiedad.formatearMiles(f.precioMax) : "");
        int tipo = R.id.chip_type_all;
        for (int i = 0; i < cgType.getChildCount(); i++) {
            Chip c = (Chip) cgType.getChildAt(i);
            if (!f.tipo.isEmpty() && c.getText().toString().equalsIgnoreCase(f.tipo)) {
                tipo = c.getId();
            }
        }
        cgType.check(tipo);
        int[] habitaciones = {R.id.chip_bedrooms_all, R.id.chip_bedrooms_1, R.id.chip_bedrooms_2, R.id.chip_bedrooms_3};
        cgBedrooms.check(habitaciones[Math.max(0, Math.min(3, f.habitaciones))]);
        actualizarConteo();
    }

    private FiltrosBusqueda leerFiltros() {
        FiltrosBusqueda f = new FiltrosBusqueda();
        String ciudad = etCity.getText() == null ? "" : etCity.getText().toString().trim();
        f.ciudad = ciudad.equals(getString(R.string.filters_all_cities)) ? "" : ciudad;
        int tipo = cgType.getCheckedChipId();
        if (tipo != R.id.chip_type_all && tipo != ChipGroup.NO_ID) {
            f.tipo = ((Chip) findViewById(tipo)).getText().toString();
        }
        f.precioMin = MilesTextWatcher.valor(etPriceMin.getText());
        f.precioMax = MilesTextWatcher.valor(etPriceMax.getText());
        int habitaciones = cgBedrooms.getCheckedChipId();
        f.habitaciones = habitaciones == R.id.chip_bedrooms_1 ? 1
                : habitaciones == R.id.chip_bedrooms_2 ? 2
                : habitaciones == R.id.chip_bedrooms_3 ? 3 : 0;
        return f;
    }

    /** "Ver 2 propiedades" según los filtros actuales. */
    private void actualizarConteo() {
        if (btnApply == null) {
            return;
        }
        int n = leerFiltros().aplicar(catalogo).size();
        btnApply.setText(getResources().getQuantityString(R.plurals.filters_see, n, n));
    }
}
