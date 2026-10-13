package com.example.miarriendo_v1;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Figma: Pagos / Historial del inmueble. Los meses ya pagados, del más reciente al más antiguo. El
 * propietario ve todos los del inmueble; el inquilino, los suyos aunque el arriendo ya haya terminado.
 */
public class HistorialInmuebleActivity extends AppCompatActivity {

    private String propiedadId;
    private Query consulta;
    private ValueEventListener escucha;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Pantallas.edgeToEdge(this);
        setContentView(R.layout.activity_historial_inmueble);
        Pantallas.aplicarInsets(findViewById(R.id.main));

        ((TextView) findViewById(R.id.tv_app_bar_title)).setText(R.string.history_title);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        propiedadId = getIntent().getStringExtra(VistasPago.EXTRA_PROPIEDAD_ID);
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (propiedadId == null || usuario == null) {
            finish();
            return;
        }

        FirebaseDatabase.getInstance().getReference("propiedades").child(propiedadId).get()
                .addOnSuccessListener(snapshot -> {
                    Propiedad p = snapshot.getValue(Propiedad.class);
                    if (p != null) {
                        ((TextView) findViewById(R.id.tv_property)).setText(p.tituloPublico());
                    }
                });

        boolean propietario = getIntent().getBooleanExtra(VistasPago.EXTRA_PROPIETARIO, false);
        consulta = propietario ? RepositorioPagos.cuotas(propiedadId)
                : RepositorioPagos.cuotasDelInquilino(propiedadId, usuario.getUid());
        escucha = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                mostrar(RepositorioPagos.leerCuotas(snapshot));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(HistorialInmuebleActivity.this,
                        getString(R.string.pay_load_error, error.getMessage()), Toast.LENGTH_LONG).show();
                mostrar(new ArrayList<>());
            }
        };
        consulta.addValueEventListener(escucha);
    }

    private void mostrar(List<Cuota> cuotas) {
        findViewById(R.id.loading).setVisibility(View.GONE);
        List<Cuota> pagadas = new ArrayList<>();
        for (Cuota cuota : cuotas) {
            if (cuota.pagada()) {
                pagadas.add(cuota);
            }
        }
        pagadas.sort((a, b) -> Long.compare(ReglasPago.fechaDePago(b), ReglasPago.fechaDePago(a)));

        LinearLayout lista = findViewById(R.id.list_payments);
        lista.removeAllViews();
        lista.setVisibility(pagadas.isEmpty() ? View.GONE : View.VISIBLE);
        findViewById(R.id.tv_empty).setVisibility(pagadas.isEmpty() ? View.VISIBLE : View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < pagadas.size(); i++) {
            if (i > 0) {
                lista.addView(divisor());
            }
            Cuota cuota = pagadas.get(i);
            Abono ultimo = ReglasPago.ultimoAprobado(cuota);
            View fila = inflater.inflate(R.layout.item_pago, lista, false);
            ((TextView) fila.findViewById(R.id.tv_month)).setText(VistasPago.nombreMes(this, cuota).isEmpty()
                    ? Formato.mes(this, cuota.vence) : capitalizar(VistasPago.nombreMes(this, cuota)));
            String fecha = Formato.diaMesCorto(this, ReglasPago.fechaDePago(cuota));
            ((TextView) fila.findViewById(R.id.tv_detail)).setText(ultimo == null || ultimo.medio == null || ultimo.medio.isEmpty()
                    ? getString(R.string.pay_paid_on_no_method, fecha)
                    : getString(R.string.pay_paid_on, fecha, ultimo.medio));
            ((TextView) fila.findViewById(R.id.tv_amount)).setText(VistasPago.pesos(this, cuota.monto));
            fila.setOnClickListener(v -> VistasPago.abrirCuota(this, propiedadId, cuota));
            lista.addView(fila);
        }
    }

    private static String capitalizar(String texto) {
        return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    /** Línea de 1dp border/card entre filas de la PaymentList. */
    private View divisor() {
        View linea = new View(this);
        linea.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                Math.max(1, Math.round(getResources().getDisplayMetrics().density))));
        linea.setBackgroundColor(ContextCompat.getColor(this, R.color.border_card));
        return linea;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (consulta != null && escucha != null) {
            consulta.removeEventListener(escucha);
        }
    }
}
