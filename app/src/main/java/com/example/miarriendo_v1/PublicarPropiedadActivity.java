package com.example.miarriendo_v1;

import android.Manifest;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.location.Address;
import android.location.Geocoder;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Figma: Propietario / Publicar — asistente de 5 pasos. */
public class PublicarPropiedadActivity extends AppCompatActivity {

    private static final int TOTAL_PASOS = 5;
    private static final int MAX_FOTOS = 10;
    private static final int FOTOS_POR_FILA = 3;
    private static final int[] DURACIONES_MESES = {3, 6, 12, 24};
    private static final int DURACION_POR_DEFECTO = 6;

    private ViewFlipper flipper;
    private MaterialButton btnNext;
    private int paso;

    // Paso 1
    private TextInputLayout tilTitle;
    private TextInputEditText etTitle, etDescription;
    private ChipGroup cgType;
    // Paso 2
    private TextInputLayout tilCity, tilAddress;
    private TextInputEditText etCity, etNeighborhood, etInternalName, etAddress;
    private Double lat, lng;
    // Paso 3
    private TextInputLayout tilBedrooms, tilBathrooms, tilArea, tilPrice;
    private TextInputEditText etBedrooms, etBathrooms, etArea, etPrice;
    private ChipGroup cgAmenities;
    // Paso 4
    private LinearLayout photoRows;
    private final ArrayList<Uri> fotos = new ArrayList<>();
    private final ExecutorService miniaturas = Executors.newSingleThreadExecutor();
    // Paso 5
    private MaterialAutoCompleteTextView etDuration, etAvailability;
    private TextInputEditText etDeposit, etClauses;
    private int duracionMeses = DURACION_POR_DEFECTO;
    private String disponibilidad = Propiedad.DISPONIBILIDAD_INMEDIATA;

    private final ActivityResultLauncher<PickVisualMediaRequest> elegirFotos =
            registerForActivityResult(new ActivityResultContracts.PickMultipleVisualMedia(MAX_FOTOS),
                    this::agregarFotos);

    private final ActivityResultLauncher<String[]> pedirUbicacion =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), permisos -> {
                if (Boolean.TRUE.equals(permisos.get(Manifest.permission.ACCESS_FINE_LOCATION))
                        || Boolean.TRUE.equals(permisos.get(Manifest.permission.ACCESS_COARSE_LOCATION))) {
                    usarMiUbicacion();
                } else {
                    Toast.makeText(this, R.string.location_permission_denied, Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Pantallas.edgeToEdge(this);
        setContentView(R.layout.activity_publicar_propiedad);
        Pantallas.aplicarInsets(findViewById(R.id.main));

        flipper = findViewById(R.id.flipper);
        btnNext = findViewById(R.id.btn_next);

        configurarPaso1();
        configurarPaso2();
        configurarPaso3();
        configurarPaso4();
        configurarPaso5();

        findViewById(R.id.btn_back).setOnClickListener(v -> volver());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                volver();
            }
        });
        btnNext.setOnClickListener(v -> siguiente());

        mostrarPaso(0);
    }

    // ---------- Navegación entre pasos ----------

    private void mostrarPaso(int nuevoPaso) {
        paso = nuevoPaso;
        flipper.setDisplayedChild(paso);
        btnNext.setText(paso == TOTAL_PASOS - 1 ? R.string.publish_submit : R.string.publish_continue);
    }

    private void volver() {
        if (paso > 0) {
            mostrarPaso(paso - 1);
        } else {
            finish();
        }
    }

    private void siguiente() {
        if (!validarPaso(paso)) {
            return;
        }
        if (paso < TOTAL_PASOS - 1) {
            mostrarPaso(paso + 1);
        } else {
            publicar();
        }
    }

    private boolean validarPaso(int p) {
        switch (p) {
            case 0:
                tilTitle.setError(null);
                if (TextUtils.isEmpty(texto(etTitle))) {
                    tilTitle.setError(getString(R.string.error_required));
                    return false;
                }
                if (cgType.getCheckedChipId() == View.NO_ID) {
                    Toast.makeText(this, R.string.error_select_type, Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            case 1:
                tilCity.setError(null);
                tilAddress.setError(null);
                if (TextUtils.isEmpty(texto(etCity))) {
                    tilCity.setError(getString(R.string.error_required));
                    return false;
                }
                if (TextUtils.isEmpty(texto(etAddress))) {
                    tilAddress.setError(getString(R.string.error_required));
                    return false;
                }
                return true;
            case 2:
                return requerirEntero(tilBedrooms, etBedrooms)
                        && requerirEntero(tilBathrooms, etBathrooms)
                        && validarArea()
                        && validarPrecio();
            default:
                return true;
        }
    }

    private boolean requerirEntero(TextInputLayout til, TextInputEditText et) {
        til.setError(null);
        if (TextUtils.isEmpty(texto(et))) {
            til.setError(getString(R.string.error_required));
            return false;
        }
        return true;
    }

    private boolean validarArea() {
        tilArea.setError(null);
        String area = texto(etArea).replace(',', '.');
        if (area.isEmpty()) {
            return true;
        }
        try {
            Double.parseDouble(area);
            return true;
        } catch (NumberFormatException e) {
            tilArea.setError(getString(R.string.error_number));
            return false;
        }
    }

    private boolean validarPrecio() {
        tilPrice.setError(null);
        if (MilesTextWatcher.valor(etPrice.getText()) <= 0) {
            tilPrice.setError(getString(R.string.error_required));
            return false;
        }
        return true;
    }

    // ---------- Paso 1: descripción ----------

    private void configurarPaso1() {
        tilTitle = findViewById(R.id.til_title);
        etTitle = findViewById(R.id.et_title);
        etDescription = findViewById(R.id.et_description);
        cgType = findViewById(R.id.cg_type);
    }

    // ---------- Paso 2: ubicación ----------

    private void configurarPaso2() {
        tilCity = findViewById(R.id.til_city);
        tilAddress = findViewById(R.id.til_address);
        etCity = findViewById(R.id.et_city);
        etNeighborhood = findViewById(R.id.et_neighborhood);
        etInternalName = findViewById(R.id.et_internal_name);
        etAddress = findViewById(R.id.et_address);

        tilAddress.setEndIconOnClickListener(v -> {
            boolean conPermiso = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
            if (conPermiso) {
                usarMiUbicacion();
            } else {
                pedirUbicacion.launch(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
            }
        });
    }

    /** Ícono my_location: toma la ubicación actual y rellena dirección, ciudad y barrio. */
    @SuppressWarnings("MissingPermission")
    private void usarMiUbicacion() {
        LocationManager lm = getSystemService(LocationManager.class);
        String proveedor = lm.isProviderEnabled(LocationManager.FUSED_PROVIDER) ? LocationManager.FUSED_PROVIDER
                : lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ? LocationManager.NETWORK_PROVIDER
                : LocationManager.GPS_PROVIDER;
        if (!lm.isProviderEnabled(proveedor)) {
            Toast.makeText(this, R.string.location_unavailable, Toast.LENGTH_LONG).show();
            return;
        }
        lm.getCurrentLocation(proveedor, null, ContextCompat.getMainExecutor(this), location -> {
            if (location == null) {
                Toast.makeText(this, R.string.location_unavailable, Toast.LENGTH_LONG).show();
                return;
            }
            lat = location.getLatitude();
            lng = location.getLongitude();
            new Geocoder(this, new Locale("es", "CO")).getFromLocation(lat, lng, 1, new Geocoder.GeocodeListener() {
                @Override
                public void onGeocode(List<Address> direcciones) {
                    if (!direcciones.isEmpty()) {
                        runOnUiThread(() -> rellenarDireccion(direcciones.get(0)));
                    }
                }

                @Override
                public void onError(String mensaje) {
                    runOnUiThread(() -> Toast.makeText(PublicarPropiedadActivity.this,
                            R.string.location_unavailable, Toast.LENGTH_LONG).show());
                }
            });
        });
    }

    private void rellenarDireccion(Address a) {
        String calle = a.getThoroughfare() != null
                ? a.getThoroughfare() + (a.getSubThoroughfare() != null ? " # " + a.getSubThoroughfare() : "")
                : a.getAddressLine(0);
        if (calle != null) {
            etAddress.setText(calle);
        }
        if (TextUtils.isEmpty(texto(etCity)) && a.getLocality() != null) {
            etCity.setText(a.getAdminArea() != null ? a.getLocality() + ", " + a.getAdminArea() : a.getLocality());
        }
        if (TextUtils.isEmpty(texto(etNeighborhood)) && a.getSubLocality() != null) {
            etNeighborhood.setText(a.getSubLocality());
        }
    }

    // ---------- Paso 3: detalles y precio ----------

    private void configurarPaso3() {
        tilBedrooms = findViewById(R.id.til_bedrooms);
        tilBathrooms = findViewById(R.id.til_bathrooms);
        tilArea = findViewById(R.id.til_area);
        tilPrice = findViewById(R.id.til_price);
        etBedrooms = findViewById(R.id.et_bedrooms);
        etBathrooms = findViewById(R.id.et_bathrooms);
        etArea = findViewById(R.id.et_area);
        etPrice = findViewById(R.id.et_price);
        cgAmenities = findViewById(R.id.cg_amenities);
        etPrice.addTextChangedListener(new MilesTextWatcher(etPrice));
    }

    // ---------- Paso 4: fotos ----------

    private void configurarPaso4() {
        photoRows = findViewById(R.id.photo_rows);
        findViewById(R.id.btn_add_photos).setOnClickListener(v -> abrirSelectorFotos());
        configurarConsejo(findViewById(R.id.tip_photos), R.string.tip_photos);
        dibujarCasillasFotos();
    }

    private void abrirSelectorFotos() {
        if (fotos.size() >= MAX_FOTOS) {
            Toast.makeText(this, getString(R.string.photos_limit, MAX_FOTOS), Toast.LENGTH_SHORT).show();
            return;
        }
        elegirFotos.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    private void agregarFotos(List<Uri> nuevas) {
        for (Uri uri : nuevas) {
            if (fotos.size() >= MAX_FOTOS) {
                Toast.makeText(this, getString(R.string.photos_limit, MAX_FOTOS), Toast.LENGTH_SHORT).show();
                break;
            }
            if (!fotos.contains(uri)) {
                fotos.add(uri);
            }
        }
        dibujarCasillasFotos();
    }

    /**
     * Filas de 3 casillas con las fotos elegidas. Las casillas "+" solo completan las 3 primeras
     * posiciones mientras haya menos de 3 fotos; después solo se ven las fotos (se agregan más con
     * el botón "Agregar fotos"). La última fila se completa con espacios invisibles para que todas
     * las casillas tengan el mismo ancho.
     */
    private void dibujarCasillasFotos() {
        photoRows.removeAllViews();
        int ocupadas = Math.max(FOTOS_POR_FILA, fotos.size());
        int total = (int) Math.ceil(ocupadas / (double) FOTOS_POR_FILA) * FOTOS_POR_FILA;
        int alto = getResources().getDimensionPixelSize(R.dimen.photo_slot_height);
        int gap = getResources().getDimensionPixelSize(R.dimen.spacing_xs);

        LinearLayout fila = null;
        for (int i = 0; i < total; i++) {
            if (i % FOTOS_POR_FILA == 0) {
                fila = new LinearLayout(this);
                fila.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams lpFila = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, alto);
                lpFila.topMargin = i == 0 ? 0 : gap;
                photoRows.addView(fila, lpFila);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, alto, 1f);
            lp.setMarginStart(i % FOTOS_POR_FILA == 0 ? 0 : gap);
            fila.addView(crearCasilla(i), lp);
        }
    }

    private View crearCasilla(int indice) {
        if (indice < fotos.size()) {
            ShapeableImageView foto = (ShapeableImageView) LayoutInflater.from(this)
                    .inflate(R.layout.item_photo_slot_filled, photoRows, false);
            cargarMiniatura(foto, fotos.get(indice));
            foto.setContentDescription(getString(R.string.photo_slot_description, indice + 1));
            foto.setOnClickListener(v -> Dialogo.nuevo(this)
                    .titulo(R.string.photos_remove_title)
                    .mensaje(R.string.photos_remove_message)
                    .cancelar(R.string.cancel)
                    .confirmar(R.string.photos_remove, Dialogo.Tono.DESTRUCTIVO, () -> {
                        fotos.remove(indice);
                        dibujarCasillasFotos();
                    })
                    .mostrar());
            return foto;
        }
        FrameLayout vacia = (FrameLayout) LayoutInflater.from(this)
                .inflate(R.layout.item_photo_slot_empty, photoRows, false);
        if (indice < FOTOS_POR_FILA) {
            vacia.setOnClickListener(v -> abrirSelectorFotos());
        } else {
            // Relleno de la última fila: ocupa el espacio pero no se ve.
            vacia.setVisibility(View.INVISIBLE);
        }
        return vacia;
    }

    /** Carga una miniatura (no la foto completa) en segundo plano para no gastar memoria. */
    private void cargarMiniatura(ImageView destino, Uri uri) {
        int lado = getResources().getDimensionPixelSize(R.dimen.photo_slot_height) * 2;
        miniaturas.execute(() -> {
            Bitmap bitmap;
            try {
                bitmap = getContentResolver().loadThumbnail(uri, new Size(lado, lado), null);
            } catch (IOException e) {
                bitmap = null;
            }
            Bitmap resultado = bitmap;
            runOnUiThread(() -> {
                if (resultado != null && !isDestroyed()) {
                    destino.setImageBitmap(resultado);
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        miniaturas.shutdownNow();
    }

    // ---------- Paso 5: contrato ----------

    private void configurarPaso5() {
        etDuration = findViewById(R.id.et_duration);
        etAvailability = findViewById(R.id.et_availability);
        etDeposit = findViewById(R.id.et_deposit);
        etClauses = findViewById(R.id.et_clauses);
        etDeposit.addTextChangedListener(new MilesTextWatcher(etDeposit));
        configurarConsejo(findViewById(R.id.tip_contract), R.string.tip_contract);

        String[] duraciones = new String[DURACIONES_MESES.length];
        for (int i = 0; i < DURACIONES_MESES.length; i++) {
            duraciones[i] = getString(R.string.duration_months, DURACIONES_MESES[i]);
        }
        etDuration.setAdapter(new ArrayAdapter<>(this, R.layout.item_dropdown, duraciones));
        etDuration.setText(getString(R.string.duration_months, DURACION_POR_DEFECTO), false);
        etDuration.setOnItemClickListener((parent, view, pos, id) -> duracionMeses = DURACIONES_MESES[pos]);

        String[] opciones = {getString(R.string.availability_now), getString(R.string.availability_pick)};
        etAvailability.setAdapter(new ArrayAdapter<>(this, R.layout.item_dropdown, opciones));
        etAvailability.setText(opciones[0], false);
        etAvailability.setOnItemClickListener((parent, view, pos, id) -> {
            if (pos == 0) {
                disponibilidad = Propiedad.DISPONIBILIDAD_INMEDIATA;
            } else {
                elegirFechaDisponibilidad();
            }
        });
    }

    private void elegirFechaDisponibilidad() {
        String textoAnterior = disponibilidad.equals(Propiedad.DISPONIBILIDAD_INMEDIATA)
                ? getString(R.string.availability_now) : formatearFecha(disponibilidad);

        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.field_availability)
                .setCalendarConstraints(new CalendarConstraints.Builder()
                        .setValidator(DateValidatorPointForward.now()).build())
                .build();
        picker.addOnPositiveButtonClickListener(millis -> {
            SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            iso.setTimeZone(TimeZone.getTimeZone("UTC"));
            disponibilidad = iso.format(new Date(millis));
            etAvailability.setText(formatearFecha(disponibilidad), false);
        });
        picker.addOnNegativeButtonClickListener(v -> etAvailability.setText(textoAnterior, false));
        picker.addOnCancelListener(d -> etAvailability.setText(textoAnterior, false));
        picker.show(getSupportFragmentManager(), "disponibilidad");
    }

    /** "2026-10-15" → "15/10/2026" */
    private static String formatearFecha(String iso) {
        String[] p = iso.split("-");
        return p.length == 3 ? p[2] + "/" + p[1] + "/" + p[0] : iso;
    }

    /** TipCard sin botón de cerrar, como en Ingreso con huella. */
    private void configurarConsejo(View tarjeta, int texto) {
        ((TextView) tarjeta.findViewById(R.id.tv_tip_text)).setText(texto);
        tarjeta.findViewById(R.id.btn_tip_close).setVisibility(View.GONE);
    }

    // ---------- Publicar ----------

    private void publicar() {
        Propiedad p = new Propiedad();
        p.titulo = texto(etTitle);
        p.descripcion = texto(etDescription);
        Chip tipo = findViewById(cgType.getCheckedChipId());
        p.tipo = tipo.getText().toString();

        p.ciudad = texto(etCity);
        p.barrio = texto(etNeighborhood);
        p.nombreInterno = texto(etInternalName);
        p.direccion = texto(etAddress);
        p.lat = lat;
        p.lng = lng;

        p.habitaciones = Integer.parseInt(texto(etBedrooms));
        p.banos = Integer.parseInt(texto(etBathrooms));
        String area = texto(etArea).replace(',', '.');
        p.area = area.isEmpty() ? null : Double.parseDouble(area);
        p.precio = MilesTextWatcher.valor(etPrice.getText());
        for (int id : cgAmenities.getCheckedChipIds()) {
            p.comodidades.add(((Chip) findViewById(id)).getText().toString());
        }

        p.duracionMeses = duracionMeses;
        p.deposito = MilesTextWatcher.valor(etDeposit.getText());
        p.disponibilidad = disponibilidad;
        p.clausulas = texto(etClauses);

        Intent intent = new Intent(this, PublicandoActivity.class);
        intent.putExtra(PublicandoActivity.EXTRA_PROPIEDAD, p);
        intent.putParcelableArrayListExtra(PublicandoActivity.EXTRA_FOTOS, fotos);
        if (!fotos.isEmpty()) {
            // Pasa el permiso de lectura de las fotos elegidas a la pantalla que las sube.
            ClipData clip = ClipData.newRawUri(null, fotos.get(0));
            for (int i = 1; i < fotos.size(); i++) {
                clip.addItem(new ClipData.Item(fotos.get(i)));
            }
            intent.setClipData(clip);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        startActivity(intent);
    }

    private static String texto(TextView campo) {
        return campo.getText() == null ? "" : campo.getText().toString().trim();
    }
}
