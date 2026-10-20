# Aether capability inventory

Scope: the Aether module of this app only (`app/src/main/java/com/manjaniq/{AetherBridge.kt,core/Aether*,service/AetherService.kt,ui/**/Aether*,res/values/strings_aether.xml}`,
`aether-wrapper/`), checked against the vendored Aether v2.3.0 source in
`third_party/Aether/aether/src/`.

Nothing here is inferred from a feature name. Every row cites the file and line
that establishes it, or says explicitly that it could not be established.

---

## 1. What "reachable" means in this build

`AetherService` drives Aether through three granular FFI calls plus polling:

| FFI call | Payload struct | Fields Aether actually reads |
|---|---|---|
| `aether_identity_open` | `OpenPayload` (`ffi.rs:215-225`) | `path`, `transport`, `model`, `locale`, `team` |
| `aether_scan_start` | `ScanPayload` (`ffi.rs:228-241`) | `transport`, `mode`, `ip`, `profile`, `ports`, `excluded`, `ech` |
| `aether_tunnel_start` | `TunnelPayload` (`ffi.rs:246-260`) | `peer`, `transport`, `socks`, `http`, `profile`, `keepalive`, `ech` |
| `aether_job_poll` / `aether_job_cancel` / `aether_job_free` | — | `id` |

`tunnel_spec_of` (`ffi.rs:430-447`) maps that payload onto `api::TunnelSpec`
(`api.rs:449-459`), whose complete field list is `transport`, `socks`, `http`,
`ech`, `aethernoize`, `keepalive`, `verify_timeout`. There is **no** field for
DNS, an upstream proxy, quick reconnect, Tor, Psiphon or fragmenting.

`api::connect` (`api.rs:546-572`) then:

* MASQUE → `run_masque_tunnel(identity, peer, spec.ech, spec.socks)` — it does
  **not** pass `spec.aethernoize`;
* WireGuard → `run_wireguard_tunnel(identity, peer, spec.aethernoize, spec.socks)`.

A fourth entry point exists, `aether_core_start` (`ffi.rs:492`), which runs the
full CLI (`cli.rs`) and therefore honours every flag and `AETHER_*` environment
variable. `AetherBridge.coreStart` wraps it, and
`AetherConfig.toAetherArgs()` builds its argument list — but **nothing in the app
calls either**, so the CLI-only settings below are recorded and displayed, never
applied. Android also offers no public API for setting the process environment
from Kotlin, which is why the `AETHER_*` variables cannot be set around the call
either.

The classification used below:

* **Accessible** — the value reaches Aether through a call the service makes.
* **Partial** — the value reaches Aether but is honoured only on part of the path.
* **Recorded only** — persisted, validated, exported, shown with the reason, but
  not forwarded by the path this build uses.
* **Not implemented** — no code anywhere in this repository or in the vendored
  Aether source implements it.

---

## 2. Settings the app exposes

| Setting (`AetherConfig` / `AetherProfileConfig`) | Status | Evidence |
|---|---|---|
| `transport` = `masque`, `wg` | Accessible | `OpenPayload.transport`, `ScanPayload.transport`, `TunnelPayload.transport`; `api.rs:20-26` `Transport::parse` |
| `transport` = `gool`, `mim` | **Recorded only** | `api::Transport` has two variants only (`api.rs:13-16`); `Transport::parse` maps `wg\|wireguard\|warp` → WireGuard and *everything else* → Masque (`api.rs:20-26`). gool/mim exist as CLI protocols (`cli.rs` `--gool`, `--mim`) reachable only through `aether_core_start`. |
| `scanMode` (turbo, balanced, thorough, verified, ironclad) | Accessible | `ScanPayload.mode` → `prober::ScanMode::parse` (`prober.rs:155-165`), which also accepts the aliases `fast`, `deep`/`pro`, `proven`/`stealth`/`quiet`, `real`/`verify`/`guaranteed`; anything unknown silently means `balanced`. |
| `ipVersion` (v4, v6, both) | Accessible (**fixed** — see §4a) | `ScanPayload.ip` → `prober::IpScan::parse` (`prober.rs:120-127`): `6\|v6\|ipv6` → V6, `both\|all\|dual` → Both, anything else → V4. |
| `socksBind` | Accessible | `TunnelPayload.socks` → `TunnelSpec.socks` → `run_masque_tunnel` / `run_wireguard_tunnel`. Loopback-only is enforced by `AetherConfig.validate()`. |
| `httpProxyBind` | Accessible | `TunnelPayload.http` → `TunnelSpec.http`; `api::connect` exports it as `AETHER_HTTP_PROXY` (`api.rs:552-555`). |
| `peer` | Accessible | `TunnelPayload.peer`; when set, `AetherService` skips the scan entirely. |
| `identityPath` | Accessible | `OpenPayload.path`, resolved inside the app's private files dir by `AetherService`. |
| `echEnabled` | **Partial** | `ScanPayload.ech` and `TunnelPayload.ech` → `job_ech(want, transport)`; for WireGuard there is no TLS handshake whose server name could be hidden, so `job_ech` yields nothing. |
| `noizeProfile` (off, light, firewall, balanced, gfw, aggressive) | **Partial** | Scan: `ScanPayload.profile` → `noize::from_profile` (`noize.rs:73-80`). WireGuard tunnel: `TunnelPayload.profile` → `aethernoize::from_profile` (`aethernoize.rs:147-156`). MASQUE tunnel: **not** passed — `api::connect` gives `run_masque_tunnel` no noize argument, so it comes from `AETHER_NOIZE`. The two parsers also disagree: `noize::from_profile` has no `balanced` case (it falls through to `firewall`), `aethernoize::from_profile` has no `heavy`-style alias for `firewall` and falls through to `balanced`. |
| `teamName` | **Partial** | `OpenPayload.team` (`TeamPayload`, `api.rs:109-149`). Enrolment additionally needs a service token or a one-time code; `AetherBridge` wraps `aether_team_sign_in`, `aether_team_code_request`, `aether_team_code_resend`, `aether_team_code_submit`, `aether_team_token_set`, `aether_team_token_clear` (`ffi.rs:521-612`) and **no screen calls them**, so a team name alone cannot enrol. |
| `dns` | **Recorded only** | No field on any of the three payloads; CLI-only (`cli.rs` `--dns`, env `AETHER_DNS`). |
| `upstream` | **Recorded only** | No field on any of the three payloads; CLI-only (`cli.rs` `--upstream`, env `AETHER_UPSTREAM`). |
| `quickReconnect` | **Recorded only** | No field on any of the three payloads; CLI-only (`cli.rs` `--quick-reconnect` / `--no-quick-reconnect`). |
| `fragmentEnabled`, `fragmentSize`, `fragmentDelay`, `fragmentSniSplit` | **Recorded only** | See §3. |
| `psiphonEnabled`, `psiphonEndpoint`, `psiphonRegion` | **Not implemented** | No Psiphon component is bundled (`third_party/` contains Aether only) and no payload field exists. The route is listed with `ProfileCapability.RequiresExternalComponent` and the reason is shown next to the fields. |
| `torSocks`, `torBridges`, `torUseBridges` | **Not implemented** | Same: no Tor component is bundled and no payload field exists. Aether itself has `--tor` / `AETHER_TOR_*` handling in the CLI, which this build never invokes. |

Route-level (`AetherProfile`) statuses are unchanged and still truthful:
`WARP_DIRECT` is the only route marked `Available`; the other five are marked
`RequiresExternalComponent` and remain listed, never hidden.

---

## 3. Fragmenting — full trace

**Implemented upstream.** `third_party/Aether/aether/src/fragment.rs`:

* `FragmentConfig { enabled, size_min, size_max, delay_min_ms, delay_max_ms, sni_split }` (`fragment.rs:11-18`);
* `FragmentConfig::disabled()` (`fragment.rs:21-30`) and `FragmentConfig::from_env()` (`fragment.rs:32-60`);
* `parse_range` accepts `n` or `a-b` and swaps a reversed range;
* `FragmentingStream<S>` wraps an async stream and fragments **only until the
  first read**, splitting at the midpoint of the SNI host name when `sni_split`
  is set, otherwise at a random offset, with a random size and delay per chunk;
* the module carries its own unit tests.

**Consumed in exactly one place.** `masque_h2.rs`:

* `use crate::fragment::{FragmentConfig, FragmentingStream}` (`masque_h2.rs:16`);
* `connect_tls(cfg, fragment: FragmentConfig)` returns
  `SslStream<FragmentingStream<TcpStream>>` (`masque_h2.rs:224-239`);
* callers pass `FragmentConfig::from_env()` (`masque_h2.rs:284`, `masque_h2.rs:402`);
* it logs `[h2] fragmenting client hello: size={}..{} delay={}..{}ms` (`masque_h2.rs:407`).

So fragmenting affects **the MASQUE-over-HTTP/2 carrier only** — never HTTP/3
(QUIC), never WireGuard. That carrier is itself opt-in: `masque_h2::enabled()`
reads `AETHER_MASQUE_HTTP2` (`masque_h2.rs:141-142`), default off, and this build
never sets it.

**Configured only through the environment / CLI.**

| Variable | CLI flag | Source |
|---|---|---|
| `AETHER_MASQUE_H2_FRAGMENT` | `--fragment` / `--no-fragment` | `cli.rs:129`, `cli.rs:132`, `cli.rs:392`, `cli.rs:602-603` |
| `AETHER_MASQUE_H2_FRAGMENT_SIZE` | `--fragment-size <n\|a-b>` | `cli.rs:133`, `cli.rs:393`, `cli.rs:604` |
| `AETHER_MASQUE_H2_FRAGMENT_DELAY` | `--fragment-delay <n\|a-b>` | `cli.rs:134`, `cli.rs:394`, `cli.rs:605` |
| `AETHER_MASQUE_H2_FRAGMENT_SNI` | **none** | `fragment.rs:49-51`; no flag exists in `cli.rs` |

`cli.rs:65` also documents an unrelated `--api-fragment`
(`AETHER_API_FRAGMENT`, `cli.rs:372`, `cli.rs:558`) — "reach the warp api only
over the fragmented route". It is a different feature and is not exposed here.

**Why it cannot be applied from this build.** The service calls
`aether_tunnel_start`, whose payload (`ffi.rs:246-260`) and spec mapping
(`ffi.rs:430-447`) contain no fragmenting field, and whose MASQUE branch
(`api.rs:557-561`) goes to `run_masque_tunnel` (`lib.rs:1612-1666`) — not through
`masque_h2::connect_tls` with a caller-supplied config. The only paths that could
apply it are `aether_core_start` (which runs the CLI and sets the variables
itself) or a new export in `aether-wrapper/src/lib.rs`. Both are outside the
scope of a UI/logic fix: the first changes how the tunnel is started, the second
adds native surface that cannot be built or verified here.

**Upstream default conflict (reported, not guessed).**

| Source | enabled | size | delay |
|---|---|---|---|
| `fragment.rs:32-43` (code) | off (`unwrap_or(false)`) | `8-16` | `2-10` |
| `cli.rs:133-134` (help text) | — | `16-32` | `2-10` |
| `cli.rs:392` (env table) | **"default on"** | — | — |
| `Docs/GUIDE.en.md:203-205` | **off by default** | `16-32` | `2-10` |

The app follows the *documented* defaults (`off`, `16-32`, `2-10`) and records the
conflict instead of picking a side. `AetherFragmenting.DEFAULT_*` and
`AetherConfig.DEFAULT` carry them; `AetherCapabilitiesTest` pins them.

**What the UI does.** The Fragmenting group is listed for every route that can
carry MASQUE (`getSectionsForProfile` → `profileSupportsFragmenting`), shows the
stored values, names the four variables Aether reads, and states plainly that
they are *recorded, not applied in this build*. The values are persisted
(`AetherConfig.toJson`/`fromJson`), validated against Aether's own limits
(`AetherFragmenting.validateRange`, max 1500 bytes / 5000 ms), exported in the
route's JSON and mapped onto the real CLI flags by `toAetherArgs()`
(`--fragment`, `--no-fragment`, `--fragment-size`, `--fragment-delay`; never a
`--fragment-sni`, because that flag does not exist). They are **not** offered as
editable switches, because a switch that changes nothing at runtime would be a
decorative control.

To make it live, one of these is needed (each is a behavioural change outside
this task's scope, so it is reported rather than done):

1. start Aether through `AetherBridge.coreStart(cfg.toAetherArgs(filesDir))`
   instead of the three granular calls — this also unlocks gool, mim, DNS,
   upstream and quick reconnect, and gives up the per-step job polling the
   current flow depends on; or
2. add an export to `aether-wrapper/src/lib.rs` that sets the four
   `AETHER_MASQUE_H2_FRAGMENT*` variables before `aether_tunnel_start` and
   enables `AETHER_MASQUE_HTTP2`, then rebuild `libaether.so`.

---

## 4. Confirmed logic defects that were fixed

| # | Defect | Root cause | Fix |
|---|---|---|---|
| a | The tunnel died seconds after "Tunnel established". | `pollJob(tunnelJobId, timeoutMs = Long.MAX_VALUE)` computed `now + Long.MAX_VALUE`, which overflows to a negative deadline, so the loop exited at once, returned null, and the service called `jobFree` — and `aether_job_free` cancels the job it removes (`ffi.rs:318-330`). | `AetherJobPolling.deadlineFor` treats `null` (and any overflowing bound) as unbounded; the tunnel poll now passes `timeoutMs = null`. |
| b | The IP-version choice had no effect. | `ScanPayload.ip` was never sent, so `IpScan::parse` always saw the default and every scan was IPv4-only. | `ip = cfg.ipVersion.ifBlank { null }`. |
| c | RUNNING was published before any handshake. | The service set RUNNING and logged "Tunnel established" as soon as `aether_tunnel_start` returned a job id. | `awaitTunnelServing` waits for Aether's loopback SOCKS listener to accept a connection (Aether binds it only after the tunnel is established — `lib.rs:1612-1666`), with a 45 s budget and a truthful WARN when the budget runs out. |
| d | A stop during a start produced a spurious "there is no job N" error. | The poll loop raced `stopAether()` freeing the job. | Per-start `StartAttempt` cancellation flag, re-read live when classifying the exit; `stopped`/cancelled → STOPPED, not ERROR. |
| e | A restart could be reported as already RUNNING. | The duplicate-start guard keyed on `currentJobId`, which is set during the *scan*. | The guard keys on the live attempt, and superseded attempts publish nothing. |
| f | `cfg.noizeProfile` looked conditional but was not. | `profile = cfg.noizeProfile.takeIf { it == cfg.noizeProfile } ?: …` — a tautology. | `profile = cfg.noizeProfile.ifBlank { null }`, with a comment saying why "off" must be sent. |
| g | Settings silently vanished. | dns, upstream, quick reconnect, fragmenting and non-MASQUE transports have no field on the path in use, and nothing said so. | `AetherService.logUnforwardedSettings` logs a WARN naming what will not be applied; the UI shows the reason next to each control (`AetherCapabilities`). |
| h | Two listeners could be configured on one port. | `AetherRepository.detectConflict` compared only against the RSTA port; the UI-side guard caught socks == http, the service-side one did not. | Both guards now reject `socks == http`, and the RSTA comparison only runs when an RSTA port is known. |
| i | A blank HTTP-proxy field was rejected as "not loopback". | `validate()` checked `httpProxyBind.isNotEmpty()` while `socksBind` was trimmed first, and the service forwarded the blank string to the FFI. | `validate()` trims both; `AetherService` sends `httpProxyBind.trim().ifEmpty { null }`; `toAetherArgs` and both conflict guards trim too. |
| j | Log ring corruption / lost lines. | `logRing` and `logSeq` were mutated from an IO coroutine and from the main thread without synchronisation. | `logLock` monitor, ring capped at 500, and every message passes `AetherLogSanitizer.redact` at this boundary. |
| k | `AetherBridge.testBridge()` (a JNI round-trip) ran during composition. | It was called inline in `AetherStatusCard`'s body, so every recomposition blocked the main thread on native code. | Resolved once in a `LaunchedEffect` on `Dispatchers.IO`. |
| l | A route's settings could never be collapsed. | `expandedMap = expandedMap + (id to true)` — the map could only ever gain `true`. | A single `settingsOpen` flag toggled by tapping the already-selected route. |
| m | `--noize off` was dropped from the CLI mapping. | `toAetherArgs` emitted the flag only for non-"off" values, so choosing "off" silently left Aether on its own default. | `--noize` is emitted for every non-blank profile. |
| n | A dead read in the profile-store migration. | `val oldJson = prefs.getString("aether_config_json", null)` read a key that does not exist in that preferences file, with a comment saying "check". | Removed; the comment now names the file that does own the key. |

`aether_reach_*` strings, `AetherCapabilities` and `AetherCapabilitiesTest` keep
the matrix above executable: if a field is ever added to the FFI payloads, the
test fails until the classification is updated.

---

## 5. Implemented but not surfaced anywhere in the UI

Listed so nothing is mistaken for missing when it is only unwired:

* `AetherBridge.coreStart` / `AetherConfig.toAetherArgs` — the CLI path (see §3).
* `AetherBridge.verifyStart` → `aether_verify_start` (`ffi.rs:450`) — dials a
  candidate and performs a real request; this is what `scanMode = ironclad` does
  inside the scan, so the separate call is redundant here.
* `AetherProfileStore.loadAll` / `exportAllJson` / `importAllJson` — all-route
  export/import. Per-route export/import *is* in the UI; the all-route variant is
  code-only.
* `AetherRepository.refreshVersionFromNative` — no caller; the version is fetched
  by the service during a start.
* `AetherComingSoonContent` (`ui/screens/AetherComingSoonScreen.kt`) — no caller;
  left in place, its strings are still defined.
* `TestTags.AETHER_SETTINGS` — declared for the legacy `AetherSettingsForm` but
  never applied to it; it is applied now, so the fallback form is findable.
* `TestTags.AETHER_DISABLED_BUTTON` — still never applied. The new panel keeps
  `AETHER_TOGGLE` unconditionally so `NavigationTest.aetherIsComingSoonAndCannotStartAnything`
  and `LaunchSmokeTest` keep working; `AETHER_DISABLED_BUTTON` is deliberately
  *not* swapped in when the native library is missing, because
  `compose.waitForTag(AETHER_TOGGLE)` would then time out on such a build.
  Aether-specific tags live in `ui/AetherTestTags.kt`.
* The MASQUE-over-HTTP/2 carrier (`AETHER_MASQUE_HTTP2`, `masque_h2.rs:141`) —
  no control was added, because enabling fragmenting without it would still do
  nothing, and enabling it changes the carrier the tunnel uses.
