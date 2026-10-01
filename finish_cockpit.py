from pathlib import Path
import re, shutil, sys

ROOT = Path.cwd()
MAIN = ROOT / "app/src/main/java/com/cb13/MainActivity.java"
BACKUP = MAIN.with_suffix(".java.cockpit_backup")

if not MAIN.exists():
    print("ERROR: MainActivity.java not found")
    sys.exit(1)

src = MAIN.read_text()

if not BACKUP.exists():
    shutil.copy2(MAIN, BACKUP)

# ------------------------------------------------------------
# 1. Add cockpit imports
# ------------------------------------------------------------
imports = """
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.Space;
"""

marker = "import android.app.Activity;"
if marker in src and "import android.widget.FrameLayout;" not in src:
    src = src.replace(marker, marker + imports)

# ------------------------------------------------------------
# 2. Replace the classic onCreate UI section only.
#    Existing barcode logic, sliders, scanner and print methods
#    remain untouched.
# ------------------------------------------------------------
start = src.find("    @Override")
start = src.find("    protected void onCreate", start)

if start < 0:
    print("ERROR: onCreate not found")
    sys.exit(1)

brace = src.find("{", start)
depth = 0
end = -1

for i in range(brace, len(src)):
    if src[i] == "{":
        depth += 1
    elif src[i] == "}":
        depth -= 1
        if depth == 0:
            end = i + 1
            break

if end < 0:
    print("ERROR: cannot locate end of onCreate")
    sys.exit(1)

old_oncreate = src[start:end]

# Keep scanner setup and all existing field initialization by
# extracting everything before setContentView(root), then build
# a cockpit around the already-created controls.
m = re.search(r'(?s)(.*?)(?:\n\s*setContentView\(root\)\s*;)(.*)', old_oncreate)

if not m:
    print("ERROR: expected setContentView(root) not found")
    sys.exit(1)

prefix = m.group(1)

# Remove creation of the old root and its direct classic layout
# children only. Existing object/slider construction is retained.
prefix = re.sub(
    r'\s*LinearLayout\s+root\s*=\s*new\s+LinearLayout\(this\)\s*;\s*'
    r'root\.setOrientation\(LinearLayout\.VERTICAL\)\s*;',
    '\n        LinearLayout root = new LinearLayout(this);',
    prefix
)

cockpit = r'''
        // ============================================================
        // CB13 COCKPIT UI
        // Screen presentation only.
        // Barcode geometry and printer rendering are untouched.
        // ============================================================

        FrameLayout cockpit = new FrameLayout(this);
        cockpit.setBackgroundColor(Color.rgb(3,7,11));

        CockpitBackgroundView cockpitBg =
                new CockpitBackgroundView(this);
        cockpit.addView(cockpitBg,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));

        FrameLayout glass = new FrameLayout(this);
        glass.setBackground(new ColorDrawable(0x1600C8AE));

        FrameLayout.LayoutParams glassLp =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT);
        glassLp.gravity = Gravity.CENTER;
        glassLp.setMargins(35,55,35,55);

        cockpit.addView(glass, glassLp);

        // Existing preview remains the authoritative geometry.
        // No drawLogical() modification.
        if (preview != null) {
            preview.setBackgroundColor(Color.TRANSPARENT);
            preview.setAlpha(1f);

            FrameLayout.LayoutParams previewLp =
                    new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT);
            previewLp.gravity = Gravity.CENTER;
            previewLp.setMargins(45,70,45,70);

            glass.addView(preview, previewLp);
        }

        // ------------------------------------------------------------
        // EAN input HUD
        // ------------------------------------------------------------
        if (eanInput != null) {
            eanInput.setTextColor(0xFFE9FFFA);
            eanInput.setHintTextColor(0x8035F2C2);
            eanInput.setTextSize(20);
            eanInput.setGravity(Gravity.CENTER);
            eanInput.setSingleLine(true);
            eanInput.setBackgroundResource(com.cb13.R.drawable.cockpit_input);

            FrameLayout.LayoutParams inputLp =
                    new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            58);
            inputLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            inputLp.setMargins(110,12,110,0);

            cockpit.addView(eanInput,inputLp);
        }

        // ------------------------------------------------------------
        // Hide classic slider presentation.
        // Their values/logic remain alive.
        // ------------------------------------------------------------
        try {
            if (zoomBar != null) zoomBar.setVisibility(View.GONE);
            if (scaleXBar != null) scaleXBar.setVisibility(View.GONE);
            if (scaleYBar != null) scaleYBar.setVisibility(View.GONE);
            if (moveXBar != null) moveXBar.setVisibility(View.GONE);
            if (moveYBar != null) moveYBar.setVisibility(View.GONE);
            if (digitWidthBar != null) digitWidthBar.setVisibility(View.GONE);
            if (groupSpacingBar != null) groupSpacingBar.setVisibility(View.GONE);
            if (barHeightBar != null) barHeightBar.setVisibility(View.GONE);
            if (feedBar != null) feedBar.setVisibility(View.GONE);
        } catch (Throwable ignored) {}

        // ------------------------------------------------------------
        // Neon controls.
        // Existing SeekBars are still the source of truth.
        // ------------------------------------------------------------
        final int dialSize = 118;
        final int gap = 8;

        LinearLayout topDials = new LinearLayout(this);
        topDials.setOrientation(LinearLayout.HORIZONTAL);
        topDials.setGravity(Gravity.CENTER);

        LinearLayout bottomDials = new LinearLayout(this);
        bottomDials.setOrientation(LinearLayout.HORIZONTAL);
        bottomDials.setGravity(Gravity.CENTER);

        final NeonDialView[] dials = new NeonDialView[8];

        String[] labels = {
                "ZOOM","SCALE X","SCALE Y","MOVE X",
                "MOVE Y","DIGIT","GROUP","BAR"
        };

        SeekBar[] bars = {
                zoomBar,scaleXBar,scaleYBar,moveXBar,
                moveYBar,digitWidthBar,groupSpacingBar,barHeightBar
        };

        for (int i=0;i<8;i++) {
            final int n=i;

            NeonDialView d = new NeonDialView(this);
            d.setLabel(labels[i]);

            if (bars[i] != null) {
                int max = Math.max(1,bars[i].getMax());
                d.setValue((float)bars[i].getProgress()/max);
                d.setValueText(Integer.toString(bars[i].getProgress()));

                bars[i].setOnSeekBarChangeListener(
                        new SeekBar.OnSeekBarChangeListener() {
                            public void onProgressChanged(
                                    SeekBar s,int value,boolean fromUser) {
                                int max = Math.max(1,s.getMax());
                                d.setValue((float)value/max);
                                d.setValueText(Integer.toString(value));
                            }
                            public void onStartTrackingTouch(SeekBar s) {}
                            public void onStopTrackingTouch(SeekBar s) {}
                        });

                d.setOnValueChangedListener(
                        new NeonDialView.OnValueChangedListener() {
                            public void onValueChanged(
                                    NeonDialView view,
                                    float value,
                                    boolean fromUser) {
                                if (!fromUser) return;

                                int max = Math.max(1,bars[n].getMax());
                                int p = Math.round(value * max);

                                bars[n].setProgress(p);
                                view.setValueText(Integer.toString(p));

                                // Force the existing listener chain to run.
                                bars[n].performClick();
                            }
                        });
            }

            LinearLayout.LayoutParams dp =
                    new LinearLayout.LayoutParams(dialSize,dialSize);
            dp.setMargins(gap,2,gap,2);

            if (i < 4)
                topDials.addView(d,dp);
            else
                bottomDials.addView(d,dp);

            dials[i]=d;
        }

        FrameLayout.LayoutParams topLp =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        dialSize + 10);
        topLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        topLp.topMargin = 78;
        cockpit.addView(topDials,topLp);

        FrameLayout.LayoutParams bottomLp =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        dialSize + 10);
        bottomLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        bottomLp.bottomMargin = 62;
        cockpit.addView(bottomDials,bottomLp);

        // ------------------------------------------------------------
        // Existing buttons remain functional.
        // ------------------------------------------------------------
        LinearLayout commandBar = new LinearLayout(this);
        commandBar.setOrientation(LinearLayout.HORIZONTAL);
        commandBar.setGravity(Gravity.CENTER);

        if (scanButton != null) {
            scanButton.setText("SCAN");
            commandBar.addView(scanButton,
                    new LinearLayout.LayoutParams(150,58));
        }

        if (shareButton != null) {
            shareButton.setText("SHARE / PRINT");
            LinearLayout.LayoutParams bp =
                    new LinearLayout.LayoutParams(220,58);
            bp.setMargins(20,0,0,0);
            commandBar.addView(shareButton,bp);
        }

        FrameLayout.LayoutParams cmdLp =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,58);
        cmdLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        cmdLp.bottomMargin = 8;
        cockpit.addView(commandBar,cmdLp);

        setContentView(cockpit);

        // Cockpit is screen decoration only.
        // Printer path is deliberately untouched.
'''

new_oncreate = prefix + "\n" + cockpit + "\n"

src = src[:start] + new_oncreate + src[end:]

# ------------------------------------------------------------
# 3. If names differ, stop with a useful report instead of
#    silently producing broken Java.
# ------------------------------------------------------------
required = [
    "preview",
    "eanInput",
    "zoomBar",
    "scaleXBar",
    "scaleYBar",
    "moveXBar",
    "moveYBar",
    "digitWidthBar",
    "groupSpacingBar",
    "barHeightBar",
    "feedBar"
]

missing = [x for x in required if re.search(r'\b'+re.escape(x)+r'\b', src) is None]

if missing:
    print("ERROR: expected variables missing:", ", ".join(missing))
    print("Backup preserved:", BACKUP)
    sys.exit(2)

# ------------------------------------------------------------
# 4. Keep applicationId com.cb13.ui
# ------------------------------------------------------------
gradle = ROOT / "app/build.gradle"
if gradle.exists():
    g = gradle.read_text()
    g = re.sub(
        r"applicationId\s+['\"]com\.cb13['\"]",
        "applicationId 'com.cb13.ui'",
        g
    )
    gradle.write_text(g)

MAIN.write_text(src)

print("COCKPIT PATCH APPLIED")
print("BACKUP:", BACKUP)
