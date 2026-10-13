package com.example.miarriendo_v1;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

/** Figma: Propietario / Inicio — Sin propiedades (144:1080) y Con propiedades (216:740). */
public class PropietarioInicioActivity extends AppCompatActivity {

    private View loading, emptyState, contentState;
    private TextView tvGreeting, tvGreetingEmpty, tvAvatar;
    private LinearLayout listProperties;

    private Query consultaPropiedades;
    private ValueEventListener escuchaPropiedades;
    private Query consultaSolicitudes;
    private ValueEventListener escuchaSolicitudes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Pantallas.edgeToEdge(this);
        setContentView(R.layout.activity_propietario_inicio);
        Pantallas.aplicarInsets(findViewById(R.id.main));

        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        loading = findViewById(R.id.loading);
        emptyState = findViewById(R.id.empty_state);
        contentState = findViewById(R.id.content_state);
        tvGreeting = findViewById(R.id.tv_greeting);
        tvGreetingEmpty = findViewById(R.id.tv_greeting_empty);
        tvAvatar = findViewById(R.id.tv_avatar);
        listProperties = findViewById(R.id.list_properties);

        tvGreeting.setText(R.string.owner_greeting_generic);
        tvGreetingEmpty.setText(R.string.owner_greeting_generic);
        tvAvatar.setText(Formato.iniciales(usuario.getEmail()));

        View.OnClickListener publicar = v -> startActivity(new Intent(this, PublicarPropiedadActivity.class));
        findViewById(R.id.btn_add_property).setOnClickListener(publicar);
        findViewById(R.id.btn_add_property_list).setOnClickListener(publicar);
        tvAvatar.setOnClickListener(v -> startActivity(new Intent(this, PerfilActivity.class)));

        cargarNombre(usuario.getUid());
        escucharPropiedades(usuario.getUid());
        escucharSolicitudes(usuario.getUid());
    }

    /** Avisa cuántas solicitudes de arriendo están por responder y lleva a la bandeja. */
    private void escucharSolicitudes(String uid) {
        View aviso = findViewById(R.id.banner_requests);
        aviso.setOnClickListener(v -> startActivity(new Intent(this, SolicitudesRecibidasActivity.class)));
        consultaSolicitudes = FirebaseDatabase.getInstance().getReference("solicitudes_arriendo")
                .orderByChild("propietarioId").equalTo(uid);
        escuchaSolicitudes = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int pendientes = 0;
                for (DataSnapshot hijo : snapshot.getChildren()) {
                    SolicitudArriendo s = hijo.getValue(SolicitudArriendo.class);
                    if (s != null && s.pendiente()) {
                        pendientes++;
                    }
                }
                if (pendientes == 0) {
                    AlertView.ocultar(aviso);
                } else {
                    AlertView.mostrar(aviso, AlertView.Tipo.INFO,
                            getResources().getQuantityString(R.plurals.received_banner_count, pendientes, pendientes));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                AlertView.ocultar(aviso);
            }
        };
        consultaSolicitudes.addValueEventListener(escuchaSolicitudes);
    }

    /** Saludo con el primer nombre y las iniciales del Avatar. */
    private void cargarNombre(String uid) {
        FirebaseDatabase.getInstance().getReference("usuarios").child(uid).get()
                .addOnSuccessListener(snapshot -> {
                    Usuario u = snapshot.getValue(Usuario.class);
                    String nombre = u == null || u.getNombre() == null ? "" : u.getNombre().trim();
                    if (nombre.isEmpty()) {
                        return;
                    }
                    String saludo = getString(R.string.owner_greeting, nombre.split("\\s+")[0]);
                    tvGreeting.setText(saludo);
                    tvGreetingEmpty.setText(saludo);
                    String apellido = u.getApellido() == null ? "" : u.getApellido();
                    tvAvatar.setText(Formato.iniciales(nombre.split("\\s+")[0] + " " + apellido));
                });
    }

    private void escucharPropiedades(String uid) {
        consultaPropiedades = FirebaseDatabase.getInstance().getReference("propiedades")
                .orderByChild("propietarioId").equalTo(uid);
        escuchaPropiedades = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Propiedad> propiedades = new ArrayList<>();
                for (DataSnapshot hijo : snapshot.getChildren()) {
                    Propiedad p = hijo.getValue(Propiedad.class);
                    if (p != null) {
                        if (p.id == null) {
                            p.id = hijo.getKey();
                        }
                        propiedades.add(p);
                    }
                }
                // Las más recientes primero.
                propiedades.sort((a, b) -> Long.compare(b.creadoEn, a.creadoEn));
                mostrar(propiedades);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // No dejar la pantalla en blanco: se muestra el estado vacío para poder seguir usando la app.
                mostrar(new ArrayList<>());
                Toast.makeText(PropietarioInicioActivity.this,
                        getString(R.string.owner_load_error, error.getMessage()), Toast.LENGTH_LONG).show();
            }
        };
        consultaPropiedades.addValueEventListener(escuchaPropiedades);
    }

    private void mostrar(List<Propiedad> propiedades) {
        loading.setVisibility(View.GONE);
        emptyState.setVisibility(propiedades.isEmpty() ? View.VISIBLE : View.GONE);
        contentState.setVisibility(propiedades.isEmpty() ? View.GONE : View.VISIBLE);

        listProperties.removeAllViews();
        int separacion = getResources().getDimensionPixelSize(R.dimen.spacing_sm);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < propiedades.size(); i++) {
            View tarjeta = inflater.inflate(R.layout.item_propiedad, listProperties, false);
            llenarTarjeta(tarjeta, propiedades.get(i));
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) tarjeta.getLayoutParams();
            lp.topMargin = i == 0 ? 0 : separacion;
            listProperties.addView(tarjeta, lp);
        }
    }

    /** OwnerPropertyCard: foto o ícono, nombre, dirección, precio y estado. */
    private void llenarTarjeta(View tarjeta, Propiedad p) {
        ImageView thumbnail = tarjeta.findViewById(R.id.iv_thumbnail);
        Bitmap portada = FotosCodec.desdeBase64(p.portada);
        if (portada != null) {
            thumbnail.setImageBitmap(portada);
        } else {
            // Sin foto: ícono home_work de 28 centrado sobre brand/container.
            int margen = Math.round(18 * getResources().getDisplayMetrics().density);
            thumbnail.setPadding(margen, margen, margen, margen);
            thumbnail.setScaleType(ImageView.ScaleType.FIT_CENTER);
            thumbnail.setImageResource(R.drawable.ic_home_work);
        }

        ((TextView) tarjeta.findViewById(R.id.tv_title)).setText(p.nombreParaTarjeta());
        ((TextView) tarjeta.findViewById(R.id.tv_address)).setText(p.direccionCompleta());
        ((TextView) tarjeta.findViewById(R.id.tv_price))
                .setText(getString(R.string.owner_price_month, Propiedad.formatearMiles(p.precio)));
        mostrarEstado(tarjeta.findViewById(R.id.tv_status), p);

        tarjeta.setOnClickListener(v -> startActivity(new Intent(this, PropiedadDetalleActivity.class)
                .putExtra(PropiedadDetalleActivity.EXTRA_PROPIEDAD_ID, p.id)));
    }

    /** Disponible (sin inquilino) o el estado del pago del arriendo. */
    private void mostrarEstado(TextView chip, Propiedad p) {
        if (!p.tieneInquilino()) {
            StatusChipView.aplicar(chip, StatusChipView.Tipo.NEUTRAL, getString(R.string.owner_status_available));
        } else if (Propiedad.PAGO_ATRASADO.equals(p.estadoPago)) {
            StatusChipView.aplicar(chip, StatusChipView.Tipo.ERROR, getString(R.string.owner_status_late));
        } else if (Propiedad.PAGO_PENDIENTE.equals(p.estadoPago)) {
            StatusChipView.aplicar(chip, StatusChipView.Tipo.ADVERTENCIA, getString(R.string.owner_status_pending));
        } else {
            StatusChipView.aplicar(chip, StatusChipView.Tipo.PRIMARIO, getString(R.string.owner_status_up_to_date));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (consultaPropiedades != null && escuchaPropiedades != null) {
            consultaPropiedades.removeEventListener(escuchaPropiedades);
        }
        if (consultaSolicitudes != null && escuchaSolicitudes != null) {
            consultaSolicitudes.removeEventListener(escuchaSolicitudes);
        }
    }
}
