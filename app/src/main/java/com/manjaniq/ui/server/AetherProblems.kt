package com.manjaniq.ui.server

import androidx.annotation.StringRes
import com.manjaniq.R
import com.manjaniq.core.AetherFmt
import com.manjaniq.core.ProfileItem

internal data class EditorMessage(@StringRes val id: Int, val argument: Any? = null)

internal fun messageOfAetherProblem(
    problem: AetherFmt.Problem,
    config: ProfileItem,
    takenPorts: Set<Int>,
    listenPort: Int,
): EditorMessage =
    when (problem) {
        AetherFmt.Problem.INVALID_MASQUE_SNI -> EditorMessage(R.string.aether_invalid_masque_sni)
        AetherFmt.Problem.INVALID_PEER -> EditorMessage(R.string.aether_invalid_endpoint)
        AetherFmt.Problem.INVALID_HOP -> EditorMessage(R.string.aether_invalid_hop)
        AetherFmt.Problem.SHARED_HOP -> EditorMessage(R.string.aether_same_hop)
        AetherFmt.Problem.INVALID_FRAGMENT -> EditorMessage(R.string.aether_invalid_fragment)
        AetherFmt.Problem.INVALID_DNS -> EditorMessage(R.string.aether_invalid_dns)
        AetherFmt.Problem.INVALID_EXIT_LOC -> EditorMessage(R.string.aether_invalid_exit_loc)
        AetherFmt.Problem.INVALID_ECH_DNS -> EditorMessage(R.string.aether_invalid_ech_dns)
        AetherFmt.Problem.INVALID_ECH_DOMAIN -> EditorMessage(R.string.aether_invalid_ech_domain)
        AetherFmt.Problem.INVALID_UPSTREAM -> EditorMessage(R.string.aether_invalid_upstream)
        AetherFmt.Problem.LISTEN_PORT_TAKEN ->
            EditorMessage(R.string.aether_listen_port_taken, AetherFmt.takenListenPort(config, takenPorts, listenPort) ?: listenPort)
        AetherFmt.Problem.LISTEN_PORT_SHARED ->
            EditorMessage(R.string.aether_listen_port_shared, AetherFmt.sharedListenPort(config, listenPort) ?: listenPort)
        AetherFmt.Problem.LISTEN_PORT_INVALID ->
            EditorMessage(R.string.aether_listen_port_invalid, AetherFmt.invalidListenPort(config, listenPort) ?: listenPort)
        AetherFmt.Problem.LISTEN_ADDRESS_INVALID ->
            EditorMessage(R.string.aether_listen_address_invalid, AetherFmt.invalidListener(config, listenPort).orEmpty())
        AetherFmt.Problem.LISTEN_ADDRESS_UNREACHABLE ->
            EditorMessage(R.string.aether_listen_address_unreachable, AetherFmt.unreachableListener(config, listenPort).orEmpty())
        AetherFmt.Problem.PSIPHON_NEEDS_MASQUE -> EditorMessage(R.string.aether_psiphon_needs_masque)
        AetherFmt.Problem.NEXT_PORT_TAKEN -> EditorMessage(R.string.aether_next_port_taken)
        AetherFmt.Problem.TOR_NEEDS_MASQUE -> EditorMessage(R.string.aether_tor_needs_masque)
        AetherFmt.Problem.TOR_PSIPHON_CONFLICT -> EditorMessage(R.string.aether_tor_psiphon_conflict)
        AetherFmt.Problem.TOR_BRIDGES_MISSING -> EditorMessage(R.string.aether_tor_bridges_missing)
        AetherFmt.Problem.INVALID_COMMAND -> EditorMessage(R.string.aether_invalid_command)
    }
