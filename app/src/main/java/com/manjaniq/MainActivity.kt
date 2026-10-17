// Package com.manjaniq provides the main Android application entry point.
package com.manjaniq

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.manjaniq.ui.theme.ManjaniqTheme
import com.manjaniq.ui.components.ManjaniqApp

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
