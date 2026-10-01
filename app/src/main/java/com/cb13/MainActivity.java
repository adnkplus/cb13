package com.cb13;
import android.widget.EditText;
import android.provider.Settings;
import android.net.Uri;
import android.view.Gravity;
import android.text.InputType;

import java.io.File;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayDeque;
import android.os.Environment;
import android.os.Build;
import android.content.SharedPreferences;
import android.app.AlertDialog;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.*;
import android.graphics.drawable.ColorDrawable;
import androidx.core.content.ContextCompat;
import androidx.core.app.ActivityCompat;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.graphics.PathParser;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.PlanarYUVLuminanceSource;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.view.ViewGroup;

import android.graphics.drawable.GradientDrawable;
import android.widget.FrameLayout;
public class MainActivity extends Activity implements LifecycleOwner {
    static class SliderCtl{
        String name; int min,max,index; SeekBar bar; TextView valueView;
        float current,normal,center; boolean fine;
        SliderCtl(String n,int mn,int mx,SeekBar b,TextView v,int i){
            name=n;min=mn;max=mx;bar=b;valueView=v;index=i;current=normal=center=mn;
        }
        float valueFromProgress(int p){
            if(!fine)return min+(max-min)*(p/1000f);
            float span=Math.max(0.01f,Math.abs(center)*.05f);
            if(index==3||index==4)span=5f;
            return Math.max(min,Math.min(max,center-span+2f*span*(p/1000f)));
        }
        int progressFromNormalValue(float v){
            return Math.max(0,Math.min(1000,Math.round((v-min)*1000f/(max-min))));
        }
    }
    private final LifecycleRegistry lifecycleRegistry = new LifecycleRegistry(this);
    @Override public Lifecycle getLifecycle() {
        return lifecycleRegistry;
    }


    @Override protected void onStart() {
        super.onStart();
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START);
    }


    @Override protected void onPause() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE);
        super.onPause();
    }

    @Override protected void onStop() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP);
        super.onStop();
    }

    @Override protected void onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY);
        super.onDestroy();
    }

    static final String PRINTER_PACKAGE = "com.farminos.print";
    BarcodeView preview;
    EditText input;
    ExecutorService cameraExecutor;
    boolean isScannerActive=false;
    Camera activeCamera;
    static final int REQ_CAMERA=4107;
    boolean shift = false;
    final SliderCtl[] sliders = new SliderCtl[9];
    final float[] fineCenter = new float[9];
    boolean suppressSeekCallbacks=false;
    SharedPreferences prefs;
    final ArrayDeque<String> inputHistory=new ArrayDeque<>();
    String fieldEntryState="";
    int lastDigitIndex=-1;
    char lastDigitOld=' ';
    Button autoBtn,prevDigitBtn,prevStateBtn,profileBtn;
    final NeonDialView[] fineDials = new NeonDialView[9];
    TextView status;
    static final int NEON_GREEN=0xFF35F2C2;
    static final int NEON_BLUE=0xFF3399FF;

    @Override protected void onResume(){
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME);
        super.onResume();
        // Do not reopen the numeric keyboard when returning from the print/share app.
    }

    @Override public void onCreate(Bundle b) {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE);
        super.onCreate(b);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(10,8,10,8);
        root.setBackgroundColor(0xFF050B0F);

        TextView title=new TextView(this);
        title.setText("CB13 • EAN-13 • FIX 812 / SCAN");
        title.setTextSize(20);
        title.setTextColor(0xFF35F2C2);
        title.setTypeface(Typeface.create(Typeface.MONOSPACE,Typeface.BOLD));
        root.addView(title,new LinearLayout.LayoutParams(-1,42));

        prefs=getSharedPreferences("cb13_settings",MODE_PRIVATE);
        LinearLayout inputRow=new LinearLayout(this);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        input=new EditText(this);
        input.setTextSize(24);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setMaxLines(1);
        input.setPadding(12,0,12,0);
        input.setTextColor(0xFFEAFBFF);
        input.setHintTextColor(0xFF60747A);
        input.setBackground(new ColorDrawable(0xFF18252B));
        String savedEan=prefs.getString("ean","5901234123457");
        input.setText(savedEan.replaceAll("[^0-9]",""));
        input.setSelection(Math.min(input.length(),prefs.getInt("cursor",input.length())));
        input.addTextChangedListener(new android.text.TextWatcher(){
            String old=""; int start=0;
            public void beforeTextChanged(CharSequence s,int st,int count,int after){old=s.toString();start=st;}
            public void onTextChanged(CharSequence s,int st,int before,int count){}
            public void afterTextChanged(android.text.Editable e){
                String now=e.toString().replaceAll("[^0-9]","");
                if(!now.equals(e.toString())){ input.setText(now); input.setSelection(Math.min(now.length(),start)); return; }
                if(now.length()>0 && !now.equals(old)){
                    if(inputHistory.size()>=30) inputHistory.removeFirst();
                    inputHistory.addLast(old);
                }
                if(preview!=null) preview.setEan(now);
                saveSettings(); updateChecksumButtons();
            }
        });
        input.setOnFocusChangeListener((v,has)->{ if(!has) saveSettings(); });
        inputRow.addView(input,new LinearLayout.LayoutParams(0,54,1f));
        prevDigitBtn=new Button(this); prevDigitBtn.setText("<"); prevDigitBtn.setMinWidth(44); prevDigitBtn.setOnClickListener(v->restorePreviousDigit());
        prevStateBtn=new Button(this); prevStateBtn.setText("<<"); prevStateBtn.setMinWidth(50); prevStateBtn.setOnClickListener(v->restorePreviousState());
        autoBtn=new Button(this); autoBtn.setText("Auto"); autoBtn.setMinWidth(62); autoBtn.setOnClickListener(v->autoChecksum());
        profileBtn=new Button(this); profileBtn.setText("⋮"); profileBtn.setMinWidth(48); profileBtn.setOnClickListener(v->showProfilesMenu());
        Button scanBtn=new Button(this); scanBtn.setText("SCAN"); scanBtn.setMinWidth(62); scanBtn.setOnClickListener(v->requestScanner());
        inputRow.addView(prevDigitBtn); inputRow.addView(prevStateBtn); inputRow.addView(autoBtn); inputRow.addView(scanBtn); inputRow.addView(profileBtn);
        root.addView(inputRow);

        preview=new BarcodeView(this);
        preview.setEan(getEan());
        LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(-1,0,1f);
        vp.topMargin=6; vp.bottomMargin=6;
        root.addView(preview,vp);

        addSlider(root,0,"Zoom",10,300,100);
        addSlider(root,1,"Scale X",10,300,100);
        addSlider(root,2,"Scale Y",10,300,100);
        addSlider(root,3,"Move X",0,100,50);
        addSlider(root,4,"Move Y",0,100,50);
        addSlider(root,5,"Digit width",10,300,100);
        addSlider(root,6,"Group spacing",0,300,100);
        addSlider(root,7,"Bar height",10,300,100);
        addSlider(root,8,"Feed",0,15,0);
        loadSettings();

        LinearLayout bottom=new LinearLayout(this);
        Switch sw=new Switch(this);
        sw.setText("Shift fine tune");
        sw.setChecked(false);
        sw.setOnCheckedChangeListener((button,checked)->setShift(checked));
        bottom.addView(sw,new LinearLayout.LayoutParams(0,52,1f));

        Button scanCmd = new Button(this);
        scanCmd.setText("SCAN");
        scanCmd.setOnClickListener(v -> startScanner());
        styleCockpitButton(scanCmd);

        bottom.addView(scanCmd,
                new LinearLayout.LayoutParams(0,52,1f));

        Button share=new Button(this);
        share.setText("SHARE / PRINT");
        share.setOnClickListener(v->shareBitmap(!shift));
        bottom.addView(share,new LinearLayout.LayoutParams(0,52,1f));
        root.addView(bottom);
        
        // ============================================================
        // REAL CB13 COCKPIT
        // ============================================================
        FrameLayout cockpit = new FrameLayout(this);
        cockpit.setBackgroundColor(0xFF02060A);

        CockpitBackgroundView bg = new CockpitBackgroundView(this);
        bg.setClickable(false);
        bg.setFocusable(false);
        FrameLayout.LayoutParams bgp=new FrameLayout.LayoutParams(-1,-1);
        cockpit.addView(bg,bgp);

        // Original controls stay alive in a tiny invisible root and continue
        // driving the existing engine/settings code.
        if (root.getParent() instanceof android.view.ViewGroup)
            ((android.view.ViewGroup)root.getParent()).removeView(root);
        root.setVisibility(View.INVISIBLE);
        cockpit.addView(root,new FrameLayout.LayoutParams(1,1));

        // ---------------- EAN ----------------
        FrameLayout glass = new FrameLayout(this);
        GradientDrawable glassBg = new GradientDrawable();
        glassBg.setColor(0x18000E14);
        glassBg.setStroke(2,0x6035F2C2);
        glass.setBackground(glassBg);
        FrameLayout.LayoutParams glp=new FrameLayout.LayoutParams(-1,-1);
        glp.setMargins(70,205,70,255);
        cockpit.addView(glass,glp);

        if(preview!=null){
            if(preview.getParent() instanceof ViewGroup)
                ((ViewGroup)preview.getParent()).removeView(preview);

            preview.setVisibility(View.VISIBLE);
            preview.setBackgroundColor(Color.TRANSPARENT);

            FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(-1,-1);
            pp.gravity=Gravity.CENTER;
            glass.addView(preview,pp);
        }

        if(input!=null){
            if(input.getParent() instanceof ViewGroup)
                ((ViewGroup)input.getParent()).removeView(input);

            input.setVisibility(View.VISIBLE);
            input.setTextColor(0xFFE9FFFA);
            input.setTextSize(21);
            input.setGravity(Gravity.CENTER);
            input.setSingleLine(true);
            input.setBackgroundResource(R.drawable.cockpit_input);
            FrameLayout.LayoutParams ep=new FrameLayout.LayoutParams(-1,60);
            ep.setMargins(155,12,155,0);
            ep.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;
            cockpit.addView(input,ep);
        }

        // ---------------- DIALS ----------------
        final int DS=118;
        final NeonDialView[] ds={
            new NeonDialView(this),new NeonDialView(this),
            new NeonDialView(this),new NeonDialView(this),
            new NeonDialView(this),new NeonDialView(this),
            new NeonDialView(this),new NeonDialView(this),
            new NeonDialView(this)
        };
        final String[] names={
            "ZOOM","SCALE X","SCALE Y","MOVE X","MOVE Y",
            "DIGIT","GROUP","BAR","FEED"
        };
        for(int i=0;i<9;i++){
            fineDials[i]=ds[i];
            ds[i].setLabel(names[i]);
            ds[i].setNeonColor(NEON_GREEN);
            ds[i].setValueText(formatValue(i,sliders[i].current));
            ds[i].setValue(sliderNormalized(i));
            ds[i].setClickable(false); // display only: touch belongs to the transparent zone
            ds[i].setFocusable(false);
        }

        // Layout requested:
        // top:  SCALE X / SCALE Y, MOVE X / MOVE Y
        // middle: barcode glass
        // lower: ZOOM / FEED / GROUP, DIGIT / BAR
        final int cx= getResources().getDisplayMetrics().widthPixels;
        final int gap=18;
        final int pairW=DS*2+gap;

        // ============================================================
        // COCKPIT GEOMETRY
        // ============================================================

        // Upper columns move 1.5 dial widths away from the vertical centre.
        final int upperLeft =
                (cx-pairW)/2 - (int)(DS*1.5f);

        final int upperRight =
                (cx-pairW)/2 + DS + gap + (int)(DS*1.5f);

        // Upper four dials: +0.5 dial height.
        final int scaleYPos = 95 + (int)(DS*0.5f);

        // MOVE X/Y: additional +0.25 dial height.
        final int moveYPos =
                scaleYPos + DS + (int)(DS*0.25f);

        addCockpitDial(cockpit,ds[1],1,upperLeft,scaleYPos,DS);
        addCockpitDial(cockpit,ds[2],2,upperRight,scaleYPos,DS);

        addCockpitDial(cockpit,ds[3],3,upperLeft,moveYPos,DS);
        addCockpitDial(cockpit,ds[4],4,upperRight,moveYPos,DS);

        // Lower five: central FEED stays centered.
        // Side columns move one complete dial width farther away.
        final int lowerLeft =
                (cx-pairW)/2 - DS;

        final int lowerRight =
                (cx-pairW)/2 + DS + gap + DS;

        final int lowerRow1 = 1555 + DS + DS;
        final int lowerRow2 = 1680 + DS + DS;

        addCockpitDial(cockpit,ds[0],0,lowerLeft,lowerRow1,DS);
        addCockpitDial(cockpit,ds[8],8,cx/2-DS/2,lowerRow1,DS);
        addCockpitDial(cockpit,ds[6],6,lowerRight,lowerRow1,DS);

        addCockpitDial(cockpit,ds[5],5,lowerLeft,lowerRow2,DS);
        addCockpitDial(cockpit,ds[7],7,lowerRight,lowerRow2,DS);

        // ---------------- TOP COMMANDS ----------------
        LinearLayout top=new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(12,0,12,0);
        Button bPrevDigit=new Button(this); bPrevDigit.setText("<");
        bPrevDigit.setOnClickListener(v->restorePreviousDigit());
        Button bEan=new Button(this); bEan.setVisibility(View.INVISIBLE);
        Button bPrevState=new Button(this); bPrevState.setText("<<");
        bPrevState.setOnClickListener(v->restorePreviousState());
        styleCockpitButton(bPrevDigit); styleCockpitButton(bPrevState);
        top.addView(bPrevDigit,new LinearLayout.LayoutParams(58,58));
        Space sp1=new Space(this); top.addView(sp1,new LinearLayout.LayoutParams(0,1,1f));
        Space sp2=new Space(this); top.addView(sp2,new LinearLayout.LayoutParams(0,1,1f));
        top.addView(bPrevState,new LinearLayout.LayoutParams(58,58));
        FrameLayout.LayoutParams tp=new FrameLayout.LayoutParams(-1,64);
        tp.gravity=Gravity.TOP; tp.topMargin=8;
        cockpit.addView(top,tp);

        // ---------------- FIRE = SHARE / PRINT ----------------
        Button fire=new Button(this);
        fire.setText("FIRE");
        fire.setOnClickListener(v->shareBitmap(!shift));
        styleCockpitButton(fire);
        // FIRE just above the bottom command bar.
        FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(-1,87);
        fp.setMargins(105,0,105,0);
        fp.gravity=Gravity.BOTTOM;
        fp.bottomMargin=139;
        cockpit.addView(fire,fp);

        // ---------------- BOTTOM COMMANDS ----------------
        LinearLayout commands=new LinearLayout(this);
        commands.setOrientation(LinearLayout.HORIZONTAL);
        commands.setGravity(Gravity.CENTER);

        Button scan=new Button(this);
        scan.setText("SCAN");
        scan.setOnClickListener(v->startScanner());

        Button tune=new Button(this);
        tune.setText("TUNE");
        tune.setOnClickListener(v->setShift(!shift));

        Button auto=new Button(this);
        auto.setText("AUTO");
        auto.setOnClickListener(v->autoChecksum());

        Button menu=new Button(this);
        menu.setText("⋮");
        menu.setOnClickListener(v->showProfilesMenu());

        styleCockpitButton(scan);
        styleCockpitButton(tune);
        styleCockpitButton(auto);
        styleCockpitButton(menu);

        commands.addView(scan,new LinearLayout.LayoutParams(0,58,2.5f));
        commands.addView(tune,new LinearLayout.LayoutParams(0,58,1f));
        commands.addView(auto,new LinearLayout.LayoutParams(0,58,1f));
        commands.addView(menu,new LinearLayout.LayoutParams(0,58,.65f));

        // Bottom command bar
        FrameLayout.LayoutParams cp =
                new FrameLayout.LayoutParams(-1,58);
        cp.gravity = Gravity.BOTTOM;
        cp.setMargins(55,0,55,75);
        cockpit.addView(commands,cp);

        // Keep a small status line above the command bar
        status = new TextView(this);
        status.setText("NEW");
        status.setTextColor(0x8035F2C2);
        status.setTextSize(10);
        status.setGravity(Gravity.CENTER);
        status.setTypeface(Typeface.MONOSPACE);

        FrameLayout.LayoutParams st =
                new FrameLayout.LayoutParams(-1,30);
        st.gravity = Gravity.BOTTOM;
        st.bottomMargin = 40;
        cockpit.addView(status,st);

        // THIS WAS MISSING: actually display the cockpit
        setContentView(cockpit);

    }

    void styleCockpitButton(Button b){
        b.setTextColor(NEON_GREEN);
        b.setTextSize(14);
        b.setAllCaps(true);
        b.setBackgroundResource(R.drawable.cockpit_button);
        b.setMinWidth(0);
        b.setMinHeight(0);
    }

    float sliderNormalized(int i){
        SliderCtl s=sliders[i];
        if(s==null)return 0.5f;
        float p;
        if(s.fine){
            float span=(i==3||i==4)?5f:Math.max(0.01f,Math.abs(s.center)*0.05f);
            float lo=s.center-span;
            float hi=s.center+span;
            p=(s.current-lo)/Math.max(0.01f,hi-lo);
        }else{
            p=(s.current-s.min)/Math.max(0.01f,s.max-s.min);
        }
        return Math.max(0f,Math.min(1f,p));
    }

    void addCockpitDial(FrameLayout cockpit, NeonDialView dial, int index, int x, int y, int size){
        // The NeonDial is DISPLAY ONLY. The transparent zone below is the real fader.
        dial.setClickable(false);
        dial.setFocusable(false);
        FrameLayout.LayoutParams dp=new FrameLayout.LayoutParams(size,size);
        dp.leftMargin=x; dp.topMargin=y;
        cockpit.addView(dial,dp);

        final View zone=new View(this);
        zone.setBackgroundColor(Color.TRANSPARENT);
        FrameLayout.LayoutParams zp=new FrameLayout.LayoutParams(size,size);
        zp.leftMargin=x; zp.topMargin=y;
        cockpit.addView(zone,zp);

        final float[] downY={0f};
        final float[] startValue={0f};
        final boolean[] moved={false};

        zone.setOnTouchListener((v,e)->{
            SliderCtl ctl=sliders[index];
            if(ctl==null)return true;
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    downY[0]=e.getY();
                    startValue[0]=ctl.current;
                    moved[0]=false;
                    // NEVER snap the fader or witness to the finger position.
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dy=e.getY()-downY[0];
                    if(Math.abs(dy)>3f)moved[0]=true;

                    float range;
                    if(ctl.fine){
                        float span=(index==3||index==4)?5f:
                                Math.max(0.01f,Math.abs(ctl.center)*0.05f);
                        range=2f*span;
                    }else{
                        range=ctl.max-ctl.min;
                    }

                    // Relative vertical fader: finger travel changes the value
                    // from the value that existed at ACTION_DOWN.
                    float value=startValue[0]-
                            (dy/Math.max(1f,v.getHeight()))*range;
                    setCockpitSliderValue(index,value,true);
                    return true;

                case MotionEvent.ACTION_UP:
                    if(!moved[0] && shift && index<8)
                        toggleFineTuneDial(index);
                    v.performClick();
                    return true;

                case MotionEvent.ACTION_CANCEL:
                    return true;
            }
            return true;
        });
    }

    void setCockpitSliderValue(int i,float value,boolean save){
        SliderCtl ctl=sliders[i];
        if(ctl==null)return;
        ctl.current=Math.max(ctl.min,Math.min(ctl.max,value));
        if(!ctl.fine)ctl.normal=ctl.current;
        suppressSeekCallbacks=true;
        // Keep the hidden SeekBar synchronized without letting it alter the value.
        ctl.bar.setProgress(ctl.progressFromNormalValue(ctl.current),false);
        suppressSeekCallbacks=false;
        if(fineDials[i]!=null){
            // Witness follows the fader value only. It has no independent state.
            fineDials[i].setValue(sliderNormalized(i));
            fineDials[i].setValueText(formatValue(i,ctl.current));
        }
        applySliders();
        if(save)saveSettings();
    }

    void toggleFineTuneDial(int i){
        if(!shift || i<0 || i>=8 || sliders[i]==null)return;
        SliderCtl ctl=sliders[i];

        if(!ctl.fine){
            for(int k=0;k<8;k++){
                if(sliders[k]!=null){
                    sliders[k].fine=false;
                    sliders[k].normal=sliders[k].current;
                }
                if(fineDials[k]!=null)fineDials[k].setNeonColor(NEON_GREEN);
            }

            // Enter Fine Tune at the EXACT current fader value.
            ctl.center=ctl.current;
            ctl.fine=true;

            // The hidden fader is centered around the current value, but its
            // visible witness is NOT moved by entering Fine Tune.
            suppressSeekCallbacks=true;
            ctl.bar.setProgress(500,false);
            suppressSeekCallbacks=false;
            fineDials[i].setNeonColor(NEON_BLUE);
            fineDials[i].setValueText(formatValue(i,ctl.current));
        }else{
            // Leave Fine Tune exactly where the user stopped.
            ctl.fine=false;
            ctl.normal=ctl.current;
            suppressSeekCallbacks=true;
            ctl.bar.setProgress(ctl.progressFromNormalValue(ctl.current),false);
            suppressSeekCallbacks=false;
            fineDials[i].setNeonColor(NEON_GREEN);
            fineDials[i].setValue(sliderNormalized(i));
            fineDials[i].setValueText(formatValue(i,ctl.current));
        }
        preview.invalidate();
        saveSettings();
    }

    String getEan(){ return input==null ? "" : input.getText().toString().replaceAll("[^0-9]",""); }
    void setEan(String value){
        if(input==null)return;
        String v=value==null?"":value.replaceAll("[^0-9]","");
        if(v.length()>13)v=v.substring(0,13);
        input.setText(v); input.setSelection(input.length());
        if(preview!=null)preview.setEan(v); updateChecksumButtons(); saveSettings();
    }
    String expectedFromFirst12(String v){
        StringBuilder d=new StringBuilder(); for(int i=0;i<12;i++) d.append(i<v.length()?v.charAt(i):'0');
        return d.toString()+preview.checksum(d.toString());
    }
    void requestScanner(){
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.CAMERA},REQ_CAMERA); return;
        }
        startScanner();
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode==REQ_CAMERA && grantResults.length>0 && grantResults[0]==PackageManager.PERMISSION_GRANTED) startScanner();
    }
    void startScanner(){
        if(isScannerActive)return; isScannerActive=true;
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(Color.BLACK);

        final PreviewView previewView=new PreviewView(this);
        FrameLayout previewWrapper=new FrameLayout(this);
        previewWrapper.addView(previewView,new FrameLayout.LayoutParams(-1,-1));
        final View zoomOverlay=new View(this);
        previewWrapper.addView(zoomOverlay,new FrameLayout.LayoutParams(-1,-1));
        box.addView(previewWrapper,new LinearLayout.LayoutParams(-1,0,1f));

        TextView hint=new TextView(this);
        hint.setText("POINT AT EAN-13 BARCODE");
        hint.setTextColor(0xFF5CFFB0);
        hint.setGravity(Gravity.CENTER);
        hint.setTextSize(14);
        box.addView(hint,new LinearLayout.LayoutParams(-1,54));

        final AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle("Scan EAN-13")
                .setView(box)
                .setNegativeButton("Cancel",(d,w)->{
                    isScannerActive=false;
                    if(cameraExecutor!=null){cameraExecutor.shutdownNow();cameraExecutor=null;}
                })
                .create();
        dialog.show();
        cameraExecutor=Executors.newSingleThreadExecutor();
        MultiFormatReader reader=new MultiFormatReader();
        reader.setHints(java.util.Collections.singletonMap(DecodeHintType.POSSIBLE_FORMATS,java.util.Collections.singletonList(BarcodeFormat.EAN_13)));
        ImageAnalysis analysis=new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build();

        final float[] targetZoom={1.0f};
        final float[] lastY={0f};
        final CameraControl[] ctrlRef={null};
        final float[] maxZoom={10.0f};
        final boolean[] ready={false};
        final long[] lastZoomTime={0};

        zoomOverlay.setOnTouchListener((v,e)->{
            if(ctrlRef[0]==null||!ready[0])return true;
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    lastY[0]=e.getY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dy=lastY[0]-e.getY();
                    lastY[0]=e.getY();
                    targetZoom[0]+=dy*0.005f;
                    targetZoom[0]=Math.max(1.0f,Math.min(targetZoom[0],maxZoom[0]));
                    long now=System.currentTimeMillis();
                    if(now-lastZoomTime[0]>50){
                        lastZoomTime[0]=now;
                        try{ctrlRef[0].setZoomRatio(targetZoom[0]);}catch(Exception ignored){}
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    return true;
            }
            return false;
        });

        analysis.setAnalyzer(cameraExecutor,imageProxy->{
            try{
                ByteBuffer buffer=imageProxy.getPlanes()[0].getBuffer();
                byte[] data=new byte[buffer.remaining()]; buffer.get(data);
                PlanarYUVLuminanceSource source=new PlanarYUVLuminanceSource(data,imageProxy.getWidth(),imageProxy.getHeight(),0,0,imageProxy.getWidth(),imageProxy.getHeight(),false);
                Result result=reader.decode(new BinaryBitmap(new HybridBinarizer(source)));
                String code=result.getText();
                if(code!=null && code.matches("\\d{13}")){ runOnUiThread(()->{ setEan(code); dialog.dismiss(); }); }
            }catch(NotFoundException ignored){}catch(Exception ignored){}finally{imageProxy.close();}
        });

        ProcessCameraProvider.getInstance(this).addListener(()->{
            try{
                ProcessCameraProvider provider=ProcessCameraProvider.getInstance(this).get();
                provider.unbindAll();
                Preview cameraPreview=new Preview.Builder().build();
                cameraPreview.setSurfaceProvider(previewView.getSurfaceProvider());
                activeCamera=provider.bindToLifecycle((androidx.lifecycle.LifecycleOwner)this,CameraSelector.DEFAULT_BACK_CAMERA,cameraPreview,analysis);
                ctrlRef[0]=activeCamera.getCameraControl();
                try{ maxZoom[0]=activeCamera.getCameraInfo().getZoomState().getValue().getMaxZoomRatio(); }
                catch(Exception e){ maxZoom[0]=10.0f; }
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->ready[0]=true,1500);
            }catch(Exception e){
                Toast.makeText(this,"Camera error: "+e.getMessage(),Toast.LENGTH_LONG).show();
                dialog.dismiss();
            }
        },ContextCompat.getMainExecutor(this));

        dialog.setOnDismissListener(d->{
            isScannerActive=false;
            ready[0]=false;
            ctrlRef[0]=null;
            activeCamera=null;
            if(cameraExecutor!=null){cameraExecutor.shutdownNow();cameraExecutor=null;}
        });
    }

    void saveSettings(){
        if(prefs==null || input==null)return;
        SharedPreferences.Editor e=prefs.edit();
        e.putString("ean",getEan()); e.putInt("cursor",input==null?0:input.getSelectionStart());
        for(int i=0;i<sliders.length;i++) if(sliders[i]!=null) e.putFloat("s"+i,sliders[i].current);
        e.apply();
    }
    void loadSettings(){
        if(prefs==null)return;
        for(int i=0;i<sliders.length;i++) if(sliders[i]!=null && prefs.contains("s"+i)){
            float v=prefs.getFloat("s"+i,sliders[i].current); sliders[i].current=v; sliders[i].normal=v;
            sliders[i].bar.setProgress(sliders[i].progressFromNormalValue(v),false); sliders[i].valueView.setText(formatValue(i,v));
        }
        if(preview!=null) applySliders();
        updateChecksumButtons();
    }
    String checksumExpected(){
        String v=getEan();
        StringBuilder d=new StringBuilder(); for(int i=0;i<12;i++){char c=i<v.length()?v.charAt(i):'0'; d.append(Character.isDigit(c)?c:'0');}
        return preview.checksum(d.toString());
    }
    void updateChecksumButtons(){
        if(input==null||autoBtn==null)return;
        String v=getEan(); String expected=checksumExpected(); char entered=v.length()==13?v.charAt(12):' ';
        boolean ok=v.length()==13&&Character.isDigit(entered)&&entered==(expected.charAt(0));
        autoBtn.setEnabled(!ok);
        input.setBackground(new ColorDrawable(ok ? 0xFF18252B : 0xFF4A1D24));
        input.invalidate();
    }
    void autoChecksum(){
        String v=getEan(); String expected=checksumExpected();
        setEan(expectedFromFirst12(v)); input.requestFocus(); input.setSelection(input.length()); saveSettings(); updateChecksumButtons();
    }
    void restorePreviousDigit(){
        if(lastDigitIndex<0||lastDigitIndex>=13)return;
        String v=getEan(); if(lastDigitIndex>=0 && lastDigitIndex<v.length()){ String nv=v.substring(0,lastDigitIndex)+lastDigitOld+v.substring(lastDigitIndex+1); setEan(nv); } input.requestFocus(); input.setSelection(Math.min(input.length(),lastDigitIndex)); saveSettings(); updateChecksumButtons();
    }
    void restorePreviousState(){
        if(inputHistory.isEmpty())return;
        String st=inputHistory.removeLast(); setEan(st); input.requestFocus(); input.setSelection(Math.min(input.length(),0)); saveSettings(); updateChecksumButtons();
    }
    boolean ensureProfileStorage(){
        if(Build.VERSION.SDK_INT>=30 && !Environment.isExternalStorageManager()){
            try{Intent i=new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,Uri.parse("package:"+getPackageName()));startActivity(i);}catch(Exception e){startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));}
            Toast.makeText(this,"Allow CB13 access to all files, then use Save/Load again",Toast.LENGTH_LONG).show(); return false;
        }
        return true;
    }
    File profilesDir(){ return new File("/storage/4A21-0000/DATA/CB13"); }
    void showProfilesMenu(){
        final String[] items={"Save current settings","Load saved settings","Cancel"};
        new AlertDialog.Builder(this).setTitle("CB13 settings").setItems(items,(d,w)->{
            if(w==0)showSaveProfile(); else if(w==1)showLoadProfile();
        }).show();
    }
    void showSaveProfile(){
        final EditText name=new EditText(this); name.setHint("Name"); name.setSingleLine(true); name.setInputType(InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this).setTitle("Save settings").setView(name).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->saveProfile(name.getText().toString().trim())).show();
    }
    void saveProfile(String name){
        if(!ensureProfileStorage())return;
        if(name.isEmpty()){Toast.makeText(this,"Name required",Toast.LENGTH_SHORT).show();return;}
        File dir=profilesDir(); if(!dir.exists()&&!dir.mkdirs()){Toast.makeText(this,"Cannot create /storage/4A21-0000/DATA/CB13",Toast.LENGTH_LONG).show();return;}
        String safe=name.replaceAll("[^A-Za-z0-9._-]","_"); if(!safe.toLowerCase(Locale.US).endsWith(".cb13")) safe += ".cb13"; File f=new File(dir,safe);
        try(BufferedWriter bw=new BufferedWriter(new FileWriter(f))){
            bw.write("EAN="+getEan().replace(" ","_")+"\n");
            for(int i=0;i<sliders.length;i++)bw.write("S"+i+"="+sliders[i].current+"\n");
            Toast.makeText(this,"Saved: "+f.getName(),Toast.LENGTH_SHORT).show();
        }catch(Exception e){Toast.makeText(this,"Save failed: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    void showLoadProfile(){
        if(!ensureProfileStorage())return;
        File dir=profilesDir(); File[] fs=dir.listFiles((d,n)->n.endsWith(".cb13"));
        if(fs==null||fs.length==0){Toast.makeText(this,"No saved settings",Toast.LENGTH_SHORT).show();return;}
        String[] names=new String[fs.length]; for(int i=0;i<fs.length;i++)names[i]=fs[i].getName();
        new AlertDialog.Builder(this).setTitle("Load settings").setItems(names,(d,w)->{ loadProfile(fs[w]); status.setText(fs[w].getName()); }).setNegativeButton("Cancel",null).show();
    }
    void loadProfile(File f){
        try(BufferedReader br=new BufferedReader(new FileReader(f))){ String line; while((line=br.readLine())!=null){ int k=line.indexOf('='); if(k<1)continue; String key=line.substring(0,k),val=line.substring(k+1);
                if(key.equals("EAN")){setEan(val.replace("_",""));} else if(key.startsWith("S")){int i=Integer.parseInt(key.substring(1)); if(i>=0&&i<sliders.length){float x=Float.parseFloat(val);sliders[i].current=x;sliders[i].normal=x;sliders[i].bar.setProgress(sliders[i].progressFromNormalValue(x),false);sliders[i].valueView.setText(formatValue(i,x));}}
            }
            applySliders(); saveSettings(); input.invalidate(); updateChecksumButtons(); Toast.makeText(this,"Loaded: "+f.getName(),Toast.LENGTH_SHORT).show();
        }catch(Exception e){Toast.makeText(this,"Load failed: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }


    void addSlider(LinearLayout root,int i,String name,int min,int max,int value){
        LinearLayout row=new LinearLayout(this);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView label=new TextView(this);
        label.setText(name);
        label.setTextSize(12);
        label.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.addView(label,new LinearLayout.LayoutParams(88,42));

        SeekBar bar=new SeekBar(this);
        bar.setMax(1000);
        bar.setProgress(Math.round((value-min)*1000f/(max-min)));
        row.addView(bar,new LinearLayout.LayoutParams(0,42,1f));

        TextView val=new TextView(this);
        val.setText(formatValue(i,value));
        val.setTextSize(12);
        row.addView(val,new LinearLayout.LayoutParams(65,42));

        SliderCtl ctl=new SliderCtl(name,min,max,bar,val,i);
        ctl.current=value; ctl.normal=value; sliders[i]=ctl;

        label.setOnClickListener(v->{
            if(!shift)return;
            if(!ctl.fine){
                ctl.center=ctl.current;
                ctl.fine=true;
                bar.setProgress(500,false);
                label.setText("● "+name);
            }else{
                ctl.fine=false;
                ctl.normal=ctl.current;
                bar.setProgress(ctl.progressFromNormalValue(ctl.current),false);
                label.setText(name);
            }
            val.setText(formatValue(i,ctl.current));
            preview.invalidate();
        });

        final float[] touchStart={0f,0f};
        bar.setOnTouchListener((v,e)->{
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){ touchStart[0]=e.getX(); touchStart[1]=bar.getProgress(); return true; }
            if(e.getActionMasked()==MotionEvent.ACTION_MOVE || e.getActionMasked()==MotionEvent.ACTION_UP){
                float dx=e.getX()-touchStart[0]; float delta=dx/Math.max(1f,bar.getWidth())*1000f;
                int p=Math.max(0,Math.min(1000,Math.round(touchStart[1]+delta))); bar.setProgress(p,true);
                if(e.getActionMasked()==MotionEvent.ACTION_UP) v.performClick(); return true;
            }
            return true;
        });

        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int p,boolean fromUser){
                if(suppressSeekCallbacks)return;
                ctl.current=ctl.valueFromProgress(p);
                if(!ctl.fine)ctl.normal=ctl.current;
                val.setText(formatValue(i,ctl.current));
                applySliders();
                saveSettings();
            }
            public void onStartTrackingTouch(SeekBar b){}
            public void onStopTrackingTouch(SeekBar b){}
        });
        root.addView(row);
    }
    class BarcodeView extends View{
        static final int PRINTER_W=464;
        static final float REF_W=753f,REF_BAR_X0=62f,REF_BAR_X1=632f;
        static final float REF_Y_TOP=8f,REF_DATA_Y1=408f,REF_GUARD_Y1=440f,REF_DIGIT_Y1=464.5f;
        static final float TOP_MARGIN_PX=8f;
        static final float[] REF_CENTERS={21.432f,105.6275f,147.725f,189.725f,231.725f,273.2695f,311.949f,381.725f,419.949f,465.6275f,507.725f,549.725f,591.725f};

        String ean="5901234123457";
        float zoom=1,scaleX=1,scaleY=1,moveX=0,moveY=0,digitWidth=1,groupSpacing=1,barHeight=1,feedMm=0;
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        Map<Character,String> glyphs=new HashMap<>();
        float downX,downY,startMoveX,startMoveY,lastDist,lastMidX,lastMidY,lastSpanX,lastSpanY;
        boolean twoFinger=false,justEndedTwoFinger=false;
float gestureLastMidX,gestureLastMidY,gestureLastDist;

        BarcodeView(Context c){super(c);setBackgroundColor(0xFF081116);initGlyphs();}
        void initGlyphs(){
            glyphs.put('9', "M14.825 424.690Q11.960 427.690 11.960 431.877Q11.960 435.940 14.694 438.502Q17.298 441.002 21.725 441.002Q29.472 441.002 30.839 436.439Q31.490 434.377 31.490 431.877Q31.490 427.190 28.756 424.440Q26.087 421.628 21.725 421.628Q17.624 421.628 14.825 424.690M10.528 442.377Q6.101 438.189 6.101 431.877Q6.101 425.378 10.788 420.691Q15.345 416.003 21.725 416.003Q28.300 416.003 32.792 420.378Q37.349 424.753 37.349 431.877Q37.349 436.315 35.982 439.877Q34.615 443.439 32.141 447.564Q26.738 456.626 18.926 463.563Q18.080 464.312 17.103 464.312Q15.996 464.312 15.150 463.500Q14.304 462.688 14.304 461.500Q14.304 460.313 15.215 459.438Q21.400 453.751 25.826 446.876L26.152 446.251L25.501 446.376Q23.808 446.626 21.725 446.626Q14.825 446.626 10.528 442.377Z");
            glyphs.put('8', "M100.647 422.878Q98.174 424.128 98.174 426.503Q98.174 428.065 98.759 429.253Q99.345 430.440 100.712 431.440Q102.014 432.377 102.861 432.877Q103.772 433.377 105.595 434.252L105.725 434.252L105.855 434.252Q107.678 433.440 108.590 432.940Q109.501 432.440 110.803 431.502Q112.105 430.502 112.691 429.315Q113.277 428.065 113.277 426.503Q113.277 424.128 110.803 422.878Q108.394 421.628 105.725 421.628Q103.056 421.628 100.647 422.878M105.725 440.689Q95.895 445.626 95.895 451.501Q95.895 455.001 98.825 456.938Q101.559 458.875 105.725 458.875L107.027 458.875Q110.477 458.875 112.951 456.688Q115.490 454.501 115.490 451.501Q115.490 445.564 105.986 440.689L105.855 440.564L105.725 440.689M94.593 460.875Q90.101 457.250 90.101 451.501Q90.101 443.939 99.345 437.814L99.736 437.564L99.345 437.314Q92.315 433.190 92.315 426.503Q92.315 421.941 96.481 419.003Q100.582 416.003 105.725 416.003Q110.803 416.003 114.969 419.003Q119.136 422.003 119.136 426.503Q119.136 430.190 117.313 432.752Q115.425 435.252 112.105 437.314L111.714 437.564L112.105 437.814Q121.349 443.939 121.349 451.501Q121.349 456.438 117.573 460.250Q113.797 464.062 108.459 464.500L105.725 464.500Q99.020 464.500 94.593 460.875Z");
            glyphs.put('7', "M132.948 421.128Q132.101 420.253 132.101 419.066Q132.101 417.878 132.948 417.066Q133.794 416.253 135.031 416.253L160.420 416.253Q161.656 416.253 162.503 417.128Q163.349 417.941 163.349 419.066Q163.349 423.815 159.508 429.378Q158.336 431.127 154.951 435.190Q151.566 439.314 150.134 441.877Q145.902 449.376 145.902 461.438Q145.902 462.625 145.056 463.500Q144.210 464.312 143.103 464.312Q141.996 464.312 141.150 463.500Q140.304 462.688 140.304 461.438Q140.304 447.814 145.251 439.002Q146.749 436.439 150.134 432.127Q153.584 427.753 154.691 426.128Q156.253 423.628 156.839 422.378L156.969 421.941L135.031 421.941Q133.859 421.941 132.948 421.128Z");
            glyphs.put('6', "M178.658 460.438Q174.297 456.376 174.101 449.689L174.101 448.939Q174.101 441.127 178.593 434.002Q180.416 431.065 183.020 427.815Q185.559 424.503 187.316 422.565Q189.074 420.566 192.329 417.003Q193.045 416.191 194.347 416.191Q195.454 416.191 196.300 417.066Q197.146 417.878 197.146 419.003Q197.146 420.066 196.430 420.941Q189.074 428.940 185.624 433.752L185.168 434.377L185.884 434.252Q187.772 433.877 189.725 433.877Q196.821 433.877 201.052 438.127Q205.349 442.439 205.349 449.689Q205.349 456.251 200.922 460.375Q196.495 464.500 189.725 464.500Q182.955 464.500 178.658 460.438M182.564 442.252Q179.960 444.939 179.960 449.689Q179.960 453.938 182.694 456.438Q185.363 458.875 189.725 458.875Q194.022 458.875 196.691 456.376Q199.490 453.876 199.490 449.689Q199.490 444.814 196.951 442.189Q194.477 439.502 189.725 439.502Q185.103 439.502 182.564 442.252Z");
            glyphs.put('5', "M218.640 463.688Q217.794 462.875 217.794 461.688Q217.794 460.438 218.640 459.625Q219.552 458.813 220.723 458.813Q233.873 458.813 237.714 451.751Q238.886 449.626 238.886 447.689Q238.886 443.127 236.673 440.814Q233.743 437.502 226.387 437.502Q224.369 437.502 222.286 437.689L222.025 437.689Q220.854 437.689 219.942 436.939Q219.096 436.127 219.096 435.002L219.096 434.815L220.007 418.878Q220.137 417.753 220.919 417.003Q221.765 416.253 222.937 416.253L241.425 416.253Q242.662 416.253 243.508 417.128Q244.354 417.941 244.354 419.066Q244.354 420.191 243.443 421.066Q242.597 421.941 241.425 421.941L225.671 421.941L225.150 431.752L226.387 431.752Q234.655 431.752 239.667 435.815Q244.745 439.814 244.745 447.689Q244.745 451.001 243.248 453.876Q241.685 456.813 239.342 458.875Q233.092 464.500 220.723 464.500Q219.552 464.500 218.640 463.688M222.221 437.689Q222.221 437.689 222.286 437.689Q222.286 437.689 222.221 437.689Z");
            glyphs.put('4', "M258.948 452.626Q258.101 451.813 258.101 450.626L258.101 447.939Q258.101 447.439 258.427 446.751L272.293 417.816Q272.553 417.128 273.269 416.691Q273.986 416.191 274.767 416.191Q275.939 416.191 276.785 417.066Q277.631 417.878 277.631 419.003Q277.631 419.628 277.306 420.191L264.286 447.814L276.980 447.814L276.980 440.252Q276.980 439.127 277.826 438.314Q278.673 437.439 279.779 437.439Q280.886 437.439 281.732 438.314Q282.644 439.127 282.644 440.252L282.644 447.814L286.420 447.814Q287.656 447.814 288.503 448.689Q289.349 449.501 289.349 450.626Q289.349 451.751 288.438 452.626Q287.591 453.438 286.420 453.438L282.644 453.438L282.644 461.500Q282.644 462.625 281.732 463.438Q280.886 464.312 279.779 464.312Q278.673 464.312 277.826 463.500Q276.980 462.625 276.980 461.500L276.980 453.438L261.031 453.438Q259.859 453.438 258.948 452.626Z");
            glyphs.put('3', "M301.468 462.125Q300.101 461.125 300.101 459.625Q300.101 458.438 300.948 457.625Q301.859 456.751 303.031 456.751Q303.421 456.751 304.137 457.001Q308.304 458.813 312.861 458.813Q317.808 458.813 321.193 456.438Q324.644 454.001 324.904 449.376L324.904 448.751Q324.904 445.564 322.821 443.314Q320.868 441.127 318.004 440.189Q315.204 439.314 311.754 439.314Q310.647 439.314 309.801 438.502Q308.955 437.627 308.955 436.439Q308.955 435.315 309.541 434.690L321.519 421.941L303.226 421.941Q302.054 421.941 301.143 421.128Q300.297 420.253 300.297 419.066Q300.297 417.878 301.143 417.066Q301.989 416.253 303.226 416.253L326.206 416.253Q327.378 416.253 328.224 417.128Q329.136 417.941 329.136 419.066L329.136 421.316Q329.136 422.316 328.224 423.190L317.743 434.252L318.264 434.377Q323.797 435.752 327.313 439.439Q330.763 443.127 330.763 448.689L330.763 449.689Q330.438 456.501 325.230 460.500Q320.152 464.500 312.861 464.500Q311.038 464.500 309.215 464.312Q307.392 464.125 305.114 463.688Q302.835 463.188 301.468 462.125Z");
            glyphs.put('2', "M368.054 424.065Q367.208 423.190 367.208 422.128Q367.208 420.566 368.445 419.753Q374.369 416.003 381.725 416.003Q387.649 416.003 391.620 419.628Q395.656 423.190 395.656 429.003Q395.656 431.315 394.810 433.440Q393.964 435.627 392.857 437.002Q391.816 438.377 389.732 440.064Q387.649 441.752 386.542 442.502Q385.501 443.252 383.222 444.689Q380.749 446.251 379.642 447.064Q378.535 447.876 376.973 449.501Q375.410 451.063 374.694 453.063Q373.978 455.001 373.848 457.688L373.848 458.000L393.118 458.000Q394.354 458.000 395.201 458.875Q396.047 459.688 396.047 460.813Q396.047 461.938 395.136 462.813Q394.289 463.625 393.118 463.625L370.854 463.625Q369.682 463.625 368.770 462.813Q367.924 461.938 367.924 460.813L367.924 459.500Q367.924 454.626 368.575 452.501Q369.682 448.751 372.741 445.814Q375.801 442.814 380.228 439.939Q386.347 436.002 387.975 433.752Q389.797 431.502 389.797 429.003Q389.797 425.690 387.389 423.628Q385.110 421.628 381.725 421.628Q375.866 421.628 371.830 424.378Q371.114 424.878 370.137 424.878Q368.966 424.878 368.054 424.065Z");
            glyphs.put('1', "M410.966 430.627Q410.119 429.752 410.119 428.628Q410.119 427.315 410.966 426.690L422.879 416.878Q423.725 416.191 424.767 416.191L426.850 416.191Q428.022 416.191 428.868 417.066Q429.779 417.878 429.779 419.003L429.779 461.500Q429.779 462.625 428.868 463.500Q428.022 464.312 426.850 464.312Q425.678 464.312 424.767 463.500Q423.920 462.688 423.920 461.500L423.920 423.440L415.002 430.752Q414.156 431.440 413.049 431.440Q411.812 431.440 410.966 430.627Z");
            glyphs.put('0', "M465.986 464.500L465.725 464.500Q461.884 464.437 459.020 463.250Q456.156 462.125 454.463 460.313Q452.770 458.438 451.794 455.501Q450.752 452.563 450.427 449.564Q450.101 446.626 450.101 442.627Q450.101 439.064 450.166 436.564Q450.297 434.002 450.687 431.065Q451.078 428.065 451.729 426.065Q452.445 424.003 453.617 421.941Q454.788 419.941 456.416 418.753Q458.043 417.566 460.387 416.816Q462.731 416.066 465.725 416.066Q468.655 416.066 470.998 416.816Q473.342 417.566 474.969 418.753Q476.597 419.941 477.834 421.941Q479.005 424.003 479.656 426.065Q480.373 428.128 480.763 431.065Q481.154 434.002 481.219 436.564Q481.349 439.127 481.349 442.627Q481.349 445.126 481.219 447.189Q481.089 449.189 480.633 451.563Q480.242 453.938 479.591 455.751Q478.940 457.500 477.703 459.188Q476.467 460.875 474.904 462.000Q473.342 463.125 471.063 463.813Q468.785 464.500 465.986 464.500M455.960 437.502L455.960 442.627Q455.960 445.439 456.090 447.439Q456.286 449.439 456.872 451.751Q457.392 454.063 458.434 455.501Q459.476 457.001 461.298 458.000Q463.121 458.938 465.725 458.938L467.092 458.938Q468.915 458.938 470.347 458.250Q471.779 457.500 472.626 456.126Q473.537 454.813 474.123 453.313Q474.709 451.751 474.969 449.814Q475.295 447.814 475.360 446.189Q475.490 444.564 475.490 442.627L475.490 439.752Q475.490 437.814 475.425 436.689Q475.425 435.502 475.360 433.752Q475.295 431.940 475.099 430.940Q474.904 429.877 474.579 428.440Q474.253 427.003 473.732 426.128Q473.212 425.253 472.495 424.315Q471.714 423.378 470.803 422.878Q469.826 422.316 468.524 422.003Q467.288 421.691 465.725 421.691Q464.098 421.691 462.796 422.066Q461.559 422.378 460.582 423.003Q459.606 423.628 458.825 424.565Q458.108 425.440 457.588 426.565Q457.067 427.628 456.741 428.940Q456.416 430.190 456.286 431.627Q456.156 433.002 456.025 434.502Q455.960 435.940 455.960 437.502Z");
        }
        void setEan(String s){ean=s;invalidate();}
        String checksum(String s){int sum=0;for(int i=0;i<12;i++)sum+=(s.charAt(i)-48)*((i%2==0)?1:3);return Integer.toString((10-sum%10)%10);}
        String normalize(){
            String s=ean; StringBuilder b=new StringBuilder(); for(int i=0;i<13;i++){char c=i<s.length()?s.charAt(i):' '; b.append(Character.isDigit(c)?c:'0');} return b.toString();
        }
        String modules(String c){
            String[] L={"0001101","0011001","0010011","0111101","0100011","0110001","0101111","0111011","0110111","0001011"};
            String[] G={"0100111","0110011","0011011","0100001","0011101","0111001","0000101","0010001","0001001","0010111"};
            String[] R={"1110010","1100110","1101100","1000010","1011100","1001110","1010000","1000100","1001000","1110100"};
            String[] P={"LLLLLL","LLGLGG","LLGGLG","LLGGGL","LGLLGG","LGGLLG","LGGGLL","LGLGLG","LGLGGL","LGGLGL"};
            StringBuilder b=new StringBuilder("101");String p=P[c.charAt(0)-48];
            for(int i=0;i<6;i++){int d=c.charAt(i+1)-48;b.append(p.charAt(i)=='L'?L[d]:G[d]);}
            b.append("01010");for(int i=7;i<13;i++)b.append(R[c.charAt(i)-48]);b.append("101");return b.toString();
        }
        float bboxWidth(String d){Path p=PathParser.createPathFromPathData(d);RectF b=new RectF();p.computeBounds(b,true);return b.width();}

        static final int PREVIEW_H=619;
        static final float MM_PX=464f/58f;
        static final float GLOBAL_MARGIN=MM_PX; // 1 mm

        float objectBottom(){
            float sy=(PRINTER_W/REF_W)*zoom*scaleY;
            float content=Math.max(REF_DIGIT_Y1,REF_GUARD_Y1*barHeight);
            float bottom=(REF_Y_TOP+content)*sy + moveY*PREVIEW_H*.35f;
            return Math.max(20f,bottom);
        }
        int outputHeight(){
            float base=PRINTER_W/REF_W;
            float sy=base*zoom*scaleY;

            // V2 visual content reaches the reference digit baseline.
            float content=Math.max(
                    REF_DIGIT_Y1,
                    REF_GUARD_Y1*barHeight);

            return Math.max(80,
                    (int)Math.ceil(
                            REF_Y_TOP*sy +
                            content*sy +
                            2f));
        }

        Bitmap renderBitmap(){
            // The printer image starts from EXACTLY the same fixed canvas
            // used by the preview: PRINTER_W x PREVIEW_H.
            Bitmap full=Bitmap.createBitmap(
                    PRINTER_W,
                    PREVIEW_H,
                    Bitmap.Config.ARGB_8888);

            drawLogical(new Canvas(full),PRINTER_W,PREVIEW_H);

            // Crop only rows that actually contain visible pixels.
            int top=-1;
            int bottom=-1;
            int[] row=new int[PRINTER_W];

            for(int y=0;y<PREVIEW_H;y++){
                full.getPixels(row,0,PRINTER_W,0,y,PRINTER_W,1);

                boolean visible=false;

                for(int x=0;x<PRINTER_W;x++){
                    int c=row[x];

                    if(Color.red(c)<245 ||
                       Color.green(c)<245 ||
                       Color.blue(c)<245){
                        visible=true;
                        break;
                    }
                }

                if(visible){
                    if(top<0)top=y;
                    bottom=y;
                }
            }

            if(top<0){
                full.recycle();

                return Bitmap.createBitmap(
                        PRINTER_W,
                        1,
                        Bitmap.Config.ARGB_8888);
            }

            Bitmap cropped=Bitmap.createBitmap(
                    full,
                    0,
                    top,
                    PRINTER_W,
                    bottom-top+1);

            full.recycle();

            // Feed is added AFTER the visible preview content.
            int feedPx=Math.max(
                    0,
                    Math.round(feedMm*8f));

            if(feedPx<=0)return cropped;

            Bitmap result=Bitmap.createBitmap(
                    PRINTER_W,
                    cropped.getHeight()+feedPx,
                    Bitmap.Config.ARGB_8888);

            Canvas out=new Canvas(result);
            out.drawColor(Color.WHITE);
            out.drawBitmap(cropped,0,0,null);

            cropped.recycle();

            return result;
        }

        void drawLogical(Canvas canvas,int w,int h){
            canvas.drawColor(Color.WHITE);

            String code=normalize();

            // ==================================================
            // TEST FIX 812:
            // Exact V2 relationship:
            // height controls digit width/spacing.
            // Scale X controls the effective horizontal layout.
            // Scale Y controls the V2 height master.
            // ==================================================
            float base=w/REF_W;

            float widthRatio=
                    1f + (zoom*scaleX*base-1f);

            float effectiveWidth=
                    REF_W*widthRatio;

            float sy=
                    base*zoom*scaleY;

            float digitSy=sy;
            float digitSxFromHeight=sy;

            char leadChar=code.charAt(0);
            String leadPath=glyphs.get(leadChar);

            if(leadPath==null)return;

            float leadRawWidth=bboxWidth(leadPath);

            float leadWidth=
                    leadRawWidth*
                    digitSxFromHeight*
                    digitWidth;

            float leftMargin=leadWidth*0.5f;
            float leadGap=leadWidth*0.5f;
            float rightMargin=leadWidth*0.5f;

            float availableBarWidth=
                    effectiveWidth-
                    leftMargin-
                    leadWidth-
                    leadGap-
                    rightMargin;

            if(availableBarWidth<1f)return;

            float layoutX=
                    (w-effectiveWidth)/2f;

            float barWidth=availableBarWidth;

            float barLeft=
                    layoutX+
                    leftMargin+
                    leadWidth+
                    leadGap;

            float dataH=
                    Math.max(
                            1f,
                            (REF_DATA_Y1-REF_Y_TOP)*
                            sy*
                            barHeight);

            float guardH=
                    Math.max(
                            dataH,
                            (REF_GUARD_Y1-REF_Y_TOP)*
                            sy*
                            barHeight);

            // Fixed physical side margins.
            // They belong to the canvas, NOT to the moving/scaling object.
            final float SIDE_MARGIN=GLOBAL_MARGIN;
            final float TOP_MARGIN=8f;

            // Object movement is independent from its size.
            // This gives enough travel even when the barcode is very large.
            float objectX=moveX*w;
            float objectY=moveY*h;

            canvas.save();
            canvas.translate(objectX,objectY);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.BLACK);

            String m=modules(code);
            float moduleW=barWidth/95f;

            for(int i=0;i<95;){
                if(m.charAt(i)=='1'){
                    int j=i+1;
                    while(j<95 && m.charAt(j)=='1')j++;

                    boolean guard=
                            i<3 ||
                            i>=92 ||
                            (i>=45 && i<50);

                    float bh=guard?guardH:dataH;

                    canvas.drawRect(
                            barLeft+i*moduleW,
                            TOP_MARGIN,
                            barLeft+j*moduleW,
                            TOP_MARGIN+bh,
                            paint);

                    i=j;
                }else{
                    i++;
                }
            }

            // ==================================================
            // EXACT V2 SLOT CENTERS
            // ==================================================
            float refBarWidth=REF_BAR_X1-REF_BAR_X0;

            float[] scaledCenters=new float[13];

            for(int i=0;i<13;i++){
                scaledCenters[i]=
                        barLeft+
                        (REF_CENTERS[i]-REF_BAR_X0)/
                        refBarWidth*
                        barWidth;
            }

            // Leading digit: fixed V2 half-width relation.
            float leadingCenter=
                    layoutX+
                    leftMargin+
                    leadWidth/2f;

            drawDigit(
                    canvas,
                    code.charAt(0),
                    leadingCenter,
                    digitSxFromHeight*digitWidth,
                    digitSy,
                    TOP_MARGIN);

            // Two six-digit groups.
            drawGroup(
                    canvas,
                    code,
                    1,7,
                    scaledCenters,
                    digitSxFromHeight*digitWidth,
                    digitSy,
                    TOP_MARGIN);

            drawGroup(
                    canvas,
                    code,
                    7,13,
                    scaledCenters,
                    digitSxFromHeight*digitWidth,
                    digitSy,
                    TOP_MARGIN);

            canvas.restore();

            // Fixed white margins.
            // These NEVER move when the barcode moves or changes size.
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);

            canvas.drawRect(
                    0,
                    0,
                    SIDE_MARGIN,
                    h,
                    paint);

            canvas.drawRect(
                    w-SIDE_MARGIN,
                    0,
                    w,
                    h,
                    paint);

            canvas.drawRect(
                    0,
                    0,
                    w,
                    TOP_MARGIN,
                    paint);
        }

        void drawGroup(
                Canvas canvas,
                String code,
                int start,
                int end,
                float[] centers,
                float sx,
                float sy,
                float top){

            float first=centers[start];
            float last=centers[end-1];
            int count=end-start;

            float step=
                    (last-first)/
                    Math.max(1,count-1);

            // V2: group spacing is independent.
            step*=groupSpacing;

            float mid=(first+last)/2f;

            // Keep the V2 group centered while changing spacing.
            for(int k=0;k<count;k++){
                float cx=
                        mid+
                        (k-(count-1)/2f)*step;

                drawDigit(
                        canvas,
                        code.charAt(start+k),
                        cx,
                        sx,
                        sy,
                        top);
            }
        }

        void drawDigit(
                Canvas canvas,
                char digit,
                float centerX,
                float sx,
                float sy,
                float top){

            String d=glyphs.get(digit);
            if(d==null)return;

            Path p=
                    PathParser.createPathFromPathData(d);

            RectF bb=new RectF();
            p.computeBounds(bb,true);

            float sourceCx=
                    (bb.left+bb.right)/2f;

            Matrix mm=new Matrix();

            mm.setScale(sx,sy);

            mm.postTranslate(
                    centerX-sourceCx*sx,
                    top-REF_Y_TOP*sy);

            p.transform(mm);

            canvas.drawPath(p,paint);
        }

        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            float cw=Math.min(getWidth(),(float)getHeight()*3f/4f);
            float ch=cw*4f/3f;
            float x=(getWidth()-cw)/2f,y=(getHeight()-ch)/2f;
            c.drawColor(0xFF050B0F);
            c.save(); c.clipRect(x,y,x+cw,y+ch);
            c.translate(x,y); c.scale(cw/PRINTER_W,ch/PREVIEW_H);
            drawLogical(c,PRINTER_W,PREVIEW_H); c.restore();
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2f);paint.setColor(0xFF2B6E66);c.drawRect(x,y,x+cw,y+ch,paint);paint.setStyle(Paint.Style.FILL);
        }
        void syncGestureSliders(){
            if(sliders==null)return;

            sliders[0].current=zoom*100f;
            sliders[1].current=scaleX*100f;
            sliders[2].current=scaleY*100f;
            sliders[3].current=moveX*50f+50f;
            sliders[4].current=moveY*50f+50f;

            for(int i=0;i<5;i++){
                SliderCtl c=sliders[i];
                if(c==null)continue;
                c.bar.setProgress(c.progressFromNormalValue(c.current),false);
                c.valueView.setText(formatValue(i,c.current));
            }
        }

        float dist(MotionEvent e){
            if(e.getPointerCount()<2) return 0f;
            float dx=e.getX(0)-e.getX(1);
            float dy=e.getY(0)-e.getY(1);
            return (float)Math.sqrt(dx*dx+dy*dy);
        }

        @Override public boolean onTouchEvent(MotionEvent e){
            int n=e.getPointerCount();

            switch(e.getActionMasked()){

                case MotionEvent.ACTION_DOWN:
                    downX=e.getX();
                    downY=e.getY();
                    startMoveX=moveX;
                    startMoveY=moveY;
                    twoFinger=false;
                    justEndedTwoFinger=false;
                    return true;

                case MotionEvent.ACTION_POINTER_DOWN:
                    if(n>=2){
                        twoFinger=true;
                        justEndedTwoFinger=false;
                        gestureLastMidX=(e.getX(0)+e.getX(1))/2f;
                        gestureLastMidY=(e.getY(0)+e.getY(1))/2f;
                        gestureLastDist=dist(e);
                    }
                    return true;

                case MotionEvent.ACTION_MOVE:

                    if(n==1 && !twoFinger){

                        if(justEndedTwoFinger){
                            downX=e.getX();
                            downY=e.getY();
                            startMoveX=moveX;
                            startMoveY=moveY;
                            justEndedTwoFinger=false;
                            invalidate();
                            return true;
                        }

                        moveX=startMoveX+
                                (e.getX()-downX)/
                                Math.max(1,getWidth())*2.2f;

                        moveY=startMoveY+
                                (e.getY()-downY)/
                                Math.max(1,getHeight())*2.2f;

                        moveX=Math.max(-3f,Math.min(3f,moveX));
                        moveY=Math.max(-3f,Math.min(3f,moveY));

                        MainActivity a=(MainActivity)getContext();
                        if(a!=null){
                            a.sliders[3].current=50f+moveX*50f;
                            a.sliders[4].current=50f+moveY*50f;
                            a.sliders[3].bar.setProgress(
                                    a.sliders[3].progressFromNormalValue(
                                            a.sliders[3].current),false);
                            a.sliders[4].bar.setProgress(
                                    a.sliders[4].progressFromNormalValue(
                                            a.sliders[4].current),false);
                            a.sliders[3].valueView.setText(
                                    a.formatValue(3,a.sliders[3].current));
                            a.sliders[4].valueView.setText(
                                    a.formatValue(4,a.sliders[4].current));
                        }
                    }

                    else if(n>=2 && twoFinger){

                        float midX=(e.getX(0)+e.getX(1))/2f;
                        float midY=(e.getY(0)+e.getY(1))/2f;
                        float midDX=midX-gestureLastMidX;
                        float midDY=midY-gestureLastMidY;

                        float nd=dist(e);
                        float dDist=nd-gestureLastDist;

                        /*
                         * PINCH = ZOOM ONLY.
                         * The object position is NEVER modified here.
                         */
                        if(Math.abs(dDist)>2.5f &&
                           Math.abs(dDist) >
                           Math.max(Math.abs(midDX),Math.abs(midDY))*1.15f){

                            zoom=Math.max(
                                    0.10f,
                                    Math.min(3f,zoom+dDist/250f));
                        }

                        /*
                         * TWO-FINGER HORIZONTAL DRAG = SCALE X.
                         * TWO-FINGER VERTICAL DRAG   = SCALE Y.
                         * Position X/Y is NEVER modified.
                         */
                        else if(Math.abs(midDX)>1.2f ||
                                Math.abs(midDY)>1.2f){

                            if(Math.abs(midDX)>=Math.abs(midDY)){
                                scaleX=Math.max(
                                        0.10f,
                                        Math.min(3f,scaleX+midDX/180f));
                            }else{
                                scaleY=Math.max(
                                        0.10f,
                                        Math.min(3f,scaleY+midDY/180f));
                            }
                        }

                        gestureLastMidX=midX;
                        gestureLastMidY=midY;
                        gestureLastDist=nd;

                        MainActivity a=(MainActivity)getContext();
                        if(a!=null){

                            a.sliders[0].current=zoom*100f;
                            a.sliders[1].current=scaleX*100f;
                            a.sliders[2].current=scaleY*100f;

                            for(int i=0;i<3;i++){
                                SliderCtl c=a.sliders[i];
                                c.bar.setProgress(
                                        c.progressFromNormalValue(c.current),
                                        false);
                                c.valueView.setText(
                                        a.formatValue(i,c.current));
                            }
                        }
                    }

                    invalidate();
                    return true;

                case MotionEvent.ACTION_POINTER_UP:
                    if(n<=2){
                        twoFinger=false;
                        justEndedTwoFinger=true;
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    twoFinger=false;
                    justEndedTwoFinger=false;

                    MainActivity a=(MainActivity)getContext();
                    if(a!=null)a.saveSettings();

                    return true;
            }

            return true;
        }
    }



    String formatValue(int i,float v){
        if(i==8)return String.format(Locale.US,"%.1f mm",v);
        if(i==3||i==4)return String.format(Locale.US,"%.1f",v-50f);
        return String.format(Locale.US,"%.1f%%",v);
    }


    void setShift(boolean on){
        shift=on;
        for(int i=0;i<sliders.length;i++){
            SliderCtl s=sliders[i];
            if(s!=null){s.fine=false;s.normal=s.current;}
            if(fineDials[i]!=null)fineDials[i].setNeonColor(NEON_GREEN);
        }
        preview.invalidate();
    }


    void shareBitmap(boolean direct) {
        Bitmap bmp = preview.renderBitmap();
        java.io.File dir = new java.io.File(getCacheDir(), "cb13");
        dir.mkdirs();
        java.io.File file = new java.io.File(dir, "barcode.png");
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                this, getPackageName() + ".fileprovider", file);

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("image/png");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        if (direct) {
            intent.setPackage(PRINTER_PACKAGE);
        }
        startActivity(intent);
    }


    void applySliders(){
        if(preview==null)return;
        preview.zoom=sliders[0].current/100f;
        preview.scaleX=sliders[1].current/100f;
        preview.scaleY=sliders[2].current/100f;
        preview.moveX=(sliders[3].current-50f)/50f;
        preview.moveY=(sliders[4].current-50f)/50f;
        preview.digitWidth=sliders[5].current/100f;
        preview.groupSpacing=sliders[6].current/100f;
        preview.barHeight=sliders[7].current/100f;
        preview.feedMm=sliders[8].current;
        preview.invalidate();
        saveSettings();
    }
}