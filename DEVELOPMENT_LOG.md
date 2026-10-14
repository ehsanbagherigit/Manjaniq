# Manjaniq — Development Log

---

## 2026-10-14 12:28 UTC — Core Models and Interfaces Review

### Completed

**Core Kotlin model/interface corrections (DONE)**

The following six Kotlin files were reviewed against the Go engine source
(`engine/rstaspoof/rstaspoof.go`) and corrected where evidence required it:

- `core/models/EngineState.kt` — no changes needed; enum states confirmed.
- `core/models/RSTAConfig.kt` — corrected.
- `core/models/OperationResult.kt` — no changes needed; sealed result confirmed.
- `core/interfaces/EngineController.kt` — no changes needed.
- `core/interfaces/EngineStatusProvider.kt` — no changes needed.
- `core/interfaces/Logger.kt` — no changes needed.

Corrections to `RSTAConfig.kt` (per OpenCode audit report):

- Removed `noMonitor` (no direct engine equivalent; monitor behavior is
  governed by the `MONITOR` config value and the `-no-monitor`/`-quiet`
  flags in the engine).
- Removed `verbose` and `noRaw` — the engine parses these CLI flags but
  explicitly ignores them (`rstaspoof.go` lines 1843–1844:
  `_ = fNoRaw; _ = fVerbose`), so they have no runtime behavior to model.
- Removed `configJson` — an unused placeholder with no documented need in
  the current architecture.
- Added `fakeSNIMethod` with the source-verified default `"prefix_fake"`
  (`rstaspoof.go` lines 1385 and 1448).
- Retained `monitorEnabled` and `quietMode`.

Configuration defaults, bypass methods (`fragment`, `fake_sni`,
`combined`), and fragmentation strategies (`sni_split`, `half`, `multi`,
`tls_record_frag`) were cross-referenced against the Go engine source and
found consistent.

**Reference check (DONE)**

Per the final OpenCode reference-check report:

- Searched the project (excluding the nested `engine/rstaspoof/` repository)
  for references to the removed fields `noMonitor`, `verbose`, `noRaw`, and
  `configJson`.
- No broken references to these fields exist in the core Kotlin source files.
- Remaining mentions in `report.md` are historical documentation of the Go
  CLI, not broken Kotlin code references.
- References to `RSTAConfig` and `fakeSNIMethod` remain consistent
  (`core/models/RSTAConfig.kt`, `core/interfaces/EngineController.kt`).

### Validation

- Reference consistency between the six core files was verified by search
  (file paths, package declarations, and cross-references inspected).
- Configuration field values were verified against the Go engine source.
- **Kotlin/Android compilation has NOT been verified (NOT VERIFIED).**
- **Unit tests have NOT been run (NOT VERIFIED).**
- At the time of these checks, the project did not include an Android/Gradle
  build configuration, so no compilation or test run was possible.

### Known Limitations

- No Android/Gradle build configuration existed at the time of the core
  review and reference check, so the six Kotlin files remain uncompiled.
- The `fakeSNIMethod` values accepted by the engine's `FakeSNIBypass`
  implementation were not exhaustively enumerated; only the default
  (`"prefix_fake"`) is source-verified so far.

---

## 2026-10-14 12:45 UTC — Root .gitignore Preparation

### Completed

**Root .gitignore configuration (DONE)**

The root `.gitignore` was prepared for the initial GitHub setup. The following inspections were performed to determine the necessary ignore rules:

- **README.md**: Confirmed that `engine/rstaspoof/` is a separate repository and the nested `.git` directory is not required for the build.
- **app/build.gradle.kts**: Verified that Android source sets reference `core/` directories but do not depend on files within `engine/`.
- **settings.gradle.kts**: Verified the project structure includes only the `:app` module.
- **.github/workflows/build-debug.yml**: Confirmed that the CI build runs `./gradlew assembleDebug`, which does not require files from the `engine/` directory.

**Decisions and Changes:**

- Added `/engine/` to the ignore list based on the evidence that the current Phase 2 Android foundation does not depend on the engine source for compilation or CI.
- Preserved existing rules for build outputs (`/build`, `/app/build`, `/*/build`), Gradle cache (`.gradle`), local properties (`local.properties`), and IDE settings (`.idea/`, `*.iml`, `.vscode/`).
- Preserved OS-specific rules (`.DS_Store`).
- Preserved native build artifact rules (`*.apk`, `*.aab`, `*.so`) and the exception for `jniLibs`.

### Validation

- Verified that the final `.gitignore` contains all required rules and no redundant entries.
- **Confirmed** that no Git initialization, staging, commit, or push was performed.
- **Confirmed** that no files under `engine/rstaspoof/` were modified.
- **Confirmed** that only `.gitignore` was modified during the preparation task.

### Next Steps (PLANNED — not completed)

1. **PLANNED** — Initialize the Git repository and set up the remote origin.
2. **PLANNED** — Perform the initial commit and push to GitHub.

