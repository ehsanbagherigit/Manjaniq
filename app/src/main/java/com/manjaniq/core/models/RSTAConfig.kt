// Package com.manjaniq.core.models provides data models for the RSTA engine.
package com.manjaniq.core.models

// EngineState represents the current state of the RSTA engine.
enum class EngineState {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    ERROR
}

// RSTAConfig represents the configuration for the RSTA engine.
data class RSTAConfig(
    val listenHost: String,
    val listenPort: Int,
    val connectIp: String,
    val connectPort: Int,
    val sniHostname: String,
    val bypassMethod: String,
    val fragmentStrategy: String,
    val fragmentDelay: Double,
    val useTtlTrick: Boolean,
    val monitorEnabled: Boolean,
    val quietMode: Boolean,
    val fakeSNIMethod: String,
    val fakeSNIFragmentReal: Boolean
) {
    companion object {
        @JvmField
        val DEFAULT = RSTAConfig(
            listenHost = "0.0.0.0",
            listenPort = 40443,
            connectIp = "104.18.38.202",
            connectPort = 443,
            sniHostname = "cdnjs.cloudflare.com",
            bypassMethod = "fragment",
            fragmentStrategy = "sni_split",
            fragmentDelay = 0.1,
            useTtlTrick = false,
            monitorEnabled = true,
            quietMode = false,
            fakeSNIMethod = "prefix_fake",
            fakeSNIFragmentReal = true
        )
    }
}

// OperationResult represents the result of an operation with success/failure semantics.
sealed class OperationResult<T> {
    data class Success<T>(val data: T) : OperationResult<T>()
    data class Failure<T>(val errorMessage: String, val errorCode: Int? = null) : OperationResult<T>()
}
