package com.example.miarriendo_v1;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** "Hogares parecidos" del módulo Tu solicitud cuando no aceptaron la solicitud. */
public class FiltrosParecidosTest {

    private static Propiedad propiedad(String id, long precio) {
        Propiedad p = new Propiedad();
        p.id = id;
        p.precio = precio;
        return p;
    }

    @Test
    public void parecidosSonEntre70y130PorCientoDelPrecio() {
        FiltrosBusqueda f = FiltrosBusqueda.parecidosA(1_200_000);
        assertEquals(840_000, f.precioMin);
        assertEquals(1_560_000, f.precioMax);
        List<Propiedad> r = f.aplicar(Arrays.asList(propiedad("a", 800_000), propiedad("b", 850_000),
                propiedad("c", 1_560_000), propiedad("d", 1_600_000)));
        assertEquals(2, r.size());
        assertEquals("b", r.get(0).id);
        assertEquals("c", r.get(1).id);
    }

    @Test
    public void sinPrecioNoFiltra() {
        FiltrosBusqueda f = FiltrosBusqueda.parecidosA(0);
        assertEquals(0, f.precioMin);
        assertEquals(0, f.precioMax);
    }
}
