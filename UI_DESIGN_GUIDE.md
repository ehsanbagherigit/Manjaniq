Manjaniq — UI Design Guide

1. Project Overview

Project name: Manjaniq

Platform: Android

Primary language: Kotlin

UI framework: Jetpack Compose

Design direction: Minimal, premium, dark, technical, modern.

Manjaniq is a modular Android application for network utilities. Its initial purpose is controlling and interacting with the existing RSTA SNI Spoof Go engine through a native Android interface. Future features may include independent network tools, scanners, configuration management, and diagnostic utilities.

The UI must remain independent of the underlying engine implementation.

2. Visual Identity

Create a polished Android application inspired by the supplied dark dashboard reference and mobile UI mockup.

The visual identity must follow these principles:

- Predominantly black and near-black surfaces.
- White and soft-gray typography.
- Restrained use of deep purple.
- Selective neon-purple accents.
- Soft purple gradients and atmospheric lighting in a few carefully selected areas.
- Thin, subtle borders.
- Rounded cards and controls.
- Excellent spacing, alignment, and visual hierarchy.
- A professional, understated appearance rather than an exaggerated cyberpunk interface.

IMPORTANT: Purple must be an accent color, not the dominant background color.

Approximately 85–90% of the interface should visually remain black, near-black, neutral gray, or white. Purple should appear selectively to highlight important elements.

Do not place a purple glow around every card, icon, button, or section.

3. Color System

Use these colors as initial design tokens:

Background:

- Primary background: #080808
- Secondary background: #0D0D10
- Elevated surface: #121116
- Card surface: #15141A

Borders:

- Default border: #24232B
- Highlighted border: #393044

Typography:

- Primary text: #F5F3F8
- Secondary text: #AAA6B4
- Muted text: #777382

Purple:

- Deep purple: #251735
- Dark purple: #39204F
- Accent purple: #8250D9
- Neon purple: #A66BFF
- Soft lavender: #C5A4FF

Status colors:

- Success: restrained teal or green.
- Warning: warm amber.
- Error: muted red.

Use gradients sparingly. When appropriate, blend near-black into deep purple and finish with a small area of soft lavender light.

The exact shades may be refined during implementation, but the overall balance must remain faithful to this specification.

4. Lighting and Gradients

Use soft atmospheric purple lighting rather than thick neon outlines.

Recommended applications:

- A subtle purple gradient behind the main dashboard heading.
- A soft glow behind the primary connection control.
- A small purple highlight on the selected navigation item.
- Occasional gradient fills on primary action buttons.
- Very subtle illumination around an important active state.

Avoid:

- Full-screen purple backgrounds.
- Excessive bloom and strong outer glows.
- Bright purple outlines around every component.
- Large decorative gradients that reduce readability.
- Constant pulsing or distracting animations.

The interface should look premium and calm even when all animations are disabled.

5. Typography

Use a clean, modern Android-compatible typeface.

Typography must have a clear hierarchy:

- Screen titles: medium or semibold.
- Section headings: medium weight.
- Body text: regular weight.
- Supporting text: smaller and muted.
- Technical values and logs: readable, optionally using a monospace typeface.

Keep text concise. Avoid oversized headings that consume valuable mobile screen space.

Support Persian and English text where applicable, including correct RTL layout behavior for Persian.

6. Component Design

Cards

- Near-black surfaces.
- Subtle gray borders.
- Rounded corners, generally 14–20 dp.
- Comfortable internal spacing.
- No unnecessary shadows.
- Purple accents only when they communicate importance or state.

Buttons

Primary buttons:

- Purple gradient or restrained purple fill.
- White text.
- Rounded corners.
- Clear enabled, disabled, loading, and pressed states.

Secondary buttons:

- Dark surface.
- Thin neutral border.
- White or muted text.

Destructive actions:

- Clearly distinguishable without turning the entire screen red.

Icons

Use consistent, simple line icons.

Most icons should be white, gray, or muted lavender. Reserve brighter purple for selected navigation items and important actions.

Do not use the Manjaniq catapult logo, catapult illustrations, mascot graphics, or decorative weapon imagery anywhere in the application UI.

Inputs and Selectors

- Dark backgrounds.
- Subtle borders.
- Clear focus indicators.
- Readable placeholders.
- Consistent heights and spacing.
- Purple focus accents used sparingly.

Status Indicators

Use both color and text to communicate connection state.

Examples:

- Running
- Stopped
- Starting
- Stopping
- Error

Never communicate an important state through color alone.

7. Main Application Screens

Design the following screens as appropriate for the actual project stage.

A. Home / Dashboard

The home screen is the primary entry point.

Include:

- A compact top app bar with the Manjaniq text title.
- A concise description of the application's purpose.
- A prominent connection status card.
- A primary Start / Stop control.
- Relevant runtime information when available.
- Quick access to available tools.
- A small recent activity section.
- A restrained visual highlight behind the primary action.

The screen must feel spacious and focused, not crowded with statistics or decorative cards.

Do not display fabricated connection speeds, server details, uptime, or activity statistics. Show real values or clear empty states.

B. Connection / Engine Control

Provide a dedicated interface for controlling the existing engine.

Include:

- Current lifecycle state.
- Start and Stop actions.
- Progress or loading state during transitions.
- Relevant configuration or profile information.
- Clear error messages.
- Access to diagnostic logs.

The UI must represent the engine's real state, not a simulated successful connection.

The interface must not claim that device-wide VPN routing is active unless that capability actually exists.

C. Profiles and Configurations

Include:

- A list of available configurations.
- A clear selected state.
- Import and export actions where supported.
- Create, edit, delete, and duplicate actions only when implemented.
- Empty, loading, and error states.

Keep each profile item compact and readable.

D. Network Tools

Provide a modular entry point for available network utilities.

Potential future modules include:

- Network scanner.
- Diagnostic tools.
- Connection testing.
- Configuration inspection.
- Engine diagnostics.

Only display tools that exist or are explicitly marked as unavailable or planned.

Do not create fake functional screens for future features.

E. Logs and Diagnostics

Include:

- Timestamped events.
- Clear severity or event-type indicators.
- Readable technical output.
- Filtering when supported.
- Copy and clear actions when appropriate.
- Empty and error states.

Use a restrained monospace style for technical output. Logs should remain readable and should not glow excessively.

F. Settings

Organize settings into clear groups:

- Appearance.
- Connection behavior.
- Notifications.
- Diagnostics.
- About and version information.

Implement only settings that are supported by the application.

Avoid unnecessary customization options.

8. Navigation

Use a mobile-first navigation system.

A bottom navigation bar may be used for the primary destinations, such as:

- Home
- Tools
- Profiles
- Logs
- Settings

Choose the appropriate number of destinations based on actual project functionality.

Do not force every destination into the bottom bar if that makes it crowded. Secondary destinations may use in-screen navigation.

Navigation should have:

- A dark background.
- Minimal separators.
- Clear selected states.
- Subtle purple highlighting.
- Consistent icons and labels.

Respect Android system bars, gesture navigation, and screen insets.

9. Motion and Interaction

Animations should be subtle and purposeful.

Recommended:

- Short transitions between screens.
- Smooth button feedback.
- Gentle state transitions.
- Small progress indicators.
- A subtle connection-state transition.

Avoid:

- Continuous decorative animation.
- Aggressive pulsing glows.
- Excessive parallax.
- Long transitions.
- Animations that delay user actions.

Provide appropriate touch targets and accessible content descriptions.

10. Responsive Layout and Accessibility

The application must work on real Android devices, including compact phone screens.

Requirements:

- Use responsive layouts.
- Avoid fixed desktop-style layouts.
- Handle different screen sizes and font scaling.
- Respect Android edge-to-edge and system insets.
- Maintain sufficient text contrast.
- Support Android dark mode appropriately.
- Support Persian RTL and English LTR.
- Prevent text clipping and horizontal overflow.
- Provide clear accessibility labels.
- Ensure buttons remain usable with larger system font sizes.

11. Architecture and Code Quality

Use Kotlin and Jetpack Compose if consistent with the existing project.

Before making changes:

1. Inspect the current repository and project structure.
2. Identify the existing UI framework and build configuration.
3. Identify the existing engine integration and public interfaces.
4. Preserve existing working functionality.
5. Reuse existing architectural conventions when appropriate.

Keep presentation code separate from business logic and engine implementation.

Recommended separation:

- Presentation: screens, reusable components, theme, navigation.
- ViewModels: UI state and user actions.
- Domain: use cases and application rules.
- Data: configuration persistence and repositories.
- Engine integration: isolated interfaces and adapters.

Use immutable UI state and lifecycle-aware state collection.

Do not place engine logic inside Composable functions.

Do not rewrite or replace the existing RSTA engine just to simplify UI development.

Do not assume that engine functionality, permissions, or platform capabilities exist without inspecting the code.

12. Implementation Rules

- Build real, reusable Compose components.
- Use centralized color, typography, shape, and spacing tokens.
- Avoid duplicated styling across screens.
- Keep components modular and maintainable.
- Preserve working features and configuration formats.
- Handle loading, empty, success, and error states.
- Do not introduce unnecessary dependencies.
- Do not hardcode fake runtime data into production UI.
- Do not leave misleading controls that appear functional but do nothing.
- Keep future modules easy to add without redesigning the entire application.

13. Definition of Done

The UI implementation is complete only when:

- The actual Android application builds successfully.
- The screens run on an Android device or emulator.
- Navigation works.
- Theme and reusable components are consistent.
- Connection state is driven by actual application state.
- Existing engine integration remains intact.
- Loading and error states are handled.
- No logo or catapult illustration appears in the UI.
- Purple remains a restrained accent against predominantly black and neutral surfaces.
- The result looks like a polished native Android application, not a static mockup.

Prioritize visual quality, maintainability, correctness, and a faithful implementation of the supplied reference.
