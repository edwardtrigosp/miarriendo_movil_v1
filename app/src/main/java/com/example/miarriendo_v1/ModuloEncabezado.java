package com.example.miarriendo_v1;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;

import java.util.List;

/**
 * Módulo / Encabezado (446:3435): Avatar → Perfil, notificaciones → Mis solicitudes y saludo.
 * Inquilino sin arriendo (446:3419): además el buscador y las sugerencias.
 * El ícono de ayuda de Figma no se dibuja porque la app aún no tiene pantalla de ayuda.
 */
final class ModuloEncabezado implements ModuloInicio {

    private static final long SEMANA_MS = 7L * 24 * 60 * 60 * 1000;

    private View vista;
    private boolean activo;

    @Override
    public View crear(LayoutInflater inflater, ViewGroup padre) {
        vista = inflater.inflate(R.layout.modulo_encabezado, padre, false);
        return vista;
    }

    @Override
    public void iniciar(ContextoInicio ctx) {
        activo = true;
        TextView avatar = vista.findViewById(R.id.tv_avatar);
        TextView saludo = vista.findViewById(R.id.tv_greeting);
        boolean inquilino = ctx.datos.esInquilino();
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        saludo.setText(R.string.home_greeting_generic);
        avatar.setText(Formato.iniciales(usuario == null ? "" : usuario.getEmail()));
        avatar.setOnClickListener(v -> ctx.activity.startActivity(
                new android.content.Intent(ctx.activity, PerfilActivity.class)
                        .putExtra(PerfilActivity.EXTRA_INQUILINO, inquilino)));
        View notificaciones = vista.findViewById(R.id.btn_notifications);
        if (inquilino) {
            notificaciones.setOnClickListener(v -> ctx.abrir(MisSolicitudesActivity.class));
        } else {
            notificaciones.setOnClickListener(v -> ctx.abrir(SolicitudesRecibidasActivity.class));
        }
        cargarNombre(ctx.uid, saludo, avatar);

        if (ctx.datos.perfil() == PlanDeInicio.Perfil.INQUILINO_SIN_ARRIENDO) {
            mostrarBuscador(ctx);
            mostrarSugerencias(ctx);
        }
    }

    /** "Hola, María" con el primer nombre del perfil y sus iniciales en el Avatar. */
    private void cargarNombre(String uid, TextView saludo, TextView avatar) {
        FirebaseDatabase.getInstance().getReference("usuarios").child(uid).get()
                .addOnSuccessListener(snapshot -> {
                    Usuario u = snapshot.getValue(Usuario.class);
                    String nombre = u == null || u.getNombre() == null ? "" : u.getNombre().trim();
                    if (!activo || nombre.isEmpty()) {
                        return;
                    }
                    String primerNombre = nombre.split("\\s+")[0];
                    saludo.setText(saludo.getContext().getString(R.string.home_greeting, primerNombre));
                    String apellido = u.getApellido() == null ? "" : u.getApellido().trim();
                    avatar.setText(Formato.iniciales((primerNombre + " " + apellido).trim()));
                });
    }

    private void mostrarBuscador(ContextoInicio ctx) {
        TextView subtitulo = vista.findViewById(R.id.tv_subtitle);
        subtitulo.setText(R.string.home_subtitle_search);
        subtitulo.setVisibility(View.VISIBLE);
        vista.findViewById(R.id.buscador).setVisibility(View.VISIBLE);
        EditText buscar = vista.findViewById(R.id.et_search);
        buscar.setOnEditorActionListener((v, accion, evento) -> {
            if (accion != EditorInfo.IME_ACTION_SEARCH) {
                return false;
            }
            ctx.abrirResultados(filtros(buscar));
            return true;
        });
        vista.findViewById(R.id.btn_filters).setOnClickListener(v -> ctx.elegirFiltros(filtros(buscar)));
    }

    private static FiltrosBusqueda filtros(EditText buscar) {
        FiltrosBusqueda filtros = new FiltrosBusqueda();
        filtros.ciudad = buscar.getText() == null ? "" : buscar.getText().toString().trim();
        return filtros;
    }

    /** "Así funciona" siempre; "N arriendos nuevos esta semana" solo si hay alguno. */
    private void mostrarSugerencias(ContextoInicio ctx) {
        LinearLayout sugerencias = vista.findViewById(R.id.sugerencias);
        sugerencias.removeAllViews();
        agregarSugerencia(sugerencias, R.drawable.ic_info,
                sugerencias.getContext().getString(R.string.home_tip_how_it_works), null);
        vista.findViewById(R.id.sugerencias_scroll).setVisibility(View.VISIBLE);
        ctx.catalogo(new Catalogo.Resultado() {
            @Override
            public void listo(List<Propiedad> disponibles) {
                int nuevos = nuevosEstaSemana(disponibles, System.currentTimeMillis());
                if (!activo || nuevos == 0) {
                    return;
                }
                agregarSugerencia(sugerencias, R.drawable.ic_task_alt, sugerencias.getResources()
                                .getQuantityString(R.plurals.home_tip_new_this_week, nuevos, nuevos),
                        v -> ctx.abrirResultados(new FiltrosBusqueda()));
            }

            @Override
            public void error(Exception e) {
                // Sin catálogo solo queda "Así funciona"; Para ti muestra el error.
            }
        });
    }

    static int nuevosEstaSemana(List<Propiedad> disponibles, long ahora) {
        int n = 0;
        for (Propiedad p : disponibles) {
            if (p.creadoEn > 0 && ahora - p.creadoEn < SEMANA_MS) {
                n++;
            }
        }
        return n;
    }

    private static void agregarSugerencia(LinearLayout padre, int icono, String texto, View.OnClickListener alTocar) {
        View tarjeta = LayoutInflater.from(padre.getContext()).inflate(R.layout.item_sugerencia, padre, false);
        ((ImageView) tarjeta.findViewById(R.id.iv_icon)).setImageResource(icono);
        ((TextView) tarjeta.findViewById(R.id.tv_text)).setText(texto);
        if (alTocar != null) {
            tarjeta.setOnClickListener(alTocar);
        }
        padre.addView(tarjeta);
    }

    @Override
    public void detener() {
        activo = false;
    }
}
