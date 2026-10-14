Manjaniq — Development Roadmap

1. Purpose

This document defines the development path for Manjaniq, from initial source-code analysis to a working Android APK and future feature expansion.

It complements "ARCHITECTURE.md" by defining the implementation sequence, milestones, dependencies, and completion criteria.

Development must proceed incrementally. Each phase should produce a verifiable result before the next phase begins.

2. Development Principles

- Complete one milestone at a time.
- Verify each result before proceeding.
- Preserve the existing RSTA engine's networking behavior.
- Avoid implementing multiple unrelated features simultaneously.
- Keep the project buildable throughout development.
- Document important decisions and unresolved issues.
- Do not assume a feature works until it has been tested.
- Update this roadmap when implementation reveals new technical constraints.

3. Development Phases

Phase 1 — Project and Source Analysis

Goal: Understand the existing engine and establish a reliable technical foundation.

Tasks:

1. Review "ARCHITECTURE.md", "README.md", and "rstaspoof.go".
2. Identify the engine's configuration options, execution flow, dependencies, and shutdown behavior.
3. Determine which parts of the engine need adaptation for Android integration.
4. Verify the available development environment and Android build requirements.
5. Identify the target Android architectures and minimum supported Android version.
6. Record technical uncertainties and potential compatibility problems.

Completion criteria:

- The engine's relevant behavior is documented.
- Android integration options have been evaluated.
- Major technical risks and unresolved questions are recorded.

Phase 2 — Android Project Foundation

Goal: Create a clean, buildable Android project.

Tasks:

1. Create the Manjaniq Android project.
2. Configure the build system and package structure.
3. Establish the proposed architectural boundaries.
4. Set up Kotlin and the selected UI framework.
5. Create the initial application screen and navigation structure.
6. Configure version control and a reproducible build process.

Completion criteria:

- The project builds successfully.
- A basic Manjaniq APK can be installed and launched.
- The initial structure supports the planned feature modules.

Phase 3 — RSTA Integration Proof of Concept

Goal: Prove that the existing Go engine can run correctly inside the Android application without requiring Termux.

Tasks:

1. Select an integration approach based on the source analysis.
2. Create the smallest practical integration prototype.
3. Build the required Go component for the target Android architecture.
4. Launch the engine from the application.
5. Verify local proxy binding and basic connectivity.
6. Capture startup errors and engine output.
7. Test controlled shutdown and resource cleanup.

Completion criteria:

- The engine runs on the target Android device.
- The application can start and stop it reliably.
- The local proxy behaves as expected.
- The integration does not require a separate Termux installation.
- Any remaining limitations are documented.

Do not proceed to full UI integration until this proof of concept succeeds.

Phase 4 — Engine Adapter and Lifecycle Management

Goal: Build a stable interface between the Android application and the RSTA engine.

Tasks:

1. Implement the engine adapter.
2. Define shared configuration and status models.
3. Implement configuration validation.
4. Handle repeated start and stop requests safely.
5. Represent engine states consistently.
6. Forward logs, events, and errors to the application layer.
7. Handle unexpected termination and occupied ports.

Completion criteria:

- Engine operations are controlled through a defined interface.
- Lifecycle states reflect the actual engine state.
- Errors are reported without crashing the application.
- The integration can be tested independently of the UI.

Phase 5 — Initial RSTA User Interface

Goal: Provide a functional native interface for controlling the engine.

Tasks:

1. Build the main screen.
2. Add fields for supported engine configuration options.
3. Add controls to start and stop the engine.
4. Display current status and relevant errors.
5. Add a log or event display.
6. Prevent invalid configuration from being submitted.
7. Keep the UI responsive during engine operations.

Completion criteria:

- Users can configure, start, stop, and monitor RSTA from the UI.
- Displayed status reflects the real engine state.
- Invalid configurations produce understandable errors.
- The UI does not directly implement networking logic.

Phase 6 — Configuration Persistence

Goal: Make the application practical for repeated use.

Tasks:

1. Save and restore application settings.
2. Support named configuration profiles.
3. Validate restored configurations before starting the engine.
4. Handle configuration format changes safely.
5. Provide a way to restore defaults.

Completion criteria:

- Settings persist across application restarts.
- Profiles can be saved and loaded correctly.
- Invalid or outdated settings do not prevent the application from opening.

Phase 7 — Reliability and Compatibility Testing

Goal: Ensure that the initial release behaves predictably.

Tasks:

1. Test successful engine startup and shutdown.
2. Test invalid configuration and unavailable destinations.
3. Test port conflicts and unexpected engine termination.
4. Test app backgrounding and returning to the foreground.
5. Verify resource cleanup and repeated start/stop cycles.
6. Test supported CPU architectures and Android versions.
7. Confirm that the engine's existing behavior has not been unintentionally changed.

Completion criteria:

- Critical lifecycle and configuration tests pass.
- Known limitations are documented.
- The application handles expected failures gracefully.

Phase 8 — Initial APK Release

Goal: Produce a usable, installable version of Manjaniq.

Tasks:

1. Configure release build settings.
2. Build the APK for the intended target architecture or architectures.
3. Install and test the release APK on a real device.
4. Verify that all required native components are packaged.
5. Check permissions, application identity, and startup behavior.
6. Document installation and basic usage.

Completion criteria:

- The APK installs and launches independently of Termux.
- The RSTA feature works within the documented scope.
- Critical tests pass.
- A reproducible build procedure is documented.

Phase 9 — Future Feature Expansion

Goal: Expand Manjaniq without destabilizing its initial functionality.

Potential future features:

- Connectivity diagnostics.
- Endpoint availability tests.
- Additional network utilities.
- Improved configuration import and export.
- Extended logging and diagnostic tools.
- Scanner functionality, subject to separately defined requirements.

For each new feature:

1. Define its purpose and requirements.
2. Determine whether it needs shared Core services.
3. Design its module boundaries.
4. Implement and test it independently.
5. Verify compatibility with existing features.
6. Update the architecture and roadmap when necessary.

Completion criteria:

- The new feature has clearly defined responsibilities.
- Existing RSTA functionality remains operational.
- The feature can be maintained without unnecessary changes to unrelated modules.

4. Priority Order

The development priorities are:

1. Understand the existing Go source.
2. Build a minimal Android application.
3. Prove that RSTA can run inside the application.
4. Stabilize the engine interface and lifecycle.
5. Implement the RSTA UI.
6. Add configuration persistence.
7. Test reliability and compatibility.
8. Produce the initial APK.
9. Add future features incrementally.

The most important technical milestone is Phase 3: RSTA Integration Proof of Concept. It reduces the risk of building a complete UI around an integration method that later proves incompatible.

5. Scope of the Initial Release

The first usable release should focus on:

- A standalone Android application.
- Configuration of supported RSTA options.
- Starting and stopping the local RSTA proxy.
- Runtime status and error reporting.
- Basic logs.
- Reliable resource cleanup.

Device-wide VPN routing, automatic routing of every application's traffic, and additional scanning tools are outside the initial release scope.

6. Progress Tracking

Track each task using one of these statuses:

- "NOT_STARTED"
- "IN_PROGRESS"
- "BLOCKED"
- "COMPLETED"

A task may be marked "COMPLETED" only after its completion criteria have been verified.

When blocked, record:

- The specific problem.
- The evidence or error encountered.
- The options investigated.
- The next smallest experiment or action needed to resolve it.

7. Definition of Project Completion

The initial Manjaniq release is complete when a user can install the APK without installing Termux, configure the existing RSTA engine, start and stop its local proxy, observe its status and errors, and use the application reliably within its documented limitations.

Future features should be developed only after this baseline is stable.
