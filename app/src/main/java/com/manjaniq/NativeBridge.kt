package com.manjaniq

import android.util.Log

object NativeBridge {

    /** Captured [System.loadLibrary] failure, or null when loading succeeded. */
    private val loadError: Throwable?

    init {
        val error = try {
            System.loadLibrary("manjaniq_native")
            null
        } catch (t: Throwable) {
            t
        }
        loadError = error
        if (error != null) {
            try {
                Log.e("NativeBridge", "Failed to load manjaniq_native: ${error.message}")
            } catch (t: Throwable) {
                // Logging must never mask the library-load failure.
            }
        }
    }

    external fun GoHelloWorld(): String?

    fun testBridge(): String {
        return try {
            GoHelloWorld() ?: "Native call returned null"
        } catch (t: Throwable) {
            val loadFailure = loadError?.let { "; library load failed: $it" } ?: ""
            "Native call failed: ${t.message}$loadFailure"
        }
    }
}
