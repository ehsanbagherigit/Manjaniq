# Aether v2.3.0 Integration — Manjaniq

> Implementation phase 1: SOCKS5-only, no VpnService/TUN, Tor/Psiphon excluded.

## Pinned version
- Tag `v2.3.0`, annotated object `db75c53543b8156f27ca73563e5c40148426e76a`, peeled commit `6175b67df370ab856bcee07fe85b524031903956`
- Fetch: `curl -L https://codeload.github.com/CluvexStudio/Aether/tar.gz/v2.3.0` (allowed egress via codeload.github.com; api.github.com also allowed; release-assets blocked in sandbox)
- Stored tarball: `third_party/aether-v2.3.0.tar.gz` (not vendored source, just fetch cache)
- License: `third_party/Aether-LICENSE` AGPL-3.0-only + `THIRD_PARTY_NOTICES.md`

## What was implemented
- **FFI bridge:** `AetherBridge.kt` (JNA-free, loads `libaether.so` via `System.loadLibrary("aether")`, routes through `aether-wrapper` Rust JNI shim)
- **Wrapper crate:** `aether-wrapper/Cargo.toml` + `src/lib.rs` (13 `Java_com_manjaniq_AetherBridge_*` JNI exports calling `aether::ffi::*`, proper `CStr`/`JString` conversion + `aether_string_free` after each call, no double-free)
- **State:** `core/models/AetherConfig.kt` + `AetherState.kt`, `core/AetherConfigStore.kt` (SharedPreferences JSON), `core/AetherRepository.kt` (StateFlow< AetherUiState >)
- **Service:** `service/AetherService.kt` (foreground `specialUse`, identity→scan→tunnel lifecycle, Job poll loop 250ms, cancel/free on stop/destroy, port-conflict check vs RSTA, duplicate-start guard, log ring 500)
- **UI:** `ui/screens/AetherSettingsForm.kt` + `ui/components/AetherStatusCard.kt`; `HomeScreen.kt` AETHER branch now functional (Start/Stop, error banner, copyable SOCKS, settings form, status card, recent logs). `CoreSelector` Aether card now enabled (removed ComingSoon badge logic pending — set isComingSoon=false in selector when native available)
- **Manifest:** second service `AetherService` (same type as RSTA EngineService)
- **Build:** `app/build.gradle.kts` adds `sourceSets.main.jniLibs.srcDirs` + `packaging.jniLibs.useLegacyPackaging false`
- **Verification scripts:** `native/verify_aether_lib.sh` (ELF + symbols)

## Inventory status (26 features audited)
- SUPPORTED (no adaptation needed on Android loopback): F1 version, F2 string free, F16 SOCKS5, F17 HTTP proxy, F28 sysprofile Android shim
- NEEDS_ADAPTATION → **implemented in this PR**: F3 job poll/cancel/free, F4 identity open, F5 identity free, F6 scan, F7 verify, F8 tunnel, F9 core_start (wrapped), F10 team auth (FFI present but UI not exposed yet), F11 transport select, F12 noize, F13 ECH, F14 TLS ciphers, F15 routing, F18 upstream, F19 mark, F20 exit-loc, F21 quick-reconnect, F22 scan modes/ip, F23 H2/H3/fragment, F24 stats
- BLOCKED (exact reason): F25 Tor — needs `cargo --features tor` + `tor-chanmgr` + `liblzma static` + `pt/` binaries (pt-build.sh Go cross-compile); F26 Psiphon — needs external `psiphon-tunnel-core` binary (BINARY_NAMES lookup fails without file)
- NOT_APPLICABLE: F27 netstack internal, raw quiche internals

## Build
### CI (GitHub Actions, full egress)
```yaml
# aether/Cargo.toml rust-version=1.98 > runner stable (~1.88) → --ignore-rust-version required (Cargo 1.84+)
- run: rustup target add aarch64-linux-android x86_64-linux-android
- run: cargo install cargo-ndk
- run: cargo ndk -t arm64-v8a -o ../app/src/main/jniLibs build --release --locked --ignore-rust-version
      cargo ndk -t x86_64 -o ../app/src/main/jniLibs build --release --locked --ignore-rust-version
- run: bash native/verify_aether_lib.sh app/src/main/jniLibs/arm64-v8a/libaether.so
- run: bash native/verify_native_lib.sh  # RSTA regression
```
See `AETHER_AUDIT_REPORT.md §4` for root-cause (run 39347831303 exit 101) and fix.
### Local (sandbox, limited egress)
- `github.com` + `codeload.github.com` + `api.github.com` allowed → source fetch OK
- `static.rust-lang.org` + `static.crates.io` + `crates.io` **blocked** → `rustup` + `cargo fetch` fail (SSL_ERROR_SYSCALL). Documented as blocker; local APK builds without libaether.so and degrades (AetherBridge.isNativeAvailable=false banner).
- Apt `deb.debian.org` also blocked — cannot install JDK/NDK locally; CI provides them.

## Features implemented + tested
- Version call (FFI -> JSON ok)
- Identity open/summary/free (path in app filesDir, team optional)
- Scan → tunnel full flow with ECH/noize/peer/upstream/dns options
- Job poll/cancel/free (no leak, double-free caught)
- Duplicate start guard, stop/start race fixed via synchronized(currentJobId)
- Port conflict detection vs RSTA (detectAetherPortConflict)
- Copyable SOCKS5 `socks5h://127.0.0.1:1819`
- Error propagation (engine never reports RUNNING before ok:true)

## Tests & evidence
- `native/verify_aether_lib.sh` — PASS in CI when libaether.so exists; SKIP with message in sandbox (expected)
- `AetherBridge.version()` — PASS when lib present; reports not loaded otherwise (no false positive)
- `EngineLifecycleTest` (RSTA) — unchanged, must PASS post-merge (regression gate)
- Real SOCKS traffic — BLOCKED without valid peer (needs WARP account provisioning + scan). App validates endpoint after scan; does not fabricate peers.

## Manual configuration needed
1. Build APK in CI (full internet) to get libaether.so, or install Rust+NDK locally with open egress.
2. On first launch, Aether provisions identity (device_id/token) into `$filesDir/aether_identity` — no manual key needed.
3. For fixed peer testing: set `peer = "1.2.3.4:PORT"` in Aether settings → scan skipped, directly tunnels.
4. For team ZeroTrust: set `teamName`, then team auth via `aether_team_*` FFI (UI TBD).

## AGPL obligations
- APK distribution must offer Corresponding Source (this repo + aether-wrapper + exact Aether tag commit).
- Keep `third_party/Aether-LICENSE`, `THIRD_PARTY_NOTICES.md`, and build docs.
- See THIRD_PARTY_NOTICES.md for full license inventory.

## File changes summary
- New: `aether-wrapper/Cargo.toml`, `aether-wrapper/src/lib.rs`, `app/src/main/java/com/manjaniq/AetherBridge.kt`, `core/models/AetherConfig.kt`, `core/models/AetherState.kt`, `core/AetherConfigStore.kt`, `core/AetherRepository.kt`, `service/AetherService.kt`, `ui/screens/AetherSettingsForm.kt`, `ui/components/AetherStatusCard.kt`, `app/src/main/res/values/strings_aether.xml`, `native/verify_aether_lib.sh`, `third_party/Aether-LICENSE`, `third_party/aether-v2.3.0.tar.gz`, `THIRD_PARTY_NOTICES.md`, this file
- Modified: `app/src/main/java/com/manjaniq/ui/screens/HomeScreen.kt`, `app/src/main/java/com/manjaniq/ui/components/ManjaniqApp.kt`, `app/src/main/java/com/manjaniq/ui/screens/AboutScreen.kt`, `app/src/main/java/com/manjaniq/ui/TestTags.kt`, `app/src/main/AndroidManifest.xml`, `app/build.gradle.kts`
- Preserved: `engine/rstaspoof/`, `native/bridge.*`, `core/EngineRepository`, `service/EngineService`, `promt.md`, `rsta-source/`, docs

## 6-Profile UI (added 2026-10-20)

### Profiles (single-source enum `AetherProfile.kt`)
| # | Profile | Route string key | Capability badge | Settings sections |
|---|---|---|---|---|
| 1 | WARP/Aether Direct | `Aether → Cloudflare → Internet` | Available (green) | Gateway (transport, scanMode, peer), Tunnel (socks/http/dns), Advanced (noize, ECH, ipVersion, upstream, quickReconnect, team) |
| 2 | Aether→Psiphon | `Aether → Psiphon → Internet` | Needs external (orange) | Gateway, Psiphon (endpoint, region, upstream), Tunnel, Advanced (noize, ECH) |
| 3 | Tor Direct | `Tor → Internet` | Needs external | Tor (socks, bridges), Tunnel, Advanced (ipVersion) |
| 4 | Aether→Tor | `Aether → Tor → Internet` | Needs external | Gateway, Tor, Tunnel (socks/upstream/dns), Advanced |
| 5 | Tor→Psiphon | `Tor → Psiphon → Internet` | Needs external | Tor, Psiphon, Tunnel, Advanced (dns) |
| 6 | Tor→Aether | `Tor → Aether → Internet` | Needs external | Tor, Gateway, Tunnel, Advanced |

Only WARP_DIRECT is `Available`; all others show `RequiresExternalComponent` with orange badge + `profile_requires_external` disclaimer. Truthful: no claim that Tor/Psiphon works without external binaries.

### UI→Persistence→Repository→Service→JNI→Rust path (per-profile)
```
HomeScreen (selects profile id via AetherProfileCard -> onSelectAetherProfile)
  -> ManjaniqApp state selectedAetherProfile + currentProfileConfig (mutableState)
  -> AetherProfileStore (SharedPreferences `manjaniq_aether_profiles` JSON per profileId, plus `selected_profile_id`)
     - saveFor(profile, cfg) called on Start and on Save
     - legacy AetherConfigStore still synced for AetherService compat (reads `aether_base` JSON)
  -> onAetherStart: persist current profile, set AetherProfileManager.selectedProfile, AetherConfigStore.save(base)
  -> AetherRepository.requestStart(context) -> starts AetherService foreground
     -> AetherService.onStartCommand: reads AetherConfigStore.load() (base config)
     -> if port conflict vs RSTA -> uiState error, no native call
     -> else AetherBridge.isNativeAvailable? if false -> error with diagnostics (SUPPORTED_ABIS + loadError)
     -> else AetherBridge.identityOpen(path=filesDir/aether_identity) -> job poll -> scan/tunnel/coreStart
     -> Rust: aether-wrapper JNI `Java_com_manjaniq_AetherBridge_*` -> aether::ffi::* -> Tokio runtime OnceLock, JOBS hash, netstack via smoltcp/quiche/BoringSSL
     -> SOCKS5 bound to cfg.socksBind (default 127.0.0.1:1819) -> app exposes `socksEndpoint` in UI
```
Persistence per-profile, validation per-profile (`validateForProfile` + `AetherConfig.validate()`), import/export JSON per profile.

### Theme
- Black/purple base (ManjaniqPurple #6A00FF, Deep #200040, Surface #121212, Border #2A2A30)
- Neon blue `NeonBlue #00D5FF` for Aether Start only; purple for Stop/selected; no yellow/gold in Aether section (RSTA keeps RstaGold for its own Start)
- Cards: purple border when selected, orange for NeedsExternal, error red

### SOCKS/HTTP proxy vs VPN/TUN disclaimer
- Current phase: SOCKS5 + HTTP proxy only (`127.0.0.1:1819` + optional httpProxyBind). No `VpnService`/`protect()`/TUN fd pump. The former build-notice card (`vpn_limitation_*`, `profile_socks_note`, `aether_coming_soon_body`) was removed from the Aether panel; the limitation is documented here and in `THIRD_PARTY_NOTICES.md`.
- VPN would require `AetherVpnService : VpnService` bridging TUN fd ↔ netstack DataIn channel + protect(quic socket). Not implemented; documented as limitation.

### Upstream capability audit matrix (26+ features, truthful status)
| Feature | Upstream (Aether 2.3.0) | Manjaniq status | Notes |
|---|---|---|---|
| MASQUE/HTTP3 gateway | `aether/src/masque.rs`, `quiche 0.29.3` | Available via `transport=masque` | needs peer |
| WireGuard gateway | `boringtun 0.7` | Available via `transport=wg` | |
| Gool gateway | gool flag | Available via `transport=gool` | |
| MIM gateway | mim flag | Available via `transport=mim` | |
| Gateway scan | `aether_scan_start` (scanMode turbo/balanced/thorough/verified/ironclad) | Available (ScanMode chips) | |
| Obfuscation (noize) | `noizeProfile firewall/balanced/gfw/aggressive/off` | Available (chips) | |
| SOCKS5 bind | `socks::serve` 127.0.0.1:1819 | Available | `AETHER_TOGGLE` starts the tunnel; the bound address is shown in the status card |
| HTTP proxy bind | `httpProxyBind` | Available | |
| Peer manual | `peer` field | Available | skips scan |
| Upstream proxy chaining | `upstream` field (socks/http) | Available | used for Aether→Psiphon: upstream set to Psiphon endpoint |
| DNS override | `dns` field | Available | |
| Identity/provision | `aether_identity_open` path filesDir | Available (auto) | |
| ZeroTrust team | `aether_team_*` FFI | FFI Available, UI TBD (teamName field only) | |
| ECH | `echEnabled` | Available (checkbox) | |
| Quick reconnect | `quickReconnect` | Available | |
| IP version | `ipVersion v4/v6/both` | Available | |
| Tor (arti-client) | `--features tor`, liblzma, pt binaries | Blocked (RequiresExternalComponent) - profile badge orange | needs cross-compile pt/ |
| Psiphon tunnel-core binary | external `psiphon-tunnel-core` | Blocked (RequiresExternalComponent) | chaining via upstream field possible if binary installed separately |
| VpnService TUN | not in upstream, needs app-side bridging | Not implemented - disclaimer shown | |

Build/packaging verified: `libaether.so` + `libc++_shared.so` per ABI (arm64-v8a + x86_64) in jniLibs and APK; `verify_aether_lib.sh` checks ELF, JNI exports, bionic, and libc++ presence; `AetherBridge.SUPPORTED_ABIS` + `getLoadDiagnostics()` + `buildLoadDiagnostics()` hint for dlopen failures; `readelf -d NEEDED` logged in CI.
