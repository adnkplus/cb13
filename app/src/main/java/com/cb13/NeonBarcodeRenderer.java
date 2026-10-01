package com.cb13;

/*
 * Preview-only barcode paint helper.
 *
 * The geometry is supplied by the existing BarcodeView.
 * This class changes ONLY the visual paint.
 *
 * Printer mode remains pure black on white.
 */

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;

public final class NeonBarcodeRenderer {
    private NeonBarcodeRenderer() {}

    public static void clearPreview(Canvas c) {
        c.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);
    }

    public static void drawBar(
            Canvas c, Paint p,
            float left,float top,float right,float bottom) {

        p.setStyle(Paint.Style.FILL);

        // Halo.
        p.setColor(0x1835F2C2);
        c.drawRect(left-3f,top-3f,right+3f,bottom+3f,p);

        p.setColor(0x3535F2C2);
        c.drawRect(left-1.5f,top-1.5f,right+1.5f,bottom+1.5f,p);

        // Solid neon core.
        p.setColor(0xFF35F2C2);
        c.drawRect(left,top,right,bottom,p);
    }

    public static void drawDigit(
            Canvas c, Paint p, Path path) {

        p.setStyle(Paint.Style.FILL);

        p.setColor(0x1835F2C2);
        c.drawPath(path,p);

        p.setColor(0xFF35F2C2);
        c.drawPath(path,p);
    }

    public static void preparePrinter(Canvas c, Paint p) {
        c.drawColor(Color.WHITE);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.BLACK);
        p.clearShadowLayer();
    }
}
