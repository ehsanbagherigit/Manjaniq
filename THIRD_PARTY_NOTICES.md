# Third Party Notices — Manjaniq + Aether v2.3.0

> This file aggregates license information for the combined work (Manjaniq Android app + Aether core) for public distribution review.
> **Not legal advice** — requires qualified counsel before distribution.

## Aether v2.3.0 (pinned)
- **Source:** https://github.com/CluvexStudio/Aether tag `v2.3.0` peeled commit `6175b67df370ab856bcee07fe85b524031903956` (annotated tag object `db75c53543b8156f27ca73563e5c40148426e76a`)
- **License:** `AGPL-3.0-only` (`aether/Cargo.toml:8`, `LICENSE` in third_party/Aether-LICENSE)
- **Included via:** `aether-wrapper/Cargo.toml` git dependency `tag="v2.3.0"` + fetched tarball `third_party/aether-v2.3.0.tar.gz` (via codeload.github.com, allowed egress)
- **Vendored `quiche/` inside Aether:** `quiche/` is plain directory, not submodule — no separate fetch.

## Direct Rust dependencies (from aether/Cargo.toml + aether-wrapper)
| Crate | Version | License | Notes |
|---|---|---|---|
| aether | 2.3.0 | AGPL-3.0-only | core |
| quiche | 0.29.3 (vendored) | BSD-2-Clause | Cloudflare, workspace |
| boring | 5.2 | MIT/ISC (BoringSSL) + Apache | BoringSSL C, needs cmake/perl/clang |
| tokio | 1.52 | MIT | rt-multi-thread |
| smoltcp | 0.14 | BSD-3 | userspace TCP (no VpnService) |
| boringtun | 0.7 | MIT | WireGuard |
| reqwest | 0.12 | MIT/Apache | rustls-tls |
| serde / serde_json | 1 | MIT/Apache | |
| jni | 0.21.1 | MIT/Apache | JNI shim in wrapper |
| ring | 0.17 | MIT/Apache | crypto |
| x25519-dalek | 2.0.1 | BSD-3 | |
| arti-client (optional) | 0.46 | MIT/Apache | **tor feature OFF** — not built in this phase |
| liblzma (optional) | 0.4 | MIT/BSD | tor only |

## Manjaniq native (existing)
- `engine/rstaspoof` — Go stdlib-only; MIT-like (check rsta-source/README.md)
- `native/bridge.go` — Manjaniq, MIT

## Android / Gradle
- androidx.*, kotlin, compose — Apache-2.0
- Gradle 8.9 wrapper

## Distribution obligations (AGPL-3.0-only)
- Must offer **Corresponding Source** for combined binary (Manjaniq + Aether + quiche + wrapper + build scripts) to all recipients.
- Preserve copyright notices (CluvexStudio, Cloudflare).
- Provide install/run instructions for Corresponding Source.
- Do not remove Aether LICENSE file.
- Network use without distribution **does not** trigger offer, but APK distribution does.
- Document exact Aether commit and build steps (see AETHER_INTEGRATION.md §Build).

## Verification
- Source fetch: `curl -L https://codeload.github.com/CluvexStudio/Aether/tar.gz/v2.3.0` → OK (allowed host)
- Release assets via `release-assets.githubusercontent.com` **blocked** in sandbox egress — CI must fetch via `gh release download` with full internet.
- Rust toolchain blocked in sandbox (`static.rust-lang.org`, `crates.io` blocked) — CI builds real `libaether.so`; local APK degrades gracefully.

## Remaining blocked features
- **Tor** (`--tor`): requires `arti-client` + `liblzma` + PT binaries (`pt-build.sh` output `pt/`). Excluded — would need Go `pt` cross-compile for Android.
- **Psiphon** (`--psiphon`): requires `psiphon-tunnel-core` binary; excluded.
