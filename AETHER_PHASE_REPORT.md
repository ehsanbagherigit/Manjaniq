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

### Ran in this workspace

| Command | Result |
|---|---|
| `./gradlew :app:compileDebugKotlin --offline` | **exit 1** — `ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.` |
| `./gradlew :app:testDebugUnitTest --tests "com.manjaniq.Aether*" --offline` | **exit 1** — same JAVA_HOME error |
| `python3 scripts/check_assets.py` | **exit 0 — ASSET CHECK PASSED** (25 raster files; `ic_aether_logo.png` matches the original, mean diff 0.03) |
| `bash native/verify_aether_lib.sh` | **exit 0**, `SKIP: …/jniLibs/arm64-v8a/libaether.so not found (local build without Rust - CI must build it). This is expected in sandbox.` |
| `command -v java javac gradle kotlinc cargo go adb sdkmanager` | all `(not found)`; `python3` is the only toolchain present |

`scripts/check_assets.py` needed `Pillow`, installed with
`python3 -m pip install --break-system-packages Pillow` (Pillow 13.0.0) — an
environment change only, nothing in the repository.

So: **no compilation and no test execution happened.** There is no JDK, no Gradle
distribution (`gradle/wrapper` can't fetch one without Java), no Android SDK, and
no `libaether.so`, so the JNI path cannot be exercised at all. This is not a
pre-existing failure versus a regression — nothing was runnable before my changes
either.

### What was verified instead

1. **Brace/paren/bracket balance** over all 23 changed and new Kotlin files
   (string- and comment-aware scanner): all balanced.
2. **Resource resolution**: every `R.string.*` reference in `app/src/main`,
   `app/src/test` and `app/src/androidTest` (472 references, 339 distinct names)
   resolves against the 382 defined strings; **0 missing**. Every `R.drawable.*`
   resolves; **0 missing**.
3. **XML validity**: `strings_aether.xml` parses with `xml.dom.minidom`; no
   duplicate names; no unescaped apostrophes.
4. **Identifier cross-reference**: every `Object.member` reference to the Aether
   types (`AetherTestTags`, `AetherCapabilities`, `AetherFragmenting`, the four
   choice enums, `AetherJobPolling`, `AetherLogSanitizer`, `AetherRepository`,
   `AetherProfile*`, `AetherConfig`, `TestTags`, `AetherBridge`) resolves to a real
   declaration; every capitalised call in the new UI files resolves to an import, a
   same-package declaration or the stdlib.
5. **Manual reads** of the whole of `AetherPanel.kt`, `AetherFlowControls.kt`,
   `AetherLogPanel.kt` and the modified regions of `AetherService.kt`,
   `AetherRepository.kt`, `AetherConfig.kt`, `AetherProfile.kt`,
   `AetherStatusCard.kt`, `AetherProfileCard.kt`, `HomeScreen.kt`.
6. **Diff review** (§7): `git status --porcelain` and `git diff -U0` on
   `HomeScreen.kt` confirm the shared-file edits stay inside the Aether branch
   (hunks at old lines 175, 278 and 303 only; lines 1-174 untouched).

### Commands to run where a JDK and the Android SDK exist

```
./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin
./gradlew :app:testDebugUnitTest --tests "com.manjaniq.Aether*"
./gradlew :app:testDebugUnitTest          # regression: AetherBridgeTest, NativeBridgeTest, AppDestinationTest, RobolectricLaunchTest
./gradlew :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest  # LaunchSmokeTest, NavigationTest, EngineLifecycleTest, AppLaunch
```
These are the same steps `.github/workflows/build-debug.yml` runs through
`scripts/ci/gradle_step.sh`.

The 7 new test classes are **written but never executed**. The riskiest
assumptions a compiler would confirm or reject: Compose Material3 signatures used
by `AetherFlowControls`/`AetherLogPanel` (`Card(onClick=…)`, `FilterChip`,
`HorizontalDivider`, `OutlinedTextField(minLines=…)`), `internal` visibility from
the unit-test source set (`sectionKey`, `aetherBindError`, `MAX_RENDERED_LINES`),
and Robolectric's Compose viewport behaviour in `AetherPanelUiTest`
(`performScrollTo` before `performClick`, `performTextClearance`).

---

## 6. Unresolved items

1. **Nothing was compiled or run here.** Highest priority: run the commands in §5.
2. **Fragmenting cannot be applied** without either starting Aether through
   `aether_core_start` or adding an `aether-wrapper` export that sets the
   `AETHER_MASQUE_H2_FRAGMENT*` variables and `AETHER_MASQUE_HTTP2`. Both need
   approval: the first changes the connection path, the second adds native surface
   that cannot be built or tested in this workspace.
3. **gool / mim** need the same decision (they are CLI-only protocols on the
   `aether_core_start` path).
4. **DNS / upstream / quick reconnect** likewise — `TunnelSpec` has no fields for
   them. Today they are recorded, shown with the reason, and warned about in the
   operational log.
5. **Zero Trust enrolment** is incomplete by design: the six `aether_team_*`
   exports are wrapped but uncallable from any screen, and a team name without a
   token or one-time code cannot enrol. Needs a product decision (a token entry
   screen) before any UI can honestly offer it.
6. **Fragmenting default conflict upstream** (`fragment.rs` vs `cli.rs` vs
   `Docs/GUIDE.en.md`) — reported, not guessed at.
7. **`AETHER_MASQUE_HTTP2` carrier** has no control; adding one without a working
   fragmenting path would be a switch that changes nothing.
8. **All-route export/import** exists in `AetherProfileStore` and is still not
   surfaced.
9. The legacy `AetherSettingsForm` chip rows can overflow horizontally (unreachable
   fallback path; see §4).

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
