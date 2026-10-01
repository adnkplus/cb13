from pathlib import Path
import re, shutil, sys

p=Path("app/src/main/java/com/cb13/MainActivity.java")
s=p.read_text()
bak=p.with_name("MainActivity.java.before_real_cockpit")

if not bak.exists():
    shutil.copy2(p,bak)

# Remove previous cockpit wrapper completely by restoring the pre-cockpit source.
old=s
if "CB13 COCKPIT LIVE" in s:
    s=s.replace(
'''        // === CB13 COCKPIT LIVE ===''',
'''        // === CB13 COCKPIT LIVE ==='''
    )
    # restore from backup if available
    s=bak.read_text()

# Imports
imports=[
"import android.graphics.Color;",
"import android.graphics.drawable.GradientDrawable;",
"import android.view.Gravity;",
"import android.widget.FrameLayout;",
]
for imp in imports:
    if imp not in s:
        pos=s.find("import ")
        # insert after last import
        matches=list(re.finditer(r'^import .*?;\s*$',s,re.M))
        s=s[:matches[-1].end()]+"\n"+imp+s[matches[-1].end():]

# Find onCreate exactly
m=re.search(r'@Override\s+public\s+void\s+onCreate\s*\(\s*Bundle\s+b\s*\)\s*\{',s)
if not m:
    print("ERROR onCreate")
    sys.exit(1)

brace=s.find("{",m.start())
depth=0
end=None
for i in range(brace,len(s)):
    if s[i]=="{": depth+=1
    elif s[i]=="}":
        depth-=1
        if depth==0:
            end=i+1
            break

if end is None:
    print("ERROR onCreate end")
    sys.exit(1)

oc=s[m.start():end]

# Existing UI is retained until its final setContentView(root).
x=oc.find("setContentView(root);")
if x<0:
    print("ERROR setContentView(root)")
    sys.exit(1)

# Everything before final setContentView remains: all initialization,
# scanner wiring, barcode listeners and existing controls.
prefix=oc[:x]

cockpit=r'''
        // ============================================================
        // REAL CB13 COCKPIT
        // ============================================================

        FrameLayout cockpit = new FrameLayout(this);
        cockpit.setBackgroundColor(0xFF02060A);

        CockpitBackgroundView bg = new CockpitBackgroundView(this);
        cockpit.addView(bg,new FrameLayout.LayoutParams(-1,-1));

        // Existing root is never displayed as the classic UI.
        // Its controls remain alive and continue driving the engine.
        if (root.getParent() instanceof android.view.ViewGroup)
            ((android.view.ViewGroup)root.getParent()).removeView(root);

        root.setVisibility(View.INVISIBLE);
        cockpit.addView(root,new FrameLayout.LayoutParams(1,1));

        // ---------------- CENTRAL GLASS ----------------
        FrameLayout glass = new FrameLayout(this);

        GradientDrawable glassBg = new GradientDrawable();
        glassBg.setColor(0x18000E14);
        glassBg.setStroke(2,0x6035F2C2);
        glass.setBackground(glassBg);

        FrameLayout.LayoutParams glp =
                new FrameLayout.LayoutParams(-1,-1);
        glp.setMargins(120,235,120,250);
        cockpit.addView(glass,glp);

        // ---------------- PREVIEW ----------------
        if (preview != null) {
            preview.setVisibility(View.VISIBLE);
            preview.setBackgroundColor(Color.TRANSPARENT);

            FrameLayout.LayoutParams pp =
                    new FrameLayout.LayoutParams(-1,-1);
            pp.gravity=Gravity.CENTER;
            glass.addView(preview,pp);
        }

        // ---------------- EAN FIELD ----------------
        if (eanInput != null) {
            eanInput.setVisibility(View.VISIBLE);
            eanInput.setTextColor(0xFFE9FFFA);
            eanInput.setTextSize(21);
            eanInput.setGravity(Gravity.CENTER);
            eanInput.setSingleLine(true);
            eanInput.setBackgroundResource(R.drawable.cockpit_input);

            FrameLayout.LayoutParams ep =
                    new FrameLayout.LayoutParams(-1,64);
            ep.setMargins(180,18,180,0);
            ep.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;
            cockpit.addView(eanInput,ep);
        }

        // ---------------- DIAL FACTORY ----------------
        final int DS=150;

        NeonDialView dZoom=new NeonDialView(this);
        NeonDialView dSX=new NeonDialView(this);
        NeonDialView dSY=new NeonDialView(this);
        NeonDialView dMX=new NeonDialView(this);
        NeonDialView dMY=new NeonDialView(this);
        NeonDialView dDW=new NeonDialView(this);
        NeonDialView dGS=new NeonDialView(this);
        NeonDialView dBH=new NeonDialView(this);

        NeonDialView[] ds={
            dZoom,dSX,dSY,dMX,dMY,dDW,dGS,dBH
        };

        String[] names={
            "ZOOM","SCALE X","SCALE Y","MOVE X",
            "MOVE Y","DIGIT","GROUP","BAR"
        };

        SeekBar[] sb={
            zoomBar,scaleXBar,scaleYBar,moveXBar,
            moveYBar,digitWidthBar,groupSpacingBar,barHeightBar
        };

        for(int i=0;i<ds.length;i++){
            final NeonDialView d=ds[i];
            final SeekBar sbar=sb[i];
            final int n=i;

            d.setLabel(names[i]);

            if(sbar!=null){
                int mx=Math.max(1,sbar.getMax());
                d.setValue((float)sbar.getProgress()/mx);
                d.setValueText(Integer.toString(sbar.getProgress()));

                sbar.setVisibility(View.GONE);

                d.setOnValueChangedListener(
                    new NeonDialView.OnValueChangedListener(){
                        @Override public void onValueChanged(
                                NeonDialView v,float value,boolean user){
                            if(!user || sbar==null)return;
                            int mx=Math.max(1,sbar.getMax());
                            int val=Math.round(value*mx);
                            sbar.setProgress(val);
                            v.setValueText(Integer.toString(val));
                        }
                    });
            }
        }

        // ---------------- DIAL POSITIONING ----------------
        int[][] xy={
            {8,300},{8,475},
            {8,650},{8,825},
            {-1,300},{-1,475},
            {-1,650},{-1,825}
        };

        for(int i=0;i<4;i++){
            FrameLayout.LayoutParams q=
                    new FrameLayout.LayoutParams(DS,DS);
            q.leftMargin=12;
            q.topMargin=270+i*165;
            cockpit.addView(ds[i],q);
        }

        for(int i=4;i<8;i++){
            FrameLayout.LayoutParams q=
                    new FrameLayout.LayoutParams(DS,DS);
            q.gravity=Gravity.TOP|Gravity.RIGHT;
            q.rightMargin=12;
            q.topMargin=270+(i-4)*165;
            cockpit.addView(ds[i],q);
        }

        // ---------------- COMMAND BUTTONS ----------------
        LinearLayout commands=new LinearLayout(this);
        commands.setOrientation(LinearLayout.HORIZONTAL);
        commands.setGravity(Gravity.CENTER);

        // Reuse the original command buttons already wired by onCreate.
        for(int i=0;i<root.getChildCount();i++){
            View v=root.getChildAt(i);
            if(v instanceof Button){
                Button b=(Button)v;
                String t=b.getText()==null?"":b.getText().toString();
                if(t.contains("SCAN")||t.contains("PRINT")||t.contains("SHARE")){
                    b.setVisibility(View.VISIBLE);
                    b.setText(t.toUpperCase());
                    b.setTextColor(0xFF35F2C2);
                    b.setBackgroundResource(R.drawable.cockpit_button);
                    commands.addView(b,new LinearLayout.LayoutParams(190,58));
                }
            }
        }

        FrameLayout.LayoutParams cp=
                new FrameLayout.LayoutParams(-2,62);
        cp.gravity=Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL;
        cp.bottomMargin=18;
        cockpit.addView(commands,cp);

        // ---------------- HUD MARKINGS ----------------
        TextView status=new TextView(this);
        status.setText("CB13  /  EAN-13  /  COCKPIT");
        status.setTextColor(0x8035F2C2);
        status.setTextSize(11);
        status.setTypeface(android.graphics.Typeface.MONOSPACE);
        status.setGravity(Gravity.CENTER);

        FrameLayout.LayoutParams st=
                new FrameLayout.LayoutParams(-1,30);
        st.gravity=Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL;
        st.bottomMargin=92;
        cockpit.addView(status,st);

        setContentView(cockpit);
'''

newoc=prefix+cockpit+"\n    }"
s=s[:m.start()]+newoc+s[end:]

# Remove old cockpit helper; it is no longer needed.
s=re.sub(
r'\s*// === CB13 COCKPIT INTEGRATION BEGIN ===.*?// === CB13 COCKPIT INTEGRATION END ===\s*',
'\n',
s,flags=re.S)

p.write_text(s)
print("REAL COCKPIT INSTALLED")
