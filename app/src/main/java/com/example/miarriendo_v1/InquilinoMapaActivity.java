package com.example.miarriendo_v1;

import android.Manifest;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
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
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Figma: Inquilino / Mapa · Explorar arriendos (342:2892) y Casa Los Mayales seleccionada (342:3011).
 * Mapa de Google Maps con un pin de precio por cada propiedad disponible; al tocar uno aparece su tarjeta
 * con el botón "Ir a propiedad". Usa los mismos filtros de búsqueda que Inicio y Resultados.
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

    private TextInputEditText etSearch;
    private TextView tvAvailable, tvHelpTitle, tvHelpText;
    private TextView tvFeatureBedrooms, tvFeatureBathrooms, tvFeatureArea;
    private View loading, mapArea, mapContainer, cardHelp, cardSelected, resumen;
    private View btnLocate, btnLocateInline, featureArea, spaceArea;

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
                    mostrarPropiedades(true);
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

        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        EncabezadoInquilino.configurar(this, usuario);
        getOnBackPressedDispatcher().addCallback(this, cerrarTarjeta);

        etSearch = findViewById(R.id.et_search);
        tvAvailable = findViewById(R.id.tv_available);
        tvHelpTitle = findViewById(R.id.tv_help_title);
        tvHelpText = findViewById(R.id.tv_help_text);
        tvFeatureBedrooms = findViewById(R.id.tv_feature_bedrooms);
        tvFeatureBathrooms = findViewById(R.id.tv_feature_bathrooms);
        tvFeatureArea = findViewById(R.id.tv_feature_area);
        loading = findViewById(R.id.loading);
        mapArea = findViewById(R.id.map_area);
        mapContainer = findViewById(R.id.map);
        cardHelp = findViewById(R.id.card_help);
        cardSelected = findViewById(R.id.card_selected);
        resumen = findViewById(R.id.resumen);
        btnLocate = findViewById(R.id.btn_locate);
        btnLocateInline = findViewById(R.id.btn_locate_inline);
        featureArea = findViewById(R.id.feature_area);
        spaceArea = findViewById(R.id.space_area);

        FiltrosBusqueda recibidos = getIntent().getSerializableExtra(FiltrosActivity.EXTRA_FILTROS, FiltrosBusqueda.class);
        if (recibidos != null) {
            filtros = recibidos;
        }
        etSearch.setText(filtros.ciudad);

        etSearch.setOnEditorActionListener((v, accion, evento) -> {
            if (accion != EditorInfo.IME_ACTION_SEARCH) {
                return false;
            }
            filtros.ciudad = texto(etSearch);
            ocultarTeclado();
            mostrarPropiedades(true);
            return true;
        });
        findViewById(R.id.btn_filters).setOnClickListener(v -> {
            filtros.ciudad = texto(etSearch);
            elegirFiltros.launch(new Intent(this, FiltrosActivity.class).putExtra(FiltrosActivity.EXTRA_FILTROS, filtros));
        });
        View.OnClickListener centrar = v -> {
            if (UbicacionActual.tienePermiso(this)) {
                centrarEnMiUbicacion();
            } else {
                pedirUbicacion.launch(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
            }
        };
        btnLocate.setOnClickListener(centrar);
        btnLocateInline.setOnClickListener(centrar);
        findViewById(R.id.btn_close_card).setOnClickListener(v -> deseleccionar());
        View.OnClickListener irAPropiedad = v -> {
            Propiedad p = visibles.get(seleccionadaId);
            if (p != null) {
                abrirDetalle(p);
            }
        };
        findViewById(R.id.btn_go).setOnClickListener(irAPropiedad);
        resumen.setOnClickListener(irAPropiedad);
        // El logo de Google queda siempre por encima de las tarjetas que flotan sobre el mapa.
        View.OnLayoutChangeListener alCambiarTarjeta = (v, l, t, r, b, ol, ot, or, ob) -> actualizarPaddingDelMapa();
        cardHelp.addOnLayoutChangeListener(alCambiarTarjeta);
        cardSelected.addOnLayoutChangeListener(alCambiarTarjeta);

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
        findViewById(R.id.tv_map_caption).setVisibility(View.GONE);
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

    /** Aplica los filtros, actualiza el contador y deja un pin por cada propiedad que cumple. */
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
        tvAvailable.setText(getResources().getQuantityString(R.plurals.tenant_available, lista.size(), lista.size()));

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
        actualizarAyuda();

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
        // El encuadre se calcula sobre la parte que no tapa la tarjeta de ayuda o la de la propiedad.
        int altoVisible = Math.max(alto / 2, alto - Math.max(0, paddingInferior));
        int margen = Math.min(dp(72), Math.min(ancho, altoVisible) / 4);
        mapa.animateCamera(CameraUpdateFactory.newLatLngBounds(limites.build(), ancho, altoVisible, margen));
    }

    // ---------- Selección ----------

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
        actualizarAyuda();
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
        actualizarAyuda();
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

    /** Tarjeta de propiedad seleccionada: Resumen de propiedad (342:3132) y Características (342:3146). */
    private void mostrarTarjeta(Propiedad p) {
        TarjetasPropiedad.compacta(this, resumen, p);
        tvFeatureBedrooms.setText(getResources().getQuantityString(R.plurals.stat_bedrooms_long, p.habitaciones, p.habitaciones));
        tvFeatureBathrooms.setText(getResources().getQuantityString(R.plurals.stat_bathrooms, p.banos, p.banos));
        String area = TarjetasPropiedad.area(this, p);
        int visibilidadArea = area == null ? View.GONE : View.VISIBLE;
        featureArea.setVisibility(visibilidadArea);
        spaceArea.setVisibility(visibilidadArea);
        tvFeatureArea.setText(area);
    }

    /**
     * Con una propiedad seleccionada se ve su tarjeta y "Centrar mapa" pasa junto al buscador; sin selección
     * "Centrar mapa" flota sobre el mapa y, si hay precios que tocar, se ve la ayuda de exploración (342:2892).
     * Sin propiedades no hay tarjeta: "0 disponibles" de la barra ya lo dice.
     */
    private void actualizarAyuda() {
        boolean haySeleccion = seleccionadaId != null;
        boolean vacio = visibles.isEmpty();
        cardSelected.setVisibility(haySeleccion ? View.VISIBLE : View.GONE);
        btnLocateInline.setVisibility(haySeleccion ? View.VISIBLE : View.GONE);
        cardHelp.setVisibility(haySeleccion || vacio ? View.GONE : View.VISIBLE);
        btnLocate.setVisibility(haySeleccion ? View.GONE : View.VISIBLE);
        if (!haySeleccion && !vacio) {
            tvHelpTitle.setText(R.string.map_help_title);
            tvHelpText.setText(R.string.map_help_text);
        }
        cerrarTarjeta.setEnabled(haySeleccion);
    }

    /** El mapa deja libre el espacio que tapa la tarjeta (o la ayuda): ahí quedan el logo de Google y el centrado. */
    private void actualizarPaddingDelMapa() {
        if (mapa == null) {
            return;
        }
        int inferior = 0;
        if (cardSelected.getVisibility() == View.VISIBLE) {
            inferior = cardSelected.getHeight();
        } else if (cardHelp.getVisibility() == View.VISIBLE) {
            inferior = Math.max(0, mapArea.getHeight() - cardHelp.getTop());
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

    private static String texto(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }
}
