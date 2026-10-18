// Package com.manjaniq provides the main Android application entry point.
package com.manjaniq

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.manjaniq.ui.theme.ManjaniqTheme
import com.manjaniq.ui.components.ManjaniqApp

class MainActivity : ComponentActivity() {

    // Notifications show the foreground-service status; the engine
    // still runs without the permission, just less visibly.
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            ManjaniqTheme {
                ManjaniqApp()
            }
        }
    }
}
