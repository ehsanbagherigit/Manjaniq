// Package com.manjaniq provides the main Android application entry point.
package com.manjaniq

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.manjaniq.ui.theme.ManjaniqTheme
import com.manjaniq.ui.components.ManjaniqApp

/**
 * Single-activity host for the Compose UI.
 *
 * Startup is deliberately minimal: no permission requests, no
 * background work and no native calls happen here. The
 * POST_NOTIFICATIONS permission is requested by the UI only when
 * the user starts the engine (the only moment a notification can
 * appear) — requesting it during `onCreate` put the system
 * permission-controller activity on top of MainActivity right
 * after launch, which made `am start` return "ok" while the
 * expected Home screen was not the resumed activity and made
 * automated launch checks unreliable.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ManjaniqTheme {
                ManjaniqApp()
            }
        }
    }
}
