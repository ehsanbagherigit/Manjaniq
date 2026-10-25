package com.manjaniq

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import com.manjaniq.core.AppPreferences
import com.manjaniq.core.LocaleHelper
import com.manjaniq.ui.components.ManjaniqApp
import com.manjaniq.ui.theme.ManjaniqTheme

/**
 * Single launcher activity. It always composes the real application UI;
 * there is deliberately no test-mode branch, so instrumentation tests
 * exercise exactly what users see.
 */
class MainActivity : ComponentActivity() {

    // Registered in onCreate (before the activity is STARTED) as required by
    // the ActivityResult API. The request only runs on API 33+ and only on
    // the first creation (not on recreate(), e.g. after a language change).
    private var notificationPermission: ActivityResultLauncher<String>? = null

    override fun attachBaseContext(newBase: Context) {
        val wrapped = try {
            val lang = AppPreferences(newBase).currentLanguage()
            LocaleHelper.wrapContext(newBase, lang)
        } catch (e: Exception) {
            // Locale wrapping is cosmetic; never let it block startup.
            // Falls back to the system locale.
            newBase
        }
        super.attachBaseContext(wrapped)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        notificationPermission = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { /* The foreground service works either way; the notification is simply hidden if denied. */ }

        if (savedInstanceState == null &&
            Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission?.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            ManjaniqTheme {
                ManjaniqApp()
            }
        }
    }
}
