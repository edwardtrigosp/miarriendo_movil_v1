package com.example.miarriendo_v1;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.firebase.database.FirebaseDatabase;

/** Llena las tarjetas de propiedad que ve el inquilino: PropertyCard y OwnerPropertyCard. */
final class TarjetasPropiedad {

    private TarjetasPropiedad() {
    }

    /** PropertyCard (43:1059): foto, precio sobre la foto, nombre, dirección y datos. */
    static View grande(Context context, ViewGroup parent, Propiedad p, View.OnClickListener alTocar) {
        View tarjeta = LayoutInflater.from(context).inflate(R.layout.item_propiedad_grande, parent, false);
        ImageView foto = tarjeta.findViewById(R.id.iv_photo);
        mostrarFoto(foto, FotosCodec.desdeBase64(p.portada));
        // La portada es pequeña (256 px): se reemplaza por la primera foto completa cuando llega.
        if (p.id != null && p.numFotos > 0) {
            FirebaseDatabase.getInstance().getReference("fotos_propiedades").child(p.id).child("0").get()
                    .addOnSuccessListener(s -> mostrarFoto(foto, FotosCodec.desdeBase64(s.getValue(String.class))));
        }
        ((TextView) tarjeta.findViewById(R.id.tv_price_badge)).setText(precio(context, p));
        ((TextView) tarjeta.findViewById(R.id.tv_title)).setText(p.tituloPublico());
        ((TextView) tarjeta.findViewById(R.id.tv_address)).setText(p.direccionCompleta());
        ((TextView) tarjeta.findViewById(R.id.tv_bedrooms))
                .setText(context.getString(R.string.stat_bedrooms_short, p.habitaciones));
        ((TextView) tarjeta.findViewById(R.id.tv_bathrooms))
                .setText(context.getResources().getQuantityString(R.plurals.stat_bathrooms, p.banos, p.banos));
        String area = area(context, p);
        tarjeta.findViewById(R.id.iv_area).setVisibility(area == null ? View.GONE : View.VISIBLE);
        TextView tvArea = tarjeta.findViewById(R.id.tv_area);
        tvArea.setVisibility(area == null ? View.GONE : View.VISIBLE);
        tvArea.setText(area);
        tarjeta.setOnClickListener(alTocar);
        return tarjeta;
    }

    /** OwnerPropertyCard (223:902) con el estado "Disponible". También sirve para un include ya inflado. */
    static void compacta(Context context, View tarjeta, Propiedad p) {
        ImageView thumbnail = tarjeta.findViewById(R.id.iv_thumbnail);
        Bitmap portada = FotosCodec.desdeBase64(p.portada);
        if (portada != null) {
            thumbnail.setPadding(0, 0, 0, 0);
            thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumbnail.setImageBitmap(portada);
        } else {
            // Sin foto: ícono home_work de 28 centrado sobre brand/container.
            int margen = Math.round(18 * context.getResources().getDisplayMetrics().density);
            thumbnail.setPadding(margen, margen, margen, margen);
            thumbnail.setScaleType(ImageView.ScaleType.FIT_CENTER);
            thumbnail.setImageResource(R.drawable.ic_home_work);
        }
        ((TextView) tarjeta.findViewById(R.id.tv_title)).setText(p.tituloPublico());
        ((TextView) tarjeta.findViewById(R.id.tv_address)).setText(p.direccionCompleta());
        ((TextView) tarjeta.findViewById(R.id.tv_price)).setText(precio(context, p));
        StatusChipView.aplicar(tarjeta.findViewById(R.id.tv_status), StatusChipView.Tipo.NEUTRAL,
                context.getString(R.string.owner_status_available));
    }

    static View compacta(Context context, ViewGroup parent, Propiedad p, View.OnClickListener alTocar) {
        View tarjeta = LayoutInflater.from(context).inflate(R.layout.item_propiedad, parent, false);
        compacta(context, tarjeta, p);
        tarjeta.setOnClickListener(alTocar);
        return tarjeta;
    }

    /** "$1.200.000 COP/mes" */
    static String precio(Context context, Propiedad p) {
        return context.getString(R.string.owner_price_month, Propiedad.formatearMiles(p.precio));
    }

    /** "110 m²", o null si no se registró el área. */
    static String area(Context context, Propiedad p) {
        if (p.area == null || p.area <= 0) {
            return null;
        }
        double a = p.area;
        String valor = a == Math.rint(a) ? String.valueOf((long) a) : String.valueOf(a).replace('.', ',');
        return context.getString(R.string.stat_area, valor);
    }

    private static void mostrarFoto(ImageView vista, Bitmap foto) {
        if (foto == null) {
            return;
        }
        vista.setScaleType(ImageView.ScaleType.CENTER_CROP);
        vista.setImageBitmap(foto);
    }
}
