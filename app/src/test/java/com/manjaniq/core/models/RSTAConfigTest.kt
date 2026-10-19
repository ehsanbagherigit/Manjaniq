package com.manjaniq.core.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for the genuine RSTA config model:
 * JSON round-trip (the exact format the Go engine consumes),
 * default-per-field fallback for partial JSON, and the
 * validation contract shared with the Go side.
 */
class RSTAConfigTest {

    @Test
    fun defaultConfigIsValid() {
        assertNull("bundled defaults must be startable", RSTAConfig.DEFAULT.validate())
    }

    @Test
    fun toJsonFromJsonRoundTripPreservesEveryField() {
        val original = RSTAConfig(
            listenHost = "127.0.0.1",
            listenPort = 8080,
            connectIp = "203.0.113.7",
            connectPort = 8443,
            sniHostname = "example.org",
            bypassMethod = "combined",
            fragmentStrategy = "multi",
            fragmentDelay = 0.25,
            useTtlTrick = true,
            monitorEnabled = false,
            quietMode = true,
            fakeSNIMethod = "prefix_fake",
            fakeSNIFragmentReal = false
        )
        val restored = RSTAConfig.fromJson(original.toJson())
        assertNotNull(restored)
        assertEquals(original, restored)
    }

    @Test
    fun fromJsonUsesGenuineConfigKeys() {
        // The genuine rsta-source/config.json format.
        val json = """
        {
          "LISTEN_HOST": "0.0.0.0",
          "LISTEN_PORT": 40443,
          "CONNECT_IP": "104.18.38.202",
          "CONNECT_PORT": 443,
          "FAKE_SNI": "cdnjs.cloudflare.com",
          "BYPASS_METHOD": "fragment",
          "FRAGMENT_STRATEGY": "sni_split",
          "FRAGMENT_DELAY": 0.1,
          "USE_TTL_TRICK": false,
          "FAKE_SNI_METHOD": "prefix_fake",
          "FAKE_SNI_FRAGMENT_REAL": true,
          "MONITOR": true,
          "quiet": false
        }
        """.trimIndent()
        val config = RSTAConfig.fromJson(json)
        assertNotNull(config)
        assertEquals(40443, config!!.listenPort)
        assertEquals("cdnjs.cloudflare.com", config.sniHostname)
        assertEquals("fragment", config.bypassMethod)
        assertEquals("sni_split", config.fragmentStrategy)
        assertEquals(0.1, config.fragmentDelay, 0.0001)
        assertEquals(false, config.useTtlTrick)
        assertEquals("prefix_fake", config.fakeSNIMethod)
        assertEquals(true, config.fakeSNIFragmentReal)
        assertEquals(true, config.monitorEnabled)
        assertEquals(false, config.quietMode)
    }

    @Test
    fun fromJsonFallsBackToDefaultsPerField() {
        val partial = """{ "LISTEN_PORT": 12345, "FAKE_SNI": "allowed.example" }"""
        val config = RSTAConfig.fromJson(partial)
        assertNotNull(config)
        assertEquals(12345, config!!.listenPort)
        // Every other field falls back to the documented default.
        assertEquals(RSTAConfig.DEFAULT.listenHost, config.listenHost)
        assertEquals(RSTAConfig.DEFAULT.connectIp, config.connectIp)
        assertEquals(RSTAConfig.DEFAULT.connectPort, config.connectPort)
        assertEquals(RSTAConfig.DEFAULT.bypassMethod, config.bypassMethod)
        assertEquals(RSTAConfig.DEFAULT.fragmentStrategy, config.fragmentStrategy)
        assertEquals(RSTAConfig.DEFAULT.fragmentDelay, config.fragmentDelay, 0.0001)
        assertEquals(RSTAConfig.DEFAULT.useTtlTrick, config.useTtlTrick)
        assertEquals(RSTAConfig.DEFAULT.monitorEnabled, config.monitorEnabled)
        assertEquals(RSTAConfig.DEFAULT.quietMode, config.quietMode)
        assertEquals(RSTAConfig.DEFAULT.fakeSNIMethod, config.fakeSNIMethod)
        assertEquals(RSTAConfig.DEFAULT.fakeSNIFragmentReal, config.fakeSNIFragmentReal)
    }

    @Test
    fun fromJsonRejectsNonJsonObject() {
        assertNull(RSTAConfig.fromJson("not json at all"))
        assertNull(RSTAConfig.fromJson("[1,2,3]"))
        assertNull(RSTAConfig.fromJson(""))
    }

    @Test
    fun validateRejectsOutOfRangePorts() {
        assertProblem(listenPort = 0)
        assertProblem(listenPort = 65536)
        assertProblem(connectPort = -1)
        assertProblem(connectPort = 70000)
    }

    @Test
    fun validateRejectsBlankHostsAndInvalidSni() {
        assertProblem(listenHost = " ")
        assertProblem(listenHost = "0.0.0.0 ")
        assertProblem(connectIp = "")
        assertProblem(sniHostname = "not a hostname")
        assertProblem(sniHostname = "bad_host_name")
    }

    @Test
    fun validateRejectsUnknownMethodStrategyAndDelay() {
        assertProblem(bypassMethod = "magic")
        assertProblem(fragmentStrategy = "quantum")
        assertProblem(fragmentDelay = -0.1)
        assertProblem(fragmentDelay = 5.1)
    }

    @Test
    fun validateAcceptsBoundaryPortsAndDelay() {
        assertNull(
            RSTAConfig.DEFAULT.copy(listenPort = 1, connectPort = 65535)
                .validate()
        )
        assertNull(RSTAConfig.DEFAULT.copy(fragmentDelay = 0.0).validate())
        assertNull(RSTAConfig.DEFAULT.copy(fragmentDelay = 5.0).validate())
    }

    @Test
    fun savedConfigSurvivesSerializationWithoutDrift() {
        // What Export shows, what the engine receives and what is
        // persisted must be the same JSON (spec: UI and engine
        // configuration cannot silently diverge).
        val config = RSTAConfig.DEFAULT.copy(listenPort = 51820)
        val json = config.toJson()
        assertTrue("serialized config must carry the edited port",
            json.contains("\"LISTEN_PORT\": 51820"))
        val reloaded = RSTAConfig.fromJson(json)
        assertEquals(config, reloaded)
        assertNotEquals(RSTAConfig.DEFAULT, reloaded)
    }

    private fun assertProblem(
        listenHost: String = RSTAConfig.DEFAULT.listenHost,
        listenPort: Int = RSTAConfig.DEFAULT.listenPort,
        connectIp: String = RSTAConfig.DEFAULT.connectIp,
        connectPort: Int = RSTAConfig.DEFAULT.connectPort,
        sniHostname: String = RSTAConfig.DEFAULT.sniHostname,
        bypassMethod: String = RSTAConfig.DEFAULT.bypassMethod,
        fragmentStrategy: String = RSTAConfig.DEFAULT.fragmentStrategy,
        fragmentDelay: Double = RSTAConfig.DEFAULT.fragmentDelay
    ) {
        val problem = RSTAConfig.DEFAULT.copy(
            listenHost = listenHost,
            listenPort = listenPort,
            connectIp = connectIp,
            connectPort = connectPort,
            sniHostname = sniHostname,
            bypassMethod = bypassMethod,
            fragmentStrategy = fragmentStrategy,
            fragmentDelay = fragmentDelay
        ).validate()
        assertNotNull("expected a validation problem", problem)
    }
}
