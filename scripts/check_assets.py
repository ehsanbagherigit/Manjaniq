#!/usr/bin/env python3
"""
Asset integrity gate (run in CI before the Android build).

Checks:
  1. Every raster image under assets/logos and app/src/main/res has a
     file extension that matches its real encoded format (no JPEG bytes
     hiding behind a .png name) and decodes without error.
  2. The original uploads listed in assets/logos/ASSET_MANIFEST.json are
     present and their SHA-256 matches the manifest.
  3. Required brand resources exist: in-app logos and every launcher
     density, plus the adaptive-icon XML and background colour.
  4. The in-app Manjaniq drawable and the launcher foreground are derived
     from the *current* authoritative upload (pixel comparison against a
     downscaled copy of the original), so a stale logo cannot ship.

Exit status is non-zero on any problem; findings are printed as GitHub
annotations so they are visible without downloading logs.
"""
from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path

try:
    from PIL import Image, ImageChops, ImageStat
except ImportError:  # pragma: no cover
    sys.exit("Pillow is required: pip install Pillow")

ROOT = Path(__file__).resolve().parents[1]
LOGOS = ROOT / "assets" / "logos"
RES = ROOT / "app" / "src" / "main" / "res"
MANIFEST = LOGOS / "ASSET_MANIFEST.json"

FORMAT_FOR_EXT = {".png": "PNG", ".jpg": "JPEG", ".jpeg": "JPEG", ".webp": "WEBP", ".gif": "GIF"}
DENSITIES = ["mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"]
LAUNCHER_NAMES = ["ic_launcher.png", "ic_launcher_round.png", "ic_launcher_foreground.png"]
REQUIRED = [
    RES / "drawable" / "ic_manjaniq_logo.png",
    RES / "drawable" / "ic_rsta_logo.png",
    RES / "drawable" / "ic_aether_logo.png",
    RES / "mipmap-anydpi-v26" / "ic_launcher.xml",
    RES / "mipmap-anydpi-v26" / "ic_launcher_round.xml",
    RES / "values" / "colors.xml",
    LOGOS / "manjaniq_logo.png",
    LOGOS / "rsta_logo.png",
    LOGOS / "aether_logo.png",
] + [RES / f"mipmap-{d}" / n for d in DENSITIES for n in LAUNCHER_NAMES]

errors: list[str] = []


def err(msg: str) -> None:
    errors.append(msg)
    print(f"::error title=Asset check::{msg}")


def rel(p: Path) -> str:
    return str(p.relative_to(ROOT))


def check_format(path: Path) -> None:
    expected = FORMAT_FOR_EXT.get(path.suffix.lower())
    if expected is None:
        return
    try:
        with Image.open(path) as im:
            actual = im.format
            im.verify()
        with Image.open(path) as im:  # verify() invalidates; reopen to decode
            im.load()
    except Exception as e:  # noqa: BLE001
        err(f"{rel(path)}: cannot be decoded ({e})")
        return
    if actual != expected:
        err(f"{rel(path)}: extension says {expected} but bytes are {actual}")


def content_similarity(a: Image.Image, b: Image.Image) -> float:
    """Mean absolute RGB difference (0 = identical, 255 = opposite)."""
    size = (128, 128)
    a2 = a.convert("RGB").resize(size, Image.LANCZOS)
    b2 = b.convert("RGB").resize(size, Image.LANCZOS)
    diff = ImageChops.difference(a2, b2)
    return sum(ImageStat.Stat(diff).mean) / 3.0


def main() -> int:
    # 1) format/extension integrity for all raster assets
    rasters = [p for base in (LOGOS, RES) for p in base.rglob("*") if p.suffix.lower() in FORMAT_FOR_EXT]
    for p in sorted(rasters):
        check_format(p)
    print(f"checked {len(rasters)} raster files for format/extension integrity")

    # 2) manifest + originals
    if not MANIFEST.exists():
        err(f"{rel(MANIFEST)} missing - run scripts/generate_brand_assets.py")
        manifest = {}
    else:
        manifest = json.loads(MANIFEST.read_text())
    originals: dict[str, Path] = {}
    for name in ("manjaniq_logo", "rsta_logo", "aether_logo"):
        info = manifest.get(name)
        if not info:
            err(f"manifest has no entry for {name}")
            continue
        orig = ROOT / info["original"]
        originals[name] = orig
        if not orig.exists():
            err(f"original upload missing: {info['original']}")
            continue
        digest = hashlib.sha256(orig.read_bytes()).hexdigest()
        if digest != info["original_sha256"]:
            err(f"{info['original']}: sha256 {digest} != manifest {info['original_sha256']}")

    # 3) required resources
    for p in REQUIRED:
        if not p.exists():
            err(f"required resource missing: {rel(p)}")

    # 4) derived assets must come from the current originals
    for name, orig in originals.items():
        if not orig.exists():
            continue
        with Image.open(orig) as o:
            o.load()
            for derived in (LOGOS / f"{name}.png", RES / "drawable" / f"ic_{name}.png"):
                if not derived.exists():
                    continue
                with Image.open(derived) as d:
                    score = content_similarity(o, d)
                if score > 6.0:
                    err(f"{rel(derived)} does not match {rel(orig)} (mean diff {score:.2f}); regenerate assets")
                else:
                    print(f"ok: {rel(derived)} matches original (mean diff {score:.2f})")
            if name == "manjaniq_logo":
                # Launcher foreground: the artwork is padded into the safe zone, so
                # compare the content boxes rather than the whole canvases.
                def crop_content(im: Image.Image) -> Image.Image:
                    mask = im.convert("L").point(lambda v: 255 if v > 24 else 0)
                    return im.crop(mask.getbbox())

                fg_path = RES / "mipmap-xxxhdpi" / "ic_launcher_foreground.png"
                if fg_path.exists():
                    with Image.open(fg_path) as fg:
                        fg.load()
                        score = content_similarity(crop_content(o.convert("RGB")), crop_content(fg.convert("RGB")))
                    if score > 12.0:
                        err(f"{rel(fg_path)} artwork does not match the official logo (mean diff {score:.2f})")
                    else:
                        print(f"ok: launcher foreground matches official logo (mean diff {score:.2f})")

    # adaptive icon background colour must be the artwork's flat background (black)
    colors = (RES / "values" / "colors.xml").read_text() if (RES / "values" / "colors.xml").exists() else ""
    if 'name="ic_launcher_background">#000000<' not in colors:
        err("values/colors.xml: ic_launcher_background must be #000000 to match the logo background")

    if errors:
        print(f"\nASSET CHECK FAILED: {len(errors)} problem(s)")
        return 1
    print("\nASSET CHECK PASSED")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
