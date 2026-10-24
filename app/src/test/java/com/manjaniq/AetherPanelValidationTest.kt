package com.manjaniq

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.manjaniq.core.models.AetherConfig
import com.manjaniq.ui.AetherTestTags
import com.manjaniq.ui.TestTags
import com.manjaniq.ui.screens.aetherBindError
import com.manjaniq.ui.screens.aetherFragmentDelayError
import com.manjaniq.ui.screens.aetherFragmentSizeError
import com.manjaniq.ui.screens.sectionKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The Aether panel validates each field as it is typed, so a bad value is marked
 * where it is instead of only after Save. These tests keep the inline checks in
 * step with [AetherConfig.validate], which stays the gate before a start, and
 * pin the test tags the UI tests rely on.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AetherPanelValidationTest {

    private val bindSamples = listOf(
        "",
        "127.0.0.1:1819",
        "localhost:1819",
        "127.0.0.1:65535",
        "0.0.0.0:1819",
        "8.8.8.8:1819",
        "127.0.0.1:80",
        "127.0.0.1:99999",
        "127.0.0.1:notaport",
        "127.0.0.1",
        "   "
    )

    @Test
    fun inlineBindCheckAgreesWithTheModel() {
        assertNull(aetherBindError(""))
        assertNull(aetherBindError("127.0.0.1:1819"))
        assertNull(aetherBindError("localhost:1819"))
        assertNotNull(aetherBindError("0.0.0.0:1819"))
        assertNotNull(aetherBindError("127.0.0.1:80"))
        assertNotNull(aetherBindError("127.0.0.1:notaport"))

        bindSamples.forEach { value ->
            val inlineSaysOk = aetherBindError(value) == null
            val modelSaysOk = AetherConfig.DEFAULT.copy(socksBind = value).validate() == null
            assertEquals("inline and model disagree for socksBind='$value'", modelSaysOk, inlineSaysOk)

            // The optional HTTP proxy field is validated by the same rule, except
            // that empty means "do not bind a second listener". The comparison uses
            // a SOCKS port no sample can clash with: socks == http is a two-field
            // rule that a single-field helper cannot know about (AetherSetting
            // checks it per field, and AetherConfigLogicTest pins it).
            val httpInline = aetherBindError(value) == null
            val httpModel = AetherConfig.DEFAULT.copy(
                socksBind = "127.0.0.1:2001",
                httpProxyBind = value
            ).validate() == null
            assertEquals("inline and model disagree for httpProxyBind='$value'", httpModel, httpInline)
        }
    }

    @Test
    fun twoListenersOnOnePortAreRejectedByTheModel() {
        assertNotNull(
            AetherConfig.DEFAULT.copy(
                socksBind = "127.0.0.1:1819",
                httpProxyBind = "127.0.0.1:1819"
            ).validate()
        )
        assertNull(
            AetherConfig.DEFAULT.copy(
                socksBind = "127.0.0.1:1819",
                httpProxyBind = "127.0.0.1:1820"
            ).validate()
        )
        // Blank (including all-whitespace) means "no second listener".
        assertNull(AetherConfig.DEFAULT.copy(httpProxyBind = "   ").validate())
    }

    @Test
    fun inlineFragmentChecksUseAetherLimits() {
        assertNull(aetherFragmentSizeError(""))
        assertNull(aetherFragmentSizeError("16-32"))
        assertNull(aetherFragmentSizeError("64"))
        assertNotNull(aetherFragmentSizeError("16-9000"))
        assertNotNull(aetherFragmentSizeError("big"))

        assertNull(aetherFragmentDelayError(""))
        assertNull(aetherFragmentDelayError("2-10"))
        assertNotNull(aetherFragmentDelayError("2-900000"))
        assertNotNull(aetherFragmentDelayError("soon"))

        listOf("", "16-32", "64", "16-9000", "big").forEach { value ->
            assertEquals(
                "inline and model disagree for fragmentSize='$value'",
                AetherConfig.DEFAULT.copy(fragmentSize = value).validate() == null,
                aetherFragmentSizeError(value) == null
            )
        }
        listOf("", "2-10", "2-900000", "soon").forEach { value ->
            assertEquals(
                "inline and model disagree for fragmentDelay='$value'",
                AetherConfig.DEFAULT.copy(fragmentDelay = value).validate() == null,
                aetherFragmentDelayError(value) == null
            )
        }
    }

    @Test
    fun sectionKeysAreStableTagMaterial() {
        assertEquals("gateway", sectionKey(R.string.section_gateway))
        assertEquals("fragmenting", sectionKey(R.string.section_fragmenting))
        assertEquals("tunnel", sectionKey(R.string.section_tunnel))
        assertEquals("advanced", sectionKey(R.string.section_advanced))
        assertEquals("psiphon", sectionKey(R.string.section_psiphon))
        assertTrue("unknown sections still need a tag", sectionKey(987654).startsWith("section_"))
    }

    @Test
    fun aetherTagsDoNotCollideWithTheSharedOnes() {
        // The Aether panel reuses the tags instrumentation already waits on.
        assertEquals("aether_toggle_button", TestTags.AETHER_TOGGLE)
        assertEquals("core_card_aether", TestTags.CORE_CARD_AETHER)
        assertEquals("aether_status_card", TestTags.AETHER_STATUS)
        // ...and keeps the identifiers the route list has always used.
        assertEquals("aether_profiles_title", AetherTestTags.ROUTE_TITLE)
        assertEquals("profile_card_warp_direct", AetherTestTags.routeCard("warp_direct"))
        assertEquals("profile_settings_warp_direct", AetherTestTags.routeSettings("warp_direct"))

        val tags = listOf(
            AetherTestTags.PANEL,
            AetherTestTags.ROUTE_LIST,
            AetherTestTags.SAVE,
            AetherTestTags.RESET,
            AetherTestTags.LOG_PANEL,
            AetherTestTags.LOG_BODY,
            AetherTestTags.section("gateway"),
            AetherTestTags.sectionToggle("gateway"),
            AetherTestTags.transportChip("masque"),
            AetherTestTags.scanChip("balanced"),
            AetherTestTags.ipChip("v4"),
            AetherTestTags.noizeChip("light"),
            AetherTestTags.FRAGMENT_SIZE,
            AetherTestTags.logLine(1L)
        )
        assertEquals("duplicate Aether test tags: $tags", tags.size, tags.distinct().size)
        tags.forEach { assertTrue("tag must be usable: '$it'", it.isNotBlank()) }
    }
}
