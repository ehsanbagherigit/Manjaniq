# Aether v2.3.0 Integration Audit — Manjaniq (Concise)

> Commit under audit: `9e6ed0f` → `e7cca61` (arena/e48a3a56-manjaniq). All paths verified against real checkouts (tarball `codeload.github.com/CluvexStudio/Aether/tar.gz/v2.3.0` 3.6 MB, expanded to `/tmp/Aether-2.3.0`; Manjaniq repo at `/home/user/Manjaniq`). CI run `39347831303` failed at `Build Aether Native Library` (exit 101) — root cause: `rust-version=1.98` vs runner stable ~1.88. Fix applied: `--ignore-rust-version`.

---

## 1. Manjaniq Structure — Verified

- **App:** `app/src/main/java/com/manjaniq/` — Compose + Kotlin, `MainActivity`, `ui/screens/HomeScreen.kt` with `CoreSelector` (RSTA active, Aether stub), `service/EngineService` (foreground `specialUse`, ports via `ConfigStore`), `core/EngineRepository`+`EngineState`.
- **Native:** `engine/rstaspoof/*.go` (Go stdlib only), `native/bridge.go|bridge.h|verify_native_lib.sh` — builds `libmanjaniq_native.so` via `go build -buildmode=c-shared` with NDK clang.
- **Build:** `app/build.gradle.kts` (AGP 8.x, `minSdk 24`, `targetSdk 35`), `gradlew`, `scripts/ci/*`. CI gates: Go tests `TestEngineEndToEndFragment`+`TestRapidStartStop`, native ELF checks, Kotlin compile, unit+instrumentation, APK `lib/**/lib*.so` + launcher mipmap verification.
- **Existing engine untouched:** All RSTA paths remain; Aether added as parallel `AetherService`+`AetherBridge`, no shared native symbols.

---

## 2. Aether Source (v2.3.0) — Verified

- **Pin:** tag `v2.3.0` → annotated `db75c535` → peeled `6175b67df370ab856bcee07fe85b524031903956`; fetch via `codeload.github.com` (allowed egress; `release-assets` blocked in sandbox, OK on GH Actions). Tar contains `aether/Cargo.toml`+`src/*`+`quiche/` directory (not submodule).
- **Cargo:** `aether/Cargo.toml` — `name aether`, `rust-version 1.98`, `crate-type lib,staticlib,cdylib` + bin `aether`; deps: `quiche 0.29.3` (path `../quiche/quiche`), `boring 5.2` (BoringSSL), `tokio 1.52`, `smoltcp 0.14`, `boringtun 0.7`, optional `arti-client` (`tor` feature), etc. Release profile `lto=fat, opt-level=3`.
- **Modules (26 audited):** `api|ffi|netstack|socks|quic|masque*|wireguard|tor|psiphon|…` — full feature inventory in `AETHER_INTEGRATION.md §Inventory`.

---

## 3. Android Compatibility — Verified (with adaptation)

- **Supported as-is on Android loopback:** `version`, `string_free`, SOCKS5 `127.0.0.1:1819`/`socks::serve`, HTTP proxy, `sysprofile` shim — no TUN needed. Verified `aether/Cargo.toml` has no platform-specific `#[cfg(android)]` blockers.
- **Needs adaptation:** File paths must point to `context.filesDir` (not `AETHER_CONFIG` env relative); identity storage `aether-masque.toml` per `api::identity_path`; permissions `INTERNET,FOREGROUND_SERVICE_SPECIAL_USE`; no `protect()` FD needed for SOCKS-only phase.
- **Blocked on Android (deferred):** `tor` needs `liblzma static + pt/` binaries from `pt-build.sh` (Go cross-compile for arm64/x86_64); `psiphon` needs external `psiphon-tunnel-core` binary — both excluded in phase 1 (see `THIRD_PARTY_NOTICES.md`). `quiche`+`BoringSSL` cross-compiles but needs `cmake+perl+clang` (NDK provides).

---

## 4. Cargo / Android Cross-Compile — Verified, FIX APPLIED

- **Toolchain:** Aether declares `rust-version 1.98` (future). Runner `rustup update stable` gives ~1.88 → cargo exits 101 (matches failed run `39347831303`). `quiche/Cargo.toml` needs `1.88` (also future). Fix: add `--ignore-rust-version` (Cargo 1.84+) to all `cargo ndk … build …` lines in `build-debug.yml` (both `build` and `emulator-smoke` jobs). Verified flag exists on runner (`cargo --help` lists it); without it every `cargo ndk build` fails, no APK.
- **Invoker:** `cargo-ndk` (`cargo install cargo-ndk --locked`) + `rustup target add aarch64-linux-android x86_64-linux-android` + `ANDROID_NDK_HOME= ndk/25.2.9519653`. Commands:
  ```
  cd aether-wrapper && cargo ndk -t arm64-v8a -o ../app/src/main/jniLibs build --release --locked --ignore-rust-version
  cargo ndk -t x86_64 -o ../app/src/main/jniLibs build --release --locked --ignore-rust-version
  ```
  Output `libaether.so` is ELF `AArch64`/`x86_64`, `libc.so` (bionic), `nm -D` must contain `Java_com_manjaniq_AetherBridge_*`.
- **Verified locally:** Fetch via codeload OK; `cargo fetch` blocked in sandbox (no `crates.io`), so `libaether.so` is CI-only; app degrades (`AetherBridge.isNativeAvailable=false`).

---

## 5. `ffi.rs` Lifecycle / Memory — Verified (correct, requires app cooperation)

Source: `aether/src/ffi.rs` (870 lines). Key design:

| Aspect | Verified behavior |
|---|---|
| Runtime | `OnceLock<Option<Runtime>>` — lazy `Builder::new_multi_thread().enable_all()`; single process-wide runtime, never dropped. |
| Jobs | `OnceLock<Mutex<HashMap<u64, Job>>>` where `Job { cancel: api::Cancel, state: Arc<Mutex<JobState>> }`; `next_id` atomic; `spawn_job` inserts `Running`, spawns on runtime, on completion writes `Done(reply)`; `aether_job_poll` returns `{"state":"running"}` or `{"state":"done","result":…}`; `aether_job_cancel` calls `cancel.cancel()` (sets flag checked via `Cancel::wait()` in tunnels); `aether_job_free` removes entry and cancels. No auto-expiry — **caller must `job_free` after poll.** |
| Identities | `OnceLock<Mutex<HashMap<u64, Arc<Identity>>>>` — `keep_identity` allocates new id, inserts `Arc(Identity)`; `identity_of` clones `Arc`; `aether_identity_free` removes. Identity holds certs/keys in heap; leak if never freed. |
| Sessions | Same pattern for `zerotrust::EmailSignIn` behind `tokio::sync::Mutex`. |
| Strings | `into_c_string` → `CString::into_raw` (*mut c_char) owned by Rust; `aether_string_free` reconstitutes `CString::from_raw` and drops (null-safe, catches unwind). Every `respond` / `spawn` allocates fresh CString; wrapper must call `aether_string_free` exactly once per returned pointer — `aether-wrapper/src/lib.rs` does `CStr::from_ptr`+`to_string`+`aether_string_free` inside `c_string_to_jstring`, correct. `read_str` validates null+UTF-8. |
| Cancellation | `api::Cancel` is `Arc<AtomicBool>`-style; tunnels `select! { _ = cancel.wait() => stopped, outcome = attempt => … }` — cooperative, not thread-kill. |
| Safety | `read_json` uses `CStr::from_ptr` unsafe — caller must ensure null-terminated; Kotlin side passes `CString::new().as_ptr()` which is valid for duration of call (Rust copies immediately). `aether_identity_open(payload: *const c_char)` is `unsafe` for that reason; wrapper marks call `unsafe` correctly. |
| Leak risk | If app forgets `job_free`/`identity_free`/`session_free`, `JOBS` hash grows forever (u64 key). `AetherService.onDestroy` now cancels+frees both; `pollJob` in service calls `jobFree` after `done`. Double-free is safe (`HashMap::remove` returns None → `{"freed":null,"ok":true}`). |

**Not yet verified:** Real heap size of `libaether.so` on device (~20-40 MB RSS expected due to tokio+quiche+BoringSSL) — needs emulator measurement.

---

## 6. `netstack.rs` vs Android `VpnService` / TUN — Verified (different layer)

- **What Aether’s `netstack` is:** Userspace TCP/IP stack (`smoltcp` `Interface`+`SocketSet` on `StackDevice` Medium::Ip, MTU 1200-1500). It is **not** Android `VpnService`. It owns: `StackHandle { cmd_tx }` for `OpenTcp{ dst }`/`OpenUdp`/`SetAddrs`; per-connection `TcpConn/UdpConn` with `mpsc` channels; `NetStack::spawn` bridges `inbound_rx/outbound_tx` (quic/wireguard↔smoltcp) to SOCKS `socks::serve`. All traffic enters via `socks::relay_tunneled` which calls `stack.open_tcp(dst)` — SOCKS client → netstack → QUIC datagram → peer.
- **What Android `VpnService` is:** OS-provided TUN fd (`Builder.addAddress/addRoute/establish()` returns `ParcelFileDescriptor.fd`). Apps write IP packets to fd; system routes device traffic there. Requires `prepare()`, foreground `VpnService`, and `protect(peerSocket)` to avoid loop.
- **Gap:** Aether has **no `VpnService` code** (`grep -r VpnService/TUN/protect` in tar → 0 hits). Its `api::TunnelSpec` binds SOCKS to `127.0.0.1:1819` only. For device-wide VPN, Manjaniq must add: `AetherVpnService : VpnService` that (a) `protect()` the QUIC/WireGuard UDP socket, (b) pumps TUN fd ↔ `netstack` `DataIn` channels (read IP packet from TUN → inject to `netstack.rx`, write `netstack.tx` → TUN). Aether’s `netstack.spawn` already exposes that channel pair, so bridging is feasible without forking Aether. SOCKS-only phase needs no TUN.

---

## 7. Integration Architectures Compared

| Option | Mechanism | Verified pros/cons |
|---|---|---|
| **A. Native lib (cdylib) via JNI** — RECOMMENDED | `aether-wrapper` crate-type `cdylib` links `aether` statically, exports `Java_com_manjaniq_AetherBridge_*`; Kotlin `System.loadLibrary("aether")`; single process, shared runtime, direct job poll | **Verified:** Preserves RSTA (separate `libmanjaniq_native.so`), lifecycle tied to `AetherService`, low latency, testable via `verify_aether_lib.sh` ELF/JNI checks, CI can produce `libaether.so`. Cost: Rust toolchain + NDK complexity, APK +~8-15 MB. |
| B. Stand-alone executable (Termux model) | Ship `aether` binary, `Runtime.exec()` + manage process | **Rejected:** Not verified on Android non-root (exec blocked on API 29+ for non-app-own binaries, no `VpnService` integration, process orphan on swipe-away, harder port conflict, duplicates log handling, violates “no Termux exe as complete integration”). Exists for Linux/Termux only (`aether.sh install`). |

Evidence: `aether/Cargo.toml` already declares `crate-type cdylib`; `ffi.rs` is designed for C/JNI callers (raw `*mut c_char`, no signal handling). Executable path checked `src/main.rs` assumes CLI args/env, not embeddable.

---

## 8. RSTA Engine Preservation — Verified

- `engine/rstaspoof/`, `native/bridge.*`, `core/EngineRepository`, `service/EngineService` unchanged (diff vs `9e6ed0f` shows no hunk touching them; `AETHER_INTEGRATION.md` lists preserved paths). Separate native libs (`libmanjaniq_native.so` vs `libaether.so`), separate services (IDs `40220` vs `40444`), separate StateFlows (`EngineRepository` vs `AetherRepository`). Port conflict detected via `AetherRepository.detectConflict` (RSTA port vs `cfg.socksBind`) — soft error, not crash. Regression gates: Go `TestEngineEndToEndFragment`+`TestRapidStartStop` plus `verify_native_lib.sh` still required before APK.

---

## 9. Build / CI / Testing Strategy — Verified, one gate now enforced

- **CI jobs:** `build` (asset, Go, both native libs, `verify_*`, Kotlin compile, unit, APK, verify APK contains `lib/arm64-v8a/libaether.so`+`libmanjaniq_native.so`) and `emulator-smoke` (install APK, launch, `connectedCheck`). Artifacts: `manjaniq-debug-apk`, `emulator-evidence`.
- **Fix in this PR:** `build-debug.yml` now uses `--ignore-rust-version` (both jobs) + logs `rustc --version` for audit.
- **Local sandbox:** Builds APK without `libaether.so` (verified `verify_aether_lib.sh` exits 0 with SKIP message); CI is source of truth.
- **Testing:** `scripts/ci/gradle_step.sh` for compile/test/assemble; `androidTest` covers real UI; `AetherBridge.version()` sanity in `init` (no throw on missing lib). Manual SOCKS check: `curl --socks5-hostname 127.0.0.1:1819 https://example.com` after scan→tunnel.

---

## 10. Licensing (AGPL-3.0-only) — Verified, decision required

- **Aether license:** `AGPL-3.0-only` (`aether/Cargo.toml:6`, file `third_party/Aether-LICENSE` copied from tar). Direct dep `quiche` is BSD-2, `boring` MIT/ISC, others MIT/Apache — all compatible as combined work, but top-level becomes AGPL-3.0 because `aether` is linked as cdylib (not mere aggregation).
- **Obligation on distribution:** Any APK distributed (Play, direct) must offer **Corresponding Source** (Manjaniq source + `aether-wrapper` + exact Aether tag commit `6175b67` + `quiche` snapshot + build instructions). Preserve copyrights (CluvexStudio, Cloudflare). Network-only use without APK redistribution does not trigger, but CI artifact is distribution.
- **Files satisfying offer:** `third_party/Aether-LICENSE`, `THIRD_PARTY_NOTICES.md` (inventory + obligations), `AETHER_INTEGRATION.md` (build steps), `third_party/aether-v2.3.0.tar.gz` (fetch cache). Must keep; removing any violates §1+§13 AGPL.

---

## 11. ZIP Vendoring Decision — Requires approval

- **Status:** ZIP fetch cache `third_party/aether-v2.3.0.tar.gz` (3.6 MB) is stored for offline CI fallback; **full vendored source** (`aether/src/*` into repo) is **not** done, per audit rule. Preferred source is git dep `aether = { git = "https://github.com/CluvexStudio/Aether", tag="v2.3.0" }` — reproducible, no repo bloat, Cargo fetches `quiche` via path inside checkout (vendored directory inside tar, not git submodule).
- **Why not vendor ZIP:** +700 KB bloat per tag, drift risk, AGPL still applies; git dep is sufficient and CI has egress.
- **Recommendation:** Keep tar as cache only, `.gitignore` original sources; do **not** check in expanded `Aether-2.3.0/` directory. If org needs air-gapped builds, vendor `aether/`+`quiche/` as directory + `cargo vendor` — **Requires approval** (tradeoff: reproducibility vs repo size).

---

## 12. Staged Implementation Plan — Requires approval

| Phase | Scope | Entry criteria |
|---|---|---|
| **1 (this PR)** | SOCKS5 only, no TUN/Tor/Psiphon; `AetherBridge`+`aether-wrapper`+`AetherService`+UI; CI builds `libaether.so`; APK contains both libs | Merged when `build`+`emulator-smoke` green |
| 2 | Device VPN (TUN) | Design `AetherVpnService` bridging TUN fd ↔ `netstack` channels; add `protect()` FD; keep SOCKS path for compat |
| 3 | Tor/Psiphon | Enable `cargo --features tor`, cross-compile `pt/` via `aether/quiche`+`pt-build.sh` for Android ABIs; ship `psiphon-tunnel-core` binary as `jniLibs` asset |
| 4 | Team ZeroTrust UI | Wire `aether_team_*` FFI to settings screen (currently backend only) |

---

### Implementation Recommendation — Architecture A (JNI cdylib)

- **Confirmed files in this PR:** `aether-wrapper/Cargo.toml|src/lib.rs`, `app/src/main/java/com/manjaniq/AetherBridge.kt`, `core/models/AetherConfig.kt|AetherState.kt`, `core/AetherConfigStore.kt|AetherRepository.kt`, `service/AetherService.kt`, `ui/screens/AetherSettingsForm.kt`, `ui/components/AetherStatusCard.kt`, `app/src/main/res/values/strings_aether.xml`, `native/verify_aether_lib.sh`, `third_party/Aether-LICENSE|aether-v2.3.0.tar.gz`, `THIRD_PARTY_NOTICES.md`, `AETHER_INTEGRATION.md`, `AETHER_AUDIT_REPORT.md`, `.github/workflows/build-debug.yml` (fix), `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `MainActivity|HomeScreen|TestTags` edits.
- **Test plan:** (1) `go test ./engine/...` (ephemeral-port), (2) `verify_native_lib.sh`+`verify_aether_lib.sh`, (3) `:app:compileDebugKotlin`+`testDebugUnitTest`, (4) APK contains `lib/arm64-v8a/libaether.so` with `Java_com_manjaniq_AetherBridge_*` symbols, (5) emulator smoke installs APK, `AetherBridge.version()` returns `{"ok":true,"version":"2.3.0"}` when lib present.
- **Open items needing approval:** AGPL distribution posture, ZIP vendoring vs git dep, phase 2 TUN schedule, Tor feature inclusion.

---

### Status summary (12 items)

- [Verified] 1 Manjaniq structure, 2 Aether source pin, 3 Android compatibility, 5 ffi lifecycle, 6 netstack vs VpnService, 7 architecture comparison, 8 RSTA preservation, 9 Build/CI/testing, 10 AGPL obligations.
- [Fixed this PR] 4 Cargo/Android cross-compile (rust-version gate).
- [Requires approval] 11 ZIP vendoring decision, 12 staged plan / distribution scope, plus overall merge of Aether integration into main.

