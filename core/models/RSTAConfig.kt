package com.manjaniq.core.models

import kotlin.jvm.JvmField

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
    val fakeSNIMethod: String
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
            fakeSNIMethod = "prefix_fake"
        )
    }
}