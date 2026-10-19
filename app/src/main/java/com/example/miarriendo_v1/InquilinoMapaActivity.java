package com.example.miarriendo_v1;

import android.Manifest;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMapOptions;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Figma: Actual · App › 06 · Inquilino · Explorar en mapa (rediseño). Se entra desde "Buscar en el mapa" del
 * inicio: ← Volver, buscador y Filtros (con contador) arriba; un pin con el precio por cada propiedad disponible;
 * abajo, la cantidad de arriendos de la zona visible con "Ver lista", o la tarjeta de la propiedad tocada con
 * "Ver propiedad". Los filtros activos se ven como chips y se quitan al tocarlos.
 */
public class InquilinoMapaActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String META_CLAVE_MAPS = "com.google.android.geo.API_KEY";
    private static final LatLng VALLEDUPAR = new LatLng(10.4631, -73.2532);
    private static final float ZOOM_CIUDAD = 13f;
    private static final float ZOOM_PROPIEDAD = 16f;
    private static final float ZOOM_UBICACION = 15f;

    private GoogleMap mapa;
    private FiltrosBusqueda filtros = new FiltrosBusqueda();
    private List<Propiedad> catalogo = new ArrayList<>();
    private boolean catalogoCargado;
    /** Propiedades que cumplen los filtros, por id. */
    private final Map<String, Propiedad> visibles = new LinkedHashMap<>();
    private final Map<String, Marker> marcadores = new HashMap<>();
    private String seleccionadaId;
    /** Cada vez que cambia lo que se muestra se descartan las ubicaciones que todavía venían en camino. */
    private int generacion;
    private int pendientes;
    private boolean ajustarAlTerminar;
    private int paddingInferior = -1;

    private EditText etSearch;
    private TextView tvFiltersCount, tvCount, tvEmptyTitle, tvEmptyText;
    private TextView tvSelectedName, tvSelectedPrice, tvSelectedZone, tvSelectedFeatures;
    private ImageView ivSelectedPhoto;
    private ChipGroup cgFilters;
    private View loading, mapContainer, filtersScroll, sheet, sheetSummary, sheetEmpty, cardSelected;
    private View btnLocate, btnClearFilters;

    /** Filtros → se aplican aquí mismo sobre el mapa. */
    private final ActivityResultLauncher<Intent> elegirFiltros =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
                if (r.getResultCode() != RESULT_OK || r.getData() == null) {
                    return;
                }
                FiltrosBusqueda elegidos = r.getData().getSerializableExtra(FiltrosActivity.EXTRA_FILTROS, FiltrosBusqueda.class);
                if (elegidos != null) {
                    filtros = elegidos;
                    etSearch.setText(filtros.ciudad);
                    alCambiarFiltros();
                }
            });

    private final ActivityResultLauncher<String[]> pedirUbicacion =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), permisos -> {
                if (UbicacionActual.tienePermiso(this)) {
                    centrarEnMiUbicacion();
                } else {
                    Toast.makeText(this, R.string.location_permission_denied, Toast.LENGTH_LONG).show();
                }
            });

    /** Con una propiedad seleccionada, "Atrás" primero cierra su tarjeta. */
    private final OnBackPressedCallback cerrarTarjeta = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            deseleccionar();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Pantallas.edgeToEdge(this);
        setContentView(R.layout.activity_inquilino_mapa);
        Pantallas.aplicarInsets(findViewById(R.id.main));

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        getOnBackPressedDispatcher().addCallback(this, cerrarTarjeta);

        etSearch = findViewById(R.id.et_search);
        tvFiltersCount = findViewById(R.id.tv_filters_count);
        cgFilters = findViewById(R.id.cg_filters);
        filtersScroll = findViewById(R.id.filters_scroll);
        loading = findViewById(R.id.loading);
        mapContainer = findViewById(R.id.map);
        sheet = findViewById(R.id.sheet);
        sheetSummary = findViewById(R.id.sheet_summary);
        sheetEmpty = findViewById(R.id.sheet_empty);
        tvCount = findViewById(R.id.tv_count);
        tvEmptyTitle = findViewById(R.id.tv_empty_title);
        tvEmptyText = findViewById(R.id.tv_empty_text);
        btnClearFilters = findViewById(R.id.btn_clear_filters);
        cardSelected = findViewById(R.id.card_selected);
        ivSelectedPhoto = findViewById(R.id.iv_selected_photo);
        tvSelectedName = findViewById(R.id.tv_selected_name);
        tvSelectedPrice = findViewById(R.id.tv_selected_price);
        tvSelectedZone = findViewById(R.id.tv_selected_zone);
        tvSelectedFeatures = findViewById(R.id.tv_selected_features);
        btnLocate = findViewById(R.id.btn_locate);

        FiltrosBusqueda recibidos = getIntent().getSerializableExtra(FiltrosActivity.EXTRA_FILTROS, FiltrosBusqueda.class);
        if (recibidos != null) {
            filtros = recibidos;
        }
        etSearch.setText(filtros.ciudad);
        mostrarFiltrosActivos();

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        etSearch.setOnEditorActionListener((v, accion, evento) -> {
            if (accion != EditorInfo.IME_ACTION_SEARCH) {
                return false;
            }
            filtros.ciudad = texto(etSearch);
            ocultarTeclado();
            alCambiarFiltros();
            return true;
        });
        findViewById(R.id.btn_filters).setOnClickListener(v -> {
            filtros.ciudad = texto(etSearch);
            elegirFiltros.launch(new Intent(this, FiltrosActivity.class).putExtra(FiltrosActivity.EXTRA_FILTROS, filtros));
        });
        btnLocate.setOnClickListener(v -> {
            if (UbicacionActual.tienePermiso(this)) {
                centrarEnMiUbicacion();
            } else {
                pedirUbicacion.launch(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
            }
        });
        findViewById(R.id.btn_see_list).setOnClickListener(v -> {
            filtros.ciudad = texto(etSearch);
            startActivity(new Intent(this, ResultadosActivity.class).putExtra(FiltrosActivity.EXTRA_FILTROS, filtros));
        });
        btnClearFilters.setOnClickListener(v -> {
            filtros.quitarTodos();
            etSearch.setText("");
            alCambiarFiltros();
        });
        findViewById(R.id.btn_close_card).setOnClickListener(v -> deseleccionar());
        View.OnClickListener irAPropiedad = v -> {
            Propiedad p = visibles.get(seleccionadaId);
            if (p != null) {
                abrirDetalle(p);
            }
        };
        findViewById(R.id.btn_go).setOnClickListener(irAPropiedad);
        findViewById(R.id.selected_summary).setOnClickListener(irAPropiedad);
        // El logo de Google queda siempre por encima de la hoja o de la tarjeta.
        View.OnLayoutChangeListener alCambiarAbajo = (v, l, t, r, b, ol, ot, or, ob) -> actualizarPaddingDelMapa();
        sheet.addOnLayoutChangeListener(alCambiarAbajo);
        cardSelected.addOnLayoutChangeListener(alCambiarAbajo);

        if (!hayClaveDeMapas()) {
            mostrarSinMapa();
            return;
        }
        iniciarMapa();
        cargarCatalogo(true);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        // Al volver se recarga por si cambió alguna propiedad (precio, disponibilidad).
        if (mapa != null && catalogoCargado) {
            cargarCatalogo(false);
        }
    }

    // ---------- Google Maps ----------

    /** La clave llega por MAPS_API_KEY de local.properties (ver app/build.gradle.kts). */
    private boolean hayClaveDeMapas() {
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(getPackageName(),
                    PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA));
            String clave = info.metaData == null ? null : info.metaData.getString(META_CLAVE_MAPS);
            return clave != null && !clave.trim().isEmpty();
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void mostrarSinMapa() {
        loading.setVisibility(View.GONE);
        findViewById(R.id.map_unavailable).setVisibility(View.VISIBLE);
        btnLocate.setVisibility(View.GONE);
        sheet.setVisibility(View.GONE);
    }

    private void iniciarMapa() {
        SupportMapFragment fragmento = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (fragmento == null) {
            GoogleMapOptions opciones = new GoogleMapOptions()
                    .camera(CameraPosition.fromLatLngZoom(VALLEDUPAR, ZOOM_CIUDAD))
                    .mapToolbarEnabled(false)
                    .zoomControlsEnabled(false)
                    .tiltGesturesEnabled(false);
            fragmento = SupportMapFragment.newInstance(opciones);
            getSupportFragmentManager().beginTransaction().replace(R.id.map, fragmento).commitNow();
        }
        fragmento.getMapAsync(this);
    }

    @Override
    @SuppressWarnings("MissingPermission")
    public void onMapReady(GoogleMap googleMap) {
        mapa = googleMap;
        try {
            mapa.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style));
        } catch (Resources.NotFoundException e) {
            // Sin el estilo del diseño se ve el mapa normal de Google.
        }
        UiSettings ui = mapa.getUiSettings();
        ui.setMyLocationButtonEnabled(false);
        ui.setMapToolbarEnabled(false);
        ui.setZoomControlsEnabled(false);
        ui.setTiltGesturesEnabled(false);
        ui.setIndoorLevelPickerEnabled(false);
        if (UbicacionActual.tienePermiso(this)) {
            mapa.setMyLocationEnabled(true);
        }
        mapa.setOnMarkerClickListener(marcador -> {
            if (marcador.getTag() instanceof String) {
                seleccionar((String) marcador.getTag());
            }
            return true;
        });
        mapa.setOnMapClickListener(posicion -> deseleccionar());
        // La cantidad de la hoja es la de la parte visible: se recalcula cada vez que el mapa se detiene.
        mapa.setOnCameraIdleListener(this::actualizarCantidad);
        mostrarPropiedades(true);
    }

    // ---------- Propiedades ----------

    private void cargarCatalogo(boolean primeraVez) {
        Catalogo.cargar(new Catalogo.Resultado() {
            @Override
            public void listo(List<Propiedad> disponibles) {
                if (isDestroyed()) {
                    return;
                }
                catalogo = disponibles;
                catalogoCargado = true;
                if (!primeraVez) {
                    limpiarMarcadores();
                }
                mostrarPropiedades(primeraVez);
            }

            @Override
            public void error(Exception e) {
                if (isDestroyed()) {
                    return;
                }
                loading.setVisibility(View.GONE);
                Toast.makeText(InquilinoMapaActivity.this,
                        getString(R.string.tenant_load_error, e.getMessage()), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void alCambiarFiltros() {
        mostrarFiltrosActivos();
        mostrarPropiedades(true);
    }

    /** Aplica los filtros y deja un pin por cada propiedad que cumple. */
    private void mostrarPropiedades(boolean ajustarCamara) {
        if (mapa == null || !catalogoCargado) {
            return;
        }
        loading.setVisibility(View.GONE);

        List<Propiedad> lista = filtros.aplicar(catalogo);
        visibles.clear();
        for (Propiedad p : lista) {
            visibles.put(p.id, p);
        }

        // Fuera los pines de las propiedades que ya no cumplen los filtros.
        Iterator<Map.Entry<String, Marker>> it = marcadores.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Marker> entrada = it.next();
            if (!visibles.containsKey(entrada.getKey())) {
                entrada.getValue().remove();
                it.remove();
            }
        }
        if (seleccionadaId != null && !visibles.containsKey(seleccionadaId)) {
            deseleccionar();
        } else if (seleccionadaId != null) {
            mostrarTarjeta(visibles.get(seleccionadaId));
        }
        actualizarAbajo();

        List<Propiedad> porUbicar = new ArrayList<>();
        for (Propiedad p : lista) {
            if (!marcadores.containsKey(p.id)) {
                porUbicar.add(p);
            }
        }
        int actual = ++generacion;
        ajustarAlTerminar = ajustarCamara;
        pendientes = porUbicar.size();
        if (porUbicar.isEmpty()) {
            terminarUbicaciones();
            return;
        }
        for (Propiedad p : porUbicar) {
            UbicacionesPropiedades.resolver(this, p, posicion -> alUbicar(actual, p.id, posicion));
        }
    }

    private void alUbicar(int deGeneracion, String id, LatLng posicion) {
        if (deGeneracion != generacion || isDestroyed() || mapa == null) {
            return;
        }
        Propiedad p = visibles.get(id);
        if (p != null && posicion != null && !marcadores.containsKey(id)) {
            MarcadoresMapa.Pin pin = MarcadoresMapa.crear(this, p, id.equals(seleccionadaId));
            Marker marcador = mapa.addMarker(new MarkerOptions()
                    .position(posicion)
                    .icon(pin.icono)
                    .anchor(pin.anclaX, pin.anclaY)
                    .zIndex(id.equals(seleccionadaId) ? 1f : 0f)
                    .title(p.tituloPublico()));
            if (marcador != null) {
                marcador.setTag(id);
                marcadores.put(id, marcador);
            }
        }
        if (--pendientes <= 0) {
            terminarUbicaciones();
        }
    }

    private void terminarUbicaciones() {
        if (ajustarAlTerminar) {
            ajustarAlTerminar = false;
            ajustarCamara();
        }
        actualizarCantidad();
    }

    private void limpiarMarcadores() {
        for (Marker marcador : marcadores.values()) {
            marcador.remove();
        }
        marcadores.clear();
    }

    /** Encuadra todos los pines; con uno solo, se acerca a él. */
    private void ajustarCamara() {
        if (mapa == null || marcadores.isEmpty()) {
            return;
        }
        int ancho = mapContainer.getWidth();
        int alto = mapContainer.getHeight();
        if (ancho == 0 || alto == 0) {
            mapContainer.post(this::ajustarCamara);
            return;
        }
        if (marcadores.size() == 1) {
            LatLng unica = marcadores.values().iterator().next().getPosition();
            mapa.animateCamera(CameraUpdateFactory.newLatLngZoom(unica, ZOOM_PROPIEDAD));
            return;
        }
        LatLngBounds.Builder limites = new LatLngBounds.Builder();
        for (Marker marcador : marcadores.values()) {
            limites.include(marcador.getPosition());
        }
        // El encuadre se calcula sobre la parte que no tapa la hoja o la tarjeta.
        int altoVisible = Math.max(alto / 2, alto - Math.max(0, paddingInferior));
        int margen = Math.min(dp(72), Math.min(ancho, altoVisible) / 4);
        mapa.animateCamera(CameraUpdateFactory.newLatLngBounds(limites.build(), ancho, altoVisible, margen));
    }

    // ---------- Filtros activos ----------

    /** Contador en Filtros y un chip por filtro aplicado; tocar el chip quita ese filtro. */
    private void mostrarFiltrosActivos() {
        List<FiltrosBusqueda.Activo> activos = filtros.activos(this);
        int n = activos.size();
        tvFiltersCount.setVisibility(n == 0 ? View.GONE : View.VISIBLE);
        tvFiltersCount.setText(String.valueOf(n));
        findViewById(R.id.btn_filters).setContentDescription(n == 0 ? getString(R.string.tenant_filters)
                : getResources().getQuantityString(R.plurals.map_filters_active, n, n));

        cgFilters.removeAllViews();
        filtersScroll.setVisibility(n == 0 ? View.GONE : View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (FiltrosBusqueda.Activo activo : activos) {
            Chip chip = (Chip) inflater.inflate(R.layout.item_filtro_quitable, cgFilters, false);
            chip.setText(activo.etiqueta);
            chip.setContentDescription(getString(R.string.map_remove_filter, activo.etiqueta));
            View.OnClickListener quitar = v -> {
                activo.quitar.run();
                alCambiarFiltros();
            };
            chip.setOnClickListener(quitar);
            chip.setOnCloseIconClickListener(quitar);
            cgFilters.addView(chip);
        }
    }

    // ---------- Hoja inferior y selección ----------

    /** Abajo se ve la tarjeta si hay una propiedad seleccionada; si no, la hoja (cantidad o sin resultados). */
    private void actualizarAbajo() {
        boolean haySeleccion = seleccionadaId != null;
        boolean vacio = visibles.isEmpty();
        cardSelected.setVisibility(haySeleccion ? View.VISIBLE : View.GONE);
        sheet.setVisibility(haySeleccion ? View.GONE : View.VISIBLE);
        sheetSummary.setVisibility(vacio ? View.GONE : View.VISIBLE);
        sheetEmpty.setVisibility(vacio ? View.VISIBLE : View.GONE);
        if (vacio) {
            boolean sinCatalogo = catalogo.isEmpty();
            tvEmptyTitle.setText(sinCatalogo ? R.string.map_empty_title : R.string.map_empty_filters_title);
            tvEmptyText.setText(sinCatalogo ? R.string.tenant_empty : R.string.map_empty_filters_text);
            btnClearFilters.setVisibility(sinCatalogo ? View.GONE : View.VISIBLE);
        }
        cerrarTarjeta.setEnabled(haySeleccion);
        actualizarCantidad();
    }

    /** "N arriendos en esta zona" (o "con tus filtros"): los pines que caen en la parte visible del mapa. */
    private void actualizarCantidad() {
        if (mapa == null || visibles.isEmpty()) {
            return;
        }
        LatLngBounds zona = mapa.getProjection().getVisibleRegion().latLngBounds;
        int enZona = 0;
        for (Marker marcador : marcadores.values()) {
            if (zona.contains(marcador.getPosition())) {
                enZona++;
            }
        }
        int plural = filtros.activos(this).isEmpty() ? R.plurals.map_count_zone : R.plurals.map_count_filtered;
        tvCount.setText(getResources().getQuantityString(plural, enZona, enZona));
    }

    private void seleccionar(String id) {
        Propiedad p = visibles.get(id);
        if (p == null) {
            return;
        }
        String anterior = seleccionadaId;
        seleccionadaId = id;
        if (anterior != null && !anterior.equals(id)) {
            marcarMarcador(anterior, false);
        }
        marcarMarcador(id, true);
        mostrarTarjeta(p);
        actualizarAbajo();
        Marker marcador = marcadores.get(id);
        if (marcador != null) {
            LatLng destino = marcador.getPosition();
            // Cuando la tarjeta ya midió su alto, el mapa reserva ese espacio y centra el pin en lo que queda visible.
            cardSelected.post(() -> {
                actualizarPaddingDelMapa();
                mapa.animateCamera(CameraUpdateFactory.newLatLng(destino));
            });
        }
    }

    private void deseleccionar() {
        String anterior = seleccionadaId;
        if (anterior == null) {
            return;
        }
        seleccionadaId = null;
        marcarMarcador(anterior, false);
        actualizarAbajo();
    }

    private void marcarMarcador(String id, boolean seleccionada) {
        Marker marcador = marcadores.get(id);
        Propiedad p = visibles.get(id);
        if (marcador == null || p == null) {
            return;
        }
        MarcadoresMapa.Pin pin = MarcadoresMapa.crear(this, p, seleccionada);
        marcador.setIcon(pin.icono);
        marcador.setAnchor(pin.anclaX, pin.anclaY);
        marcador.setZIndex(seleccionada ? 1f : 0f);
    }

    /** Mapa / Tarjeta seleccionada: foto, nombre, precio, zona aproximada y características. */
    private void mostrarTarjeta(Propiedad p) {
        Bitmap portada = FotosCodec.desdeBase64(p.portada);
        if (portada != null) {
            ivSelectedPhoto.setImageTintList(null);
            ivSelectedPhoto.setPadding(0, 0, 0, 0);
            ivSelectedPhoto.setScaleType(ImageView.ScaleType.CENTER_CROP);
            ivSelectedPhoto.setImageBitmap(portada);
        } else {
            int margen = dp(28);
            ivSelectedPhoto.setImageTintList(getColorStateList(R.color.brand_primary));
            ivSelectedPhoto.setPadding(margen, margen, margen, margen);
            ivSelectedPhoto.setScaleType(ImageView.ScaleType.FIT_CENTER);
            ivSelectedPhoto.setImageResource(R.drawable.ic_home_work);
        }
        tvSelectedName.setText(p.tituloPublico());
        tvSelectedPrice.setText(getString(R.string.map_price_month, Propiedad.formatearMiles(p.precio)));
        String zona = noVacio(p.barrio) ? p.barrio : p.ciudad;
        tvSelectedZone.setText(getString(R.string.map_zone_approx, zona == null ? "" : zona));
        tvSelectedZone.setVisibility(noVacio(zona) ? View.VISIBLE : View.GONE);

        List<String> datos = new ArrayList<>();
        if (p.habitaciones > 0) {
            datos.add(getString(R.string.filter_chip_bedrooms, String.valueOf(p.habitaciones)));
        }
        if (p.banos > 0) {
            datos.add(getResources().getQuantityString(R.plurals.stat_bathrooms, p.banos, p.banos));
        }
        String area = TarjetasPropiedad.area(this, p);
        if (area != null) {
            datos.add(area);
        }
        tvSelectedFeatures.setText(String.join(" · ", datos));
        tvSelectedFeatures.setVisibility(datos.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** El mapa deja libre el espacio que tapa la hoja o la tarjeta: ahí quedan el logo de Google y Mi ubicación. */
    private void actualizarPaddingDelMapa() {
        if (mapa == null) {
            return;
        }
        int inferior = 0;
        if (cardSelected.getVisibility() == View.VISIBLE) {
            inferior = cardSelected.getHeight();
        } else if (sheet.getVisibility() == View.VISIBLE) {
            inferior = sheet.getHeight();
        }
        if (inferior != paddingInferior) {
            paddingInferior = inferior;
            mapa.setPadding(0, 0, 0, inferior);
        }
    }

    // ---------- Ubicación y navegación ----------

    @SuppressWarnings("MissingPermission")
    private void centrarEnMiUbicacion() {
        if (mapa != null) {
            mapa.setMyLocationEnabled(true);
        }
        UbicacionActual.obtener(this, ubicacion -> {
            if (ubicacion == null || mapa == null) {
                Toast.makeText(this, R.string.location_unavailable, Toast.LENGTH_LONG).show();
                return;
            }
            mapa.animateCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(ubicacion.getLatitude(), ubicacion.getLongitude()), ZOOM_UBICACION));
        });
    }

    private void abrirDetalle(Propiedad p) {
        startActivity(new Intent(this, DetalleArriendoActivity.class)
                .putExtra(DetalleArriendoActivity.EXTRA_PROPIEDAD_ID, p.id));
    }

    private void ocultarTeclado() {
        InputMethodManager teclado = getSystemService(InputMethodManager.class);
        teclado.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
        etSearch.clearFocus();
    }

    private int dp(float valor) {
        return Math.round(valor * getResources().getDisplayMetrics().density);
    }

    private static boolean noVacio(String texto) {
        return texto != null && !texto.trim().isEmpty();
    }

    private static String texto(EditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }
}
