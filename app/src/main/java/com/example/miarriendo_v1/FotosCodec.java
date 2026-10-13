package com.example.miarriendo_v1;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.util.Base64;
import android.util.Size;

import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Convierte fotos a JPEG comprimido en Base64 para guardarlas en Realtime Database.
 * (Firebase Storage exige el plan Blaze; esto permite trabajar con el plan gratuito.)
 */
final class FotosCodec {

    static final int LADO_FOTO = 1280;
    static final int LADO_PORTADA = 256;
    private static final int CALIDAD_JPEG = 70;

    private FotosCodec() {
    }

    /** Lee la imagen, la reduce para que su lado mayor no pase de {@code ladoMaximo} y la codifica. */
    static String aBase64(ContentResolver resolver, Uri uri, int ladoMaximo) throws IOException {
        ImageDecoder.Source source = ImageDecoder.createSource(resolver, uri);
        Bitmap bitmap = ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            Size size = info.getSize();
            int mayor = Math.max(size.getWidth(), size.getHeight());
            if (mayor > ladoMaximo) {
                float escala = (float) ladoMaximo / mayor;
                decoder.setTargetSize(Math.round(size.getWidth() * escala), Math.round(size.getHeight() * escala));
            }
        });

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, CALIDAD_JPEG, out);
        bitmap.recycle();
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }

    @Nullable
    static Bitmap desdeBase64(@Nullable String base64) {
        if (base64 == null || base64.isEmpty()) {
            return null;
        }
        try {
            byte[] bytes = Base64.decode(base64, Base64.NO_WRAP);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
