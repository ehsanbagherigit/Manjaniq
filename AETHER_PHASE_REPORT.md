# Aether UI redesign + logic audit — final report

> **Historical.** This report describes the build that had two start paths
> (granular FFI calls and `aether_core_start`). That architecture is gone: the app
> now starts Aether only through `aether_core_start`, the reach notes, the
> "Not selectable here" badge, the start-path choice and the helper summary row
> were removed with it, and `AetherScanOutcome` / `AetherStartMode` /
> `AetherCapabilities.reachOf` no longer exist. File names and line counts below
> are as they were when this report was written; see
> [`AETHER_CAPABILITIES.md`](AETHER_CAPABILITIES.md) §9 for the path in use.

Branch: `arena/780f8f24-manjaniq`. Base: `47edd94302f285588844ac9969788778ba84fafb`.
Companion document: [`AETHER_CAPABILITIES.md`](AETHER_CAPABILITIES.md) (the
capability inventory with per-row source evidence).

> **Read section 5 first.** This workspace has no JDK, no Gradle, no Android SDK
> and no Rust/Go toolchain, so nothing here was compiled or executed except two
> Python/shell CI checks. Every Kotlin change is statically reviewed, not built.

---

## 1. Aether scope — files changed and why

All paths relative to the repository root.

### New, Aether-only

| File | Lines | Why it exists |
|---|---|---|
| `app/src/main/java/com/manjaniq/ui/screens/AetherPanel.kt` | 1306 | The redesigned Aether UI: notice, start control, two-step route flow, per-setting rendering, import/export dialogs, inline validation. Everything the `SelectedCore.AETHER` branch used to inline in `HomeScreen.kt`. |
| `app/src/main/java/com/manjaniq/ui/components/AetherFlowControls.kt` | 309 | Aether-only primitives for the progressive flow: `AetherSection` (collapsible group), `AetherOptionGroup`/`AetherOptionRow` (a choice list drawn the way the Aether CLI lists it, with an unavailable state), `AetherReachNote` (honest "how far does this setting reach" line). |
| `app/src/main/java/com/manjaniq/ui/components/AetherLogPanel.kt` | 361 | The Aether operational log panel: black background, cyan monospace text, bounded height, auto-follow, "jump to latest", clear/copy, problems filter. |
| `app/src/main/java/com/manjaniq/ui/AetherTestTags.kt` | 62 | Aether-specific test tags, so new UI tests do not have to reuse or extend the shared `ui/TestTags.kt`. |
| `app/src/main/java/com/manjaniq/core/models/AetherCapabilities.kt` | 261 | The executable capability matrix (`AetherReach`, `AetherCapabilities`, the four choice enums, `AetherFragmenting`). |
| `app/src/main/java/com/manjaniq/core/AetherJobPolling.kt` | 51 | Deadline arithmetic for job polling, extracted so the overflow that killed every tunnel can be tested without a service. |
| `app/src/main/java/com/manjaniq/core/AetherLogSanitizer.kt` | 107 | Credential redaction at the Aether logging boundary. |
| `app/src/test/java/com/manjaniq/AetherJobPollingTest.kt` | 65 | Polling-deadline regressions (plain JUnit). |
| `app/src/test/java/com/manjaniq/AetherLogSanitizerTest.kt` | 118 | Redaction contract: secrets masked, operational detail preserved (plain JUnit). |
| `app/src/test/java/com/manjaniq/AetherConfigLogicTest.kt` | 216 | `validate()`, CLI mapping, port-conflict detection, fragment ranges (Robolectric — reads `R`). |
| `app/src/test/java/com/manjaniq/AetherCapabilitiesTest.kt` | 204 | The capability matrix and every route's section→setting mapping (Robolectric). |
| `app/src/test/java/com/manjaniq/AetherConfigJsonTest.kt` | 149 | Persistence, including configs written before the fragmenting fields existed (Robolectric — real `org.json`). |
| `app/src/test/java/com/manjaniq/AetherPanelValidationTest.kt` | 136 | Inline field validation kept in step with `AetherConfig.validate()`, and the tag surface (Robolectric). |
| `app/src/test/java/com/manjaniq/AetherPanelUiTest.kt` | 381 | The panel itself: log visibility/collapse/scroll/redaction/filter, progressive disclosure, collapse of a selected route, drafts surviving a round trip, inline errors, unreachable choices (Robolectric + Compose). |
| `AETHER_CAPABILITIES.md` | 218 | Capability inventory with repository evidence. |

### Modified, Aether-only

| File | Change |
|---|---|
| `core/models/AetherConfig.kt` | Fragmenting fields added and persisted; validation widened to the real choice IDs (and made lenient where empty means "Aether picks"); `toAetherArgs` fixed; `httpProxyBind` trimmed consistently; `detectAetherPortConflict` extended. |
| `core/models/AetherProfile.kt` | Sections reorganised into the groups the new flow shows; `section_fragmenting` added for the routes that can carry MASQUE; `profileUsesAetherGateway` / `profileSupportsFragmenting` helpers. |
| `core/AetherRepository.kt` | `log()` synchronised, ring capped, messages redacted through `AetherLogSanitizer`; `clearLogs()` added; conflict detection extended to socks == http. |
| `core/AetherProfileStore.kt` | Removed a dead read of a key that does not exist in that preferences file and corrected the misleading comment. Migration behaviour unchanged. |
| `service/AetherService.kt` | The Phase-2 fixes: unbounded tunnel poll, `ip` forwarded to the scan, readiness-gated RUNNING, per-start cancellation, superseded-attempt handling, truthful exit classification, unforwarded-settings warnings. |
| `ui/components/AetherStatusCard.kt` | No JNI call during composition; `TestTags.AETHER_STATUS` applied. |
| `ui/components/AetherProfileCard.kt` | Recoloured from the purple family to the Aether cyan/surface tokens, so the Aether region reads as Aether. Structure, tags and behaviour unchanged. |
| `ui/screens/AetherSettingsForm.kt` | `TestTags.AETHER_SETTINGS` applied (it was declared for this form and never used). Nothing else touched. |
| `res/values/strings_aether.xml` | +127 lines: labels, reasons and states for the new flow and the log panel. No existing key removed or reworded. |

### Modified, shared — one file, one region

`ui/screens/HomeScreen.kt` (613 → 200 lines). It is a shared screen: it hosts both
cores. Only its `SelectedCore.AETHER` branch changed — it is now a single
`AetherPanel(...)` call that forwards the 14 Aether bindings it already received.
Three private composables used **only** by that branch (`ProfilesSection`,
`ProfileSettingsContent`, `ProfileImportExport`, 330 lines) were deleted with it;
they had no other caller. The RSTA branch, the `HomeScreen` signature, the imports
and every other file that reads `HomeScreen` are byte-identical, so
`ui/components/ManjaniqApp.kt` needed no edit.

This is the smallest Aether-local integration possible: the alternative was to
leave ~430 lines of Aether UI inside a shared file, which is what made the Aether
boundary unmaintainable in the first place. No shared component, theme, colour,
navigation destination or service was modified for Aether's benefit.

---

## 2. UI changes — how the progressive flow works

The Aether core now renders four bands, in this order:

1. **Notice** — one 48 dp row ("Aether · Warp MASQUE" + "Local SOCKS5 proxy · no
   device-wide VPN · socks5h://…"). The five-paragraph block that used to sit here
   is behind that row's toggle, so the start button is on the first screen.
2. **Error / native-missing** — only rendered when there is something to say.
3. **Start control** — one full-width 52 dp button: START ENGINE, a spinner plus
   "Stopping…", STOP ENGINE, or "Cancel start" while starting. It is `STARTING`
   green-ish/cyan, `RUNNING` purple, `ERROR` uses the theme error colour through
   the status card below. The button keeps `TestTags.AETHER_TOGGLE`
   unconditionally so the existing instrumentation tests still find it.
4. **Status card** (existing `AetherStatusCard`, now tagged) and the
   **operational log panel**.

Between 3 and 4 sits the selection-driven flow:

**Step 1 — "Choose your route".** Six full-width cards, one per `AetherProfile`,
each showing the route name, its one-line path (`Cloudflare WARP → MASQUE`,
`Aether → Tor → Psiphon`, …) and a status badge. `Available` routes are
selectable; `RequiresExternalComponent` routes are selectable too (nothing was
hidden), and selecting one shows the existing `ProfileStatusBanner` explaining what
is missing. Tapping the already-selected route collapses its settings — the old
code could only ever add `true` to an expansion map, so a route's settings could
never be closed again.

**Step 2 — "SETTINGS FOR <route>".** Only the groups that route owns are listed
(`getSectionsForProfile`), and only the first one starts open:

* `Gateway` — Protocol (MASQUE / WireGuard / Gool / MIM), Scan mode
  (turbo…ironclad), IP version (v4/v6/both), Peer override. Scan mode and IP
  version are hidden while a peer is set, because `AetherService` skips the scan
  entirely in that case; the panel says so ("Scan skipped: a fixed peer is set").
* `Fragmenting` — only for routes that can carry MASQUE. *(Superseded twice since:
  §8.2 moved it out of the per-route groups into one area before the log, and §10 made
  it an applied, editable control.)*
* `Tor`, `Psiphon` — only for the routes that own them.
* `Local proxy` — SOCKS5 bind, HTTP proxy bind, DNS, upstream proxy.
* `Advanced` — obfuscation profile, ECH, quick reconnect, Zero Trust team,
  identity file.

Each collapsed group shows a one-line summary of what it currently holds
(`MASQUE · balanced · IPv4 only`, `127.0.0.1:1819`), so the whole route stays
scannable without opening anything. *(The fragmenting summary once read `Off ·
Recorded, not applied in this build`; the fragmenting area now reports the carrier the
stored value implies — see §10.)*

**States are visually distinct.** Selected choice = filled cyan dot, cyan border,
`AetherSurface` fill. Unselected = hollow dot, thin border. Unavailable = 70 %
alpha, no click handler at all, and the reason in amber where the description used
to be, plus a "Not selectable here" badge. Expanded group = cyan border and cyan
title; collapsed = neutral. Loading = spinner in the start button. Invalid = the
`OutlinedTextField` turns red with the reason underneath, as you type. Error = the
error card at the top with a Dismiss action, and `StatusError` in the status card.

**Nothing is lost when you go back.** Per-route drafts and per-route open groups
are hoisted into `AetherRouteFlow` (`drafts: Map<String, AetherProfileConfig>`,
`openSections: Map<String, Set<Int>>`), keyed by route id. `AetherRouteSettings` is
recreated on every route switch, so before this change its `remember`ed draft died
with it. Now selecting route B and coming back to route A restores what you typed
*and* which groups were open; the panel flags it with "Unsaved changes" and the
Save button stays where it was.

**Density.** Every fixed height was replaced by `heightIn(min = 32..52.dp)`; the
log panel is the only bounded-height element and it sizes to its content up to
220 dp. No spacers fill space, no decorative panels, no reserved empty areas.

**Accessibility.** Every tappable row is at least 48 dp tall; every choice has a
visible label plus a one-line description; every group header is a single 48 dp
target; the log panel's header states its content ("120 events · RUNNING"), and the
follow state is a labelled button, not an icon.

---

## 3. Capability inventory

Full version with per-row evidence in [`AETHER_CAPABILITIES.md`](AETHER_CAPABILITIES.md).
Summary:

| Capability | Status |
|---|---|
| Transport `masque`, `wg` | Implemented & accessible |
| Transport `gool`, `mim` | Implemented upstream, **not reachable on this path** — shown, disabled, reason given (`api.rs:13-26`) |
| Scan mode (5 values + aliases) | Implemented & accessible (`prober.rs:155-165`) |
| IP version (v4/v6/both) | Implemented & accessible — **was silently dropped, now sent** (`prober.rs:120-127`) |
| SOCKS5 bind, HTTP proxy bind, peer, identity path | Implemented & accessible |
| ECH | Partial — no-op on WireGuard (no TLS handshake to hide a name in) |
| Obfuscation profile (off/light/firewall/balanced/gfw/aggressive) | Partial — applied to the scan and to WireGuard, **not** to a MASQUE tunnel (`api.rs:557-561`); the two upstream parsers also disagree about `balanced` |
| Zero Trust team name | Partial — the team payload is sent, but enrolment needs a token/one-time code and no screen calls the six `aether_team_*` exports |
| DNS, upstream proxy, quick reconnect | Implemented upstream, CLI/env-only → **recorded, validated, exported, shown with the reason**; not editable-as-if-they-worked |
| **Fragmenting** | Implemented upstream (`fragment.rs`, consumed only by `masque_h2.rs`), configured only via `AETHER_MASQUE_H2_FRAGMENT*` → **recorded only**. Displayed read-only with the four variable names and why; persisted, validated, exported, and mapped onto the real CLI flags in `toAetherArgs()`. No decorative switch was added. See §3 of the companion doc for the two ways to make it live, both of which need approval because they change how the tunnel starts or add native surface. |
| Psiphon chaining, Tor chaining | Not implemented — no component is bundled and no payload field exists; routes stay listed with `RequiresExternalComponent` |
| MASQUE-over-HTTP/2 carrier (`AETHER_MASQUE_HTTP2`) | Not surfaced — without it, fragmenting has no carrier to act on |
| All-route export/import (`AetherProfileStore.exportAllJson`/`importAllJson`/`loadAll`) | Implemented, never surfaced (per-route export/import **is** in the UI) |
| `aether_core_start`, `aether_verify_start` | Wrapped by `AetherBridge`, no caller |

Fragmenting default conflict, reported rather than resolved: code says
off/`8-16`/`2-10` (`fragment.rs:32-43`), `cli.rs:133-134` and
`Docs/GUIDE.en.md:203-205` say `16-32`/`2-10` and off, and `cli.rs:392` claims
"(default on)". The app follows the documented values and pins them in
`AetherCapabilitiesTest`.

---

## 4. Logic findings

Confirmed, reproduced by reading the code path end to end, and fixed — the table in
§4 of [`AETHER_CAPABILITIES.md`](AETHER_CAPABILITIES.md) lists all fourteen with
root cause and fix. The headline:

**Every Aether tunnel was killed seconds after it started.** `AetherService`
polled the tunnel job with `pollJob(tunnelJobId, timeoutMs = Long.MAX_VALUE)`;
`System.currentTimeMillis() + Long.MAX_VALUE` overflows negative, the loop's first
`shouldContinue` check failed, `pollJob` returned `null`, and the service treated
that as "the tunnel ended" and called `AetherBridge.jobFree(tunnelJobId)` — and
`aether_job_free` cancels the job it removes (`third_party/Aether/aether/src/ffi.rs:318-330`).
The UI had just reported RUNNING. Fix: `AetherJobPolling.deadlineFor` returns `null`
for an unbounded (or overflowing) deadline and `shouldKeepPolling` treats `null` as
"keep going"; the tunnel poll passes `timeoutMs = null`.

Also confirmed and fixed: `ipVersion` never reached the scan; RUNNING published
before any handshake; a stop during a start reported as an Aether error; a restart
superseded by a stale attempt; socks == http allowed by the service-side guard but
not the UI-side one; a blank HTTP-proxy field rejected as "not loopback" while the
service forwarded it verbatim to the FFI; a tautological `takeIf`; unsynchronised
log ring mutation; a JNI round-trip during composition; a route that could not be
collapsed; `--noize off` dropped from the CLI mapping; a dead preferences read.

**Not fixed, deliberately:**

* gool/mim, DNS, upstream, quick reconnect, fragmenting — the gap is in the FFI
  payload, not in this app's logic. Closing it means starting Aether through
  `aether_core_start` (a connection-behaviour change) or adding a native export
  (unbuildable and unverifiable here). Both are reported with the exact
  requirement in §3 of the companion doc.
* The legacy `AetherSettingsForm` fallback still lays its transport/scan chips out
  in a horizontal `Row`, which can overflow a narrow screen. It is unreachable in
  the shipping app (`ManjaniqApp` always supplies a selected profile), and
  rewriting it would duplicate the new panel; noted instead.
* `TestTags.AETHER_DISABLED_BUTTON` is still never applied — swapping it in for
  `AETHER_TOGGLE` when the native library is missing would break
  `NavigationTest.aetherIsComingSoonAndCannotStartAnything`, which waits on
  `AETHER_TOGGLE`.

**Unverified suspicions (no code changed):**

* Whether `AetherBridge.identityOpen` survives an identity path outside the app's
  private files dir — cannot be exercised without `libaether.so`.
* Whether Aether's `verified`/`ironclad` scan modes return an endpoint this app
  can dial on a real network — needs a device and network.
* Whether `AETHER_MASQUE_HTTP2` plus fragmenting actually changes the ClientHello
  on Android — needs a build of `libaether.so` and a packet capture.

---

## 5. Tests and build — exact commands, actual results

### What this sandbox can run

| Command | Result |
|---|---|
| `command -v java javac gradle kotlinc cargo go adb sdkmanager` | all `(not found)`; `python3` is the only toolchain present |
| `./gradlew :app:compileDebugKotlin --offline` | **exit 1** — `ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.` |
| `./gradlew :app:testDebugUnitTest --tests "com.manjaniq.Aether*" --offline` | **exit 1** — same JAVA_HOME error |
| `python3 scripts/check_assets.py` | **exit 0 — ASSET CHECK PASSED** (25 raster files; `ic_aether_logo.png` matches the original, mean diff 0.03) |
| `bash native/verify_aether_lib.sh` | **exit 0**, `SKIP: …/jniLibs/arm64-v8a/libaether.so not found (local build without Rust - CI must build it). This is expected in sandbox.` |

`scripts/check_assets.py` needed `Pillow`, installed with
`python3 -m pip install --break-system-packages Pillow` (Pillow 13.0.0) — an
environment change only, nothing in the repository.

There is no JDK, no Gradle distribution, no Android SDK and no `libaether.so`
here, so **the compiler and the test runner for this work are GitHub Actions**,
which the repository already runs on every push
(`.github/workflows/build-debug.yml` → `scripts/ci/gradle_step.sh`:
`:app:compileDebugKotlin`, `:app:compileDebugUnitTestKotlin`,
`:app:compileDebugAndroidTestKotlin`, then `:app:testDebugUnitTest`,
`:app:assembleDebug`, an APK check, the Go engine tests, the Rust
`libaether.so` build and an `emulator-smoke` job).

### Actual CI runs on this branch

| Commit | Run | Result |
|---|---|---|
| `7f61c7e` | 39502681867 | **failure** — `compileDebugUnitTestKotlin`: unresolved references in `AetherPanelUiTest.kt` (`onAllNodes` is a rule member, not an import) and a bad `typeInto` helper |
| `6bf1ae3` | 39505418108 | **failure** — `testDebugUnitTest`: `70 tests completed, 9 failed`; all nine were test-side (`hasText` matches exactly by default, `assertIsDisplayed` is viewport-dependent under Robolectric, a sample port tripped the socks == http rule) |
| `7fef57c` | 39508147489 | **failure** — `compileDebugUnitTestKotlin`: `Unresolved reference 'boundsInRoot'`; compose-ui-test does not export it. The ordering assertion was dropped |
| `6029c55` | 39509804264 | **success** — build (compile ×3, unit tests, `assembleDebug`, APK verification) and `emulator-smoke` both green |
| `edfacb8` | 39519495879 | **failure** — `testDebugUnitTest`: `97 tests completed, 3 failed`, all three `AetherScanStateTest` assertions on `ui.endpoint`. Robolectric resets the Application but not Kotlin `object` state, so an endpoint an earlier test published was still in `AetherRepository`; fixed in the test's `@Before`, not in product code |
| `faacb65` | 39582881896 | **failure** — `testDebugUnitTest`: `102 tests completed, 1 failed`, `AetherScanStateTest.kt:118`. JUnit's three-argument form is `assertEquals(message, expected, actual)` and the arguments were swapped; fixed in `e04b70c` |
| `e04b70c` | 39585297915 | **success** — build (compile ×3, `102 tests / 0 failed`, `assembleDebug`, APK verification) and `emulator-smoke` both green |
| `a8b5e10` | 39696427508 | **success** — build: step 16 Rust `libaether.so`, step 17 native verification, step 19 Kotlin compile ×3, step 20 unit tests, step 24 `assembleDebug`, step 25 APK verification (`lib/arm64-v8a/libaether.so` and `libc++_shared.so` asserted present), step 26 upload; `emulator-smoke` downloaded that exact APK, booted a KVM emulator, launched it and ran instrumentation on the real UI |

No failure in that table came from product code; every red run was a test or
test-helper mistake, fixed in the commit above it.

The `emulator-smoke` job proves the APK installs, launches and passes instrumentation
on an emulator. It does **not** register an identity against the real WARP API, so it
is not evidence that registration succeeds - see §9.6 for what registration needs and
why that stays an environmental question.

Workflow logs and artifacts cannot be downloaded from this sandbox (the storage
hosts are unreachable), so failures were read from check-run annotations:

```
JOB=$(gh run view 39505418108 --json jobs -q '.jobs[]|select(.name=="build")|.databaseId')
gh api "repos/ehsanbagherigit/Manjaniq/check-runs/$JOB/annotations?per_page=100" \
  -q '.[]|select(.annotation_level=="failure")|.message'
gh run view 39509804264 --json status,conclusion,jobs \
  -q '.jobs[].steps[]|"\(.number):\(.status)/\(.conclusion)"'
```

One failure-level annotation survives on **green** runs:
`error[E0308] … --> src/lib.rs:38:74` from the x86_64 Rust build, which step 16
of the workflow deliberately tolerates — the arm64 `libaether.so` the APK needs is
verified separately in step 17. It is a pre-existing CI artifact, present on the
base commit's runs too, not a regression from this work.

### What the executed tests cover

Twelve test classes under `app/src/test/java/com/manjaniq/`, all Aether-only:

| Class | Runner | Covers |
|---|---|---|
| `AetherJobPollingTest` | plain JUnit | deadline arithmetic, the `Long.MAX_VALUE` overflow, and the ten engine scan budgets plus their fallbacks |
| `AetherLogSanitizerTest` | plain JUnit | PEM / JWT / URL-userinfo / key-value redaction at the logging boundary |
| `AetherScanOutcomeTest` | Robolectric | scan reply parsing (IPv4, bracketed IPv6, mis-shaped replies) and classification of every Aether scan error string from fixtures |
| `AetherScanStateTest` | Robolectric | what the panel is told after each outcome: success, no clean endpoint, timeout, cancellation, stop during startup, retry after both |
| `AetherConfigLogicTest` | Robolectric | `validate()`, the transport dispatch guard, port and loopback rules, CLI mapping |
| `AetherConfigJsonTest` | Robolectric | persistence round-trips, including every fragmenting field |
| `AetherCapabilitiesTest` | Robolectric | the capability matrix, the choice enums against the Rust parsers, fragmenting scope per route |
| `AetherPanelValidationTest` | Robolectric | per-field validation messages and the section/tag vocabulary |
| `AetherPanelUiTest` | Robolectric + Compose | the real `MainActivity`: progressive disclosure, collapsed groups, disabled choices, redaction, the log filter, scan-outcome display, small-input marking |
| `AetherRegistrationOutcomeTest` | plain JUnit | classification of every registration failure family, the engine's wording surviving verbatim, no report claiming success or a running tunnel, the reported 110 s inside the engine's 106.75-111.25 s window and below the app's deadline, and the identity deadline outlasting every schedule the engine can run (§9) |
| `AetherRegistrationStateTest` | Robolectric | what the panel is told while an identity registers: ERROR with the engine's wording, no identity or endpoint published, an app-side timeout not reported as success, a user stop reported as STOPPED, the identity job's active-job lifecycle, and a retry that starts clean and keeps the failure in history |
| `AetherNetworkCheckTest` | Robolectric | the read-only connectivity line: an unread state reported as unread rather than offline, a captive portal named, missing DNS or a system proxy reported rather than hidden, and no suggestion to weaken a check |

Nothing in them touches a network, a device or `libaether.so`. The scan and
registration tests use fixtures shaped exactly like the core's replies and errors —
for registration, the strings `https.rs`, `account.rs` and `error.rs` actually
produce, including the reported failure verbatim.

### Commands to run where a JDK and the Android SDK exist

```
./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin
./gradlew :app:testDebugUnitTest --tests "com.manjaniq.Aether*"
./gradlew :app:testDebugUnitTest          # regression: AetherBridgeTest, NativeBridgeTest, AppDestinationTest, RobolectricLaunchTest
./gradlew :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest  # LaunchSmokeTest, NavigationTest, EngineLifecycleTest, AppLaunch
```
These are the same steps CI runs.

---

## 6. Unresolved items

1. **Fragmenting cannot be applied** without either starting Aether through
   `aether_core_start` or adding an `aether-wrapper` export that sets the
   `AETHER_MASQUE_H2_FRAGMENT*` variables and `AETHER_MASQUE_HTTP2`. Both need
   approval: the first changes the connection path, the second adds native surface
   that cannot be built or tested in this workspace.
2. **gool / mim** need the same decision (they are CLI-only protocols on the
   `aether_core_start` path).
3. **DNS / upstream / quick reconnect** likewise — `TunnelSpec` has no fields for
   them. Today they are recorded, shown with the reason, and warned about in the
   operational log.
4. **Zero Trust enrolment** is incomplete by design: the six `aether_team_*`
   exports are wrapped but uncallable from any screen, and a team name without a
   token or one-time code cannot enrol. Needs a product decision (a token entry
   screen) before any UI can honestly offer it.
5. **Fragmenting default conflict upstream** (`fragment.rs` vs `cli.rs` vs
   `Docs/GUIDE.en.md`) — reported, not guessed at.
6. **`AETHER_MASQUE_HTTP2` carrier** has no control; adding one without a working
   fragmenting path would be a switch that changes nothing.
7. **All-route export/import** exists in `AetherProfileStore` and is still not
   surfaced.
8. The legacy `AetherSettingsForm` chip rows can overflow horizontally (unreachable
   fallback path; see §4).
9. **The scan budgets are a copy of upstream constants.** `AetherJobPolling`
   carries the ten `overall_deadline` values from `prober.rs` and `wg_prober.rs`.
   If Aether is re-vendored with different budgets, that table must be updated
   too; nothing in Kotlin can check it automatically. `AetherJobPollingTest` pins
   the current values so a change is at least deliberate.
10. **A thorough MASQUE scan now waits up to 320 s.** That is what Aether itself
    needs, and the wait happens in a foreground service with a notification, but
    it is a visible product decision: the alternative was the old behaviour,
    where the mode could never complete. There is no cancel-scan affordance other
    than Stop, which does cancel it and now reports that as a cancellation.
11. **`ip = v6` on a host without an IPv6 route fails at once** with
    `prober: no clean endpoint found` (`prober.rs:277`, `wg_prober.rs:162`).
    Pre-checking that in the app would need connectivity/network-capability APIs
    that are shared and outside this scope, so it is reported here instead: the
    message is now explained on screen, but the root cause (no IPv6 route) is not
    detected.

---

## 7. Scope confirmation

`git status --porcelain` lists 26 entries: 10 modified files, 14 new source and
test files, and 2 new documents.

* **9 of the 10 modified files are Aether-owned** by name and by content
  (`core/Aether*`, `core/models/Aether*`, `service/AetherService.kt`,
  `ui/components/Aether*Card.kt`, `ui/screens/AetherSettingsForm.kt`,
  `res/values/strings_aether.xml`).
* **The 10th is `ui/screens/HomeScreen.kt`**, a shared screen. Only its
  `SelectedCore.AETHER` branch changed: 102 lines of inline Aether UI became one
  19-line `AetherPanel(...)` call, and 330 lines of Aether-only private
  composables that nothing else called (`ProfilesSection`,
  `ProfileSettingsContent`, `ProfileImportExport`) were removed. Its signature, imports, RSTA branch and every
  call site are unchanged, which is why `ManjaniqApp.kt` — the shared composable
  that owns the Aether state — was not touched at all.
* **Not modified:** `ui/theme/Color.kt`, `ui/theme/ManjaniqTheme.kt`,
  `ui/TestTags.kt`, `ui/navigation/AppDestination.kt`,
  `ui/components/ManjaniqApp.kt`, `CoreSelector.kt`, `AppDrawer.kt`, `AppTopBar.kt`,
  `ConnectionStatusCard.kt`, `RstaOverview.kt`, `MainActivity.kt`, `NativeBridge.kt`,
  `core/EngineRepository.kt`, `core/ConfigStore.kt`, `core/AppPreferences.kt`,
  `core/models/RSTAConfig.kt`, `service/EngineService.kt`, `engine/*`, `native/*`,
  `res/values/strings.xml`, `res/values-fa/*`, `colors.xml`, `themes.xml`, both
  Gradle files, the CI workflow, `third_party/*`, and every existing test.
* No app-wide theme, shared colour, shared component, navigation element or shared
  service was changed for Aether's benefit. The Aether log panel reuses the
  existing `AetherCyan` token and `Color.Black`; no new colour was added to
  `ui/theme/Color.kt`.
* No Aether capability was removed or hidden to simplify the UI: all six routes,
  all four transports, all five scan modes, all six obfuscation profiles, both IP
  versions, ECH, quick reconnect, team name, DNS, upstream, identity path,
  fragmenting, Psiphon and Tor settings are all still reachable, and the ones this
  build cannot forward say so on screen.
* No dependency was added or updated.

---

## 8. Refinement pass — colour, fragmenting placement, dispatch, scan

A second Aether-only pass over the work above, with the same boundary: only files
Aether owns exclusively. Findings **o**, **p** and **q** in §4 and the fragmenting
placement in §3 come from this pass.

### 8.1 Colour

The amber and cyan accents that marked *selection, highlights and indicators*
inside Aether-owned components are now the purple this app already uses for
Aether. `ui/theme/Color.kt` was not touched: `ui/AetherAccent.kt` (new,
Aether-only) holds three aliases over purple tokens that already existed.

| Alias | Existing token | Value | Contrast on `#080808` / `#15141A` | Used for |
|---|---|---|---|---|
| `AetherAccent` | `ManjaniqPurpleNeon` | `#A66BFF` | 5.9:1 / 5.4:1 | selected borders, indicator dots, chevrons, short labels, section titles |
| `AetherAccentSoft` | `ManjaniqPurpleSoft` | `#C5A4FF` | 9.7:1 / 8.8:1 | small explanatory text, selected titles, reach notes, read-only values |
| `AetherAccentDeep` | `ManjaniqPurpleDeep` | `#251735` | fill | tinted surface behind a selection or a read-only block |

Both text colours clear WCAG AA (4.5:1) on the surfaces they sit on, and
`AetherAccentSoft` clears AAA. Text on an `AetherAccentDeep` fill measures 8.1:1
(soft) and 4.9:1 (accent), so the tinted blocks stay readable. Disabled content
keeps `onSurfaceVariant` and 70 % alpha rather than a purple that would read as
selected.

State mapping after the change:

| State | Treatment |
|---|---|
| selected | `AetherAccentDeep` fill + `AetherAccent` border (1.5 dp) + `AetherAccentSoft` label |
| expanded | `AetherAccent` at 45-50 % border + `AetherAccentSoft` title + purple chevron |
| unselected | `ManjaniqBorder` hairline, `onSurface`/`onSurfaceVariant` text |
| disabled / not forwardable | no click action at all, `onSurfaceVariant`, and the reason on screen in `AetherAccentSoft` |
| loading | `busy` in the state model: the Start control shows its progress state and is not clickable |
| success / running / starting / error | `StatusRunning`, `StatusStarting`, `StatusError` and the status dot — **unchanged** |

Deliberately not recoloured, because in each case the colour carries meaning
rather than decoration:

* the operational log keeps `AetherCyan` on `Color.Black` (its specified
  identity, 11.2:1);
* `StatusStarting` amber stays where it means *starting* — the status dot, the
  STARTING/STOPPING log indicators and the WARN log level. Purple there would
  have made "starting" indistinguishable from "selected";
* the Start/Stop button colours (neon blue to start, purple to stop) are the
  pre-existing theme spec, not an Aether selection state.

### 8.2 Fragmenting

Moved out of the per-route settings groups into one card near the bottom of the
screen, **immediately before the operational log**. The log panel stays the last
element on the screen, so the newest real Aether event is always at the very
bottom and the fragmenting area can never be mistaken for the live output. (An
earlier pass had the two the other way round; the current order is the one asked
for.) `getSectionsForProfile` lists no fragmenting section for any route, so it
can never read as a per-route or all-transport control.

At this point the card was informational and read-only, because nothing in the app
could apply the values. **That part is superseded by §10**: the card is in the same
place, but it is now an editable control and the values really are applied. What
§8.2 decided and §10 kept is the placement — one area, before the log, not a
per-route group.

### 8.3 Transport availability and dispatch

The full path was traced: chip → `AetherConfig.transport` → `validateForProfile`
→ `AetherProfileStore.saveFor` → `AetherService.startAether` →
`AetherBridge.tunnelStart` → `aether_tunnel_start` → `TunnelPayload.transport` →
`tunnel_spec_of` → `api::Transport::parse` → `api::connect` →
`run_masque_tunnel` / the WireGuard tunnel.

* **MASQUE** and **WireGuard** are implemented end to end on that path and are
  the only two the FFI can dispatch (`api.rs:13-26`, `transport_of`).
* **Gool** (WG-in-MASQUE) and **MIM** (MASQUE-in-MASQUE) exist in the core
  (`cli.rs` flags, `api::Transport`), but only `aether_core_start` — the CLI path
  — can select them. `aether_tunnel_start` has no way to ask for them, and
  `AetherBridge.coreStart` has no caller. The UI marks them unavailable with that
  reason, which is evidence, not assumption.
* `api::Transport::parse` maps every id that is not `wg` to Masque, so a stored or
  imported `transport = "gool"` used to start plain MASQUE with only a WARN in
  the log. `validate()` now rejects it (finding **q**), and because
  `AetherService.startAether` calls `validate()` before any FFI call, the start
  fails with a message that names the transport and what would otherwise have
  run. No entry point outside Aether was needed, so nothing was escalated.
* A transport is selectable in the UI only when the whole startup path exists:
  `AetherTransportChoice.selectable` drives both the chip's clickability and the
  validation guard, and `AetherCapabilitiesTest` pins it to `masque|wg`.

### 8.4 The two reported scan failures

**`Scan failed: prober: no clean endpoint found`** (reported for a WireGuard run
and for a MASQUE run) is Aether's own `AetherError::NoCleanEndpoint`
(`error.rs:23`), raised at `prober.rs:374` / `wg_prober.rs:264` when the budget
runs out with no verified candidate, and at `prober.rs:277` / `wg_prober.rs:162`
when an IPv6-only scan meets a host with no IPv6 route. It means *zero candidates
passed verification*, which is a reachability result, not a code fault:

* every candidate must pass `verify_one` — pinned certificate
  (`consts::MASQUE_PINS`), optional ECH injection, noize before the handshake;
* and, because `data_check_enabled()` is true unless `AETHER_MASQUE_NO_DATA_CHECK`
  is set (`quic.rs:88-90`), **two** successful DNS probe round-trips through the
  tunnel (`DATA_PROBE_REQUIRED_SUCCESSES = 2`). `--no-data-check`
  (`cli.rs:586-588`) is CLI/env-only, so the app always runs the strict path and
  cannot relax it;
* with ECH on, `job_ech` (`ffi.rs:179-188`) fails the whole MASQUE scan job if the
  ECH config cannot be fetched — a network-dependent step before any probing.

So for those two runs the verdict is **environment/network**, with one app-side
contributor that is now fixed: the WireGuard `balanced` budget is 80 s and the
MASQUE `balanced` budget is 120 s, while the app's poll deadline was a flat 120 s
— a race on MASQUE and an outright cut-off for `thorough`/`ironclad`
(finding **p**). What the app could not do is decide that no endpoint was
reachable; only the probe can.

**`Aether stopped while starting: Scan failed: cancelled`** is a stop the user
asked for during startup: `stopAether()` frees the job, `aether_job_free` cancels
it (`ffi.rs:318-330`), Aether answers `Cancelled`, and the per-attempt
cancellation flag added in the first pass reports it as INFO + STOPPED with no
error instead of ERROR. That behaviour is correct and is now pinned by
`AetherScanStateTest`. The message text goes through `AetherScanOutcome.report`,
so a cancellation reads as a cancellation.

**Tracing that surfaced a real defect:** even when a scan *did* choose an
endpoint, the service read it with `optString("endpoint")`, which stringified
`{"ip":…,"port":…,"rtt_ms":…}` and passed that JSON text to `aether_tunnel_start`,
where `socket_of` rejected it (finding **o**). Every scan-selected start therefore
failed at the tunnel step, and the endpoint the scan had verified was discarded.

Recoverability: each failure path frees the job, closes the identity, stops the
foreground service and publishes ERROR (or STOPPED for a cancellation) with
`busy = false` and no endpoint, so the next Start begins clean and Aether is never
shown as running. `AetherScanStateTest` asserts that for seven failure messages,
for cancellation, for a stop during startup and for a retry after both.

### 8.5 Files changed in this pass

New: `ui/AetherAccent.kt`, `core/AetherScanOutcome.kt`,
`test/AetherScanOutcomeTest.kt`, `test/AetherScanStateTest.kt`.

Modified: `ui/components/AetherProfileCard.kt`, `ui/components/AetherFlowControls.kt`,
`ui/screens/AetherPanel.kt`, `ui/AetherTestTags.kt`, `core/models/AetherProfile.kt`,
`core/models/AetherConfig.kt`, `core/AetherJobPolling.kt`,
`service/AetherService.kt`, `res/values/strings_aether.xml`,
`test/AetherPanelUiTest.kt`, `test/AetherConfigLogicTest.kt`,
`test/AetherJobPollingTest.kt`, `AETHER_CAPABILITIES.md`, `AETHER_PHASE_REPORT.md`.

Every one of those is Aether-owned by name and by content. `ui/theme/Color.kt`,
`ui/components/ManjaniqApp.kt`, `ui/screens/HomeScreen.kt`, the navigation, the
other engine's files, the shared tag vocabulary in `ui/TestTags.kt`,
`res/values/strings.xml` and both Gradle files are untouched in this pass; no new
dependency was added. `ui/components/AetherLogPanel.kt` keeps its cyan-on-black
text on purpose (§8.1).


---

## 9. Registration pass — the reported identity failure, end to end

The installed app was started at 02:59:33 and failed at 03:01:23 with

```
02:59:33 INFO  Aether start requested
02:59:33 INFO  Active Aether profile: warp_direct -> transport=masque
02:59:33 INFO  Aether version 2.3.0
02:59:33 INFO  Opening identity at /data/user/0/com.manjaniq/files/aether_identity transport=masque
02:59:33 INFO  Waiting for identity job 1...
03:01:23 ERROR api: registration: api: api.cloudflareclient.com did not answer within 20s
```

Aether never reached a scan or a tunnel. Everything below is read from the code
that produced those lines; nothing is inferred from the log text alone.

### 9.1 What each line proves

| Line | What it proves, and where |
|---|---|
| `Aether version 2.3.0` | `AetherService` called `AetherBridge.version()` → `nativeAetherVersion()` → `aether_version()`, which answers with `env!("CARGO_PKG_VERSION")` (`ffi.rs:276-278`). The string is compiled into the native library, so it can only appear if `libaether.so` loaded and the JNI round trip worked. `third_party/Aether/aether/Cargo.toml` says `version = "2.3.0"`, so the loaded engine is exactly the vendored one. |
| `Opening identity at …aether_identity transport=masque` | The service built `"$filesDir/${cfg.identityPath}"`, created the parent directory and called `identityOpen(path, transport = "masque", team = null)`. `aether_identity_open` (`ffi.rs:331-368`) forwards all three into `api::ProvisionRequest::for_transport` and derives the file it writes with `api::identity_path` (`api.rs:256-264`), which for MASQUE and no team is `derive_sibling_path(base, "masque")` = `…/aether_identity-masque` (`lib.rs:840-852`). |
| `Waiting for identity job 1…` | `aether_identity_open` answered `{"job":1,…}`, so the FFI accepted the payload and spawned the job. Registration runs inside that job. |
| the ERROR line | `pollJob` returned a failure whose message is the engine's, and `failed()` published it verbatim as ERROR and set state ERROR. |

That registration ran at all is itself evidence: `api::open_identity`
(`api.rs:319-338`) **loads** an existing identity file and only **provisions** a new
one when there is none, so this was a first registration for the MASQUE transport
and no `aether_identity-masque` file existed yet.

### 9.2 The 20 s is the engine's own per-attempt timeout

The message is worded by `https::send`: it wraps one `exchange` (dial + TLS + HTTP)
in `tokio::time::timeout` and, when that elapses, returns
`AetherError::Api("{authority} did not answer within {N}s")` (`https.rs:60-74`).
`{N}` is `account::API_TIMEOUT = 20 s` (`account.rs:248`). DNS resolution, the TCP
dial, the TLS handshake and the HTTP answer all sit **inside** that one budget
(`https.rs:76-110`), so "did not answer" covers any of them stalling.

The prefixes in the log line decompose exactly: `api:` is `AetherError::Api`'s
Display (`error.rs:29-30`), `registration:` is the label `api_call` was given
(`account.rs:597`), and the second `api:` is the nested `AetherError::Api` from
`https::send` folded into `format!("{label}: {error}")` (`account.rs:506`).

It is **not** an app-side timeout. The app's own deadline would have produced
`identity job timed out` from the `pollJob(jobId) == null` branch, and the identity
poll used `AetherJobPolling.DEFAULT_TIMEOUT_MS` = 120 s, while this failure arrived
at 110 s.

It is **not** a cancellation caused by polling. The app only frees (and so cancels —
`aether_job_free` cancels the job it removes, `ffi.rs:318-330`) *after* `pollJob`
returns, and a cancelled job reports `AetherError::Cancelled` → `cancelled`
(`error.rs:35-36`), not an API timeout.

### 9.3 The timing arithmetic

`api_call` (`account.rs:455-545`) makes `API_ATTEMPTS = 5` attempts
(`account.rs:250`), sleeping `backoff_delay` before attempts 2-5
(`account.rs:255-260`: 0.45-0.75 s, 0.9-1.5 s, 1.8-3.0 s, 3.6-6.0 s). When no
attempt is answered it returns the last error (`account.rs:544`).

| | seconds |
|---|---|
| 5 attempts × `API_TIMEOUT` | 100 |
| four backoff sleeps (min / max) | 6.75 / 11.25 |
| **total when nothing answers** | **106.75 – 111.25** |
| **observed (02:59:33 → 03:01:23)** | **110** |

The observed duration lands inside the engine's own window and below the app's
120 s deadline: the engine exhausted its five attempts and reported the last one.
`AetherRegistrationOutcomeTest.theObservedFailureLiesInsideTheEnginesOwnRetryWindow`
pins this arithmetic so it cannot drift.

`api_ech()` adds nothing here: it returns `Ok(None)` immediately unless the
`AETHER_API_ECH` environment variable is set (`account.rs:347-352`), and the app
cannot set process environment, so no DoH ECH lookup (and no `dns.rs:19` 12 s) is on
this path.

### 9.4 What the app forwards, against what the CLI forwards

Compared only where the code shows both paths:

| Registration input | App (`AetherService` → `AetherBridge.identityOpen`) | CLI (`lib.rs` → `account`) | Equal? |
|---|---|---|---|
| identity path | `"$filesDir/${cfg.identityPath}"`, forwarded as `path`; `api::identity_path` derives `…-masque` | `--identity` / config path | both go through `api::open_identity` with the caller's path (`api.rs:256-264, 319-338`) |
| transport | `cfg.transport` = `masque` → `Transport::Masque`, `masque_cert = true` (`api.rs:214-219`) | same enum | yes |
| team | sent only when `cfg.teamName` is non-empty | `--team`/Zero Trust settings | yes, both optional |
| model / locale | **not sent**, so `ProvisionRequest::default()` applies | the CLI has no `--model`/`--locale` option either: `lib.rs:775-779` provisions with `consts::DEFAULT_MODEL`/`DEFAULT_LOCALE` | yes — both claim `"PC"` / `"en_US"` (`consts.rs:10-11`) |
| API endpoint | `api.cloudflareclient.com:443` (`consts.rs:1`, `account.rs:290-292`) | same, unless `AETHER_ENROLL_ADDRESS` is set (`account.rs:423-434`) | same by default |
| TLS | BoringSSL with the engine's fingerprint, ECH only when a key is given, validation untouched (`https.rs:76-110`) | same function | yes — one code path |
| timeouts / retries | `API_TIMEOUT`, `API_ATTEMPTS`, backoff, `Retry-After` cap (`account.rs:248-304`) | same constants | yes |

So the app asked for the same registration the CLI would ask for. Two knobs the CLI
has and the app cannot reach are worth naming: `AETHER_ENROLL_ADDRESS` (dial a
specific address for enrollment, `account.rs:423-434`) and `AETHER_API_ECH`
(`account.rs:347-365`). Both are environment-only, and `aether_identity_open`
exposes neither, so an Android process cannot set them — a limitation, not a defect.

### 9.5 Two app-side defects this run exposed

**r — the identity poll could cancel a registration the engine was still retrying.**
The engine's worst case is longer than the 120 s the poll allowed: five 20 s
attempts plus backoff is up to 111.25 s, and a 429/408/5xx answer may carry
`Retry-After`, which `api_call` sleeps inside the loop capped at
`API_RETRY_AFTER_CAP_SECS = 30` (`account.rs:253, 293-304`) — up to four such sleeps,
so about 231 s. This run had 10 s of slack; a rate-limited one would have had the
app give up first, call `jobFree`, cancel the still-retrying job and report
`identity job timed out` instead of the engine's answer. Same family as defect p
(the scan budgets). Fix: `AetherJobPolling.identityTimeoutMs()` = attempts +
worst-case backoff + worst-case `Retry-After` + a 15 s margin = 246 s, with
`noAnswerWindowMs()` recording the 106.75-111.25 s window for the tests.

**s — a registration failure was reported with no explanation.** The user saw a raw
nested engine string with no attempt count, no elapsed time and no indication of
which layer failed, and every recovery had to be guessed. Fix: pure
`core/AetherRegistrationOutcome`, mirroring `AetherScanOutcome`, classifies the
engine's own message (NO_ANSWER, REFUSED_BY_API, IDENTITY_REFUSED, BAD_RESPONSE,
STORAGE, TLS, ECH, CANCELLED, TIMEOUT, OTHER) and keeps the engine's wording
**verbatim** in front of one factual sentence: how many attempts of how long, that
no identity was registered and no tunnel was started, and the recoveries the engine
itself names (`account.rs:379-392` for 403/429). It never converts a timeout into a
success and never suggests weakening a check.

**u — Stop during registration left the engine working and leaked the job.** The
identity block polled its job without ever publishing it, so `currentJobId` stayed
null while registration was in flight. `stopAether()` reads `currentJobId` to decide
what to `jobCancel` and `jobFree`, and `onDestroy()` does the same before
`scope.cancel()`: with nothing registered, a Stop cancelled the *attempt* (so the UI
correctly showed STOPPED) while the engine kept running its five 20 s attempts in the
background, and killing the poll coroutine meant the job was never freed at all. The
scan and tunnel blocks both publish their job; the identity block did not. Fix: the
identity job is published with `setActiveJob` + `currentJobId` before the poll and
cleared on all three exit paths, and a poll that answers `there is no job N` — the job
the app itself freed — is classified as a cancellation rather than a refusal. This
matters more now that the deadline is 246 s: a Stop has to end the registration, not
merely stop watching it.

Alongside those, the identity block now logs what the engine answered with instead
of a field that does not exist: the open reply carries `path`, `lastconn_path`,
`model` and `locale` (`ffi.rs:356-365`), never `summary`, so `summary=null` was
printed on every successful registration. It now prints the derived identity path,
the lastconn path and the model the registration claimed.

### 9.6 Environmental blocker, with the diagnostics that are safe to run

The cause of the reported failure is that `api.cloudflareclient.com:443` did not
answer this device on any of five attempts. That is a reachability result for the
device's network — name resolution, TCP, TLS or the HTTP answer — and no change in
this repository can make an unreachable endpoint reachable. It is reported as an
**environmental blocker**, not as a product fix.

To stop that being a guess, `core/AetherNetworkCheck` now logs the platform's own
read-only answer next to such a failure: active network or none, validated /
captive portal / not-internet, transport list (including vpn), DNS server count,
system proxy and metered state. It opens no socket, performs no DNS query, sends no
request and touches no certificate, and it uses only `ACCESS_NETWORK_STATE`, which
the manifest already declares — no shared file changed. When those facts say the
device was not on a usable network, a WARN says so plainly; when they say it was,
the blocker is upstream of the device and the log shows that too.

Ordinary checks that are safe to run on the device, in order: confirm the device has
working internet in a browser; confirm DNS resolves `api.cloudflareclient.com`
(e.g. from another app or `ping`); confirm no captive portal is waiting on the
network; confirm no VPN, filtering app or firewall is blocking outbound 443; then
retry the start, ideally on a different network. Nothing in that list weakens TLS:
the engine's certificate validation, pinning and fingerprint stay exactly as they
are (`https.rs:76-110`), and no code here disables them.

### 9.7 Native library and version evidence

* `libaether.so` for arm64-v8a and `libc++_shared.so` beside it are asserted by the
  CI step "Verify APK contents" (`.github/workflows/build-debug.yml:333-361`), which
  fails the build if either is missing; that step is green on run 39585297915.
* On the device, the `Aether version 2.3.0` line is the native library answering
  `aether_version()` (`ffi.rs:276-278`), and `Cargo.toml` says 2.3.0 — so the
  installed APK contains the vendored engine and it loaded.
* `native/verify_aether_lib.sh` is the local equivalent; in this sandbox it exits 0
  with SKIP because no `.so` is built here.

### 9.8 Tests added in this pass

`test/AetherRegistrationOutcomeTest.kt` (pure JUnit) — classification of every
failure family including the reported message verbatim, the engine's wording
surviving into the report, the attempt count and per-attempt budget being named, no
report claiming success or a running tunnel, recovery hints taken from what the
engine says, no suggestion to weaken certificate validation, the 110 s observation
inside the engine's window and below the app's deadline, and the identity deadline
outlasting every schedule the engine can run.

`test/AetherRegistrationStateTest.kt` (Robolectric) — what the screen is told:
ERROR with the engine's wording on screen, no identity and no endpoint published, an
app-side timeout reported as an error and not as a success, a user stop reported as
STOPPED with no error, exactly one ERROR line per failed registration, a retry that
starts clean, can succeed and keeps the earlier failure in the log history, and the
identity job being the active job while it is polled and cleared on all three exits.

`test/AetherNetworkCheckTest.kt` (Robolectric) — an unread network state is reported
as unread rather than as offline, a captive portal is named, a missing DNS server or
a system proxy is reported rather than hidden, and no line suggests disabling a
check.

Registration **success** through the real API cannot be unit-tested: it needs a
device, a network that reaches the API and the real `libaether.so`. What is tested
is the app's side of it — the state it publishes, the identity it records, the paths
it logs and the errors it refuses to hide.

### 9.9 Files changed in this pass

New: `core/AetherRegistrationOutcome.kt`, `core/AetherNetworkCheck.kt`,
`test/AetherRegistrationOutcomeTest.kt`, `test/AetherRegistrationStateTest.kt`,
`test/AetherNetworkCheckTest.kt`.

Modified: `core/AetherJobPolling.kt`, `service/AetherService.kt`,
`AETHER_CAPABILITIES.md`, `AETHER_PHASE_REPORT.md`.

All are Aether-owned by name and by content. `AndroidManifest.xml` was **not**
touched — `ACCESS_NETWORK_STATE` was already declared (line 6). No shared
component, theme, navigation, string resource, Gradle file, CI workflow, other
engine file or vendored `third_party` file changed, and no dependency was added.

---

## 10. Fragmenting pass — from "recorded, not applied" to applied

The task: find out why Fragmenting was marked *Recorded, not applied*, make the
controls interactive, and wire the values down to the engine's startup path.

### 10.1 Root cause

Three facts, each read from the source, and together they are the whole reason:

1. **Aether reads fragmenting from its environment, not from a payload.**
   `FragmentConfig::from_env` (`fragment.rs:32-61`) reads
   `AETHER_MASQUE_H2_FRAGMENT`, `_SIZE`, `_DELAY` and `_SNI`, and it is called when
   a connection is made (`masque_h2.rs:284`, `:402`).
2. **The FFI path the app uses has no field for it.** `TunnelPayload`
   (`ffi.rs:246-260`) carries `peer`, `transport`, `socks`, `http`, `profile`,
   `keepalive` and `ech`; `tunnel_spec_of` (`ffi.rs:430-447`) maps those onto
   `api::TunnelSpec` (`api.rs:449-459`), which has no fragmenting member either.
   So no value the app could put in a payload would have been read.
3. **`toAetherArgs()` did map the values onto real CLI flags** (`--fragment`,
   `--no-fragment`, `--fragment-size`, `--fragment-delay`, `cli.rs:602-605`), but
   that argv belongs to `AetherBridge.coreStart` → `aether_core_start`, which has
   **no caller**: the service starts a tunnel through
   identity → scan → `aether_tunnel_start`. The flags were therefore built and
   dropped.

An Android process cannot set the environment of a library it has already loaded
from Kotlin, which is why the earlier passes reported the gap instead of closing
it. The engine's own CLI closes it the same way it can — by writing the variables
(`cli.rs:579-580`, `:602-605`) — and the engine itself does this at runtime too
(`lib.rs:198`, `:226` for tor/psiphon, `api.rs:553` for the HTTP proxy).

One more fact shaped the design: `fragment.rs` has exactly **one** consumer,
`masque_h2.rs` (`:16`, `:239`, `:284`, `:402`), and that carrier is selected by
`masque_h2::enabled()` reading `AETHER_MASQUE_HTTP2` (`masque_h2.rs:141-149`),
consulted by the tunnel (`lib.rs:1530-1544`), by the scanner's verification
(`prober.rs:414-431`) and by the CLI's transport question (`lib.rs:3255-3268`).
Fragmenting without that carrier does nothing at all, so the two have to travel
together — the engine's own CLI pairs `--h2` with `--fragment` for the same reason.

### 10.2 What was implemented

**Native bridge** (`aether-wrapper/src/lib.rs`, the Aether JNI crate that CI builds
into `libaether.so`): one new export,
`Java_com_manjaniq_AetherBridge_nativeAetherFragmentingApply`. It takes a fixed JSON
payload `{enabled, http2, sniSplit, size, delay}`, and

* writes only `AETHER_MASQUE_HTTP2`, `AETHER_MASQUE_H2_FRAGMENT`,
  `AETHER_MASQUE_H2_FRAGMENT_SIZE`, `AETHER_MASQUE_H2_FRAGMENT_DELAY` and
  `AETHER_MASQUE_H2_FRAGMENT_SNI` — names never come from the payload, so it is not
  a general environment setter;
* validates both range specs (`n` or `a-b`, whole numbers, ≤ 1500 bytes / ≤ 5000 ms,
  the same limits the UI uses) **before** writing anything, and refuses the whole
  write otherwise;
* removes the chunk variables when fragmenting is off, so a previous route's values
  cannot survive into a tunnel that did not ask for them — this environment outlives
  one tunnel, and `from_env` is read per connection;
* answers with the values **read back** from the environment, so the app can log what
  was set rather than what was requested.

It touches no certificate, pin, fingerprint, ALPN or validation setting: it selects a
carrier the engine already implements and the chunk sizes `fragment.rs` already
parses.

**Kotlin** — `AetherBridge.fragmentingApply(...)` wraps the export through the same
`safeJsonCall` boundary as every other call. `AetherConfig` gained
`usesHttp2Carrier()` (`fragmentEnabled && transport == "masque"`) as the single
pairing rule, `validate()` now refuses fragmenting on any other transport instead of
storing a value the engine would ignore, and `toAetherArgs()` emits the carrier flag
too — `--h2` with fragmenting, `--h3` without, for every MASQUE start, because
`select_masque_transport` prompts interactively when the variable is unset and a
service cannot answer a prompt.

`AetherService.applyFragmenting()` runs **before the scan** (the scanner's
verification follows the same carrier choice), logs what the environment then holds
in both directions, and returns a reason to refuse the start when fragmenting was
asked for and could not be put in effect — including when the bridge's read-back says
the carrier or the fragment flag is not set. Starting anyway would have run an
unfragmented tunnel while the route asked for fragments, which is the same silent
substitution the transport guard already refuses. A failure while fragmenting is off
is a WARN, and says that the engine keeps whatever its environment already held.
`logUnforwardedSettings` no longer lists fragmenting as unapplied.

**UI** — `AetherFragmentingInfo` became `AetherFragmentingControl`, in the same place
( immediately before the log panel, which stays last). It has a switch for
fragmenting, the carrier consequence beside it while it is on, validated chunk-size
and delay fields, a switch for the SNI split, an unsaved-changes hint, inline errors,
and a Save button enabled only for a dirty, valid draft that asks about a restart when
Aether is RUNNING. The status line names the carrier the stored value implies. When
the selected route's transport is not MASQUE, **no controls are rendered** — the card
says why and keeps the stored values visible read-only, because a switch that cannot
reach Aether would be a lie. `AetherCapabilities` reclassifies the four keys from
`NotForwarded` to `Partial` (applied, on one carrier), with a reason string that says
so, and the SNI split keeps its own reason because upstream has no flag for it.

### 10.3 Tests

`test/AetherFragmentingWiringTest.kt` (new, Robolectric) pins both serialisations and
the rule that keeps them honest: `--h2` with fragmenting and `--h3` without, a carrier
flag for every MASQUE start so the CLI can never prompt, size and delay as their own
flags, a blank spec leaving Aether's default alone, no carrier flag for WireGuard,
`usesHttp2Carrier()` across transport × enabled, fragmenting refused on `wg`, gool and
mim still refused earlier by the transport rule, eleven unusable specs rejected
(`abc`, `1-2-3`, `16..32`, `-5`, `16-`, `1-99999`, `99999`, …) while the usable ones
(including a reversed range, which `fragment.rs` swaps) are accepted, the UI limits
equal to the bridge's, the SNI split persisted but absent from the argument list, and
— with no native library present — `fragmentingApply` reporting failure rather than
pretending.

`test/AetherPanelUiTest.kt`: `fragmentingIsEditableAndNamesTheCarrierItChanges` drives
the real `MainActivity` — switch on, carrier note appears, Save becomes enabled,
editing marks the draft dirty, `abc` is refused inline and Save will not store it,
`24-48` saves and the card then reports the route as running MASQUE over HTTP/2.
`fragmentingSaysWhyItIsUnavailableOnAWireGuardRoute` stores a WireGuard route and
asserts the explanation appears, the values stay visible and no Save button exists.
The gool/mim test keeps asserting *Recorded only* for a setting that genuinely is not
forwarded (`dns`), which is now a different row from fragmenting.

`test/AetherCapabilitiesTest.kt`: the four keys are `Partial` and forwarded, the SNI
split has its own reason, `ENV_VARS` includes the carrier variable, and fragmenting is
still not a per-route section for any profile.

### 10.4 What was not verified, and why

No device or emulator with a reachable WARP API exists in this sandbox, and no JDK,
Android SDK, Rust or Go toolchain can be installed here, so **no fragmented connection
was observed**. CI compiles the new JNI export into the arm64 `libaether.so` and the
emulator gate installs that exact APK and exercises the UI, but neither runs a
MASQUE-over-HTTP/2 tunnel. The engine's own `fragment.rs` unit tests are upstream and
this repository's CI runs no `cargo test`, and the wrapper's new function has no Rust
test either: environment mutation is process-global, so parallel `cargo test` threads
would race on it, and the workflow is shared infrastructure that this task may not
change. What is verified is the app's side of the contract — arguments, pairing,
validation, classification and screen behaviour — plus compilation of the native side.

Also unchanged, deliberately: `aether_core_start` is still not used, so gool, mim,
DNS, upstream and quick reconnect stay unwired; no independent carrier control was
added (the bridge payload already separates `http2` from `enabled`, so it needs no
native change); `--api-fragment` remains unexposed.

### 10.5 Files changed in this pass

New: `test/AetherFragmentingWiringTest.kt`.

Modified: `aether-wrapper/src/lib.rs`, `app/src/main/java/com/manjaniq/AetherBridge.kt`,
`core/models/AetherConfig.kt`, `core/models/AetherCapabilities.kt`,
`core/models/AetherProfile.kt`, `service/AetherService.kt`, `ui/AetherTestTags.kt`,
`ui/screens/AetherPanel.kt`, `res/values/strings_aether.xml`,
`test/AetherCapabilitiesTest.kt`, `test/AetherPanelUiTest.kt`,
`AETHER_CAPABILITIES.md`, `AETHER_PHASE_REPORT.md`.

Every one is Aether-owned by name and by content: the JNI crate exists only to bridge
Aether (`aether-jni`, "Manjaniq JNI wrapper for Aether v2.3.0"), and the strings file,
test tags, capability model, service, panel and tests are all Aether-specific.
`third_party/Aether/**` was **not** modified — the vendored engine is unchanged, which
is why the wiring goes through the bridge's environment write rather than a new
payload field. `AndroidManifest.xml`, both Gradle files, the CI workflow,
`ui/theme/*`, `ui/components/ManjaniqApp.kt`, `ui/screens/HomeScreen.kt`, the
navigation, `res/values/strings.xml`, the other engine's files (`engine/`, `native/`,
`service/EngineService.kt`, `core/EngineRepository.kt`) and `NativeBridge.kt` are
untouched in this pass, and no dependency was added.
