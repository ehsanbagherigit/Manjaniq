package com.manjaniq.core

/**
 * PattNG port: the exit-node of an Aether core, the outbound what the core dials out through leaves
 * the tunnel by. As a rule it is a plain exit with a final mask and a dial mode; without a chain it
 * is plain, and a profile or a hand-written command can name a profile of its own as the exit-node
 * by [node].
 *
 * Adapted to Manjaniq: this project loads `libaether.so` through the JNI bridge (`AetherBridge`)
 * rather than spawning a separate core process, so [AetherExit] describes the exit the argument
 * list handed to `AetherBridge.coreStart` dials out through.
 */
data class AetherExit(
    /**
     * A final-mask JSON object the exit applies, null when none is set. Blank or a JSON object is
     * accepted; anything else is refused by the editor before it reaches the command line.
     */
    val finalMask: String? = null,
    /** The dial mode the exit uses, null when the core's own default applies. */
    val dialMode: String? = null,
    /**
     * A digest of the profiles a proxy chain dials out through, telling the exit-node of a hop apart
     * from another; null for a plain exit.
     */
    val hops: String? = null,
    /** The name of a profile this exit dials out through, null for a plain exit. */
    val node: String? = null,
    /**
     * A digest of the profile [node] names, so the core can tell whether the profile it dials out
     * through changed under it; null when there is no [node].
     */
    val nodeContent: String? = null,
) {

    /** An exit-node with nothing set, as the core of a plain configuration dials out through. */
    companion object {
        /** An exit-node with nothing set. */
        val PLAIN = AetherExit()

        /**
         * The exit-node of [node]: a profile named by [node], or else a plain one carrying
         * [finalMask] and [dialMode].
         */
        fun of(node: String?, finalMask: String? = null, dialMode: String? = null): AetherExit =
            node?.takeIf { it.isNotBlank() }?.let { AetherExit(finalMask = finalMask, dialMode = dialMode, node = it.trim()) }
                ?: AetherExit(finalMask, dialMode)
    }
}
