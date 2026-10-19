#!/usr/bin/env python3
"""
Generate every Android brand asset from the authoritative logo sources.

Source of truth:  assets/logos/originals/   (the user's uploads, byte-for-byte,
                                             stored under their *real* format
                                             extension; never modified)
Derived:          assets/logos/*.png         (same pixels, genuine PNG encoding)
                  app/src/main/res/drawable/ic_{manjaniq,rsta,aether}_logo.png
                  app/src/main/res/mipmap-*/ic_launcher*.png

Rules (see promt.md / recovery spec, section 4):
  * The artwork is never redrawn, recolored, or reinterpreted. The only
    operations are lossless re-encoding, proportional resizing, and
    padding with the artwork's own flat background colour.
  * Launcher foregrounds keep the artwork inside the adaptive-icon safe
    zone (central 66 dp of the 108 dp canvas).

Requires Pillow. Idempotent: re-running produces identical files.
"""
from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path

try:
    from PIL import Image
except ImportError:  # pragma: no cover
    sys.exit("Pillow is required: pip install Pillow")

ROOT = Path(__file__).resolve().parents[1]
ORIGINALS = ROOT / "assets" / "logos" / "originals"
LOGOS = ROOT / "assets" / "logos"
RES = ROOT / "app" / "src" / "main" / "res"

# name -> original upload file (kept untouched)
SOURCES = {
    "manjaniq_logo": ORIGINALS / "manjaniq_logo_upload_c6b2faa.webp",
    "rsta_logo": ORIGINALS / "rsta_logo_upload.jpg",
    "aether_logo": ORIGINALS / "aether_logo_upload.jpg",
}

# In-app drawable size (px). Large enough for any on-screen use (max 64 dp
# at xxxhdpi = 256 px) while keeping decode memory small.
DRAWABLE_PX = 512

# Android launcher densities: (qualifier, scale factor relative to mdpi)
DENSITIES = [
    ("mdpi", 1.0),
    ("hdpi", 1.5),
    ("xhdpi", 2.0),
    ("xxhdpi", 3.0),
    ("xxxhdpi", 4.0),
]
LAUNCHER_DP = 48          # legacy icon canvas
ADAPTIVE_DP = 108         # adaptive foreground canvas
SAFE_ZONE_DP = 66         # adaptive safe zone diameter
# Fraction of the safe zone that the artwork's content box may occupy.
# 0.88 leaves a small optical margin inside the mask.
SAFE_FILL = 0.88
# Legacy (non-adaptive) icon: artwork content box fits within this
# fraction of the 48 dp canvas.
LEGACY_FILL = 0.84

BLACK = (0, 0, 0)


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def content_bbox(im: Image.Image, threshold: int = 24) -> tuple[int, int, int, int]:
    """Bounding box of non-background pixels (background is flat black)."""
    mask = im.convert("L").point(lambda v: 255 if v > threshold else 0)
    bbox = mask.getbbox()
    if bbox is None:
        raise SystemExit("logo appears to be fully black")
    return bbox


def save_png(im: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    im.save(path, format="PNG", optimize=True)
    # Verify the file really is a PNG and decodes.
    with open(path, "rb") as fh:
        assert fh.read(8) == b"\x89PNG\r\n\x1a\n", path
    Image.open(path).verify()


def fit_into(art: Image.Image, canvas_px: int, max_content_px: float) -> Image.Image:
    """
    Return an opaque black canvas_px x canvas_px image with `art` (already
    cropped to its content box) scaled proportionally so its larger side
    equals max_content_px, centred.
    """
    w, h = art.size
    scale = max_content_px / max(w, h)
    nw, nh = max(1, round(w * scale)), max(1, round(h * scale))
    resized = art.resize((nw, nh), Image.LANCZOS)
    canvas = Image.new("RGB", (canvas_px, canvas_px), BLACK)
    canvas.paste(resized, ((canvas_px - nw) // 2, (canvas_px - nh) // 2))
    return canvas


def circular(im: Image.Image) -> Image.Image:
    """Apply a circular alpha mask (for ic_launcher_round)."""
    size = im.size[0]
    # Supersample the mask for smooth edges.
    big = Image.new("L", (size * 4, size * 4), 0)
    from PIL import ImageDraw

    ImageDraw.Draw(big).ellipse((0, 0, size * 4 - 1, size * 4 - 1), fill=255)
    mask = big.resize((size, size), Image.LANCZOS)
    out = im.convert("RGBA")
    out.putalpha(mask)
    return out


def main() -> int:
    manifest: dict[str, dict] = {}

    for name, src in SOURCES.items():
        if not src.exists():
            sys.exit(f"missing original: {src}")
        im = Image.open(src)
        im.load()
        info = {
            "original": str(src.relative_to(ROOT)),
            "original_format": im.format,
            "original_size": list(im.size),
            "original_sha256": sha256(src),
        }
        rgb = im.convert("RGB")

        # 1) Genuine PNG in assets/logos (same pixels, lossless container).
        png_path = LOGOS / f"{name}.png"
        save_png(rgb, png_path)
        info["assets_png"] = str(png_path.relative_to(ROOT))

        # 2) In-app drawable (proportional downscale only).
        side = max(rgb.size)
        drawable = rgb if side <= DRAWABLE_PX else rgb.resize(
            (round(rgb.size[0] * DRAWABLE_PX / side), round(rgb.size[1] * DRAWABLE_PX / side)),
            Image.LANCZOS,
        )
        dpath = RES / "drawable" / f"ic_{name}.png"
        save_png(drawable, dpath)
        info["drawable"] = str(dpath.relative_to(ROOT))
        info["drawable_size"] = list(drawable.size)
        manifest[name] = info

    # 3) Launcher icons from the Manjaniq logo only.
    src_im = Image.open(SOURCES["manjaniq_logo"]).convert("RGB")
    bbox = content_bbox(src_im)
    art = src_im.crop(bbox)
    manifest["manjaniq_logo"]["content_bbox"] = list(bbox)

    launcher_files = []
    for qualifier, scale in DENSITIES:
        legacy_px = round(LAUNCHER_DP * scale)
        adaptive_px = round(ADAPTIVE_DP * scale)
        safe_px = SAFE_ZONE_DP * scale * SAFE_FILL

        legacy = fit_into(art, legacy_px, legacy_px * LEGACY_FILL)
        fg = fit_into(art, adaptive_px, safe_px)
        rnd = circular(legacy)

        d = RES / f"mipmap-{qualifier}"
        for fname, image in (
            ("ic_launcher.png", legacy),
            ("ic_launcher_round.png", rnd),
            ("ic_launcher_foreground.png", fg),
        ):
            p = d / fname
            save_png(image, p)
            launcher_files.append(str(p.relative_to(ROOT)))
    manifest["launcher"] = {
        "files": launcher_files,
        "background_color": "#000000",
        "adaptive_safe_zone_fill": SAFE_FILL,
    }

    out = LOGOS / "ASSET_MANIFEST.json"
    out.write_text(json.dumps(manifest, indent=2) + "\n")
    print(json.dumps(manifest, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
