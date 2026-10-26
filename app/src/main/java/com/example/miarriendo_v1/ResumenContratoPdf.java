package com.example.miarriendo_v1;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * El PDF de "Descargar en PDF" de Mi contrato: un resumen, en hoja carta, de lo que se registró en miarriendo
 * (inmueble, condiciones y cláusulas). Deja claro que no reemplaza el contrato firmado. Se guarda en la caché
 * (cache/pdf) y se comparte con FileProvider.
 */
final class ResumenContratoPdf {

    /** Hoja carta en puntos (612 × 792) con márgenes de 56. */
    private static final int ANCHO = 612, ALTO = 792, MARGEN = 56;

    private final Context context;
    private final PdfDocument documento = new PdfDocument();
    private PdfDocument.Page pagina;
    private Canvas lienzo;
    private float y;
    private int numero;

    private ResumenContratoPdf(Context context) {
        this.context = context;
    }

    static File crear(Context context, Propiedad propiedad, String estado, List<String[]> condiciones, String clausulas)
            throws IOException {
        ResumenContratoPdf pdf = new ResumenContratoPdf(context);
        try {
            pdf.escribir(propiedad, estado, condiciones, clausulas);
            File carpeta = new File(context.getCacheDir(), "pdf");
            if (!carpeta.exists() && !carpeta.mkdirs()) {
                throw new IOException(carpeta.getPath());
            }
            File archivo = new File(carpeta, "contrato-" + propiedad.id + ".pdf");
            try (OutputStream salida = new FileOutputStream(archivo)) {
                pdf.documento.writeTo(salida);
            }
            return archivo;
        } finally {
            pdf.documento.close();
        }
    }

    private void escribir(Propiedad p, String estado, List<String[]> condiciones, String clausulas) {
        nuevaPagina();
        String hoy = LocalDate.now().format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", new Locale("es", "CO")));
        texto(context.getString(R.string.contract_pdf_title), 20, true, R.color.text_primary, 4);
        texto(context.getString(R.string.contract_pdf_generated, hoy), 10, false, R.color.text_secondary, 20);
        texto(p.tituloPublico(), 14, true, R.color.text_primary, 2);
        texto(p.direccionCompleta(), 11, false, R.color.text_secondary, 20);

        texto(context.getString(R.string.contract_conditions), 13, true, R.color.text_primary, 8);
        fila(context.getString(R.string.contract_status), estado);
        for (String[] c : condiciones) {
            fila(c[0], c[1]);
        }
        y += 12;
        if (clausulas != null) {
            texto(context.getString(R.string.contract_agreed), 13, true, R.color.text_primary, 8);
            texto(clausulas, 11, false, R.color.text_primary, 20);
        }
        texto(context.getString(R.string.contract_pdf_disclaimer), 9, false, R.color.text_secondary, 0);
        documento.finishPage(pagina);
    }

    private void nuevaPagina() {
        if (pagina != null) {
            documento.finishPage(pagina);
        }
        numero++;
        pagina = documento.startPage(new PdfDocument.PageInfo.Builder(ANCHO, ALTO, numero).create());
        lienzo = pagina.getCanvas();
        y = MARGEN;
    }

    private TextPaint pintura(float tamano, boolean negrita, int color) {
        TextPaint t = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        t.setTextSize(tamano);
        t.setTypeface(Typeface.create(Typeface.SANS_SERIF, negrita ? Typeface.BOLD : Typeface.NORMAL));
        t.setColor(ContextCompat.getColor(context, color));
        return t;
    }

    /** Un párrafo que se parte en líneas; si no cabe, sigue en otra página. */
    private void texto(String contenido, float tamano, boolean negrita, int color, float despues) {
        if (contenido == null || contenido.isEmpty()) {
            return;
        }
        StaticLayout bloque = StaticLayout.Builder.obtain(contenido, 0, contenido.length(), pintura(tamano, negrita, color),
                ANCHO - 2 * MARGEN).setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0, 1.2f).build();
        for (int linea = 0; linea < bloque.getLineCount(); linea++) {
            float alto = bloque.getLineBottom(linea) - bloque.getLineTop(linea);
            if (y + alto > ALTO - MARGEN) {
                nuevaPagina();
            }
            lienzo.save();
            lienzo.translate(MARGEN, y - bloque.getLineTop(linea));
            lienzo.clipRect(0, bloque.getLineTop(linea), ANCHO - 2 * MARGEN, bloque.getLineBottom(linea));
            bloque.draw(lienzo);
            lienzo.restore();
            y += alto;
        }
        y += despues;
    }

    /** "Etiqueta ........ Valor", como las filas Dato de la pantalla. */
    private void fila(String etiqueta, String valor) {
        if (y + 22 > ALTO - MARGEN) {
            nuevaPagina();
        }
        TextPaint izquierda = pintura(11, false, R.color.text_secondary);
        TextPaint derecha = pintura(11, true, R.color.text_primary);
        derecha.setTextAlign(Paint.Align.RIGHT);
        float base = y + 14;
        lienzo.drawText(etiqueta, MARGEN, base, izquierda);
        lienzo.drawText(valor, ANCHO - MARGEN, base, derecha);
        Paint linea = new Paint();
        linea.setColor(ContextCompat.getColor(context, R.color.border_card));
        lienzo.drawLine(MARGEN, y + 22, ANCHO - MARGEN, y + 22, linea);
        y += 26;
    }
}
