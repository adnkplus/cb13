# uiCB13 cockpit integration

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
