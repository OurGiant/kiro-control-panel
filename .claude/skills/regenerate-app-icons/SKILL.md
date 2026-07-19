---
name: regenerate-app-icons
description: Regenerate Kiro Control Panel's app icons (src/main/resources/app-icon.png, src/packaging/app-icon.ico, src/packaging/app-icon.icns) from the GIMP source at ~/Pictures/kiro-cp-logo.xcf. Covers exporting a square PNG from the .xcf via headless GIMP (flatpak batch-mode gotchas) and feeding it through ~/scripts/make_icon.py. Use whenever the logo/icon artwork changes.
---

# Regenerating Kiro Control Panel's app icons

Three committed files, all generated from one master image:

- `src/main/resources/app-icon.png` — Linux runtime icon (`jpackage --icon`, build.yml:95)
- `src/packaging/app-icon.ico` — Windows (build.yml:45)
- `src/packaging/app-icon.icns` — macOS (build.yml:148)

Design source lives outside the repo at `~/Pictures/kiro-cp-logo.xcf` (GIMP).

## Step 1 — export a square PNG from the .xcf

GIMP is installed as a flatpak (`org.gimp.GIMP`, tested on 3.2.4), not a
native binary — invoke it via `flatpak run org.gimp.GIMP`.

**Gotchas that cost real trial-and-error time:**

- The batch interpreter flag value is case- and name-sensitive:
  `--batch-interpreter=plug-in-script-fu-eval` (Script-Fu) or
  `--batch-interpreter=python-fu-eval` (Python). Guessing wrong (e.g.
  `Script-Fu-eval`) doesn't error cleanly — GIMP disables batch mode and
  silently drops into a backgrounded GUI-less process that never exits,
  burning the full timeout.
- GIMP 3 renamed old Script-Fu procedures (`gimp-image-width` no longer
  exists; it's `gimp-image-get-width` now). Skip the churn and use
  **Python-Fu with the GI API** instead — more stable across GIMP versions
  and far easier to express the centering math in.
- `gimp-image-flatten` / `Gimp.Image.flatten()` kills the alpha channel
  (fills with background color) — don't use it. Use
  `image.merge_visible_layers(Gimp.MergeType.CLIP_TO_IMAGE)` to merge
  layers while preserving transparency.
- A second `-b '(gimp-quit 0)'` batch arg is Script-Fu syntax and throws a
  Python `SyntaxError` when the interpreter is `python-fu-eval` — harmless
  (the export already happened before it runs), but don't mistake the
  error for a failed export. Just `timeout N` the whole invocation and
  check the output log for the "saved" print, not the process exit code
  (expect `124`, not `0`).
- Don't `pkill -f gimp` between runs — it's flaky/permission-prompted in a
  sandboxed shell and unnecessary; GIMP doesn't leave a process blocking
  the next `flatpak run`.

**The actual export script** (adjust the source path/filename):

```python
import gi
gi.require_version('Gimp', '3.0')
from gi.repository import Gimp, Gio

image = Gimp.file_load(Gimp.RunMode.NONINTERACTIVE,
                        Gio.File.new_for_path("/home/ryanleach/Pictures/kiro-cp-logo.xcf"))
w, h = image.get_width(), image.get_height()
size = max(w, h)
offx, offy = (size - w) // 2, (size - h) // 2   # pad to square, centered — never crop

image.resize(size, size, offx, offy)
for layer in image.get_layers():
    layer.resize_to_image_size()

merged = image.merge_visible_layers(Gimp.MergeType.CLIP_TO_IMAGE)  # keeps alpha
Gimp.file_save(Gimp.RunMode.NONINTERACTIVE, image,
               Gio.File.new_for_path("/tmp/kiro-cp-logo-square.png"), None)
print("saved")
```

Run it:

```bash
timeout 60 flatpak run org.gimp.GIMP --no-interface \
  --batch-interpreter=python-fu-eval -b "$(cat export.py)" \
  > /tmp/gimp_out.log 2>&1
grep saved /tmp/gimp_out.log   # confirms success regardless of the process's own exit code
```

The source `.xcf` was found to be 829×817, not square — always check
`get_width()`/`get_height()` and pad rather than assume square.

## Step 2 — run make_icon.py

```bash
source ~/scripts/.venv/bin/activate   # Pillow lives here, not in system python3
python3 ~/scripts/make_icon.py <square-source.png> -t icns -o src/packaging/app-icon.icns
python3 ~/scripts/make_icon.py <square-source.png> -t ico  -o src/packaging/app-icon.ico
cp <square-source.png> src/main/resources/app-icon.png
```

`make_icon.py` does **not** enforce or check squareness — feeding it a
non-square PNG silently squishes the image into every icon slot. Square it
in GIMP first (Step 1), not here.

## Step 3 — sanity-check the output size before committing

Big size swings are usually the artwork, not a bug. Diagnose with:

```bash
python3 -c "
from PIL import Image
im = Image.open('app-icon.png').convert('RGBA')
print(im.size, 'unique colors:', len(im.getcolors(maxcolors=2_000_000)))
"
```

- A flat/simple logo (few hundred unique colors) → icns in the tens of KB.
- A gradient/shadow-heavy logo (thousands+ unique colors) → multi-MB icns
  is normal, not a regression. `.icns` carries 16px through 1024px
  variants; `.ico` caps at 256px, so it grows far less.
- **Don't pre-downscale the source "to save space."** `make_icon.py`
  already resizes down to every target slot, so a smaller source doesn't
  shrink the small slots — it only hurts the *largest* icns slot (1024px),
  which now needs a bigger upscale. Measured on this repo: downscaling a
  829×829 master to 512×512 saved only ~9% on the icns (2.32MB → 2.12MB)
  while doubling the upscale factor for the 1024px entry from 1.24x to
  2x — a strictly worse tradeoff. Feed the highest-quality square master
  you have and let Pillow downscale; don't do it by hand first.
- If the icns/ico still feels too heavy after that, the fix is simplifying
  the *artwork* (fewer gradients/colors), not the export pipeline.
