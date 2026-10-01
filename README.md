# CB13
Android 15 / Java EAN-13 renderer.

The renderer starts from the standalone_fixed_v2 geometry and embeds the vector digit glyphs, so no reference SVG is needed at runtime.

- 12 input digits: checksum is added.
- 13 input digits: checksum is recalculated from the first 12 digits for rendering.
- 13 fixed input slots: delete leaves a blank slot; typing replaces the current slot and advances.
- One finger: move.
- Two-finger pinch/spread: whole zoom.
- Two fingers mainly left/right: X resize.
- Two fingers mainly up/down: Y resize.
- Shift: each slider becomes a +/-5% fine window around its current value.
- Normal share targets com.farminos.print.
- Shift share opens the Android chooser.
