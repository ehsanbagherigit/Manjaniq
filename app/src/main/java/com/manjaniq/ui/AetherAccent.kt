package com.manjaniq.ui

import androidx.compose.ui.graphics.Color
import com.manjaniq.ui.theme.ManjaniqPurple
import com.manjaniq.ui.theme.ManjaniqPurpleDark
import com.manjaniq.ui.theme.ManjaniqPurpleDeep
import com.manjaniq.ui.theme.ManjaniqPurpleNeon
import com.manjaniq.ui.theme.ManjaniqPurpleSoft

/**
 * The Aether accent palette.
 *
 * Purple is the colour this app already uses for Aether (RSTA uses gold): the
 * start control turns purple while a tunnel runs, and the route cards were purple
 * before the panel was redesigned. These values therefore alias the existing
 * purple tokens instead of inventing a hue, and `ui/theme/Color.kt` is not
 * touched — the whole Aether palette lives in this Aether-only file.
 *
 * Every value was checked against the surfaces it sits on
 * (`ManjaniqBackground` 0xFF080808, `ManjaniqSurface` 0xFF15141A):
 *
 * | token | value | contrast on 0xFF080808 | use |
 * |---|---|---|---|
 * | [AetherAccent] | 0xFFA66BFF | 5.8:1 | selected borders, dots, chevrons, short labels |
 * | [AetherAccentSoft] | 0xFFC5A4FF | 9.7:1 | small explanatory text, selected titles |
 * | [AetherAccentStrong] | 0xFF8250D9 | 4.0:1 | fills and hairlines (UI components need 3:1) |
 * | [AetherAccentDeep] | 0xFF251735 | — | tinted surface behind a selection |
 * | [AetherAccentDark] | 0xFF39204F | — | unselected hairline on a tinted surface |
 *
 * Disabled states keep 70 % alpha on top of these plus the neutral
 * `onSurfaceVariant`, which stays above 4.5:1. Loading, success and error keep the
 * app-wide `StatusStarting` / `StatusRunning` / `StatusError` semantics: those are
 * a state language shared with the rest of the app, and turning "starting" purple
 * would make it indistinguishable from "selected". The operational log keeps its
 * cyan-on-black text, which is specified separately.
 */

/** Selected borders, indicator dots, chevrons and short accent labels. */
internal val AetherAccent: Color = ManjaniqPurpleNeon

/** Small explanatory text and selected titles on a near-black surface. */
internal val AetherAccentSoft: Color = ManjaniqPurpleSoft

/** Fills and hairlines: 4.0:1 on the app background, above the 3:1 UI minimum. */
internal val AetherAccentStrong: Color = ManjaniqPurple

/** Tinted surface behind a selection or a read-only value block. */
internal val AetherAccentDeep: Color = ManjaniqPurpleDeep

/** Hairline border on a tinted surface. */
internal val AetherAccentDark: Color = ManjaniqPurpleDark
