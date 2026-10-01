# CB13 Cockpit integration

This generator supplies the missing visual/resource layer around the existing MainActivity.

It intentionally does NOT replace MainActivity.java because that file contains the working:
- EAN logic
- checksum logic
- scanner
- CameraX lifecycle handling
- barcode V2 geometry
- printer bitmap generation
- gesture logic

## 1. Preview versus printer

The most important architectural rule:

SCREEN PREVIEW:
- transparent canvas
- cyan/neon bars
- cyan/neon digits
- cockpit background
- HUD decoration

PRINTER:
- white background
- black bars
- black digits
- no cockpit
- no neon
- no transparency

Never call the neon renderer from renderBitmap().

## 2. Central preview

Recommended root:

FrameLayout
    CockpitBackgroundView
    BarcodeView
    HUD overlays

BarcodeView should clear its screen canvas before drawing the barcode:

NeonBarcodeRenderer.clearPreview(canvas);

Then existing drawLogical() calculates exactly the same geometry as before.

For preview bars replace only:

canvas.drawRect(...,paint);

with:

NeonBarcodeRenderer.drawBar(canvas,paint,...);

For preview digits replace only:

canvas.drawPath(path,paint);

with:

NeonBarcodeRenderer.drawDigit(canvas,paint,path);

Do NOT modify the coordinates, scaling equations, module positions, or glyph paths.

## 3. Printer

renderBitmap() must continue to create a bitmap and call a printer rendering path using:

NeonBarcodeRenderer.preparePrinter(canvas,paint);

or equivalent existing black/white code.

Do not add alpha/transparency to the exported bitmap.

## 4. EAN field

Keep the existing EditText and TextWatcher.

Only change its visual properties:
- background = @drawable/cockpit_input
- text color = @color/text_primary
- monospace
- dark panel

Keep all existing checksum/history behavior.

## 5. Circular controls

NeonDialView is a visual replacement for SeekBar.

There are nine existing logical controls:

Zoom
Scale X
Scale Y
Move X
Move Y
Digit width
Group spacing
Bar height
Feed

Each dial returns 0..1.

Convert to the existing slider value:

value = min + normalized * (max-min)

Do not change SliderCtl.

## 6. Fine tune

The existing Shift fine tune mechanism should remain the source of truth.

A dial must modify SliderCtl.current, then call the same existing update path.

Do not create a second independent settings system.

## 7. Scanner

Do not modify scanner code.

The current PreviewView + CameraX implementation is already working.

## 8. Build

This package uses no new external library.

Existing project dependencies are sufficient for these Java classes and XML resources.

The FileProvider fragment is supplied separately because the existing application manifest was not provided to this generator. Merge it into the current <application> element.

## 9. Final visual structure

A practical arrangement is:

TOP:
    EAN cockpit input + SCAN

CENTER:
    cockpit HUD
    transparent barcode canvas

BOTTOM / SIDES:
    circular neon dials

BOTTOM:
    SHIFT FINE TUNE
    SHARE / PRINT

The exact positioning can be tuned later without touching barcode geometry.
