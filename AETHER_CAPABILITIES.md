# Aether capability inventory

Scope: the Aether module of this app only (`app/src/main/java/com/manjaniq/{AetherBridge.kt,core/Aether*,service/AetherService.kt,ui/**/Aether*,res/values/strings_aether.xml}`,
`aether-wrapper/`), checked against the vendored Aether v2.3.0 source in
`third_party/Aether/aether/src/`.

Nothing here is inferred from a feature name. Every row cites the file and line
that establishes it, or says explicitly that it could not be established.

> **Updated: start modes.** The inventory below describes the *granular* FFI path
> (`aether_identity_open` -> `aether_scan_start` -> `aether_tunnel_start`), which
> is what this app used exclusively when the table was written. That path is
> still the default and still reports each step, but it is no longer the only
> one: a route can be started through `aether_core_start`, i.e. the core's own
> command line, where every flag in `cli.rs` applies. §9 covers that path, the
> packaged helper processes it needs, and what Android 10+ (W^X) allows. Where a
> row below says "recorded only" or "not implemented", read it as *on the granular
> path* - `AetherCapabilities.reachOf(key, mode)` answers the same question for
> either mode, and the settings screen uses it.

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
DNS, an upstream proxy, quick reconnect, Tor, Psiphon or fragmenting. Fragmenting
nonetheless reaches the engine, because Aether reads it from its environment when it
makes the connection rather than from this spec — see §3.

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
reads `AETHER_MASQUE_HTTP2` (`masque_h2.rs:141-149`), default off, and it is
consulted at connection time by the tunnel (`lib.rs:1530-1544`), by the scanner's
verification (`prober.rs:414-431`) and by the CLI's own transport question
(`lib.rs:3255-3268`). This build now sets it — see below — and pairs it with
fragmenting, because fragments have nowhere to be written without it.

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

**How it is applied from this build.** `aether_tunnel_start`'s payload
(`ffi.rs:246-260`) still has no fragmenting field, and that is not how the values
travel. Aether reads them from its own environment when it makes the connection
(`FragmentConfig::from_env` at `masque_h2.rs:284` and `:402`), so the bridge writes
them there:

`AetherFragmentingControl` (UI) → `AetherConfig.fragmentEnabled/Size/Delay/SniSplit`
→ saved with the route → `AetherService.applyFragmenting()` before the scan →
`AetherBridge.fragmentingApply()` → JNI
`Java_com_manjaniq_AetherBridge_nativeAetherFragmentingApply`
(`aether-wrapper/src/lib.rs`) → `AETHER_MASQUE_HTTP2`,
`AETHER_MASQUE_H2_FRAGMENT`, `_SIZE`, `_DELAY`, `_SNI` → `masque_h2::enabled()` and
`FragmentConfig::from_env()` → `FragmentingStream` around the carrier's TCP stream.

Why the environment and not a payload field: the vendored engine is not modified,
and its own CLI applies these settings exactly this way — `--h2` and `--fragment*`
are `set(...)` calls (`cli.rs:579-580`, `:602-605`) — as does the engine itself at
runtime for tor/psiphon (`lib.rs:198`, `:226`) and for the HTTP proxy
(`api.rs:553`). The JNI function takes a fixed payload and writes only those five
variables, so it is not a general environment setter; it validates the two range
specs against the same limits the UI uses and refuses the write otherwise, and it
answers with the values read back from the environment so the log reports what was
set rather than what was asked for.

Ordering matters: it runs **before the scan**, because the scanner's verification
follows the same carrier choice (`prober.rs:414-431`), and a later write would
leave the scan and the tunnel disagreeing. Turning fragmenting **off** is written
too — `AETHER_MASQUE_H2_FRAGMENT=0` with the carrier and chunk variables removed —
because this environment outlives one tunnel and a stale value would otherwise keep
fragmenting a route that no longer asks for it.

`toAetherArgs()` carries the same decision as CLI flags for the `aether_core_start`
path: `--h2` when fragmenting is on and `--h3` when it is off (emitted for every
MASQUE start, because `select_masque_transport` prompts interactively when the
variable is unset — `lib.rs:3255-3268` — and a service cannot answer a prompt),
plus `--fragment`/`--no-fragment`/`--fragment-size`/`--fragment-delay`. The SNI
split has no flag upstream, so it travels only through the bridge.

`AetherConfig.usesHttp2Carrier()` is the single pairing rule
(`fragmentEnabled && transport == "masque"`), and `validate()` refuses fragmenting
on any other transport instead of letting it be stored and ignored — WireGuard has
no HTTP/2 carrier at all (`api.rs:562-570`), and gool/mim are refused earlier by
the transport rule.

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

**What the UI does.** Fragmenting is edited **once**, in a single card near the
bottom of the Aether screen — immediately before the operational log, which stays
last so the newest real event is always at the very bottom — and not as a per-route
settings group: it belongs to one carrier, so a per-route group would read as
"this route can do fragmenting" and would repeat the same explanation four times.
`AetherFragmentingControl` (in `ui/screens/AetherPanel.kt`) shows, in this order:
the title and a status line that names the carrier the stored value implies
(*Applied — this route runs MASQUE over HTTP/2* / *Off — this route runs MASQUE over
HTTP/3 (QUIC)*); what fragmenting is; **the exact carrier it applies to**; the reach
note from `AetherCapabilities` (*Partly applied*, with the reason); whose values
these are (the selected route, named); then the controls — a switch for fragmenting,
the carrier consequence next to it while it is on, chunk size and delay as validated
fields, a switch for the SNI split — an unsaved-changes hint, inline errors, and a
Save button that is enabled only for a dirty, valid draft and asks about a restart
when Aether is RUNNING. The environment variables Aether reads and how the values
reach it are stated at the bottom.

When the selected route's transport is not MASQUE, **no controls are rendered**:
there is no HTTP/2 carrier to fragment on, so a switch there would change nothing.
The card says why (`aether_fragment_needs_masque`, naming the transport) and keeps
the stored values visible read-only, so nothing is hidden and nothing pretends to
work. `validate()` refuses the same combination at start time, so a stored value
cannot be applied to a transport that ignores it either.

`getSectionsForProfile` still lists no fragmenting section for any route, and
`profileSupportsFragmenting` still answers "can this route carry MASQUE at all" by
delegating to `profileUsesAetherGateway`. The values remain per-route: persisted
(`AetherConfig.toJson`/`fromJson`), validated against Aether's own limits
(`AetherFragmenting.validateRange`, max 1500 bytes / 5000 ms, matching
`FRAGMENT_MAX_SIZE`/`FRAGMENT_MAX_DELAY` in `aether-wrapper/src/lib.rs`), exported in
the route's JSON, and mapped onto the real CLI flags by `toAetherArgs()`.

**What is still not verified, and what was deliberately not done.**

* No device or emulator with a reachable WARP API was available, so the fragmenting
  path has not been observed carrying a tunnel: CI compiles the JNI function into
  `libaether.so` for arm64 and the emulator gate installs and exercises the UI, but
  neither runs a MASQUE-over-HTTP/2 connection. What is tested is the app's side —
  the arguments, the pairing rule, the validation, the classification, the screen.
* The engine's own `fragment.rs` unit tests are upstream and are not run by this
  repository's CI (no `cargo test` step, and the host Rust build of the vendored
  crate is not part of the workflow).
* `aether_core_start` is still not used, so gool, mim, DNS, upstream and quick
  reconnect remain unwired exactly as before; enabling fragmenting did not change
  how the tunnel is started, only what the engine's environment holds when it does.
* A separate carrier choice (HTTP/2 without fragmenting, for a network that blocks
  QUIC) is now one field away — the bridge payload already carries `http2`
  separately — but it was not added, because it is a new setting rather than part of
  wiring fragmenting.
* `--api-fragment` (`AETHER_API_FRAGMENT`, `cli.rs:65`, `:372`, `:558`) is a
  different feature — reaching the WARP API over the fragmented route — and is still
  not exposed.

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
| o | A **successful** scan still failed the tunnel start, and its endpoint was thrown away. | `aether_scan_start` finishes with `{"endpoint":{"ip":..,"port":..,"rtt_ms":..},"ok":true}` (`ffi.rs:418-424`, `api.rs:340-345`); the service read that member with `optString`, which stringifies the object, and passed the JSON text to `aether_tunnel_start`, where `socket_of` (`ffi.rs:269-273`) rejects it as `the peer address '…' is not an address:port`. | New pure `core/AetherScanOutcome.endpointFrom` builds `ip:port` (bracketing IPv6 the way `SocketAddr::from_str` needs it) and accepts only text that really parses as an address, so a mis-shaped reply fails here with a clear message instead. |
| p | `thorough` and `ironclad` scans could never succeed through the app. | The scan poll used the flat `DEFAULT_TIMEOUT_MS` (120 s) while Aether's own budget is per transport and per mode: `prober::ScanMode::strategy` gives thorough 300 s / ironclad 180 s and `wg_prober::WgScanMode::strategy` 250 s / 180 s. The deadline expired first, then `aether_job_free` cancelled the still-working scan (`ffi.rs:318-330`). | `AetherJobPolling.scanBudgetFor` / `scanTimeoutFor` carry the engine's ten budgets (with the engine's own fallbacks: unknown transport → MASQUE, unknown mode → balanced) plus a 20 s margin for in-flight probes and FFI round trips; the service polls with that. |
| q | A stored or imported transport this build cannot dispatch silently ran as MASQUE. | `api::Transport::parse` maps every id that is not `wg` to Masque, while `validate()` accepted all four ids, so `transport = "gool"` started plain MASQUE with only a WARN in the log. | `validate()` rejects any id outside `AetherTransportChoice.SELECTABLE_IDS` (`masque`, `wg`) and names what would otherwise have run. `AetherService` calls `validate()` before touching the FFI, so the start fails with that message instead of mislabelling the connection. |
| r | The identity poll could cancel a registration the engine was still retrying, and then report its own timeout. | `pollJob(jobId)` used the flat `DEFAULT_TIMEOUT_MS` (120 s). Registration is bounded by the engine: `API_TIMEOUT = 20 s` per attempt and `API_ATTEMPTS = 5` with backoff (`account.rs:248-260`) is up to 111.25 s, and a 429/408/5xx answer may add `Retry-After` sleeps of up to 30 s each inside the loop (`account.rs:253, 293-304`) — about 231 s. When the app gave up first it called `jobFree`, which cancels the job it removes (`ffi.rs:318-330`). | `AetherJobPolling.identityTimeoutMs()` carries the engine's worst case plus a 15 s margin (246 s), and `noAnswerWindowMs()` records the 106.75-111.25 s window an unanswered registration occupies; the identity block polls with the former and logs the budget before it waits. |
| s | A registration failure reached the user as a raw nested engine string, with no attempt count, no elapsed time and no way to tell reachability from refusal. | Nothing classified identity/registration failures — unlike scans, which `core/AetherScanOutcome` interprets. The reported run showed only `api: registration: api: api.cloudflareclient.com did not answer within 20s`. | New pure `core/AetherRegistrationOutcome` classifies the engine's own message (NO_ANSWER, REFUSED_BY_API, IDENTITY_REFUSED, BAD_RESPONSE, STORAGE, TLS, ECH, CANCELLED, TIMEOUT, OTHER) and keeps that message **verbatim** in front of one factual sentence: what was tried, that no identity was registered and no tunnel started, and the recoveries the engine itself names (`account.rs:379-392`). A timeout is never dressed as a success and no check is ever suggested to be weakened. `core/AetherNetworkCheck` adds the platform's read-only connectivity facts beside a reachability failure. |
| t | A successful registration logged `summary=null`. | `aether_identity_open` replies with `id`, `job`, `path`, `lastconn_path`, `model` and `locale` (`ffi.rs:356-365`) — there is no `summary` member, which only `aether_identity_summary` returns. | The log now prints the identity path the engine derived (`api::identity_path` → `…-masque`, `api.rs:256-264`, `lib.rs:840-852`), the lastconn path and the model the registration claimed. |
| u | Stop during registration left the engine working and leaked the job. | The identity block polled its job without ever publishing it: `currentJobId` stayed null, so `stopAether()` found nothing to `jobCancel`/`jobFree` and `onDestroy()` freed nothing before `scope.cancel()` killed the poll — the engine kept running its five attempts after the user pressed Stop, and the job stayed in the FFI map for the process lifetime. The scan (`AetherService`:471) and tunnel (`:539`) blocks both publish their job; the identity block did not. | The identity job is now published with `setActiveJob` + `currentJobId` before the poll and cleared on all three exit paths (failure, no identity id, success), so Stop cancels it, `onDestroy` frees it, and the panel shows the job that is really in flight. A poll that answers `there is no job N` — the job the app just freed — is classified as a cancellation, not as a refusal. |
| v | Fragmenting was stored, validated, exported and shown — and never applied. | `aether_tunnel_start`'s payload (`ffi.rs:246-260`) has no fragmenting field, and Aether reads these settings from its environment (`fragment.rs:32-61`, `masque_h2.rs:141-149`), which nothing in the app wrote. `toAetherArgs()` did map them onto real CLI flags, but that argv belongs to `AetherBridge.coreStart`, which has no caller. | The JNI bridge gained one purpose-built export, `nativeAetherFragmentingApply` (`aether-wrapper/src/lib.rs`), which writes `AETHER_MASQUE_HTTP2` and the four `AETHER_MASQUE_H2_FRAGMENT*` variables and nothing else, validates the two range specs against the UI's own limits, and answers with the values read back. `AetherService.applyFragmenting()` calls it before the scan, logs what the environment then holds, and refuses to start when fragmenting was asked for and could not be put in effect. `AetherConfig.usesHttp2Carrier()` pairs fragmenting with the carrier, `validate()` refuses it on any other transport, `toAetherArgs()` now emits `--h2`/`--h3` too, and the panel's card became editable (see §3). |

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
* `AetherBridge.coreStart` / `AetherConfig.toAetherArgs` and `verifyStart` above
  remain the only unwired entry points. The audit removed the code with no
  caller at all: `AetherBridge.pollUntilDone`, `AetherProfileStore.loadAll` /
  `exportAllJson` / `importAllJson`, `AetherRepository.refreshVersionFromNative`
  and the dead `AetherComingSoonContent` screen (with its strings).
* `TestTags.AETHER_SETTINGS` — declared for the legacy `AetherSettingsForm` but
  never applied to it; it is applied now, so the fallback form is findable.
* `TestTags.AETHER_DISABLED_BUTTON` — still never applied. The new panel keeps
  `AETHER_TOGGLE` unconditionally so `NavigationTest.aetherIsComingSoonAndCannotStartAnything`
  and `LaunchSmokeTest` keep working; `AETHER_DISABLED_BUTTON` is deliberately
  *not* swapped in when the native library is missing, because
  `compose.waitForTag(AETHER_TOGGLE)` would then time out on such a build.
  Aether-specific tags live in `ui/AetherTestTags.kt`.
* The MASQUE-over-HTTP/2 carrier (`AETHER_MASQUE_HTTP2`, `masque_h2.rs:141-149`) —
  it is now written, but only as a consequence of fragmenting: there is still no
  independent carrier control, so a network that blocks QUIC cannot ask for HTTP/2
  while keeping fragmenting off. The bridge payload already carries `http2`
  separately from `enabled`, so adding that control needs no native change.
* `AETHER_ENROLL_ADDRESS` (`account.rs:423-434`) — dials a named address or
  address:port for enrollment instead of resolving the API's own name, keeping the
  API's name for SNI and the HTTP host. Environment-only: `aether_identity_open`
  has no field for it and an Android process cannot set the engine's environment, so
  the app always enrolls against `api.cloudflareclient.com:443` (`consts.rs:1`).
  Note that `api_call` requires it to be an IP address, not a name, when the egress
  socket is marked (`account.rs:455-466`).
* `AETHER_API_ECH` (`account.rs:347-365`, `dns.rs:19`) — offers an ECH key for the
  API call, fetching it over DoH within 12 s when it is not already remembered.
  Also environment-only, and unset here, so registration offers no ECH and spends no
  time on that lookup.
* The `model` and `locale` fields of `OpenPayload` (`ffi.rs:215-225`) — the bridge
  accepts them (`AetherBridge.identityOpen`) but the service sends neither, so
  registration claims `consts::DEFAULT_MODEL` / `DEFAULT_LOCALE` = `"PC"` /
  `"en_US"`. This matches the CLI exactly: it has no `--model`/`--locale` option and
  provisions with the same two constants (`lib.rs:775-779`). Sending the real device
  model would change what the app claims to the WARP API, so it is left alone; the
  model the engine reports back is logged on success, so what was claimed is visible.
* `AetherBridge.identitySummary` → `aether_identity_summary` (`ffi.rs:371-377`) —
  returns device id, addresses, organization, gateway proxy, assigned endpoint and
  the MASQUE certificate state (`api.rs:228-238`). No caller: it is a separate call
  from the open reply, and nothing in the panel needs it yet.

---

## 9. The full-engine start path

### 9.1 Why a second path exists

The three granular calls carry fixed payload structs (`ffi.rs:215-260`):

| call | fields |
|---|---|
| `aether_identity_open` | `path`, `transport`, `model`, `locale`, `team` |
| `aether_scan_start` | `transport`, `mode`, `ip`, `profile`, `ports`, `excluded`, `ech` |
| `aether_tunnel_start` | `peer`, `transport`, `socks`, `http`, `profile`, `keepalive`, `ech` |

Everything else the core implements - `--gool`, `--mim`, `--tor*`, `--psiphon*`,
`--team`/`--access-*`/`--gateway`, `--routes`/`--route-block`/`--route-direct`,
`--upstream`, `--dns`, `--quick-reconnect`, `--stats`, `--mark`, `--register`,
`--tls-*`, `--ech*`, `--h2`/`--h3` - has no field there, because it is parsed by
`cli.rs` into `AETHER_*` variables before `run_with` starts (`lib.rs:77`).

`aether_core_start` (`ffi.rs:492`) runs `cli::parse_args` over a JSON array of
arguments and then `run_with`, returning a pollable, cancellable job exactly like
the granular calls. `AetherConfig.toAetherArgs` builds that list from the same
model the settings screen edits, and `AetherService.startFullEngine` drives it:
start the job, poll it, probe the loopback SOCKS listener until it answers, then
hold the job until it ends. Stop, restart, readiness reporting and state
publication are shared with the granular path.

The mode is stored per route (`AetherConfig.startMode`, `AetherStartMode`), shown
in the settings, and validated (`AetherConfig.validate`): an unknown mode is
refused, and the protocols only the CLI has are refused on the granular path
instead of silently degrading to MASQUE (`api::Transport::parse`, `api.rs:20-26`).

### 9.2 What a child process needs, and why it lives in lib/

Two Aether features spawn an executable:

| child process | spawned by | flag | needed for |
|---|---|---|---|
| `psiphon-tunnel-core` | `psiphon.rs:749` | `--psiphon-bin` | every `--psiphon*` mode |
| lyrebird (obfs4, snowflake, webtunnel) | `bridges.rs` | `--tor-pt [name=]<path>` | bridges that name a pluggable transport; plain Tor needs none |

Upstream ships both in a `pt/` folder built as static `GOOS=linux CGO_ENABLED=0`
ELFs - a pure-Go binary needs no libc, so it runs unchanged under Android
(`third_party/Aether/.github/workflows/release.yml`, `android` job).

Android 10 (API 29) enforces W^X: an app may not `execve` a file in its writable
data directory. The read-only native library directory is therefore the only
place these may live, so:

1. `scripts/ci/build_helpers.sh` runs the upstream build scripts and installs the
   results as `lib/arm64-v8a/liblyrebird.so` and
   `lib/arm64-v8a/libpsiphon_tunnel_core.so` (and the same for `x86_64`);
2. `app/build.gradle.kts` sets `useLegacyPackaging = true`, i.e. the APK installs
   with `extractNativeLibs=true`, so those entries become real files in
   `nativeLibraryDir`;
3. `AetherHelpers` resolves them **only** there, requires the execute bit, and
   refuses a path outside that directory (`AetherHelpers.isExecutableLibraryFile`);
4. `AetherConfig.toAetherArgs` names the resolved files on the command line, and
   only where the user left the field blank - a path the user typed always wins.

State is the other way round: tor's cache and psiphon's data must be writable, so
`--tor-dir` / `--psiphon-dir` point into `filesDir` (`filesDir/aether/tor`,
`filesDir/aether/psiphon`). Data is writable, code is not.

Because a helper can be absent (upstream unreachable during the build, or a build
that chooses not to ship one), both paths fail closed:

* `AetherConfig.validate()` refuses a Psiphon route with no binary and no packaged
  helper, and a bridged Tor route with no transport, naming the missing file;
* `AetherHelpers.summary()` states what the installation can start, and the
  settings screen shows that line;
* the CI step that packages them reports a `::warning::`, never a failure, and
  the APK verification prints what was actually packaged.

### 9.3 Reach, per mode

`AetherCapabilities.reachOf(key, mode)` answers this in code; the table is the
short version.

| setting | granular | full engine |
|---|---|---|
| transport, scanMode, ipVersion, socksBind, httpProxyBind, peer, identityPath | Applied | Applied |
| echEnabled, noizeProfile, teamName, fragmentEnabled/Size/Delay | Partial | Applied |
| fragmentSniSplit | Partial (bridge sets the variable; no CLI flag upstream) | Partial (same reason) |
| dns, upstream, quickReconnect | NotForwarded | Applied |
| gool, mim | NotForwarded (runs as MASQUE) | Applied (`--gool`, `--mim`) |
| Tor layering, bridges, relays, PT paths | NotForwarded | Applied / Partial when a PT is needed |
| Psiphon layering, region, CDN fronting | NotForwarded | Applied / Partial when the child process is absent |
| Zero Trust (`--team`, `--access-*`, `--gateway`) | NotForwarded | Applied |
| routing (`--routes`, `--route-block`, `--route-direct`) | NotForwarded | Applied |
| `--stats`, `--exit-loc`, `--mark`, `--register`, `--tls-*`, `--ech*`, `--dns` | NotForwarded | Applied |
| fragmenting carrier | `AETHER_MASQUE_HTTP2` via `fragmentingApply` | `--h2`/`--h3` on the command line |

### 9.4 UI

* The selected route shows a **Start path** choice (`granular` / `full`), stored
  with the route.
* Transport chips follow the mode: `gool` and `mim` become selectable exactly
  when the route runs the full engine, and stay unclickable with the reason
  beside them otherwise. `AetherTransportChoice.isSelectable(id, fullEngine)` is
  the single rule the chips and `validate()` share.
* Every reach note is mode-aware (`AetherCapabilities.reachOf(key, mode)`), so a
  setting shown as applied is applied.
* `AetherConfig.commandLineOnlyFeatures()` names anything the chosen mode cannot
  apply, both above the groups and in the service log.
* An **Engine options** group edits the remaining flags (`AetherEngineOptionsPanel`):
  Tor mode/bridges/lines/relays/PT/dir, Psiphon mode/region/CDN, Zero Trust
  team and service-token/email/JWT credentials plus the gateway toggle, routing
  lists and routes file, TLS ciphers/groups/GREASE, ECH key/DoH resolver/domain,
  `--mark`, `--exit-loc`, `--stats`, and `--register`.

### 9.5 Registration (headless identities)

`--register <masque|wg|gool|mim|all>` registers identities and exits without
scanning or tunnelling (`cli.rs`). It is edited in the engine options group and
run by the full-engine path as a one-shot job bounded by the engine's own
registration budget (`AetherJobPolling.identityTimeoutMs`), with failures
reported through `AetherRegistrationOutcome`. `--enroll-address` (custom WARP API
endpoint) and `--dns` (custom tunnel DNS) are part of the same argument list.
