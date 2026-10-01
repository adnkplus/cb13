#!/usr/bin/env python3
from pathlib import Path
from datetime import datetime
import re, shutil, sys

ROOT = Path(__file__).resolve().parent
STAMP = datetime.now().strftime("%Y%m%d_%H%M%S")
BACKUP = ROOT / "_uiCB13_backups" / STAMP
JAVA = ROOT / "app/src/main/java/com/cb13/MainActivity.java"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
BEGIN = "// === CB13 COCKPIT INTEGRATION BEGIN ==="
END = "// === CB13 COCKPIT INTEGRATION END ==="

def die(s):
    print("\nERROR:", s)
    sys.exit(1)

def backup(p):
    if p.exists():
        q = BACKUP / p.relative_to(ROOT)
        q.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(p, q)

def write(p, s):
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(s, encoding="utf-8")

def add_import(s, imp):
    line = "import " + imp + ";"
    if line in s:
        return s
    pos = s.find("import ")
    if pos < 0:
        return s
    return s[:pos] + line + "\n" + s[pos:]

def detect_package(s):
    m = re.search(r'^\s*package\s+([A-Za-z0-9_.]+)\s*;', s, re.M)
    return m.group(1) if m else None

HELPER = r"""package com.cb13;

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
"""

README = r"""# uiCB13 cockpit integration

Independent cockpit branch of CB13.

Screen:
- transparent preview;
- dark HUD background;
- neon barcode/digits;
- circular neon controls;
- cockpit EAN input.

Printer:
- white background;
- black bars;
- black digits;
- no glow;
- no transparency.

Existing V2 geometry, scanner, gestures, sliders and printer pipeline remain
the source of truth.

Backups: `_uiCB13_backups/<timestamp>/`

Build:
`JAVA_HOME=/home/vortex/android-studio/jbr ./gradlew :app:assembleDebug`
"""

def patch_main():
    if not JAVA.exists():
        die("MainActivity.java not found.")
    s = JAVA.read_text(encoding="utf-8")
    if detect_package(s) != "com.cb13":
        die("Expected package com.cb13.")
    if BEGIN in s:
        print("MainActivity already contains cockpit marker; skipped.")
        return

    backup(JAVA)
    block = """
// === CB13 COCKPIT INTEGRATION BEGIN ===
private void cb13CockpitPrepareView(View preview, EditText eanInput) {
    CockpitIntegration.prepareTransparentPreview(preview);
    CockpitIntegration.styleInput(eanInput);
}
// === CB13 COCKPIT INTEGRATION END ===
"""
    s = add_import(s, "android.graphics.Color")
    s = add_import(s, "android.view.View")
    s = add_import(s, "android.widget.EditText")
    pos = s.rfind("}")
    if pos < 0:
        die("Cannot find end of MainActivity.")
    s = s[:pos] + "\n" + block + "\n" + s[pos:]
    JAVA.write_text(s, encoding="utf-8")
    print("Patched MainActivity.java")

def patch_manifest():
    if not MANIFEST.exists():
        die("AndroidManifest.xml not found.")
    s = MANIFEST.read_text(encoding="utf-8")
    if "androidx.core.content.FileProvider" in s:
        print("FileProvider already present.")
        return
    backup(MANIFEST)
    m = re.search(r"<application\b[^>]*>", s, re.S)
    if not m:
        die("Cannot find <application>.")
    provider = """
        <!-- CB13 cockpit/share FileProvider -->
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
"""
    MANIFEST.write_text(s[:m.end()] + provider + s[m.end():], encoding="utf-8")
    print("Added FileProvider.")

def main():
    if not ((ROOT/"settings.gradle").exists() or (ROOT/"settings.gradle.kts").exists()):
        die("Run this generator from the uiCB13 project root.")

    required = [
        ROOT/"app/src/main/java/com/cb13/NeonDialView.java",
        ROOT/"app/src/main/java/com/cb13/CockpitBackgroundView.java",
        ROOT/"app/src/main/java/com/cb13/NeonBarcodeRenderer.java",
        ROOT/"app/src/main/res/drawable/cockpit_panel.xml",
        ROOT/"app/src/main/res/drawable/cockpit_button.xml",
        ROOT/"app/src/main/res/drawable/cockpit_input.xml",
        ROOT/"app/src/main/res/drawable/neon_dial_bg.xml",
    ]
    missing = [str(x.relative_to(ROOT)) for x in required if not x.exists()]
    if missing:
        die("Missing cockpit files:\n  " + "\n  ".join(missing))

    BACKUP.mkdir(parents=True, exist_ok=True)

    helper = ROOT/"app/src/main/java/com/cb13/CockpitIntegration.java"
    if not helper.exists():
        write(helper, HELPER)
        print("Created CockpitIntegration.java")

    paths = ROOT/"app/src/main/res/xml/file_paths.xml"
    if not paths.exists():
        write(paths, """<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <cache-path name="cb13_cache" path="cb13/" />
</paths>
""")
        print("Created file_paths.xml")

    patch_main()
    patch_manifest()
    write(ROOT/"COCKPIT_NEXT_STEPS.md", README)

    print("\n============================================================")
    print("uiCB13 cockpit integration finished.")
    print("Only uiCB13 was modified.")
    print("Backup:", BACKUP)
    print("Build:")
    print("JAVA_HOME=/home/vortex/android-studio/jbr ./gradlew :app:assembleDebug")
    print("============================================================")

if __name__ == "__main__":
    main()
