package com.example.miarriendo_v1;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.Arrays;

/**
 * Figma: Inquilino / Registrar cuenta bancaria y Cuenta bancaria registrada. El propietario la usa para
 * recibir sus arriendos y el inquilino para devoluciones; cambian los textos, no el formulario.
 * Guardarla no hace ninguna transferencia ni verifica la cuenta con el banco.
 */
public class CuentaBancariaActivity extends AppCompatActivity {

    private DatabaseReference refCuenta;
    private boolean propietario;
    private CuentaBancaria guardada;

    private View loading, grupoForm, grupoGuardada;
    private TextInputLayout tilBank, tilType, tilNumber, tilHolder, tilDocument;
    private MaterialAutoCompleteTextView etBank, etType;
    private TextInputEditText etNumber, etHolder, etDocument;
    private MaterialButton btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Pantallas.edgeToEdge(this);
        setContentView(R.layout.activity_cuenta_bancaria);
        Pantallas.aplicarInsets(findViewById(R.id.main));

        ((TextView) findViewById(R.id.tv_app_bar_title)).setText(R.string.bank_title);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null) {
            finish();
            return;
        }

        loading = findViewById(R.id.loading);
        grupoForm = findViewById(R.id.group_form);
        grupoGuardada = findViewById(R.id.group_saved);
        tilBank = findViewById(R.id.til_bank);
        tilType = findViewById(R.id.til_type);
        tilNumber = findViewById(R.id.til_number);
        tilHolder = findViewById(R.id.til_holder);
        tilDocument = findViewById(R.id.til_document);
        etBank = findViewById(R.id.et_bank);
        etType = findViewById(R.id.et_type);
        etNumber = findViewById(R.id.et_number);
        etHolder = findViewById(R.id.et_holder);
        etDocument = findViewById(R.id.et_document);
        btnSave = findViewById(R.id.btn_save);

        etBank.setAdapter(new ArrayAdapter<>(this, R.layout.item_dropdown, getResources().getStringArray(R.array.banks)));
        etType.setAdapter(new ArrayAdapter<>(this, R.layout.item_dropdown, getResources().getStringArray(R.array.account_types)));
        findViewById(R.id.btn_back_form).setOnClickListener(v -> finish());
        findViewById(R.id.btn_back_saved).setOnClickListener(v -> finish());
        findViewById(R.id.btn_edit).setOnClickListener(v -> mostrarFormulario());
        btnSave.setOnClickListener(v -> guardar());

        refCuenta = FirebaseDatabase.getInstance().getReference("usuarios").child(usuario.getUid()).child("cuenta");
        FirebaseDatabase.getInstance().getReference("usuarios").child(usuario.getUid()).get()
                .addOnSuccessListener(snapshot -> {
                    Usuario u = snapshot.getValue(Usuario.class);
                    propietario = u != null && RegisterRoleActivity.ROL_PROPIETARIO.equals(u.getRol());
                    guardada = snapshot.child("cuenta").getValue(CuentaBancaria.class);
                    configurarTextos();
                    loading.setVisibility(View.GONE);
                    if (guardada != null && guardada.completa()) {
                        mostrarGuardada();
                    } else {
                        mostrarFormulario();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, getString(R.string.bank_save_error, e.getMessage()), Toast.LENGTH_LONG).show();
                    finish();
                });
    }

    /** Los textos cambian según para qué sirve la cuenta. */
    private void configurarTextos() {
        ((TextView) findViewById(R.id.tv_form_title)).setText(propietario ? R.string.bank_owner_heading : R.string.bank_tenant_heading);
        ((TextView) findViewById(R.id.tv_form_subtitle)).setText(propietario ? R.string.bank_owner_subtitle : R.string.bank_tenant_subtitle);
        ((TextView) findViewById(R.id.tv_form_note)).setText(propietario ? R.string.bank_owner_note : R.string.bank_tenant_note);
        ((TextView) findViewById(R.id.tv_form_note_bottom)).setText(propietario ? R.string.bank_owner_note_bottom : R.string.bank_tenant_note_bottom);
        AlertView.mostrar(findViewById(R.id.alert_form), AlertView.Tipo.ADVERTENCIA,
                getString(propietario ? R.string.bank_owner_warning : R.string.bank_tenant_warning));
        ((TextView) findViewById(R.id.tv_saved_subtitle)).setText(propietario ? R.string.bank_owner_subtitle : R.string.bank_tenant_subtitle);
        ((TextView) findViewById(R.id.tv_saved_note)).setText(propietario ? R.string.bank_owner_saved_note : R.string.bank_tenant_saved_note);
        AlertView.mostrar(findViewById(R.id.alert_saved), AlertView.Tipo.INFO, getString(R.string.bank_saved_info));
    }

    private void mostrarFormulario() {
        grupoGuardada.setVisibility(View.GONE);
        grupoForm.setVisibility(View.VISIBLE);
        if (guardada != null) {
            // setText(..., false): no abre el menú desplegable al rellenar.
            etBank.setText(guardada.banco, false);
            etType.setText(guardada.tipo, false);
            etNumber.setText(guardada.numero);
            etHolder.setText(guardada.titular);
            etDocument.setText(guardada.documento);
        }
    }

    private void mostrarGuardada() {
        grupoForm.setVisibility(View.GONE);
        grupoGuardada.setVisibility(View.VISIBLE);
        ViewGroup filas = VistasPago.filas(findViewById(R.id.card_saved));
        filas.removeAllViews();
        TextView titulo = findViewById(R.id.card_saved).findViewById(R.id.tv_card_title);
        titulo.setVisibility(View.VISIBLE);
        titulo.setText(R.string.bank_saved_card_title);
        VistasPago.dato(this, filas, getString(R.string.bank_saved_row_bank), guardada.banco);
        VistasPago.dato(this, filas, getString(R.string.bank_saved_row_type), guardada.tipo);
        VistasPago.dato(this, filas, getString(R.string.bank_saved_row_number), CuentaBancaria.enmascararNumero(guardada.numero));
        VistasPago.dato(this, filas, getString(R.string.bank_saved_row_holder), guardada.titular);
        VistasPago.dato(this, filas, getString(R.string.bank_saved_row_document), CuentaBancaria.enmascararDocumento(guardada.documento));
    }

    private void guardar() {
        CuentaBancaria cuenta = new CuentaBancaria();
        cuenta.banco = texto(etBank);
        cuenta.tipo = texto(etType);
        cuenta.numero = texto(etNumber).replaceAll("\\s", "");
        cuenta.titular = texto(etHolder).replaceAll("\\s+", " ");
        cuenta.documento = texto(etDocument).replaceAll("\\s", "");
        cuenta.actualizadaEn = System.currentTimeMillis();

        boolean bancoOk = Arrays.asList(getResources().getStringArray(R.array.banks)).contains(cuenta.banco);
        boolean tipoOk = Arrays.asList(getResources().getStringArray(R.array.account_types)).contains(cuenta.tipo);
        tilBank.setError(bancoOk ? null : getString(R.string.bank_error_bank));
        tilType.setError(tipoOk ? null : getString(R.string.bank_error_type));
        tilNumber.setError(CuentaBancaria.numeroValido(cuenta.numero) ? null : getString(R.string.bank_error_number));
        tilHolder.setError(CuentaBancaria.titularValido(cuenta.titular) ? null : getString(R.string.bank_error_holder));
        tilDocument.setError(CuentaBancaria.documentoValido(cuenta.documento) ? null : getString(R.string.bank_error_document));
        if (!bancoOk || !tipoOk || !cuenta.completa()) {
            return;
        }

        btnSave.setEnabled(false);
        btnSave.setText(R.string.bank_saving);
        refCuenta.setValue(cuenta)
                .addOnSuccessListener(v -> {
                    guardada = cuenta;
                    btnSave.setEnabled(true);
                    btnSave.setText(R.string.bank_save);
                    mostrarGuardada();
                })
                .addOnFailureListener(e -> {
                    btnSave.setEnabled(true);
                    btnSave.setText(R.string.bank_save);
                    Toast.makeText(this, getString(R.string.bank_save_error, e.getMessage()), Toast.LENGTH_LONG).show();
                });
    }

    private static String texto(android.widget.EditText campo) {
        return campo.getText() == null ? "" : campo.getText().toString().trim();
    }
}
