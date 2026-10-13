package com.example.miarriendo_v1;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.List;

/** Figma: Inquilino / Casa Los Mayales · Detalle (315:6840). */
public class DetalleArriendoActivity extends AppCompatActivity {

    static final String EXTRA_PROPIEDAD_ID = "propiedad_id";

    private String propiedadId;
    private MaterialButton btnRequest;
    private TextView tvNote, tvPhotoCounter;
    private boolean yaSolicitada;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Pantallas.edgeToEdge(this);
        setContentView(R.layout.activity_detalle_arriendo);
        Pantallas.aplicarInsets(findViewById(R.id.main));

        ((TextView) findViewById(R.id.tv_app_bar_title)).setText(R.string.tenant_detail_title);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        btnRequest = findViewById(R.id.btn_request);
        tvNote = findViewById(R.id.tv_note);
        tvPhotoCounter = findViewById(R.id.tv_photo_counter);

        propiedadId = getIntent().getStringExtra(EXTRA_PROPIEDAD_ID);
        if (propiedadId == null) {
            noEncontrada();
            return;
        }
        btnRequest.setOnClickListener(v -> {
            if (yaSolicitada) {
                startActivity(new Intent(this, MisSolicitudesActivity.class));
            } else {
                startActivity(new Intent(this, SolicitarArriendoActivity.class).putExtra(EXTRA_PROPIEDAD_ID, propiedadId));
            }
        });

        FirebaseDatabase.getInstance().getReference("propiedades").child(propiedadId).get()
                .addOnSuccessListener(snapshot -> {
                    Propiedad p = snapshot.getValue(Propiedad.class);
                    if (p == null) {
                        noEncontrada();
                    } else {
                        p.id = propiedadId;
                        mostrarPropiedad(p);
                    }
                })
                .addOnFailureListener(e -> noEncontrada());
        FirebaseDatabase.getInstance().getReference("fotos_propiedades").child(propiedadId).get()
                .addOnSuccessListener(this::mostrarFotos);
    }

    @Override
    protected void onResume() {
        super.onResume();
        revisarSolicitudExistente();
    }

    private void noEncontrada() {
        Toast.makeText(this, R.string.detail_not_found, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void mostrarPropiedad(Propiedad p) {
        ((TextView) findViewById(R.id.tv_name)).setText(p.tituloPublico());
        ((TextView) findViewById(R.id.tv_address)).setText(p.direccionCompleta());
        ((TextView) findViewById(R.id.tv_price)).setText(TarjetasPropiedad.precio(this, p));
        ((TextView) findViewById(R.id.tv_feature_bedrooms)).setText(
                getResources().getQuantityString(R.plurals.stat_bedrooms_long, p.habitaciones, p.habitaciones));
        ((TextView) findViewById(R.id.tv_feature_bathrooms)).setText(
                getResources().getQuantityString(R.plurals.stat_bathrooms, p.banos, p.banos));
        String area = TarjetasPropiedad.area(this, p);
        findViewById(R.id.feature_area).setVisibility(area == null ? View.GONE : View.VISIBLE);
        ((TextView) findViewById(R.id.tv_feature_area)).setText(area);

        TextView descripcion = findViewById(R.id.tv_description);
        boolean hayDescripcion = p.descripcion != null && !p.descripcion.trim().isEmpty();
        descripcion.setVisibility(hayDescripcion ? View.VISIBLE : View.GONE);
        descripcion.setText(hayDescripcion ? p.descripcion.trim() : null);
        TextView comodidades = findViewById(R.id.tv_amenities);
        boolean hayComodidades = p.comodidades != null && !p.comodidades.isEmpty();
        comodidades.setVisibility(hayComodidades ? View.VISIBLE : View.GONE);
        if (hayComodidades) {
            comodidades.setText(String.join(" · ", p.comodidades));
        }

        // Si la propiedad no tiene fotos completas, al menos se ve la portada.
        if (((RecyclerView) findViewById(R.id.rv_photos)).getAdapter() == null) {
            ImageView placeholder = findViewById(R.id.iv_placeholder);
            android.graphics.Bitmap portada = FotosCodec.desdeBase64(p.portada);
            if (portada != null) {
                placeholder.setScaleType(ImageView.ScaleType.CENTER_CROP);
                placeholder.setImageBitmap(portada);
            }
        }

        btnRequest.setEnabled(p.disponible());
        cargarPropietario(p.propietarioId);
    }

    /** "Edward · Propietario" con su primer nombre (regla: usuarios/{uid}/nombre es legible). */
    private void cargarPropietario(String uid) {
        TextView nombre = findViewById(R.id.tv_owner_name);
        TextView iniciales = findViewById(R.id.tv_owner_initials);
        nombre.setText(R.string.tenant_detail_owner_generic);
        iniciales.setText(Formato.iniciales(getString(R.string.tenant_detail_owner_generic)));
        if (uid == null) {
            return;
        }
        FirebaseDatabase.getInstance().getReference("usuarios").child(uid).child("nombre").get()
                .addOnSuccessListener(s -> {
                    String n = s.getValue(String.class);
                    if (n != null && !n.trim().isEmpty()) {
                        nombre.setText(getString(R.string.tenant_detail_owner, n.trim().split("\\s+")[0]));
                        iniciales.setText(Formato.iniciales(n));
                    }
                });
    }

    /** Galería deslizable con "1 de 4". */
    private void mostrarFotos(DataSnapshot snapshot) {
        List<String> fotos = new ArrayList<>();
        for (DataSnapshot hijo : snapshot.getChildren()) {
            String base64 = hijo.getValue(String.class);
            if (base64 != null) {
                fotos.add(base64);
            }
        }
        if (fotos.isEmpty()) {
            return;
        }
        RecyclerView rv = findViewById(R.id.rv_photos);
        LinearLayoutManager lm = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        rv.setLayoutManager(lm);
        rv.setAdapter(new FotosAdapter(fotos));
        new PagerSnapHelper().attachToRecyclerView(rv);
        tvPhotoCounter.setVisibility(View.VISIBLE);
        tvPhotoCounter.setText(getString(R.string.tenant_detail_photo_counter, 1, fotos.size()));
        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                int pos = lm.findFirstCompletelyVisibleItemPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    tvPhotoCounter.setText(getString(R.string.tenant_detail_photo_counter, pos + 1, fotos.size()));
                }
            }
        });
    }

    /** Si ya hay una solicitud pendiente para esta propiedad, el botón lleva a Mis solicitudes. */
    private void revisarSolicitudExistente() {
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null || propiedadId == null) {
            return;
        }
        FirebaseDatabase.getInstance().getReference("solicitudes_arriendo")
                .orderByChild("inquilinoId").equalTo(usuario.getUid()).get()
                .addOnSuccessListener(snapshot -> {
                    yaSolicitada = false;
                    for (DataSnapshot hijo : snapshot.getChildren()) {
                        SolicitudArriendo s = hijo.getValue(SolicitudArriendo.class);
                        if (s != null && propiedadId.equals(s.propiedadId) && s.pendiente()) {
                            yaSolicitada = true;
                        }
                    }
                    btnRequest.setText(yaSolicitada ? R.string.tenant_detail_see_request : R.string.tenant_detail_request);
                    tvNote.setText(yaSolicitada ? R.string.tenant_detail_note_sent : R.string.tenant_detail_note);
                });
    }

    private final class FotosAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private final List<String> fotos;

        FotosAdapter(List<String> fotos) {
            this.fotos = fotos;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_foto_galeria, parent, false);
            return new RecyclerView.ViewHolder(v) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ImageView foto = (ImageView) holder.itemView;
            foto.setImageBitmap(FotosCodec.desdeBase64(fotos.get(position)));
            foto.setContentDescription(getString(R.string.tenant_detail_photo, position + 1));
        }

        @Override
        public int getItemCount() {
            return fotos.size();
        }
    }
}
