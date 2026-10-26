package com.example.miarriendo_v1;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Figma: Inquilino / Pagar completo. Muestra qué se va a cobrar (el mes completo, o lo que falta de un mes viejo
 * que quedó a medias en abonos), pide el celular
 * Nequi y la aceptación de los contratos de Wompi, y pide el cobro al servidor. Nequi manda la
 * notificación al celular; el resultado se sigue en {@link ConfirmarNequiActivity}.
 */
public class PagarNequiActivity extends AppCompatActivity {

    private String propiedadId, cuotaId;

    private Cuota cuota;
    private PlanDeCobro plan;
    /** Tokens y enlaces de los contratos de Wompi; null hasta que llegan. */
    private JSONObject aceptacion;
    private boolean enviando;

    private TextInputLayout tilPhone;
    private TextInputEditText etPhone;
    private CheckBox cbTerms;
    private View alert, btnRetryTerms;
    private MaterialButton btnPay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Pantallas.edgeToEdge(this);
        setContentView(R.layout.activity_pagar_nequi);
        Pantallas.aplicarInsets(findViewById(R.id.main));

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        propiedadId = getIntent().getStringExtra(VistasPago.EXTRA_PROPIEDAD_ID);
        cuotaId = getIntent().getStringExtra(VistasPago.EXTRA_CUOTA_ID);
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (propiedadId == null || cuotaId == null || usuario == null) {
            finish();
            return;
        }

        tilPhone = findViewById(R.id.til_phone);
        etPhone = findViewById(R.id.et_phone);
        cbTerms = findViewById(R.id.cb_terms);
        alert = findViewById(R.id.alert);
        btnRetryTerms = findViewById(R.id.btn_retry_terms);
        btnPay = findViewById(R.id.btn_pay);

        etPhone.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                validarCelular();
                actualizarBoton();
            }
        });
        cbTerms.setOnCheckedChangeListener((b, marcada) -> actualizarBoton());
        btnRetryTerms.setOnClickListener(v -> cargarAceptacion());
        btnPay.setOnClickListener(v -> pagar());

        cargarCuota();
        cargarCelular(usuario.getUid());
        cargarAceptacion();
    }

    // ---------- Datos ----------

    private void cargarCuota() {
        RepositorioPagos.cuota(propiedadId, cuotaId).get()
                .addOnSuccessListener(snapshot -> {
                    cuota = RepositorioPagos.leerCuota(snapshot);
                    plan = PlanDeCobro.de(cuota);
                    if (plan == null) {
                        // Nada que cobrar: ya está pagada.
                        finish();
                        return;
                    }
                    mostrarResumen();
                    actualizarBoton();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, getString(R.string.pay_load_error, e.getMessage()), Toast.LENGTH_LONG).show();
                    finish();
                });
    }

    /** El celular del perfil, si es un número Nequi válido, para no tener que escribirlo. */
    private void cargarCelular(String uid) {
        FirebaseDatabase.getInstance().getReference("usuarios").child(uid).child("telefono").get()
                .addOnSuccessListener(snapshot -> {
                    String telefono = snapshot.getValue(String.class);
                    if (telefono != null && CelularNequi.normalizar(telefono) != null && etPhone.getText() != null
                            && etPhone.getText().length() == 0) {
                        etPhone.setText(CelularNequi.formatear(telefono));
                    }
                });
    }

    private void cargarAceptacion() {
        btnRetryTerms.setVisibility(View.GONE);
        AlertView.ocultar(alert);
        ApiBackend.get(this, "/api/pagos/aceptacion", new ApiBackend.Respuesta() {
            @Override
            public void ok(JSONObject datos) {
                if (isDestroyed()) {
                    return;
                }
                aceptacion = datos;
                mostrarConsentimiento();
                actualizarBoton();
            }

            @Override
            public void error(int estado, String mensaje) {
                if (isDestroyed()) {
                    return;
                }
                AlertView.mostrar(alert, AlertView.Tipo.ERROR, getString(R.string.nequi_terms_error, mensaje));
                btnRetryTerms.setVisibility(View.VISIBLE);
            }
        });
    }

    // ---------- Pantalla ----------

    private void mostrarResumen() {
        String periodo = VistasPago.periodo(this, cuota);
        String titulo;
        String etiqueta;
        String chip;
        if (plan.tipo == PlanDeCobro.Tipo.SALDO) {
            titulo = getString(R.string.nequi_title_balance);
            etiqueta = getString(R.string.nequi_label_balance);
        } else {
            titulo = getString(R.string.nequi_title_single);
            etiqueta = getString(R.string.nequi_label_single);
        }
        chip = periodo;
        ((TextView) findViewById(R.id.tv_app_bar_title)).setText(titulo);
        VistasPago.tarjetaPago(findViewById(R.id.card_summary), etiqueta, VistasPago.pesos(this, plan.monto),
                StatusChipView.Tipo.PRIMARIO, chip);
    }

    /** "Acepto los términos de pago y la política de privacidad de Wompi…" con enlaces a los contratos. */
    private void mostrarConsentimiento() {
        String terminos = getString(R.string.nequi_consent_terms);
        String privacidad = getString(R.string.nequi_consent_privacy);
        String datos = getString(R.string.nequi_consent_data);
        String texto = getString(R.string.nequi_consent, terminos, privacidad, datos);
        SpannableString spannable = new SpannableString(texto);
        enlazar(spannable, texto, terminos, aceptacion.optString("terminosUrl"));
        enlazar(spannable, texto, privacidad, aceptacion.optString("terminosUrl"));
        enlazar(spannable, texto, datos, aceptacion.optString("datosUrl"));
        cbTerms.setText(spannable);
        cbTerms.setMovementMethod(LinkMovementMethod.getInstance());
        cbTerms.setEnabled(true);
    }

    private void enlazar(SpannableString spannable, String texto, String fragmento, String url) {
        int inicio = texto.indexOf(fragmento);
        // Solo se abren enlaces seguros: la dirección la entrega el servidor de Wompi.
        if (inicio < 0 || !url.startsWith("https://")) {
            return;
        }
        spannable.setSpan(new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            }

            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                ds.setColor(ContextCompat.getColor(PagarNequiActivity.this, R.color.brand_primary));
                ds.setUnderlineText(true);
            }
        }, inicio, inicio + fragmento.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private String celular() {
        return CelularNequi.normalizar(etPhone.getText() == null ? null : etPhone.getText().toString());
    }

    /** El error aparece cuando ya se escribieron los 10 dígitos y el número no sirve. */
    private void validarCelular() {
        String texto = etPhone.getText() == null ? "" : etPhone.getText().toString();
        int digitos = texto.replaceAll("\\D", "").length();
        tilPhone.setError(digitos >= 10 && celular() == null ? getString(R.string.nequi_phone_error) : null);
    }

    private void actualizarBoton() {
        btnPay.setEnabled(plan != null && aceptacion != null && cbTerms.isChecked() && celular() != null && !enviando);
    }

    // ---------- Cobro ----------

    private void pagar() {
        JSONObject cuerpo = new JSONObject();
        try {
            cuerpo.put("propiedadId", propiedadId);
            cuerpo.put("cuotaId", cuotaId);
            cuerpo.put("celular", celular());
            cuerpo.put("acceptanceToken", aceptacion.optString("acceptanceToken"));
            cuerpo.put("personalAuthToken", aceptacion.optString("personalAuthToken"));
            cuerpo.put("modalidad", plan.modalidad());
        } catch (JSONException e) {
            return;
        }
        enviando = true;
        btnPay.setText(R.string.nequi_paying);
        AlertView.ocultar(alert);
        actualizarBoton();

        ApiBackend.post(this, "/api/pagos/nequi", cuerpo, new ApiBackend.Respuesta() {
            @Override
            public void ok(JSONObject datos) {
                if (isDestroyed()) {
                    return;
                }
                startActivity(VistasPago.intent(PagarNequiActivity.this, ConfirmarNequiActivity.class, propiedadId, cuotaId)
                        .putExtra(VistasPago.EXTRA_ABONO, datos.optInt("abono", 1))
                        .putExtra(VistasPago.EXTRA_CELULAR_FIN, datos.optString("celularFin", null)));
                finish();
            }

            @Override
            public void error(int estado, String mensaje) {
                if (isDestroyed()) {
                    return;
                }
                enviando = false;
                btnPay.setText(R.string.nequi_pay);
                AlertView.mostrar(alert, AlertView.Tipo.ERROR, mensaje);
                actualizarBoton();
            }
        });
    }
}
