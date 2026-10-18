package com.manjaniq

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import com.manjaniq.core.AppPreferences
import com.manjaniq.core.LocaleHelper
import com.manjaniq.ui.components.ManjaniqApp
import com.manjaniq.ui.theme.ManjaniqTheme

class MainActivity : ComponentActivity() {

    private var notificationPermission: androidx.activity.result.ActivityResultLauncher<String>? = null

    override fun attachBaseContext(newBase: Context) {
        val wrapped = try {
            val prefs = AppPreferences(newBase)
            val lang = prefs.currentLanguage()
            LocaleHelper.wrapContext(newBase, lang)
        } catch (e: Exception) {
            // Never crash during attachBaseContext — fallback to system locale
            newBase
        }
        super.attachBaseContext(wrapped)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Register permission launcher safely (field initializer can break ActivityScenario in tests)
        try {
            notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermission?.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } catch (_: Exception) {
            // In instrumentation tests the permission request may not be needed/available
        }
        setContent {
            ManjaniqTheme {
                ManjaniqApp()
            }
        }
    }
}
