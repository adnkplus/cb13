package com.cb13;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

/**
 * Screen-only cockpit helpers.
 * Printer output remains black on white.
 */
public final class CockpitIntegration {
    private CockpitIntegration() {}

    public static void prepareTransparentPreview(View preview) {
        if (preview == null) return;
        preview.setBackgroundColor(Color.TRANSPARENT);
        preview.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    public static void styleInput(EditText input) {
        if (input == null) return;
        input.setTextColor(Color.rgb(105,245,255));
        input.setHintTextColor(Color.rgb(70,120,130));
        input.setSingleLine(true);
        input.setBackgroundResource(com.cb13.R.drawable.cockpit_input);
        input.setPadding(dp(input,14),dp(input,8),dp(input,14),dp(input,8));
    }

    public static void styleLabel(TextView label) {
        if (label == null) return;
        label.setTextColor(Color.rgb(90,220,235));
    }

    public static void drawNeonCore(Canvas canvas, Paint paint,
                                    float l, float t, float r, float b) {
        if (canvas == null || paint == null) return;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(80,245,255));
        paint.setShadowLayer(18f,0f,0f,Color.argb(150,0,235,255));
        canvas.drawRect(new RectF(l,t,r,b),paint);
        paint.clearShadowLayer();
        paint.setColor(Color.WHITE);
        canvas.drawRect(new RectF(l,t,r,b),paint);
    }

    private static int dp(View v, int value) {
        float d = v.getResources().getDisplayMetrics().density;
        return (int)(value*d+0.5f);
    }
}
