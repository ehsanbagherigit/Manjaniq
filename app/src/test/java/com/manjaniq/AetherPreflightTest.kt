package com.manjaniq

import com.manjaniq.core.AetherPreflight
import com.manjaniq.core.models.AetherConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.ServerSocket

/**
 * The start-time checks, on the host.
 *
 * `AetherService` cannot run here (foreground service, real FFI), so the object it
 * calls is exercised directly. Each case is a failure that used to reach the user as
 * an engine message - or as a start that reported success while a listener was
 * silently missing.
 *
 * Plain JUnit: [AetherPreflight] uses only `java.io` and `java.net`.
 */
class AetherPreflightTest {

    private fun tempDir(): File =
        File(System.getProperty("java.io.tmpdir") ?: ".", "aether-preflight-${System.nanoTime()}")
            .apply { mkdirs() }

    private val alwaysFree: (String, Int) -> Boolean = { _, _ -> true }

    @Test
    fun aPlainIdentityPathResolvesInsideTheDataDirectory() {
        val root = tempDir()
        try {
            val file = AetherPreflight.identityFile(AetherConfig.DEFAULT, root.absolutePath)
            assertNotNull(file)
            assertEquals(File(root, "aether_identity").absolutePath, file!!.absolutePath)
            assertNull(AetherPreflight.check(AetherConfig.DEFAULT, root.absolutePath, alwaysFree))
        } finally {
            root.delete()
        }
    }

    @Test
    fun aNestedIdentityPathGetsItsDirectoryCreated() {
        val root = tempDir()
        try {
            val cfg = AetherConfig.DEFAULT.copy(identityPath = "aether/identity/main")
            assertNull(
                "a nested path must be usable, not an engine failure",
                AetherPreflight.check(cfg, root.absolutePath, alwaysFree)
            )
            val file = AetherPreflight.identityFile(cfg, root.absolutePath)!!
            assertTrue("the directory must exist before the core writes there", file.parentFile!!.isDirectory)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun anIdentityPathThatEscapesTheDataDirectoryIsRefused() {
        val root = tempDir()
        try {
            listOf("/etc/aether_identity", "../aether_identity", "a/../../identity").forEach { path ->
                assertNull("'$path' must not resolve", AetherPreflight.identityFile(
                    AetherConfig.DEFAULT.copy(identityPath = path), root.absolutePath
                ))
                val message = AetherPreflight.check(
                    AetherConfig.DEFAULT.copy(identityPath = path), root.absolutePath, alwaysFree
                )
                assertNotNull("'$path' must be refused before the start: $message", message)
                assertTrue(message!!.contains("identity path", ignoreCase = true))
            }
            // A path that only looks like a sibling directory is still outside.
            assertNull(
                AetherPreflight.identityFile(
                    AetherConfig.DEFAULT.copy(identityPath = "../${root.name}-elsewhere/id"),
                    root.absolutePath
                )
            )
        } finally {
            root.deleteRecursively()
        }
    }

    /**
     * The check is a real write, not a `canWrite()` guess: the directory has to
     * accept a file *and its contents*, or the core cannot write the identity it
     * registers.
     */
    @Test
    fun theIdentityDirectoryMustAcceptARealWrite() {
        val root = tempDir()
        try {
            val inside = File(root, "identity")
            assertTrue(inside.mkdirs())
            val message = AetherPreflight.prepareIdentity(File(inside, "aether_identity"))
            assertNull("a writable directory must pass: $message", message)
            // The probe leaves nothing behind.
            assertEquals(
                "no probe file may be left in the data directory",
                0,
                inside.listFiles()!!.size
            )
        } finally {
            root.deleteRecursively()
        }
    }

    /** An identity path that names a directory is refused, not quarantined later. */
    @Test
    fun anIdentityPathThatIsADirectoryIsRefused() {
        val root = tempDir()
        try {
            val identity = File(root, "aether_identity")
            assertTrue(identity.mkdirs())
            val message = AetherPreflight.check(AetherConfig.DEFAULT, root.absolutePath, alwaysFree)
            assertNotNull("a directory at the identity path must be refused", message)
            assertTrue("names the path: $message", message!!.contains("not a file"))
            assertTrue("the directory must be left alone", identity.isDirectory)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun aBusySocksPortIsReportedBeforeAnythingIsStarted() {
        val root = tempDir()
        try {
            val message = AetherPreflight.check(
                AetherConfig.DEFAULT.copy(socksBind = "127.0.0.1:1919"),
                root.absolutePath
            ) { host, port -> !(host == "127.0.0.1" && port == 1919) }
            assertNotNull(message)
            assertTrue("names the address: $message", message!!.contains("127.0.0.1:1919"))
            assertTrue("says what is wrong: $message", message.contains("already in use"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun aBusyHttpProxyPortIsReportedToo() {
        val root = tempDir()
        try {
            val message = AetherPreflight.check(
                AetherConfig.DEFAULT.copy(httpProxyBind = "127.0.0.1:1920"),
                root.absolutePath
            ) { _, port -> port != 1920 }
            assertNotNull(message)
            assertTrue(message!!.contains("1920"))
        } finally {
            root.deleteRecursively()
        }
    }

    /** The check is a real bind, not a guess: a held port is detected. */
    @Test
    fun theRealProbeSeesAHeldPort() {
        val root = tempDir()
        try {
            ServerSocket().use { held ->
                held.reuseAddress = false
                held.bind(java.net.InetSocketAddress("127.0.0.1", 0), 1)
                val port = held.localPort
                assertFalse("a bound port must not look free", AetherPreflight.isFree("127.0.0.1", port))
                val message = AetherPreflight.check(
                    AetherConfig.DEFAULT.copy(socksBind = "127.0.0.1:$port"),
                    root.absolutePath
                )
                assertNotNull("a start on a held port must be refused: $message", message)
                assertTrue(message!!.contains(port.toString()))
            }
        } finally {
            root.deleteRecursively()
        }
    }

    /**
     * `localhost` is a valid answer for a person and an invalid `SocketAddr` for the
     * engine, which falls back to its default instead of complaining
     * (`lib.rs:115-118`). The port the user typed is what must be bound.
     */
    @Test
    fun localhostIsResolvedInsteadOfBeingSilentlyDroppedByTheEngine() {
        val cfg = AetherConfig.DEFAULT.copy(
            socksBind = "localhost:1819",
            httpProxyBind = "localhost:1820"
        )
        val args = cfg.toAetherArgs("/data/user/0/com.manjaniq/files")
        assertEquals("127.0.0.1:1819", args[args.indexOf("--bind") + 1])
        assertEquals("127.0.0.1:1820", args[args.indexOf("--http-proxy") + 1])
        assertFalse("no unparsable literal may reach the engine: $args", args.contains("localhost:1819"))
    }
}
