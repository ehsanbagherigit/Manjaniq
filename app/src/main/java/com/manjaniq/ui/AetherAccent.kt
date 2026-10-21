package com.manjaniq.ui

import androidx.compose.ui.graphics.Color
import com.manjaniq.ui.theme.ManjaniqPurpleDeep
import com.manjaniq.ui.theme.ManjaniqPurpleNeon
import com.manjaniq.ui.theme.ManjaniqPurpleSoft

/**
 * The Aether accent palette.
 *
 * Purple is the colour this app already uses for Aether (RSTA uses gold): the
 * stop control is purple per the theme spec, and the route cards were purple
 * before the panel was redesigned. These three values therefore alias existing
 * purple tokens instead of inventing a hue, and `ui/theme/Color.kt` is not
 * touched — the whole Aether palette lives in this Aether-only file.
 *
 * Contrast was measured against the surfaces these sit on
 * (`ManjaniqBackground` 0xFF080808, `ManjaniqSurface` 0xFF15141A):
 *
 * | token | value | on 0xFF080808 | on 0xFF15141A | use |
 * |---|---|---|---|---|
 * | [AetherAccent] | 0xFFA66BFF | 5.9:1 | 5.4:1 | selected borders, dots, chevrons, short labels |
 * | [AetherAccentSoft] | 0xFFC5A4FF | 9.7:1 | 8.8:1 | small explanatory text, selected titles |
 * | [AetherAccentDeep] | 0xFF251735 | fill | fill | tinted surface behind a selection |
 *
 * Both text colours clear WCAG AA (4.5:1) for normal text and [AetherAccentSoft]
 * clears AAA. Text on an [AetherAccentDeep] fill measures 8.1:1 with
 * [AetherAccentSoft] and 4.9:1 with [AetherAccent], so the tinted read-only
 * blocks stay legible.
 *
 * What is deliberately *not* purple:
 *
 *  - disabled and not-forwardable content keeps `onSurfaceVariant` plus reduced
 *    alpha, because a purple disabled control reads as selected;
 *  - `StatusStarting` / `StatusRunning` / `StatusError` keep their meaning
 *    wherever they express *state* rather than selection (the status dot, the
 *    STARTING and STOPPING log indicators, the WARN log level). Turning
 *    "starting" purple would make it indistinguishable from "selected";
 *  - the operational log keeps its cyan-on-black text (0xFF3DD4E6, 11.2:1), which
 *    is specified separately for that panel.
 */

/** Selected borders, indicator dots, chevrons and short accent labels. */
internal val AetherAccent: Color = ManjaniqPurpleNeon

/** Small explanatory text and selected titles on a near-black surface. */
internal val AetherAccentSoft: Color = ManjaniqPurpleSoft

/** Tinted surface behind a selection or a read-only value block. */
internal val AetherAccentDeep: Color = ManjaniqPurpleDeep
