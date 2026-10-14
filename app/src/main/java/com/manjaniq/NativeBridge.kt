package com.manjaniq

import android.util.Log

object NativeBridge {
    init {
        try {
            System.loadLibrary("manjaniq_native")
        } catch (e: Throwable) {
            Log.e("NativeBridge", "Failed to load manjaniq_native: ${e.message}")
        }
    }

    external fun GoHelloWorld(): String?

    fun testBridge(): String {
        return try {
            GoHelloWorld() ?: "Native call returned null"
        } catch (e: Throwable) {
            "Native call failed: ${e.message}"
        }
    }
}
