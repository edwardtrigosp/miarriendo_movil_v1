package com.example.miarriendo_v1;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;

/**
 * Dibuja el pin de cada propiedad en el mapa: precio mensual y punta (Figma: Mapa / Pin de precio, Normal y
 * Seleccionado). El nombre no va en el pin: se ve en la tarjeta al seleccionarlo.
 */
final class MarcadoresMapa {

    /** Imagen del marcador y el punto de ella que cae sobre la coordenada (la punta del pin). */
    static final class Pin {
        final BitmapDescriptor icono;
        final float anclaX, anclaY;

        Pin(BitmapDescriptor icono, float anclaX, float anclaY) {
            this.icono = icono;
            this.anclaX = anclaX;
            this.anclaY = anclaY;
        }
    }

    private MarcadoresMapa() {
    }

    /** Solo se puede llamar cuando el mapa ya está listo (BitmapDescriptorFactory lo exige). */
    @SuppressLint("InflateParams")
    static Pin crear(Context context, Propiedad p, boolean seleccionada) {
        View vista = LayoutInflater.from(context).inflate(R.layout.view_map_pin, null, false);
        TextView precio = vista.findViewById(R.id.tv_chip);
        ImageView punta = vista.findViewById(R.id.iv_tip);

        precio.setText(context.getString(R.string.map_pin_price, Propiedad.formatearMiles(p.precio)));
        int colorPrecio = ContextCompat.getColor(context, seleccionada ? R.color.brand_primary : R.color.surface_default);
        if (seleccionada) {
            precio.setBackgroundResource(R.drawable.bg_map_pin_chip_selected);
            precio.setTextColor(ContextCompat.getColor(context, R.color.surface_white));
            punta.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.brand_primary)));
        }

        vista.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int ancho = vista.getMeasuredWidth();
        int alto = vista.getMeasuredHeight();
        vista.layout(0, 0, ancho, alto);

        Bitmap bitmap = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // Sombra del precio (drop-shadow 0 3 6 rgba(46,16,101,0.08)): las elevaciones no se dibujan sobre un bitmap.
        float densidad = context.getResources().getDisplayMetrics().density;
        Paint sombra = new Paint(Paint.ANTI_ALIAS_FLAG);
        sombra.setColor(colorPrecio);
        sombra.setShadowLayer(6 * densidad, 0, 3 * densidad, Color.argb(20, 46, 16, 101));
        RectF caja = new RectF(precio.getLeft(), precio.getTop(), precio.getRight(), precio.getBottom());
        canvas.drawRoundRect(caja, caja.height() / 2f, caja.height() / 2f, sombra);

        vista.draw(canvas);
        return new Pin(BitmapDescriptorFactory.fromBitmap(bitmap), 0.5f, punta.getBottom() / (float) alto);
    }
}
