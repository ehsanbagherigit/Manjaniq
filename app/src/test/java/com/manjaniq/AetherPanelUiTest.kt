package com.manjaniq

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.manjaniq.core.AetherRepository
import com.manjaniq.ui.AetherTestTags
import com.manjaniq.ui.components.MAX_RENDERED_LINES
import com.manjaniq.ui.TestTags
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Host-JVM (Robolectric) UI tests for the redesigned Aether panel.
 *
 * They drive the real `MainActivity`, so they cover the Aether region the way a
 * user reaches it: pick the Aether core, then walk the progressive flow. The
 * native library is absent on the host, which is exactly the state these tests
 * assert against — no test here needs a tunnel and none simulates one. Log
 * entries are written through the real `AetherRepository` logging boundary, the
 * same one `AetherService` writes to.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AetherPanelUiTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @After
    fun cleanUp() {
        AetherRepository.clearLogs()
        AetherRepository.clearError()
    }

    /** Selects the Aether core and waits for the panel to be on screen. */
    private fun openAether() {
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag(TestTags.HOME_SCREEN)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(TestTags.CORE_CARD_AETHER).performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag(TestTags.AETHER_TOGGLE)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag(AetherTestTags.PANEL)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun nodeCount(tag: String) =
        compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().size

    private fun textCount(text: String) =
        compose.onAllNodes(hasText(text)).fetchSemanticsNodes().size

    private fun waitForNode(tag: String, present: Boolean, timeoutMs: Long = 10_000) {
        compose.waitUntil(timeoutMs) { (nodeCount(tag) > 0) == present }
    }

    private fun waitForText(text: String, present: Boolean, timeoutMs: Long = 10_000) {
        compose.waitUntil(timeoutMs) { (textCount(text) > 0) == present }
    }

    private fun toggle(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo().performClick()
    }

    /**
     * Replaces the contents of a text field.
     *
     * Each action is called on the same interaction instead of chaining the
     * return values: `performTextClearance` does not hand back the interaction
     * in this Compose version, so a chain does not compile.
     */
    private fun typeInto(tag: String, text: String) {
        val field = compose.onNodeWithTag(tag)
        field.performScrollTo()
        field.performTextClearance()
        field.performTextInput(text)
    }

    @Test
    fun panelKeepsTheStartControlReachableAndShowsEveryAetherArea() {
        openAether()
        // The start control sits above the settings, so it is on screen without
        // scrolling past a wall of configuration.
        compose.onNodeWithTag(TestTags.AETHER_TOGGLE).assertExists().assertIsDisplayed()
        compose.onNodeWithTag(AetherTestTags.NOTICE).assertExists()
        compose.onNodeWithTag(AetherTestTags.ROUTE_LIST).assertExists()
        compose.onNodeWithTag(AetherTestTags.ROUTE_TITLE).assertExists()
        compose.onNodeWithTag(TestTags.AETHER_STATUS).assertExists()
        compose.onNodeWithTag(AetherTestTags.LOG_PANEL).assertExists()
        // The selected route owns its settings, and they start open.
        compose.onNodeWithTag(AetherTestTags.routeSettings("warp_direct")).assertExists()
        // Routes that need an external component stay listed, never hidden.
        listOf("tor_direct", "aether_psiphon", "aether_tor", "tor_psiphon", "tor_aether").forEach {
            compose.onNodeWithTag(AetherTestTags.routeCard(it)).assertExists()
        }
    }

    @Test
    fun noticeIsCompactAndStillRevealsTheVpnLimitation() {
        openAether()
        // Collapsed: the long body and the limitation paragraph are not composed,
        // so the panel does not open with a wall of text above the start button.
        compose.onAllNodes(hasText(LIMITATION_TITLE)).assertCountEquals(0)
        // The one-line summary still names the limitation.
        waitForText("no device-wide VPN", present = true)

        toggle(AetherTestTags.NOTICE_TOGGLE)
        waitForText(LIMITATION_TITLE, present = true)

        toggle(AetherTestTags.NOTICE_TOGGLE)
        waitForText(LIMITATION_TITLE, present = false)
    }

    @Test
    fun logPanelShowsRealEventsAndCanBeCollapsed() {
        openAether()
        AetherRepository.clearLogs()
        waitForNode(AetherTestTags.LOG_EMPTY, present = true)

        AetherRepository.log(
            "INFO",
            "Opening identity at /data/user/0/com.manjaniq/files/aether_identity transport=masque"
        )
        val scanEntry = "Scan chose endpoint 203.0.113.7:2408"
        AetherRepository.log("OK", scanEntry)
        waitForText("203.0.113.7:2408", present = true)
        compose.onAllNodes(hasTestTag(AetherTestTags.LOG_EMPTY)).assertCountEquals(0)

        val scanSeq = AetherRepository.logs.value.last().seq
        compose.onAllNodes(hasTestTag(AetherTestTags.logLine(scanSeq))).assertCountEquals(1)
        // Timestamps are part of every rendered entry.
        compose.onNodeWithTag(AetherTestTags.logLine(scanSeq)).assertExists()

        // Collapsing hides the body but keeps the panel header reachable.
        toggle(AetherTestTags.LOG_TOGGLE)
        waitForNode(AetherTestTags.LOG_BODY, present = false)
        compose.onNodeWithTag(AetherTestTags.LOG_PANEL).assertExists()

        toggle(AetherTestTags.LOG_TOGGLE)
        waitForNode(AetherTestTags.LOG_BODY, present = true)
        waitForText("203.0.113.7:2408", present = true)
    }

    @Test
    fun longOutputStaysScrollableAndKeepsTheNewestEntryReachable() {
        openAether()
        AetherRepository.clearLogs()
        val total = 250
        repeat(total) { i -> AetherRepository.log("INFO", "poll ${i + 1}: state=running") }

        val entries = AetherRepository.logs.value
        assertEquals("the repository ring must hold everything", total, entries.size)
        waitForText("poll $total: state=running", present = true)

        // The newest entry is reachable...
        compose.onNodeWithTag(AetherTestTags.logLine(entries.last().seq)).assertExists()
        // ...and the panel composes a bounded window instead of the whole ring,
        // so a long run cannot grow without limit or push the controls off
        // screen. The window is the newest MAX_RENDERED_LINES entries.
        val firstRendered = entries.size - MAX_RENDERED_LINES
        compose.onAllNodes(hasTestTag(AetherTestTags.logLine(entries[firstRendered].seq))).assertCountEquals(1)
        compose.onAllNodes(hasTestTag(AetherTestTags.logLine(entries[firstRendered - 1].seq))).assertCountEquals(0)
        compose.onAllNodes(hasTestTag(AetherTestTags.logLine(entries.first().seq))).assertCountEquals(0)
        compose.onNodeWithTag(AetherTestTags.LOG_BODY).assertExists()
    }

    @Test
    fun credentialsAreRedactedBeforeTheyReachThePanel() {
        openAether()
        AetherRepository.clearLogs()
        AetherRepository.log("INFO", "team enrolment token=SuperSecret123 accepted for team acme")

        waitForText(MASK_TEXT, present = true)
        compose.onAllNodes(hasText("SuperSecret123")).assertCountEquals(0)
        // Redaction happens at the logging boundary, not in the panel, so the
        // StateFlow every Aether surface collects is already clean.
        assertTrue(
            "the repository must not hold the secret",
            AetherRepository.logs.value.none { it.message.contains("SuperSecret123") }
        )
        // Non-secret operational detail survives.
        waitForText("team acme", present = true)
    }

    @Test
    fun errorEventsAreShownAndCanBeFiltered() {
        openAether()
        AetherRepository.clearLogs()
        AetherRepository.log("OK", "SOCKS5 127.0.0.1:1819 is accepting connections")
        waitForText("accepting connections", present = true)

        // Nothing wrong yet: the problems filter reports an empty result.
        toggle(AetherTestTags.LOG_FILTER_PROBLEMS)
        waitForNode(AetherTestTags.LOG_EMPTY, present = true)

        AetherRepository.log("ERROR", "Tunnel failed: tunnel ended before SOCKS5 127.0.0.1:1819 came up")
        waitForText("Tunnel failed", present = true)
        compose.onAllNodes(hasTestTag(AetherTestTags.LOG_EMPTY)).assertCountEquals(0)

        // Back to all events: the successful step is visible again too.
        toggle(AetherTestTags.LOG_FILTER)
        waitForText("accepting connections", present = true)
        waitForText("Tunnel failed", present = true)
    }

    @Test
    fun settingsAreRevealedPerGroupAndCanBeHiddenAgain() {
        openAether()
        // Step 2 opens with the first group only: the protocol choices.
        compose.onNodeWithTag(AetherTestTags.section("gateway")).assertExists()
        compose.onNodeWithTag(AetherTestTags.transportChip("masque")).assertExists()
        // The other groups are present as headers, their settings are not composed.
        listOf("tunnel", "fragmenting", "advanced").forEach { key ->
            compose.onNodeWithTag(AetherTestTags.section(key)).assertExists()
        }
        listOf(
            "aether_socks_field", "aether_dns_field",
            AetherTestTags.FRAGMENT_SIZE, "aether_ech_toggle"
        ).forEach { tag ->
            compose.onAllNodes(hasTestTag(tag)).assertCountEquals(0)
        }

        toggle(AetherTestTags.sectionToggle("tunnel"))
        waitForNode("aether_socks_field", present = true)
        waitForNode("aether_dns_field", present = true)
        // Opening one group does not open the others.
        compose.onAllNodes(hasTestTag(AetherTestTags.FRAGMENT_SIZE)).assertCountEquals(0)
        compose.onAllNodes(hasTestTag("aether_ech_toggle")).assertCountEquals(0)

        toggle(AetherTestTags.sectionToggle("tunnel"))
        waitForNode("aether_socks_field", present = false)
    }

    @Test
    fun everySettingTheAvailableRouteOwnsIsReachableInTheUi() {
        openAether()
        // Gateway group: protocol, scan mode, IP version and the peer override.
        listOf("masque", "wg", "gool", "mim").forEach {
            compose.onNodeWithTag(AetherTestTags.transportChip(it)).assertExists()
        }
        listOf("turbo", "balanced", "thorough", "verified", "ironclad").forEach {
            compose.onNodeWithTag(AetherTestTags.scanChip(it)).assertExists()
        }
        listOf("v4", "v6", "both").forEach {
            compose.onNodeWithTag(AetherTestTags.ipChip(it)).assertExists()
        }
        compose.onNodeWithTag("aether_peer_field").assertExists()

        toggle(AetherTestTags.sectionToggle("fragmenting"))
        // Fragmenting: implemented upstream, shown with its real status.
        waitForNode(AetherTestTags.FRAGMENT_TOGGLE, present = true)
        compose.onNodeWithTag(AetherTestTags.FRAGMENT_SIZE).assertExists()
        compose.onNodeWithTag(AetherTestTags.FRAGMENT_DELAY).assertExists()
        compose.onNodeWithTag(AetherTestTags.FRAGMENT_SNI).assertExists()
        waitForText("AETHER_MASQUE_H2_FRAGMENT", present = true)

        toggle(AetherTestTags.sectionToggle("tunnel"))
        waitForNode("aether_socks_field", present = true)
        listOf("aether_http_field", "aether_dns_field", "aether_upstream_field").forEach {
            compose.onNodeWithTag(it).assertExists()
        }

        toggle(AetherTestTags.sectionToggle("advanced"))
        // Obfuscation profiles, including the one the old chip list omitted
        // (`light` is accepted by noize::from_profile).
        waitForNode("aether_ech_toggle", present = true)
        listOf("off", "light", "firewall", "balanced", "gfw", "aggressive").forEach {
            compose.onNodeWithTag(AetherTestTags.noizeChip(it)).assertExists()
        }
        listOf("aether_quick_toggle", "aether_team_field", "aether_identity_field").forEach {
            compose.onNodeWithTag(it).assertExists()
        }

        // Save / reset are still there; nothing was dropped for simplicity.
        compose.onNodeWithTag(AetherTestTags.SAVE).assertExists()
        compose.onNodeWithTag(AetherTestTags.RESET).assertExists()
    }

    @Test
    fun choicesThisBuildCannotForwardSaySoAndAreNotClickable() {
        openAether()
        // gool and mim are real Aether protocols, but api::Transport::parse maps
        // everything that is not wg to Masque on the path this build uses.
        compose.onNodeWithTag(AetherTestTags.transportChip("masque")).assert(hasClickAction())
        compose.onNodeWithTag(AetherTestTags.transportChip("wg")).assert(hasClickAction())
        compose.onNodeWithTag(AetherTestTags.transportChip("gool")).assert(hasNoClickAction())
        compose.onNodeWithTag(AetherTestTags.transportChip("mim")).assert(hasNoClickAction())
        // The reason is on screen next to them, not in a manual somewhere.
        waitForText("aether_tunnel_start", present = true)

        toggle(AetherTestTags.sectionToggle("fragmenting"))
        waitForNode(AetherTestTags.FRAGMENT_SIZE, present = true)
        // Recorded, shown, and honestly not an interactive control.
        compose.onNodeWithTag(AetherTestTags.FRAGMENT_SIZE).assert(hasNoClickAction())
        waitForText(NOT_FORWARDED_LABEL, present = true)
    }

    @Test
    fun selectedRouteSettingsCanActuallyBeCollapsed() {
        openAether()
        compose.onNodeWithTag(AetherTestTags.routeSettings("warp_direct")).assertExists()
        // Tapping the already-selected route closes its settings; the previous
        // expansion map could only ever add `true`, so they could never close.
        toggle(AetherTestTags.routeCard("warp_direct"))
        waitForNode(AetherTestTags.routeSettings("warp_direct"), present = false)
        toggle(AetherTestTags.routeCard("warp_direct"))
        waitForNode(AetherTestTags.routeSettings("warp_direct"), present = true)
    }

    @Test
    fun switchingRoutesAndComingBackKeepsWhatWasTyped() {
        openAether()
        toggle(AetherTestTags.sectionToggle("tunnel"))
        waitForNode("aether_socks_field", present = true)

        typeInto("aether_socks_field", "127.0.0.1:1999")
        waitForText("127.0.0.1:1999", present = true)
        // The panel flags unsaved edits instead of silently keeping them.
        waitForText(UNSAVED_HINT, present = true)

        toggle(AetherTestTags.routeCard("tor_aether"))
        waitForNode(AetherTestTags.routeSettings("tor_aether"), present = true)

        toggle(AetherTestTags.routeCard("warp_direct"))
        waitForNode(AetherTestTags.routeSettings("warp_direct"), present = true)
        waitForNode("aether_socks_field", present = true)
        waitForText("127.0.0.1:1999", present = true)
    }

    @Test
    fun invalidInputIsMarkedWhereItIsTyped() {
        openAether()
        toggle(AetherTestTags.sectionToggle("tunnel"))
        waitForNode("aether_socks_field", present = true)

        typeInto("aether_socks_field", "0.0.0.0:1819")
        waitForText("Must be loopback", present = true)

        typeInto("aether_socks_field", "127.0.0.1:1819")
        waitForText("Must be loopback", present = false)

        // Save refuses an unusable configuration and says why.
        typeInto("aether_socks_field", "127.0.0.1:80")
        toggle(AetherTestTags.SAVE)
        waitForText("Invalid SOCKS port", present = true)
    }

    private fun hasClickAction() =
        SemanticsMatcher("Has an OnClick action") { it.config.contains(SemanticsActions.OnClick) }

    private fun hasNoClickAction() =
        SemanticsMatcher("Has no OnClick action") { !it.config.contains(SemanticsActions.OnClick) }

    private companion object {
        /** Must stay in sync with [com.manjaniq.core.AetherLogSanitizer.MASK]. */
        const val MASK_TEXT = "[redacted]"

        /** `R.string.vpn_limitation_title`. */
        const val LIMITATION_TITLE = "No device-wide VPN"

        /** `R.string.aether_choice_not_forwarded`. */
        const val NOT_FORWARDED_LABEL = "Recorded only"

        /** `R.string.aether_unsaved_hint`. */
        const val UNSAVED_HINT = "Unsaved changes"
    }
}
