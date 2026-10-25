package com.manjaniq

import com.manjaniq.core.AetherCoreManager
import com.manjaniq.core.AetherFmt
import com.manjaniq.core.ProfileItem
import com.manjaniq.ui.server.EditorMessage
import com.manjaniq.ui.server.messageOfAetherProblem
import org.junit.Assert.assertEquals
import org.junit.Test

class AetherProblemsTest {

    private fun profile(text: String? = null) = ProfileItem.create().apply { aetherCommand = text }

    private val listenerProblems = setOf(
        AetherFmt.Problem.LISTEN_PORT_TAKEN,
        AetherFmt.Problem.LISTEN_PORT_SHARED,
        AetherFmt.Problem.LISTEN_PORT_INVALID,
        AetherFmt.Problem.LISTEN_ADDRESS_INVALID,
        AetherFmt.Problem.LISTEN_ADDRESS_UNREACHABLE,
    )

    @Test
    fun eachProblemHasItsMessage() {
        val plain = mapOf(
            AetherFmt.Problem.INVALID_MASQUE_SNI to R.string.aether_invalid_masque_sni,
            AetherFmt.Problem.INVALID_PEER to R.string.aether_invalid_endpoint,
            AetherFmt.Problem.INVALID_HOP to R.string.aether_invalid_hop,
            AetherFmt.Problem.SHARED_HOP to R.string.aether_same_hop,
            AetherFmt.Problem.INVALID_FRAGMENT to R.string.aether_invalid_fragment,
            AetherFmt.Problem.INVALID_DNS to R.string.aether_invalid_dns,
            AetherFmt.Problem.INVALID_EXIT_LOC to R.string.aether_invalid_exit_loc,
            AetherFmt.Problem.INVALID_ECH_DNS to R.string.aether_invalid_ech_dns,
            AetherFmt.Problem.INVALID_ECH_DOMAIN to R.string.aether_invalid_ech_domain,
            AetherFmt.Problem.PSIPHON_NEEDS_MASQUE to R.string.aether_psiphon_needs_masque,
            AetherFmt.Problem.NEXT_PORT_TAKEN to R.string.aether_next_port_taken,
            AetherFmt.Problem.TOR_NEEDS_MASQUE to R.string.aether_tor_needs_masque,
            AetherFmt.Problem.TOR_PSIPHON_CONFLICT to R.string.aether_tor_psiphon_conflict,
            AetherFmt.Problem.TOR_BRIDGES_MISSING to R.string.aether_tor_bridges_missing,
            AetherFmt.Problem.INVALID_COMMAND to R.string.aether_invalid_command,
        )
        plain.forEach { (problem, id) ->
            assertEquals(problem.name, EditorMessage(id), messageOfAetherProblem(problem, profile(), emptySet(), 10819))
        }
        assertEquals(AetherFmt.Problem.entries.toSet() - listenerProblems, plain.keys)
    }

    @Test
    fun aMessageAboutTheListenersOfACommandNamesThePortOrTheOption() {
        val taken = profile("aether --wg --bind 127.0.0.1:20808 --http-proxy 127.0.0.1:10808")
        assertEquals(
            EditorMessage(R.string.aether_listen_port_taken, 10808),
            messageOfAetherProblem(AetherFmt.Problem.LISTEN_PORT_TAKEN, taken, setOf(10808), 10819),
        )
        val shared = profile("aether --wg --bind 127.0.0.1:20808 --http-proxy 127.0.0.1:20808")
        assertEquals(
            EditorMessage(R.string.aether_listen_port_shared, 20808),
            messageOfAetherProblem(AetherFmt.Problem.LISTEN_PORT_SHARED, shared, emptySet(), 10819),
        )
        val noPort = profile("aether --wg --bind 127.0.0.1:70000")
        assertEquals(
            EditorMessage(R.string.aether_listen_port_invalid, 70000),
            messageOfAetherProblem(AetherFmt.Problem.LISTEN_PORT_INVALID, noPort, emptySet(), 10819),
        )
        val noAddress = profile("aether --wg --tor --tor-bind 127.0.0.1:20808 --bind localhost:20809")
        assertEquals(
            EditorMessage(R.string.aether_listen_address_invalid, "--bind"),
            messageOfAetherProblem(AetherFmt.Problem.LISTEN_ADDRESS_INVALID, noAddress, emptySet(), 10819),
        )
        val unreachable = profile("aether --wg --tor --tor-bind [::1]:20808")
        assertEquals(
            EditorMessage(R.string.aether_listen_address_unreachable, AetherCoreManager.TOR_BIND),
            messageOfAetherProblem(AetherFmt.Problem.LISTEN_ADDRESS_UNREACHABLE, unreachable, emptySet(), 10819),
        )
    }
}
