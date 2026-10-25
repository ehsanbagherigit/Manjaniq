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
| arti-client | 0.46 | MIT/Apache-2.0 | **tor feature ON** — pure-Rust Tor client, built into libaether.so |
| tor-chanmgr / tor-rtcompat | 0.46 | MIT/Apache-2.0 | tor feature (bridges, runtime) |
| liblzma | 0.4 | MIT/BSD | tor feature (compressed directory documents, static build) |
| rusqlite (via arti `static-sqlite`) | bundled | Public domain (SQLite) | tor state storage |

## Helper processes shipped beside libaether.so

Aether spawns two child processes. They are **separate executables**, not linked
into the app, and are built by `scripts/ci/build_helpers.sh` with the upstream
build scripts (`third_party/Aether/pt-build.sh`, `third_party/Aether/psiphon-build.sh`).

| Component | Source | License | Notes |
|---|---|---|---|
| lyrebird | `gitlab.torproject.org/tpo/anti-censorship/pluggable-transports/lyrebird` (pinned `fc105a03c0e0`) | BSD-3-Clause (Tor Project) | obfs4 / snowflake / webtunnel pluggable transports; Tor only, started only when a bridge names one |
| psiphon-tunnel-core | `github.com/CluvexStudio/psiphon-tunnel-core` branch `shirokhorshid` (pinned `83aa73b9…`), derived from `Psiphon-Labs/psiphon-tunnel-core` | **GPL-3.0** (upstream Psiphon) | ConsoleClient binary; started only for `--psiphon*` modes |

Both are built `GOOS=linux CGO_ENABLED=0` exactly as upstream's own android
release job does, and packaged as `lib<name>.so` entries so Android 10+ extracts
them into the read-only native library directory — the only place an app may
exec from under W^X. They run as separate processes and are not linked into the
APK's own code.

**GPL-3.0 consequence:** distributing an APK that contains
`libpsiphon_tunnel_core.so` means distributing a GPL-3.0 binary, so the
Corresponding Source for that binary (the pinned commit above plus
`scripts/ci/build_helpers.sh`) must be offered alongside it. `AETHER_INTEGRATION.md`
records the exact build steps. If the project does not want that obligation, do
not package the helper: `AetherHelpers` reports it as missing and the Psiphon
routes refuse to start with that reason instead of failing inside the engine.

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

## Feature status after this change
- **Tor** (`--tor`, `--tor-reverse`, `--tor-only`, bridgedb/onionoo, bridges): the
  `tor` feature is compiled in, so arti runs inside libaether.so. A bridge that
  names a pluggable transport additionally needs lyrebird, which CI builds and
  packages. Plain Tor needs no child process.
- **Psiphon** (`--psiphon*`): needs the `psiphon-tunnel-core` child process,
  which CI builds and packages. See the GPL-3.0 note above.
- **One start path:** the app starts Aether through `aether_core_start`, the
  core's own command line, so every flag a setting produces is applied. The
  granular FFI calls of earlier builds are no longer used or bound.
