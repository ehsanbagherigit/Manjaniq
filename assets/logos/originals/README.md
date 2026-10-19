# Original logo uploads (do not edit)

Byte-for-byte copies of the owner's uploads, stored under their real
format extension. These are the single authoritative brand sources; every
PNG in `assets/logos/` and every drawable/mipmap in `app/src/main/res/` is
generated from them by `scripts/generate_brand_assets.py` and verified by
`scripts/check_assets.py`.

| File | Origin | SHA-256 |
|---|---|---|
| `manjaniq_logo_upload_a16b18e.jpg` | `assets/logos/manjaniq_logo.png` as uploaded in commit `a16b18ee050f5b7cdc9f3680f4b7e5c57bf5ef5f` (JPEG bytes, 3464x3464) | `4f33d00bf09d29a726e40eb7bb511c8cf651eaa3a7a97223401bc168c2917241` |
| `rsta_logo_upload.jpg` | `assets/logos/rsta_logo.png` as uploaded in commit `c651d29` (JPEG bytes, 1254x1254) | `8c4a9feca1af45e785173523bb20f5c4b72cec71d7780c8ce484567efb56acc8` |
| `aether_logo_upload.jpg` | `assets/logos/aether_logo.png` as uploaded in commit `c651d29` (JPEG bytes, 1254x1254) | `0565ee28ab8c33ad128192ddf3ea4137fe8d752b0c1394f82a030eeebb681826` |

Regenerate derived assets with:

    pip install Pillow
    python3 scripts/generate_brand_assets.py
    python3 scripts/check_assets.py
