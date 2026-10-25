package com.manjaniq.core

import com.manjaniq.R

data class AetherExit(
    val finalMask: String = "",
    val dialMode: String = "",
    val node: String = "",
) {
    init {
        require(!finalMask.contains('\u0000') && !dialMode.contains('\u0000') && !node.contains('\u0000'))
    }

    val key: String = "$finalMask\t$dialMode\t$node"

    companion object {
        val PLAIN = AetherExit()

        fun of(profile: ProfileItem): AetherExit = AetherExit(
            finalMask = profile.aetherFinalMask?.trim().orEmpty(),
            dialMode = profile.aetherDialMode?.trim().orEmpty(),
            node = profile.aetherExitNode?.trim().orEmpty(),
        )

        fun takesFinalMask(finalMask: String?): Boolean {
            val trimmed = finalMask?.trim().orEmpty()
            if (trimmed.isEmpty()) return true
            return try {
                org.json.JSONObject(trimmed)
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}

data class AetherExitNode(val name: String, val subscriptionId: Int = 0)

sealed interface ExitNodeOutbound {
    val message: Int

    data object NotFound : ExitNodeOutbound {
        override val message: Int = R.string.toast_profile_name_not_found
    }

    data object SameName : ExitNodeOutbound {
        override val message: Int = R.string.toast_profile_name_duplicate
    }

    data object NoOutbound : ExitNodeOutbound {
        override val message: Int = R.string.aether_exit_node_unusable
    }

    data object EchUnusable : ExitNodeOutbound {
        override val message: Int = R.string.aether_exit_node_ech_unusable
    }

    data class Built(val tag: String = "proxy") : ExitNodeOutbound {
        override val message: Int = 0
    }
}
