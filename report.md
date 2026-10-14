# Manjaniq — Phase 1 Report: Project and Source Analysis

Date: 2026-10-13
Scope: Read-only inspection of the Manjaniq project and the RSTA SNI Spoof engine, evaluation of Android integration options, and identification of next steps. No project files were modified; the only file created is this report.

---

## 1. Executive Summary

- The Manjaniq project currently consists of **three planning/design documents** (`ARCHITECTURE.md`, `ROADMAP.md`, `UI_DESIGN_GUIDE.md`) and a vendored copy of the RSTA SNI Spoof engine under `rstaspoof/`. **No Android code exists yet.** The file `DEVELOPMENT_LOG.md` referenced in the Phase 1 instructions **does not exist** (and neither does a root-level `README.md`, which `ROADMAP.md` Phase 1 also references).
- The RSTA engine is a **single-file, stdlib-only Go program** (`rstaspoof/rstaspoof.go`, 1,973 lines, `package main`, version 4.0.0). It runs a **local raw TCP forward-proxy to one fixed destination** that parses and (optionally) fragments/fakes the TLS ClientHello SNI to evade DPI. It is **not** a VPN, **not** an HTTP/SOCKS proxy, and it does **not** route device traffic.
- **Verified in this environment:** the engine compiles for `GOOS=android GOARCH=arm64` with `CGO_ENABLED=0` (exit 0), a `c-shared` `.so` also builds with `CGO_ENABLED=1` + Termux clang (exit 0), and the checked-in prebuilt binary **executes successfully on this device** (`-version` and `-info` both exit 0; reports `android/arm64`, `go1.27.1`).
- **Not yet verified:** executing a Go binary from inside an APK (Android 10+ W^X restriction), `gomobile bind` (no NDK installed), and end-to-end proxy traffic on-device. These are inherently Phase 3 proof-of-concept items and cannot be fully validated without creating an APK, which Phase 1 restrictions forbid.
- **Development environment gap:** Go 1.27.1 (Termux) is present, but **JDK, Android SDK, Gradle 8.x, NDK, and adb are not installed** (`java: command not found`, apt candidate for `openjdk-17-jdk` exists, apt's `gradle` is 4.4.1 which is far too old for modern AGP). Disk has only ~5.6 GB free (95% used), which constrains SDK/NDK installation.
- **Recommended integration path (to be validated by PoC):** package and execute the Go binary as a child process from the APK (extract or ship it under the native library directory), because it requires **zero engine changes**, gives a natural process lifecycle for start/stop, isolates `os.Exit` from the app process, and its stdout already carries all logs. gomobile/c-shared embedding is the fallback but requires engine refactoring (no stop API exists today).
- **Phase 1 status: INCOMPLETE (analysis done; technical verification partially done).** Details and blockers in Section 8.

---

## 2. Current Project Structure and Progress

### 2.1 Actual file inventory (verified with `ls`/`glob`)

```
/mnt/sdcard/TermuxProjects/manjaniq/
├── ARCHITECTURE.md          (12,964 B — architecture, layers, phases)
├── ROADMAP.md               (9,198 B — 9 development phases)
├── UI_DESIGN_GUIDE.md       (11,507 B — UI/UX spec)
├── report.md                (this file — created in Phase 1)
└── rstaspoof/               (git clone of https://github.com/rstagit/rstaspoof.git)
    ├── .git/                (HEAD b64486d, branch main; tracked files clean)
    ├── README.md            (Persian documentation of the CLI tool)
    ├── rstaspoof.go         (1,973 lines — the engine, matches upstream HEAD)
    ├── config.json          (untracked; sample config, matches defaults)
    ├── rstaspoof            (untracked; 5.0 MB prebuilt ELF, GOOS=android GOARCH=arm64)
    └── در                   (untracked; 0 bytes — empty, apparently accidental)
```

### 2.2 Missing files (explicitly requested but absent)

| Requested file | Status |
|---|---|
| `DEVELOPMENT_LOG.md` (project root) | **Does not exist.** Nothing was assumed about its contents. |
| `README.md` (project root, referenced by ROADMAP Phase 1 task 1) | **Does not exist.** Only `rstaspoof/README.md` exists. |
| `go.mod` / `go.sum` / any Go module files | **Do not exist** anywhere in the project. |
| Any Android project files (`*.gradle*`, `AndroidManifest.xml`, `app/`, Kotlin sources) | **Do not exist.** |
| Version control at project root | Project root is **not** a git repo (only `rstaspoof/.git` exists). |

### 2.3 Progress versus `ARCHITECTURE.md` / `ROADMAP.md`

| Item | Evidence-based status |
|---|---|
| Planning documents (architecture, roadmap, UI guide) | **COMPLETED** — all three exist and are detailed. |
| Engine source vendored and provenance known | **COMPLETED** — clean git checkout of `rstagit/rstaspoof` @ `b64486d`; untracked: `config.json`, `rstaspoof` (binary), `در`. |
| Phase 1 tasks 1–3 (review docs/source, identify config/exec flow/shutdown, identify adaptation needs) | **COMPLETED by this report** (Sections 3–5). |
| Phase 1 task 4 (verify development environment and Android build requirements) | **PARTIAL** — Go toolchain verified present and working; Android toolchain (JDK/SDK/Gradle/NDK/adb) verified **absent**; installation not attempted (would be an implementation action). |
| Phase 1 task 5 (target Android architectures and minimum version) | **PARTIAL** — device verified: `arm64-v8a`, Android 16 (API 36). Project minSdk/targetSdk and supported ABI list are still open decisions (Section 8, Questions). |
| Phase 1 task 6 (record uncertainties/risks) | **COMPLETED** — Sections 5 and 8. |
| Phase 2+ (Android project, engine adapter, UI, persistence, release) | **NOT_STARTED** — no source code exists. |
| "Select and validate the Android integration mechanism" (ARCHITECTURE Phase 1) | **PARTIAL** — options evaluated and a recommendation made; end-to-end validation requires an APK and is blocked by Phase 1 restrictions (see Section 6). |

**Conclusion:** the project is ready to *begin* Android implementation only after the two blockers in Section 8 are resolved; it is not ready today (no Android toolchain, open protocol/ABI questions).

---

## 3. Detailed RSTA Engine Analysis

Everything in this section marked **[VERIFIED]** was read directly from source or produced by an executed command. Items marked **[ASSUMPTION]** or **[UNRESOLVED]** are explicitly not asserted as fact.

### 3.1 Purpose and internal architecture

**[VERIFIED]** The engine is a local TCP forwarder with TLS ClientHello manipulation:

- Client connects to a local listen address (default `0.0.0.0:40443`).
- The engine reads the first payload (30 s deadline), parses it as a TLS ClientHello if possible, extracts the real SNI.
- It dials **one fixed upstream** (`CONNECT_IP:CONNECT_PORT`, default `104.18.38.202:443`) — the destination is configured once, **not per client connection**.
- It applies a "bypass strategy" to the first payload (fragmentation and/or a fake-SNI probe connection), then pipes bytes bidirectionally.
- A monitor collects connection events and prints them to stdout.

Internal structure of `rstaspoof/rstaspoof.go` (single file, `package main`):

| Lines (approx.) | Component |
|---|---|
| 23–105 | Constants, 1 MB buffer pool, color detection (`init()` checks `os.Stdout` TTY, `NO_COLOR`/`FORCE_COLOR`), event name constants |
| 107–467 | Monitor: `PacketEvent`, `ConnState`, `MonitorState`, `PacketMonitor` (`Emit`/`apply`/`printEvent`/`PrintStats`), globals `globalMonitor`/`monitorMu` |
| 469–678 | TLS: hardcoded cipher-suite/extension blobs, `TLSExtensionBuilder`, `ClientHelloBuilder` (`BuildClientHello`, `ParseClientHello`) |
| 680–784 | `FragmentEngine`: strategies `sni_split`, `half`, `multi` (24 B), `tls_record_frag`, `none` |
| 786–964 | `BypassStrategy` interface + `FragmentBypass`, `FakeSNIBypass`, `CombinedBypass` |
| 966–1032 | `ConnectionTracker` (failure/success counters), `newConnID` |
| 1034–1074 | `PipeWorker` (bidirectional copy with pooled 1 MB buffers, byte-count events every 256 KB) |
| 1076–1373 | `ProxyServer`: `Start()` accept loop, `handleConnection()`, `applyStrategyInstrumented()` |
| 1375–1501 | `defaultConfig`, `ConfigLoader` (load/generate/merge JSON), `StrategyFactory`, `AddressParser` |
| 1503–1522 | `resolveHost` (DNS), `getDefaultInterfaceIPv4` (UDP route probe), `PlatformInfo`, help/banner printers |
| 1824–1973 | `main()`: flag parsing, config precedence, monitor setup, signal handler, `server.Start()` |

Global mutable state: `globalMonitor`, `connCounter`, `connTracker`, `colorEnabled`, `defaultConfig`. **There is no instance-scoped engine object exposed outside `main()`; the program is not structured as a library.**

### 3.2 Main execution flow and networking behavior

**[VERIFIED]** `main()` (line 1824) order:

1. Parse flags with `flag.NewFlagSet(..., flag.ExitOnError)` (unknown flag → exit code 2).
2. Handle `-version`, `-help`, `-generate-config`, `-tddd` (preset: listen `:40443`, connect `104.19.230.21:443`, sni `www.hcaptcha.com`, method `combined`), `-info`.
3. Build config: **defaults → config file → CLI flags (highest precedence)**. Without `-config`, it auto-loads `config.json` or `snispf.json` **from the current working directory** (lines 1889–1898).
4. `resolveHost(CONNECT_IP)` via `net.LookupHost` (no-op for IP literals — the defaults are literals).
5. `getDefaultInterfaceIPv4()` dials a **UDP socket to `dest:53`** to discover the local source IP of the active route and binds outbound connections to it (`d.LocalAddr`). No DNS packets are actually sent by this call (UDP `Dial` does not transmit).
6. Initialize monitor (unless `-quiet`/`-no-monitor`/`MONITOR:false`) and build the strategy.
7. Register a signal handler: on `SIGINT`/`SIGTERM` → `PrintStats()` → **`os.Exit(0)`** (lines 1958–1967).
8. `server.Start()` — **blocks forever** in the accept loop.

**Connection handling** (`handleConnection`, line 1148): read first data → parse ClientHello → dial upstream (15 s timeout, keep-alive 30 s) → `applyStrategyInstrumented` (fragments + sleeps between pieces; on failure falls back to sending the raw first payload) → two `PipeWorker` goroutines copy both directions → when either side ends, the other connection is closed → `EvConnClose` emitted. If the server never responded, `EvDPIBlocked` is emitted.

**Shutdown behavior [VERIFIED]:**

- The only shutdown path is an OS signal; then the process exits. `Start()` has **no stop mechanism**: `defer ln.Close()` (line 1121) is unreachable, the accept loop does `continue` on **any** error (line 1133 — a broken listener would spin with no backoff), and there is no context/cancel/WaitGroup API.
- If `net.Listen` fails, `Start()` returns the error and `main` executes `os.Exit(1)` **without printing the error** (lines 1970–1972). A port conflict therefore produces **silent exit code 1, no output**. **This is a significant integration finding.**
- `AddressParser.Parse` calls `os.Exit(1)` itself on an invalid port (lines 1478, 1492).

### 3.3 Command-line arguments and configuration

**[VERIFIED]** Flags (lines 1825–1845): `-help`, `-tddd`, `-config FILE`, `-generate-config FILE`, `-listen HOST:PORT`, `-connect IP:PORT`, `-sni HOSTNAME`, `-method fragment|fake_sni|combined`, `-fragment-strategy sni_split|half|multi|tls_record_frag`, `-fragment-delay FLOAT`, `-ttl-trick`, `-no-raw`, `-no-monitor`, `-verbose`, `-quiet`, `-version`, `-info`.

- **`-no-raw` and `-verbose` are parsed but ignored** (`_ = fNoRaw; _ = fVerbose`, lines 1843–1844). The help text advertises both, including "Disable raw socket injection", **but no raw-socket code exists anywhere in the source** (only `syscall.SIGINT`/`SIGTERM` are imported from `syscall`).
- README documents a subset (missing `-help`, `-version`, `-verbose`, `-no-raw`).

**[VERIFIED]** JSON config keys (defaults, lines 1375–1387): `LISTEN_HOST`, `LISTEN_PORT`, `CONNECT_IP`, `CONNECT_PORT`, `FAKE_SNI`, `BYPASS_METHOD`, `FRAGMENT_STRATEGY`, `FRAGMENT_DELAY`, `USE_TTL_TRICK`, `FAKE_SNI_METHOD`, `MONITOR`. The `StrategyFactory` also reads `FAKE_SNI_FRAGMENT_REAL` (line 1449), which is absent from `defaultConfig`. `rstaspoof/config.json` matches the defaults (listen `0.0.0.0:40443`, connect `104.18.38.202:443`, method `fragment`).

**[VERIFIED] Notable behaviors:**

- `-tddd` help text (line 1579) claims default connect `104.19.230.21` while `defaultConfig` uses `104.18.38.202` — documentation inconsistency inside the tool itself.
- Unknown `-method` prints a warning and silently falls back to `fragment` (line 1459).
- Default listen address is `0.0.0.0` — on Android this exposes the proxy on all interfaces (Wi‑Fi/cellular), not just loopback.

### 3.4 TCP, UDP, DNS, TLS/SNI behavior

| Aspect | Finding |
|---|---|
| TCP | **[VERIFIED]** The proxy itself is TCP-only. `net.Listen("tcp", ...)`, 32,768-connection semaphore, 4 MB read/write socket buffers requested (kernel may clamp), `TCP_NODELAY` toggled around fragment writes, keep-alive 30 s. |
| UDP | **[VERIFIED]** No UDP proxying. The only UDP use is the local route probe in `getDefaultInterfaceIPv4` (line 1515). |
| DNS | **[VERIFIED]** `net.LookupHost` is called once at startup, only if `CONNECT_IP` is a hostname; defaults are IP literals so no DNS is performed. Client-side SNI/domain resolution is never seen by the engine (clients dial the local proxy). **[ASSUMPTION]** With `CGO_ENABLED=0` on Android, Go's pure resolver may behave differently from the cgo/bionic resolver (Android has no reliable `/etc/resolv.conf`); this only matters if hostnames are accepted — untested. |
| TLS/SNI | **[VERIFIED]** No use of `crypto/tls` and no certificate handling — the engine never terminates TLS, it only **manipulates bytes**: parses the ClientHello record to read SNI (`ParseClientHello`), splits the record at/around the SNI (`sni_split`) or into halves/24-byte chunks/TLS records, with inter-fragment delays (default 0.1 s). `fake_sni`/`combined` build a synthetic ClientHello (hardcoded cipher suites/versions, `BuildClientHello`) and send it on a **separate short-lived probe connection**. |
| "TTL trick" | **[VERIFIED as implemented]** Despite the name and event labels (`FAKE_SNI_TTL`, "dies before server"), **no IP TTL is ever set** (no raw sockets, no `IP_TTL` option anywhere). `-ttl-trick` only adds an extra probe connection carrying a fake ClientHello, then fragments the real ClientHello with fixed 100 ms delays (`ttlTrickAndFragment`, line 868). |
| Failover | **[VERIFIED as dead code]** `ConnectionTracker.RecordFailure/RecordSuccess` are updated and `ShouldFailover` exists (line 1014) but **is never called** — no failover ever triggers. |

### 3.5 External dependencies and Go build requirements

**[VERIFIED]** Imports are **standard library only**: `encoding/binary`, `encoding/hex`, `encoding/json`, `flag`, `fmt`, `io`, `log`, `math/rand`, `net`, `os`, `os/signal`, `runtime`, `strings`, `sync`, `sync/atomic`, `syscall`, `time`. No third-party modules, no `go.mod` (single-file build, package path `command-line-arguments`).

**[VERIFIED] Build results from this environment (Go 1.27.1, Termux):**

| Target | Command | Result |
|---|---|---|
| `android/arm64`, `CGO_ENABLED=0` | `go build -o <out> rstaspoof.go` | **OK** (exit 0, 5.1 MB) |
| `android/arm`, `android/386`, `android/amd64`, `CGO_ENABLED=0` | same | **FAIL** — "requires external (cgo) linking, but cgo is not enabled" |
| `android/arm64` `c-shared`, `CGO_ENABLED=1`, `CC=clang` | `go build -buildmode=c-shared` | **OK** (exit 0, 5.2 MB `.so`) |
| `android/arm64` `c-archive` | `go build -buildmode=c-archive` | **Not supported on android/arm64** |
| `go vet ./` | — | **Fails** — no `go.mod` ("cannot find main module"). Vet can only be run per-file/directly, or after adding a module. |

**[VERIFIED] Checked-in binary** (`rstaspoof/rstaspoof`): ELF64 AArch64, Go build info says `go1.27.1`, `GOOS=android`, `GOARCH=arm64`, `CGO_ENABLED=1`, `-buildmode=exe`, built from `command-line-arguments`. It **ran successfully on this device** when copied to an executable location:

- `rstaspoof -version` → `RSTA SNI Spoof 4.0.0`, exit 0
- `rstaspoof -info` → platform table (`android/arm64`, `go1.27.1`, 6 CPUs), exit 0

**[UNVERIFIED]** Whether the checked-in binary and freshly built binaries can be **executed from inside an APK** — see Section 4 and Section 5 (R1).

### 3.6 Potential compatibility issues on Android

**[VERIFIED from source / environment]:**

1. **No library API** — everything is `package main` + `flag` + `os.Args`; integration requires either process execution or a refactor.
2. **`os.Exit` in three places** (silent listen failure, `AddressParser` invalid port, signal handler). Fatal if embedded in-process; harmless if run as a child process.
3. **No stop/graceful-shutdown API** — `Start()` never returns; embedded use would leak a goroutine and leave the port bound until process death.
4. **Startup errors are silent** (exit 1 with zero output on bind failure) — the app layer must detect failure by exit code or pre-check the port.
5. **CWD-dependent config auto-load** (`config.json`/`snispf.json`) — an Android app process' CWD is typically `/`, so auto-load will not find app files; explicit `-config` path or flags must be used.
6. **All logging goes to stdout via `fmt.Printf`/`log.Logger`** with ANSI colors (colors auto-disable when stdout is not a TTY, which is the normal case for a piped child process — verified logic in `init()`, lines 51–65).
7. **`0.0.0.0` default listen** exposes the proxy beyond loopback; mobile-friendly default would be `127.0.0.1` (can be set via config without touching source).
8. **Resource defaults are desktop-grade**: 1 MB pooled buffers per direction, 4 MB socket buffers, 32,768 max connections — inappropriate for low-end phones; overridable only partially via config (socket buffer sizes and `MaxConcurrentConns` are compile-time constants).
9. **Fixed upstream at startup** — `getDefaultInterfaceIPv4` binds outbound to the local IP of the default route *at start time*; a network handover (Wi‑Fi → cellular) may break new connections. **[ASSUMPTION]** not tested.
10. **Toolchain gaps**: only `arm64` builds without cgo in this environment; other ABIs need cgo/NDK; no JDK/SDK/Gradle installed; only ~5.6 GB disk free.
11. **Permissions**: the app will need `android.permission.INTERNET` (normal permission) for sockets. No root is required for any code path. **[VERIFIED]** no `CAP_NET_RAW`/raw-socket usage exists.

### 3.7 Status, error, and log reporting

**[VERIFIED]:**

- Structured-ish **event stream on stdout**: `CONN_OPEN`, `TLS_PARSED`/`TLS_UNKNOWN`, `FRAG_START`/`FRAG_PIECE`, `FAKE_SNI_SEND`/`FAKE_SNI_TTL`, `SVR RESP`, `BLOCKED`, `BYPASS OK`, `DATA_C2S`/`DATA_S2C` (every 256 KB), `CONN_CLOSE`, `CONN_ERROR` — printed with timestamps, connection IDs, and ANSI colors (disabled when piped).
- **Some event constants are never emitted**: `FRAG_DONE`, `REAL_HELLO_SEND`, `FAKE_SNI_INJECT` are handled in `printEvent` but no code emits them.
- `PrintStats()` summary is only printed on SIGINT/SIGTERM before exit.
- There is **no "ready/listening" signal**, no machine-readable state machine, and no distinction between stdout/stderr. An app can only infer: (a) parse stdout text, (b) poll the port, or (c) detect silent exit-code-1 failures.
- The banner printed after successful bind (line 1122) effectively doubles as a "ready" marker (`Configure your app → 127.0.0.1:<port>`), which a stdout reader could key on — but it is formatted text, not a contract. **[ASSUMPTION]** parsing it is possible but brittle.

### 3.8 Can it be integrated without major changes?

**[VERIFIED conclusion]:**

- **As a child process: YES.** Zero source changes needed. Start = `exec -listen 127.0.0.1:40443 ...`, logs = stdout capture, stop = SIGTERM (the existing handler prints stats and exits; `os.Exit` only terminates the child), failure = exit code 1 (silent, so the app should also pre-validate config/port).
- **As an in-process library (gomobile/c-shared): NOT without adaptation.** Blockers: `main()`-only structure, no exported start/stop/status API, no shutdown mechanism, `os.Exit` paths, global singleton monitor. `ARCHITECTURE.md` §4.7 and ROADMAP Phase 3 ("Adapt the Go engine for controlled invocation") already anticipate this refactoring — it is a Phase 3 decision, not a Phase 1 change.

---

## 4. Android Compatibility and Integration Options

**Important framing (per ARCHITECTURE §10 and ROADMAP §5):** the initial app **controls a local proxy only**. It is not a VPN, does not use `VpnService`, and does not route all device traffic. Users must point compatible client apps at `127.0.0.1:<port>` themselves.

### Option A — Embed Go via an Android shared library (gomobile bind / c-shared)

**How:** wrap the engine in an exported Go API, build `librsta.aar` (gomobile) or a `.so` loaded through JNI.

| | |
|---|---|
| Advantages | In-process control; rich typed API; no process management; logs can be delivered via callback instead of stdout parsing. |
| Disadvantages/requirements | Requires engine refactor (package layout, exported `Start/Stop/Status/Logs`, removal/bypass of `os.Exit`, a real shutdown path). Requires **gomobile or NDK + clang + CGO**; gomobile itself is an extra Go toolchain dependency. |
| Risks | **Highest-risk assumptions:** `os.Exit`/signal handling must be neutralized or the whole app process dies; a panic or leaked listener cannot be cleaned up by killing a process; every Go-runtime bug now crashes the app. `c-archive` is unsupported on android/arm64 (verified), so `c-archive`-style static integration is out. |
| Verification status | `c-shared` build for android/arm64 **succeeds** (verified, 5.2 MB) but exports nothing useful without `//export` wrappers; `gomobile bind` **not installed/not tested** (no NDK). |

### Option B — Package and execute a native Go binary (process model) — **RECOMMENDED**

**How:** ship the prebuilt/cross-built `android/arm64` executable in the APK (practically: as a JNI-library-named file so it is installed under `nativeLibraryDir`, or extracted to a permitted location), start it with `ProcessBuilder`/`Runtime.exec`, read stdout, stop with `destroy()`/SIGTERM.

| | |
|---|---|
| Advantages | **Zero engine changes** (satisfies "preserve the existing implementation"); natural lifecycle (process start = engine start, process death = engine death); `os.Exit`, signal handler, and panics are isolated from the app; stdout already contains the full event stream; smallest PoC; the same binary also works for desktop/Termux testing. |
| Disadvantages/requirements | Must solve **where the binary can live and be executed** on modern Android (see R1); per-process overhead (~5–10 MB RSS for the Go runtime); stdout must be parsed (text, not structured API); only `arm64` build verified so far. |
| Risks | **R1 (primary):** Android 10+ blocks `exec()` of files in the app's *writable home* directory for modern target SDKs — the standard workaround is to ship the executable *as* a native library (installed read-only in `nativeLibraryDir`) and exec it there; this workaround is **unverified for this project and must be the first PoC experiment**. R2: process lifecycle vs. Android background-execution limits (keep the service in foreground while running). |
| Verification status | Binary builds for `android/arm64` (verified) and **runs on-device** (verified `-version`/`-info` from `/tmp`). APK packaging + exec from install location: **not verified (Phase 3 PoC)**. |

### Option C — Rewrite components in Kotlin

| | |
|---|---|
| Advantages | Full control of lifecycle/status/logging; no exec or cgo questions. |
| Disadvantages | Re-implements ClientHello parsing/building, fragmentation, bypass strategies — ~1,900 lines of delicate byte-level networking; directly contradicts `ARCHITECTURE.md` goals ("Reuse of existing, working code", "Do not rewrite or replace the existing RSTA engine") and `UI_DESIGN_GUIDE.md` §11. |
| Risks | Behavioral regressions; large upfront cost; no benefit for Phase 3 goals. |
| Recommendation | **Only for thin Kotlin glue** (process manager, config validation, log capture) — never for the networking core. |

### Why not assume anything works

Every option above carries at least one unverified assumption (marked). The ROADMAP itself designates **Phase 3 ("RSTA Integration Proof of Concept")** as the gate: "Do not proceed to full UI integration until this proof of concept succeeds." Phase 1 may therefore legitimately end with a recommendation plus a defined PoC, but **not** with a claim of validated integration.

---

## 5. Technical Risks and Unresolved Questions

### Risks (ordered by severity)

| ID | Risk | Evidence | Impact | Mitigation |
|---|---|---|---|---|
| R1 | Executing a Go binary from an APK may be blocked by Android 10+ W^X policy | Android platform restriction (not tested here) | Could invalidate Option B | **First PoC experiment:** ship binary as `jniLibs/lib*.so`, exec from `nativeLibraryDir`. Fallback: Option A. |
| R2 | No Android build toolchain installed; only 5.6 GB free disk | `java: command not found`; apt gradle = 4.4.1; `df` 95% used | Blocks Phases 2–3 | Install JDK 17 + Android cmdline-tools; use Gradle wrapper (8.x); prune caches; or build on a separate machine. |
| R3 | Engine has no stop API / silent bind failure / `os.Exit` paths | rstaspoof.go:1115–1121, 1478, 1970–1972 | Embedded integration would crash the app; process integration must pre-validate ports and read exit codes | Prefer Option B; if Option A, refactor in Phase 3 with explicit lifecycle tests. |
| R4 | Only `android/arm64` builds without cgo in this environment | Build matrix in §3.5 | 32-bit/x86 devices unsupported | Ship arm64-v8a only initially (device is arm64-v8a); add NDK builds for other ABIs later if required. |
| R5 | Misleading protocol expectations: engine is not an HTTP/SOCKS proxy (no `CONNECT` handling, single fixed upstream) | Source: no HTTP parsing anywhere; fixed `connectPort` in `ProxyServer` | Users may configure system proxy settings that cannot work; UI could make false claims | Resolve Q1 below before writing any UI copy; test with a real client in PoC. |
| R6 | Default `0.0.0.0` listen exposes the proxy on all interfaces; 32,768 conns / 4 MB buffers / 1 MB pools are desktop defaults | rstaspoof.go:33–39, 1376 | LAN exposure, memory pressure on phones | Default to `127.0.0.1` via app-side config (no source change); consider Phase 3 tuning constants if OOM observed. |
| R7 | `go vet`/module tooling unusable (no `go.mod`) | Verified error | Weaker static analysis | Phase 3: build within a module inside the Android project (without altering upstream file semantics). |
| R8 | CWD-based config auto-load behaves differently on Android | rstaspoof.go:1889–1898 | Unexpected config or none | Always pass explicit `-config <absolute path>` or flags from the app. |
| R9 | Startup DNS/resolver and network-handover behavior untested with `CGO_ENABLED=0` on Android | §3.4 | Hostname configs or post-handover connections may fail | Restrict configs to IP literals initially (Q5); test handover in Phase 7. |
| R10 | Project hygiene: empty stray file `rstaspoof/در`, untracked 5 MB binary, no root git repo, missing `DEVELOPMENT_LOG.md` | `ls -A`, `git status` | Clutter, confusion, no change-tracking at project root | User decision (Q6); not touched in Phase 1 per restrictions. |

### Unresolved questions requiring your decision

1. **Q1 — Client usage model (critical for PoC and UI).** The engine forwards raw TCP to a single fixed destination and never speaks HTTP/SOCKS. How are client apps supposed to use `127.0.0.1:40443`? (a) An app that connects with TLS *directly to that address* (treating the proxy as its TLS endpoint), or (b) something else (e.g., an HTTP proxy client, which the current code cannot serve)? This determines the PoC test client and all UI wording.
2. **Q2 — Integration path confirmation.** Approve **Option B (exec child process)** as primary for the PoC, with Option A (gomobile/c-shared + engine refactor) as the documented fallback? Or do you want Option A pursued first?
3. **Q3 — ABI/minSdk policy.** Recommend: primary `arm64-v8a` only for the PoC (the only verified build target and this device's ABI), `minSdk ≥ 26`, `targetSdk 35`. Confirm, or specify required ABIs (armeabi-v7a/x86_64 need NDK/cgo builds — unverified).
4. **Q4 — Build environment.** Install JDK 17 + Android SDK inside this Termux/PRoot environment (disk is 95% full), use a proot distro, or build/test on a separate machine? This must be decided before Phase 2.
5. **Q5 — Configuration scope for the first release.** Confirm: IP-literal `CONNECT_IP` only (avoids R9), `127.0.0.1` listen default, methods `fragment`/`fake_sni`/`combined` exposed, `-ttl-trick` label corrected or hidden given the verified no-TTL behavior (UI must not claim capabilities that don't exist — `UI_DESIGN_GUIDE.md` §7B/§12).
6. **Q6 — Missing/stray files.** Should `DEVELOPMENT_LOG.md` be created later (I was barred from creating it in Phase 1)? Should `rstaspoof/در` and the untracked binary be cleaned up (requires lifting the "do not modify" restriction)?
7. **Q7 — Engine-change authorization for Phase 3.** ROADMAP Phase 3 says "Adapt the Go engine for controlled invocation" while `ARCHITECTURE.md` says preserve the implementation. Confirm that *minimal, documented* engine changes (e.g., exported start/stop API, no silent exit) are authorized when Option A is chosen — and that Option B avoids this question entirely.

---

## 6. Recommended Integration Approach and Proof of Concept

**Recommendation: Option B — package and execute the Go binary as a child process** (with Option A held as a documented fallback).

Rationale (all evidence-based): it is the only option with **zero engine changes** (satisfying the preservation constraints), it was the only option already partially verified in this environment (compile ✓, on-device execution ✓), it isolates `os.Exit`/signal handling from the app, and its stdout already provides the full log/event stream the UI needs. Option A's prerequisites (NDK/gomobile + refactor for lifecycle) are strictly larger and riskier; Option C is ruled out by the project's own architecture documents.

**Smallest PoC that validates the critical assumption (for Phase 3, after Phase 2 scaffolding):**

1. Provision the build toolchain (JDK 17 + Android SDK + Gradle wrapper) — **hard prerequisite** (R2/Q4).
2. Create a *minimal* single-activity Android app (this counts as Phase 2's smallest slice) that packages the existing `android/arm64` binary as a native-library-named file (e.g., `app/src/main/jniLibs/arm64-v8a/librstaspoof.so`).
3. **Core experiment:** `ProcessBuilder` exec from `nativeLibraryDir` with `-listen 127.0.0.1:40443 -connect <test-ip>:443 -sni <host> -method fragment`. Success criteria:
   - Process starts (not blocked by W^X — **this is the make-or-break result**);
   - stdout is readable and contains the running banner;
   - a local TCP client can connect to `127.0.0.1:40443` and bytes flow to the configured upstream;
   - SIGTERM → stats printed → clean exit → port released → repeated start/stop works;
   - a bind conflict produces a detectable failure (expected: exit code 1, silent output).
4. If step 3 fails at exec: pivot to Option A PoC (add minimal exported Go wrapper + `gomobile bind`/c-shared + JNI), and record the pivot in `DEVELOPMENT_LOG.md`.

**Explicitly out of scope for the PoC:** full UI, profiles/persistence, VPN routing, multi-ABI builds, engine refactoring.

---

## 7. Ordered Next Steps

1. **Resolve Q1 (client usage model)** — decide how client apps consume the proxy; obtain/confirm a test client. Everything in the PoC's "bytes flow" check depends on it.
2. **Resolve Q4 (build environment)** — install JDK 17 + Android SDK cmdline-tools + Gradle wrapper in this environment (or designate a build machine), verifying free disk before installing SDK/NDK components. Record versions.
3. **Resolve Q2/Q3/Q5** — confirm Option B, `arm64-v8a`-only first, minSdk/targetSdk, and first-release config scope.
4. **Create `DEVELOPMENT_LOG.md`** (after lifting the Phase 1 restriction) and log: environment inventory, build-matrix results from §3.5, and this report's decisions.
5. **Phase 2 minimal slice:** create the Android project skeleton (Kotlin + minimal Compose activity, `INTERNET` permission, package structure per `ARCHITECTURE.md` §3), build an installable debug APK — no feature UI yet.
6. **Phase 3 PoC steps 1–4 from Section 6** — the decisive Go-to-Android validation, executed *before* any real UI work (per `ROADMAP.md` §4).
7. **On PoC success:** define the engine interface contract (`start/stop/getStatus/logs`, states STOPPED/STARTING/RUNNING/STOPPING/ERROR per `ARCHITECTURE.md` §5) mapped onto process lifecycle events; then Phase 4 (adapter) → Phase 5 (UI, per `UI_DESIGN_GUIDE.md`).
8. **Housekeeping (needs approval):** decide on `rstaspoof/در`, the untracked 5 MB binary, and root-level version control.
9. **Optional hardening later:** add `go.mod` for vet/CI; cross-compile other ABIs with NDK; evaluate reducing `MaxConcurrentConns`/buffer constants for mobile.

---

## 8. Phase 1 Completion Status

**Status: INCOMPLETE — analysis complete, verification partial, no blockers removed that require your input.**

Completed (verified):

- Engine behavior, execution flow, CLI/config surface, lifecycle/shutdown, TCP/UDP/DNS/TLS behavior, dependencies, logging/error behavior — all documented from source (Section 3).
- Android integration options evaluated with advantages/disadvantages/risks (Section 4).
- Risks and unresolved questions recorded (Section 5).
- Development environment probed: Go present and producing `android/arm64` exe + `c-shared` `.so`; prebuilt binary runs on-device; JDK/SDK/Gradle/NDK/adb **absent** (Section 3.5).

Not complete / blocked:

1. **`DEVELOPMENT_LOG.md` does not exist** — the instruction to read it could not be fulfilled; contents unknown, nothing assumed.
2. **"Validate the Android integration mechanism" (ARCHITECTURE Phase 1) cannot be fully completed under Phase 1 restrictions** — validation requires creating an APK, which is forbidden here. Recommendation + PoC plan are delivered; validation itself is Phase 3 work.
3. **Android build requirements identified but not provisioned** (no JDK/SDK/Gradle) — ROADMAP Phase 1 task 4 remains partial until the toolchain is installed (Q4 decision needed).
4. **Target minSdk/ABI policy not yet decided** (device facts verified; project policy is Q3).
5. **Q1 (how clients actually use the proxy) is unresolved** and is a prerequisite for a meaningful PoC and honest UI copy.

Phase 1 should be marked **COMPLETED** only after: (a) Q1–Q4 decisions are recorded (ideally in a new `DEVELOPMENT_LOG.md`), and (b) the Phase 3 PoC confirms or rejects the recommended integration path. Writing this report alone does **not** complete Phase 1.

---

### Appendix — Verification log (commands executed, read-only except temp builds)

| Action | Result |
|---|---|
| `ls`/`glob` of project root and `rstaspoof/` | 3 docs + engine dir; `DEVELOPMENT_LOG.md`, root `README.md`, `go.mod` absent |
| Full read of `ARCHITECTURE.md`, `ROADMAP.md`, `UI_DESIGN_GUIDE.md` | OK |
| Full read of `rstaspoof/README.md`, `config.json`, `rstaspoof.go` (1,973 lines) | OK |
| `go version` | `go1.27.1 android/arm64` (Termux) |
| `which java gradle adb sdkmanager` / `pkg list-installed` | none installed |
| `go build` `GOOS=android GOARCH=arm64 CGO_ENABLED=0` → `/tmp/opencode/` | exit 0 (5.1 MB); project files untouched |
| `go build` for `android/arm|386|amd64` (`CGO_ENABLED=0`) | fail: external (cgo) linking required |
| `go build -buildmode=c-shared` (`CGO_ENABLED=1`, `CC=clang`) | exit 0 (5.2 MB `.so`) |
| `go build -buildmode=c-archive` | not supported on android/arm64 |
| `go vet ./` | fails: no `go.mod` |
| `go version -m rstaspoof/rstaspoof` | `go1.27.1`, `GOOS=android`, `GOARCH=arm64`, `CGO_ENABLED=1`, exe |
| Ran prebuilt binary `-version` / `-info` from `/tmp` | exit 0 / exit 0, `android/arm64` |
| `git -C rstaspoof status/log/remote` | clean tracked tree @ `b64486d`; remote `rstagit/rstaspoof`; untracked `config.json`, `rstaspoof`, `در` |
| `apt-cache policy openjdk-17-jdk gradle` | JDK 17 available; gradle only 4.4.1 (too old) |
| `getprop` / `df` | Android 16, `arm64-v8a`, 5.6 GB free |
| Fetched `github.com/rstagit/rstang` (linked from engine README) | Exists: fork of v2rayNG ("spoof + auto connect"); a separate Android app, **not** this engine embedded — possible future reference only, not analyzed further |
Manjaniq — Development Progress Report

Date: 2026-10-14
Time: 02:17 (+03:30)
Session: Project Foundation and Core Architecture Setup

---

1. Session Objective

Continue preparing the Manjaniq project structure on an Android device using Termux, while keeping the existing RSTA Go engine separate from the future Android application code.

The goal of this session was to establish the initial modular directory structure and create placeholder files for the core architecture.

2. Project Directory Structure

The following directories have been created:

Manjaniq/
├── app/
├── core/
│   ├── models/
│   ├── interfaces/
│   └── common/
├── feature-rsta/
│   ├── controller/
│   ├── config/
│   ├── state/
│   └── logs/
└── engine/
    └── rstaspoof/

The project also contains the following documentation files:

- "ARCHITECTURE.md"
- "ROADMAP.md"
- "UI_DESIGN_GUIDE.md"
- "report.md"

3. RSTA Engine Placement

The existing RSTA Go project is located at:

"engine/rstaspoof/"

The directory contains the original Git repository metadata under ".git".

The RSTA source code has not been modified during this session.

The engine is kept separate from the Android application structure so that its integration mechanism can be changed later without unnecessarily coupling it to the UI.

4. Core Model Files Created

The following empty Kotlin files have been created under "core/models/":

- "EngineState.kt"
- "RSTAConfig.kt"
- "OperationResult.kt"

These files are placeholders only. Their Kotlin implementations have not yet been added or verified.

Planned Responsibilities

EngineState.kt

Represent the engine lifecycle states:

- "STOPPED"
- "STARTING"
- "RUNNING"
- "STOPPING"
- "ERROR"

RSTAConfig.kt

Represent the engine configuration, including supported settings such as the listening address, destination address, SNI hostname, method, fragmentation strategy, fragment delay, and optional TTL behavior.

The final fields must be confirmed against the actual RSTA implementation.

OperationResult.kt

Represent the outcome of application operations, including success and failure information.

5. Core Interface Files Created

The following empty Kotlin files have been created under "core/interfaces/":

- "EngineController.kt"
- "EngineStatusProvider.kt"
- "Logger.kt"

Their responsibilities are planned as follows:

- "EngineController.kt": Define the contract for requesting engine start and stop operations.
- "EngineStatusProvider.kt": Define how the application obtains engine status.
- "Logger.kt": Define a shared interface for application and engine-related log messages.

These contracts have not yet been implemented.

6. RSTA Feature Structure

The following directories have been created under "feature-rsta/":

- "controller/"
- "config/"
- "state/"
- "logs/"

They are reserved for RSTA-specific application logic, configuration handling, runtime state, and log management.

No implementation code has been added to these directories yet.

7. Technical Decisions

The current project direction is:

- Application language: Kotlin.
- UI framework: Jetpack Compose.
- Development environment: Android device with Termux.
- Initial engine integration strategy: Evaluate executing the existing Go binary as a separate process.
- APK build strategy: Prefer GitHub Actions for remote builds, avoiding the need to install the complete Android SDK on the phone.
- Initial application scope: Control the local RSTA proxy rather than implement device-wide VPN routing.
- Future extensibility: Keep additional network utilities in separate feature modules.

The separate-process integration strategy is a provisional decision. It still requires a technical proof of concept inside an Android application.

8. Work Completed

- Created the initial application directory.
- Created the shared core directory structure.
- Created the RSTA feature directory structure.
- Placed the existing RSTA source under "engine/rstaspoof/".
- Created three core model placeholders.
- Created three core interface placeholders.
- Verified the presence of the core Kotlin files using "find core -type f".

9. Work Not Yet Completed

- Implementing the core Kotlin models.
- Implementing the core interfaces.
- Creating a real Android Gradle project.
- Configuring Kotlin and Jetpack Compose.
- Implementing the RSTA process controller.
- Implementing process lifecycle and log handling.
- Integrating the engine into an Android APK.
- Configuring the GitHub Actions build workflow.
- Building and testing an installable APK.

10. Next Steps

1. Implement "EngineState.kt".
2. Implement "RSTAConfig.kt" using verified RSTA configuration options.
3. Implement "OperationResult.kt".
4. Define the core interfaces and their method contracts.
5. Review the package and source-directory structure before introducing Gradle modules.
6. Create the Android project foundation.
7. Build a minimal proof of concept to validate execution of the Go binary from an Android application.
8. Add the engine controller, state management, and log forwarding.
9. Configure remote APK builds after the project foundation is ready.

11. Current Status

Status: Initial directory structure created; core implementation not yet started.

The directory structure and placeholder Kotlin files have been created successfully. The project is not yet a buildable Android application.

The next action is to implement and verify the core model files before proceeding to the Android project setup.

---

End of report.
