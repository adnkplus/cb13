package com.cb13;

/*
 * Decorative cockpit/HUD layer.
 *
 * IMPORTANT:
 * This view is SCREEN ONLY.
 * It must never be included in renderBitmap().
 */

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

public class CockpitBackgroundView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);

    public CockpitBackgroundView(Context c) {
        super(c);
        setLayerType(View.LAYER_TYPE_SOFTWARE,null);
    }

    @Override protected void onDraw(Canvas c) {
        int w=getWidth();
        int h=getHeight();

        c.drawColor(0xFF03070B);

        // Central dark glass.
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x1400C8AE);
        c.drawRect(w*.06f,h*.06f,w*.94f,h*.94f,p);

        // Technical perspective lines.
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1f);
        p.setColor(0x3035F2C2);

        Path l=new Path();
        l.moveTo(0,h*.86f);
        l.lineTo(w*.19f,h*.64f);
        l.lineTo(w*.31f,h*.17f);
        c.drawPath(l,p);

        Path r=new Path();
        r.moveTo(w,h*.86f);
        r.lineTo(w*.81f,h*.64f);
        r.lineTo(w*.69f,h*.17f);
        c.drawPath(r,p);

        // Central frame.
        p.setColor(0x4035F2C2);
        c.drawRect(w*.12f,h*.08f,w*.88f,h*.92f,p);

        // HUD tick marks.
        p.setColor(0x5035F2C2);
        for(int i=1;i<8;i++){
            float x=w*i/8f;
            c.drawLine(x,h*.065f,x,h*.085f,p);
            c.drawLine(x,h*.915f,x,h*.935f,p);
        }
    }
}
