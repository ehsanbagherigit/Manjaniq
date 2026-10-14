Manjaniq

1. Project Name

Manjaniq — Modular Network Utility Application

2. Project Goals

The goal of Manjaniq is to build a standalone Android application that provides a native user interface for the existing RSTA SNI Spoof engine and serves as a modular platform for future network-related tools.

The application will preserve the core functionality of the existing Go implementation while separating the user interface, application logic, and engine integration into independent components.

Primary Goals

- Native Android Application: Provide an Android application that can be installed and used without requiring Termux.
- Preserve Existing Functionality: Reuse the existing RSTA Go implementation instead of unnecessarily rewriting its networking logic.
- User-Friendly Interface: Provide graphical controls for configuring, starting, stopping, and monitoring the RSTA engine.
- Modular Architecture: Allow new features to be added without requiring major changes to existing modules.
- Extensibility: Support future tools such as IP scanners, connectivity tests, configuration management, and other network utilities.
- Maintainability: Keep components isolated, clearly defined, and independently testable.
- Reliable Execution: Provide explicit handling of engine lifecycle, application state, errors, and logs.
- Standalone Packaging: Package the required application components into an installable Android APK.

Design Principles

1. Separation of concerns.
2. Modular and extensible design.
3. Reuse of existing, working code.
4. Clear interfaces between components.
5. Centralized configuration and state management where appropriate.
6. Minimal coupling between the user interface and networking engines.
7. Incremental development with independently testable milestones.

3. Architectural Overview

The application will use a layered, modular architecture.

The Android application will provide the user interface and coordinate application features. The RSTA engine will remain responsible for its existing networking functionality. Future tools will be implemented as separate modules and integrated through defined interfaces.

High-Level Architecture

Manjaniq
│
├── Presentation Layer
│   ├── Home Screen
│   ├── RSTA Controls
│   ├── Configuration Screens
│   ├── Logs and Status
│   └── Tools Screen
│
├── Application Layer
│   ├── Application Coordinator
│   ├── Feature Management
│   ├── Engine Lifecycle Management
│   └── State and Error Handling
│
├── Feature Modules
│   ├── RSTA Feature
│   ├── Scanner Feature (Future)
│   ├── Connectivity Tests (Future)
│   └── Additional Tools (Future)
│
├── Core Layer
│   ├── Engine Interfaces
│   ├── Configuration Models
│   ├── Status and Result Models
│   └── Logging Interfaces
│
├── Data Layer
│   ├── Settings Storage
│   ├── Profile Storage
│   └── History Storage (Optional)
│
├── Engine Integration Layer
│   ├── Go Engine Adapter
│   ├── Engine Lifecycle Bridge
│   └── Event and Log Bridge
│
└── RSTA Go Engine
├── Existing SNI Spoof Logic
├── TLS ClientHello Fragmentation
├── TCP Proxy Server
└── Existing Monitoring and Configuration Logic

4. Component Responsibilities

4.1. Presentation Layer

The Presentation Layer contains the Android user interface.

Responsibilities:

- Display application screens and navigation.
- Collect user input.
- Display engine status, logs, errors, and operation results.
- Send user actions to the application layer.
- Observe application state and update the interface.

The presentation layer must not directly implement networking logic or depend on command-line execution details.

Proposed technology: Kotlin and Jetpack Compose.

4.2. Application Layer

The Application Layer coordinates the application's behavior and connects the user interface to the underlying features.

Responsibilities:

- Coordinate feature execution.
- Manage engine start and stop requests.
- Validate user-provided settings.
- Coordinate configuration updates.
- Handle operation states and errors.
- Ensure that UI components do not directly control engine internals.

Long-running operations must be managed independently of the UI lifecycle.

4.3. Feature Modules

Each major capability will be implemented as a separate feature module.

RSTA Feature

The initial and primary feature of the application.

Responsibilities:

- Configure the existing RSTA engine.
- Start and stop the local TCP proxy.
- Select supported engine methods and fragmentation strategies.
- Display engine status and logs.
- Report configuration and runtime errors.

The feature will reuse the existing Go implementation wherever practical.

Scanner Feature — Future

A separate module for future scanning functionality.

Potential responsibilities:

- Scan user-specified IP ranges or candidate endpoints.
- Test connectivity to configured destinations.
- Evaluate results using explicitly defined criteria.
- Present scan results and allow appropriate configuration export.

The scanner's implementation language and internal architecture will be determined when its requirements are defined.

Additional Features — Future

Additional tools will be introduced as independent modules with clear interfaces and limited dependencies on other features.

4.4. Core Layer

The Core Layer contains shared contracts and models that must remain independent of specific screens and engine implementations.

Responsibilities:

- Define engine interfaces.
- Define shared configuration and status models.
- Define operation results and error representations.
- Provide common logging and event contracts.
- Establish consistent interfaces for feature integration.

The Core Layer must not depend on Android UI components or the implementation details of a specific feature.

4.5. Data Layer

The Data Layer manages persistent application data.

Responsibilities:

- Store application preferences.
- Save and load user-defined profiles.
- Store feature-specific settings.
- Optionally maintain operation history and previous results.

Sensitive data must not be stored unnecessarily, and stored configuration must be validated before use.

4.6. Engine Integration Layer

This layer connects the Android application to the Go engine.

Responsibilities:

- Expose a stable interface to the application layer.
- Translate application configuration into engine configuration.
- Start and stop engine execution.
- Forward logs, status changes, and errors.
- Hide implementation-specific details from the UI and feature modules.

The integration mechanism will be selected after evaluating the current Go source.

Preferred architectural approach: expose a well-defined Go API and integrate it through an Android-compatible native library if technically feasible.

Alternative execution mechanisms may be considered if they provide better compatibility with the existing code and Android runtime.

The integration must not require the end user to install Termux.

4.7. RSTA Go Engine

The existing Go source code remains the authoritative implementation of the RSTA networking functionality.

Its existing capabilities will be preserved unless a change is necessary for integration, reliability, or maintainability.

The engine should be adapted to expose a controlled lifecycle and configuration interface rather than requiring the Android application to depend directly on command-line flags.

The engine should remain as independent from the Android UI as reasonably possible.

5. Engine Interface and Lifecycle

The Android application will communicate with the engine through a defined interface rather than invoking internal functions directly from UI components.

The conceptual interface includes:

- "start(config)" — Start the engine using validated configuration.
- "stop()" — Request controlled shutdown.
- "getStatus()" — Retrieve the current execution state.
- "getLogs()" or an event stream — Observe relevant runtime messages.
- Configuration validation — Reject invalid settings before execution.

These names describe the intended contract; the final API will be determined after inspecting and adapting the Go implementation.

The application should represent at least the following states:

- "STOPPED"
- "STARTING"
- "RUNNING"
- "STOPPING"
- "ERROR"

Lifecycle operations must be safe against repeated start or stop requests. Errors must be reported to the application layer and presented clearly to the user.

6. Configuration and Data Models

Shared configuration models will separate user settings from engine-specific implementation details.

The RSTA configuration model may include:

- Listen address and port.
- Remote destination address and port.
- SNI hostname.
- Bypass method.
- Fragmentation strategy.
- Fragment delay.
- Optional engine settings supported by the existing implementation.

Each feature may define its own configuration model. Feature-specific settings must not be added indiscriminately to a single global configuration object.

Configuration models should be versionable so that future application updates can migrate stored profiles safely.

7. Dependency Rules

The following dependency rules will guide implementation:

1. The Presentation Layer depends on the Application Layer.
2. The Application Layer depends on Core contracts and feature interfaces.
3. Feature modules use Core contracts and their required services.
4. The Data Layer implements the application's storage requirements.
5. The Engine Integration Layer implements the interfaces used to control the Go engine.
6. The Go engine must not depend on Android UI code.
7. New features must not require modifications to unrelated features.
8. Shared functionality should be introduced into Core only when it is genuinely common to multiple components.

These rules are intended to reduce coupling and keep the project maintainable as its feature set grows.

8. Android and Build Strategy

The project will be developed as a native Android application using Kotlin.

Jetpack Compose is the proposed UI framework.

The Go engine will be integrated through an Android-compatible mechanism selected after source-level evaluation and a minimal build prototype.

The build system should produce a reproducible APK and support the required Android ABIs.

The application must include the permissions and lifecycle handling required by its actual functionality. It will not implement device-wide VPN routing unless that becomes an explicitly approved future requirement.

9. Development Roadmap

Phase 1 — Architecture and Source Analysis

- Inspect the existing Go implementation.
- Identify global state, command-line dependencies, and server lifecycle behavior.
- Define the engine interface and shared models.
- Select and validate the Android integration mechanism.

Phase 2 — Project Foundation

- Create the Android project.
- Establish the module and package structure.
- Implement shared configuration and state models.
- Set up the build and test workflow.

Phase 3 — RSTA Integration

- Adapt the Go engine for controlled invocation.
- Implement engine lifecycle management.
- Connect the Android UI to the engine.
- Display logs, status, and errors.
- Verify proxy behavior on the target device.

Phase 4 — Initial Application Release

- Complete configuration screens.
- Add profile persistence.
- Improve error handling and usability.
- Build and test the APK.

Phase 5 — Feature Expansion

- Define requirements for each new tool.
- Implement new features as separate modules.
- Reuse shared contracts and services where appropriate.
- Add tests without destabilizing existing functionality.

10. Architectural Constraints

- The existing RSTA networking implementation must be preserved unless a justified change is required.
- The initial release focuses on controlling the local RSTA proxy, not routing all device traffic.
- The application must operate without requiring Termux to be installed.
- Future capabilities must be integrated through defined module boundaries.
- The selected Go integration mechanism must be validated against the real source code before being treated as final.
- Architectural decisions must be documented when they materially affect future development.

11. Definition of Success

The architecture will be considered successfully implemented when:

- The application can be built as an installable Android APK.
- The RSTA engine can be configured and controlled through the Android UI.
- The engine can be started and stopped reliably.
- Runtime status, logs, and errors can be observed from the application.
- Existing RSTA behavior remains intact.
- A new feature can be added without requiring a major redesign of the existing architecture.
