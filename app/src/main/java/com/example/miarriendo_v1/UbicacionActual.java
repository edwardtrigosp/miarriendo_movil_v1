package com.example.miarriendo_v1;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

/** Ubicación actual del teléfono con LocationManager, igual que el ícono my_location del paso 2 de Publicar. */
final class UbicacionActual {

    interface Resultado {
        /** La ubicación, o null si el teléfono no pudo darla. */
        void listo(@Nullable Location ubicacion);
    }

    private UbicacionActual() {
    }

    /** Basta el permiso aproximado: si el usuario dio el preciso, también está el aproximado. */
    static boolean tienePermiso(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressWarnings("MissingPermission")
    static void obtener(Context context, Resultado resultado) {
        LocationManager lm = context.getSystemService(LocationManager.class);
        String proveedor = lm.isProviderEnabled(LocationManager.FUSED_PROVIDER) ? LocationManager.FUSED_PROVIDER
                : lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ? LocationManager.NETWORK_PROVIDER
                : LocationManager.GPS_PROVIDER;
        if (!lm.isProviderEnabled(proveedor)) {
            resultado.listo(null);
            return;
        }
        lm.getCurrentLocation(proveedor, null, ContextCompat.getMainExecutor(context), resultado::listo);
    }
}
