# Aether UI redesign + logic audit — final report

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
* `Fragmenting` — only for routes that can carry MASQUE.
* `Tor`, `Psiphon` — only for the routes that own them.
* `Local proxy` — SOCKS5 bind, HTTP proxy bind, DNS, upstream proxy.
* `Advanced` — obfuscation profile, ECH, quick reconnect, Zero Trust team,
  identity file.

Each collapsed group shows a one-line summary of what it currently holds
(`MASQUE · balanced · IPv4 only`, `127.0.0.1:1819`, `Off · Recorded, not applied in
this build`), so the whole route stays scannable without opening anything.

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
| `edfacb8` | 39519495879 | **PENDING_PASS2** |

No failure in that table came from product code; all three red runs were test or
test-helper mistakes, fixed in the commit above them.

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

Nine test classes under `app/src/test/java/com/manjaniq/`, all Aether-only:

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

Nothing in them touches a network, a device or `libaether.so`. The scan tests use
fixtures shaped exactly like the core's replies.

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

Moved out of the per-route settings groups into one informational card near the
bottom of the screen, **immediately before the operational log**. The log panel
stays the last element on the screen, so the newest real Aether event is always
at the very bottom and the fragmenting report can never be mistaken for the live
output. (An earlier pass had the two the other way round; the current order is
the one asked for.) See `AETHER_CAPABILITIES.md` §3 for the full trace and the exact
carrier scope; the card states that scope on screen ("MASQUE over HTTP/2 … never
to WireGuard, gool or mim"), names the reason it is not applied, shows the values
recorded with the selected route read-only, and offers no switch.
`getSectionsForProfile` no longer lists a fragmenting section for any route, so
it can never read as a per-route or all-transport control.

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

