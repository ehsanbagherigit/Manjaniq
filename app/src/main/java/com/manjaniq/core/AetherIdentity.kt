package com.manjaniq.core

/**
 * PattNG port: the identity a registration writes into a key file, exactly as the core reads it:
 * a device id and the IPv4 / IPv6 addresses the core bound to it.
 *
 * `deviceId` is mandatory; an address field that is absent reads as empty rather than as absent,
 * so a key that holds only a device id is still a key.
 */
data class AetherIdentity(
    val deviceId: String,
    val ipv4: String = "",
    val ipv6: String = "",
) {

    /** True when this identity holds at least a device id, i.e. reads as a key at all. */
    fun isComplete(): Boolean = deviceId.isNotBlank()
}

/** One key file of the identity folder, with the identity it holds, or null when it does not read as one. */
data class AetherKey(
    /** The file name, one of [com.manjaniq.core.AetherIdentityManager.KEY_FILES]. */
    val file: String,
    /** The identity the file holds, or null when it is missing or unreadable as a key. */
    val identity: AetherIdentity?,
) {
    /** True when this key file reads as a complete identity. */
    fun isComplete(): Boolean = identity?.isComplete() == true
}
