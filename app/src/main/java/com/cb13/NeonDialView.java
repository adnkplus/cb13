package com.cb13;

/*
 * Circular neon control used instead of a standard SeekBar visually.
 *
 * VALUE RANGE:
 *   0.0 = minimum
 *   1.0 = maximum
 *
 * MainActivity remains responsible for converting this value to its
 * existing slider range. This class deliberately knows nothing about
 * barcode geometry.
 */

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

public class NeonDialView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc = new RectF();

    private float value = 0.5f;
    private String label = "";
    private String valueText = "";
    private int neon = 0xFF35F2C2;
    private OnValueChangedListener listener;

    public interface OnValueChangedListener {
        void onValueChanged(NeonDialView view, float value, boolean fromUser);
    }

    public NeonDialView(Context c) { super(c); init(); }

    private void init() {
        p.setTypeface(android.graphics.Typeface.create(
                android.graphics.Typeface.MONOSPACE,
                android.graphics.Typeface.BOLD));
        setFocusable(true);
    }

    public void setLabel(String s) { label = s == null ? "" : s; invalidate(); }
    public void setValueText(String s) { valueText = s == null ? "" : s; invalidate(); }

    public void setValue(float v) {
        value = Math.max(0f, Math.min(1f, v));
        invalidate();
    }

    public float getValue() { return value; }

    public void setNeonColor(int c) {
        neon = c;
        invalidate();
    }

    public void setOnValueChangedListener(OnValueChangedListener l) {
        listener = l;
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);

        float cx = getWidth()/2f;
        float cy = getHeight()/2f;
        float r = Math.min(getWidth(), getHeight()) * .35f;

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);

        // Soft halo.
        for (int i=4; i>=1; i--) {
            p.setStrokeWidth(2f + i*2f);
            p.setColor((neon & 0x00FFFFFF) | ((10+i*3)<<24));
            c.drawCircle(cx, cy, r+i*1.5f, p);
        }

        arc.set(cx-r, cy-r, cx+r, cy+r);

        p.setStrokeWidth(3f);
        p.setColor((neon & 0x00FFFFFF) | 0x26300000);
        c.drawArc(arc, -220f, 260f, false, p);

        p.setStrokeWidth(5f);
        p.setColor(neon);
        c.drawArc(arc, -220f, 260f*value, false, p);

        double a=Math.toRadians(-220f+260f*value);
        float x=cx+(float)Math.cos(a)*r;
        float y=cy+(float)Math.sin(a)*r;

        p.setStyle(Paint.Style.FILL);
        p.setColor(neon);
        c.drawCircle(x,y,5f,p);

        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(Math.max(9f,getWidth()*.105f));
        c.drawText(label,cx,cy-2f,p);

        p.setColor(0xFFE9FFFA);
        p.setTextSize(Math.max(10f,getWidth()*.125f));
        c.drawText(valueText,cx,cy+16f,p);
    }

    // Display only. All user input belongs to the transparent vertical fader zone
    // created by MainActivity. There is no rotary-control logic here.
    @Override public boolean onTouchEvent(MotionEvent e) {
        return false;
    }
}
