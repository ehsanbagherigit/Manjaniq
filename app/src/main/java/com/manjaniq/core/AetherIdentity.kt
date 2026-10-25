package com.manjaniq.core

/**
 * The identity a registration writes into a key file, as the core reads it:
 * a device id and the IPv4 / IPv6 addresses bound to it.
 */
data class AetherIdentity(
    val deviceId: String,
    val ipv4: String = "",
    val ipv6: String = "",
) {
    fun isComplete(): Boolean = deviceId.isNotBlank()
}

data class AetherIdentityStatus(
    val protocol: AetherProtocol,
    val primary: AetherIdentity?,
    val secondary: AetherIdentity? = null,
)

/** One key file of the identity folder, with the identity it holds, or null when it does not read as one. */
data class AetherKey(
    val file: String,
    val identity: AetherIdentity?,
) {
    fun isComplete(): Boolean = identity?.isComplete() == true
}
