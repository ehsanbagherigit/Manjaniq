package com.manjaniq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Host-side regression test for the JNI symbol contract.
 *
 * Derives the required JNI export names from the Kotlin
 * `external` declarations and compares them with the Go
 * `//export` directives in native/bridge.go — the same
 * cross-check native/verify_native_lib.sh performs against
 * the built .so, but runnable without any toolchain, so
 * the two sides cannot drift apart unnoticed before the
 * native build even starts.
 */
class NativeBridgeContractTest {

    /** First existing file among the candidates. */
    private fun resolve(vararg candidates: String): File {
        for (c in candidates) {
            val f = File(c)
            if (f.isFile) return f
        }
        // All missing: return the first so the assertion
        // message names the expected path.
        return File(candidates[0])
    }

    @Test
    fun kotlinExternalMethodsMatchGoExports() {
        val kotlin = resolve(
            "app/src/main/java/com/manjaniq/NativeBridge.kt",
            "src/main/java/com/manjaniq/NativeBridge.kt",
            "../app/src/main/java/com/manjaniq/NativeBridge.kt"
        )
        val go = resolve(
            "native/bridge.go",
            "../native/bridge.go"
        )
        assertTrue("NativeBridge.kt not found: ${kotlin.absolutePath}",
            kotlin.isFile)
        assertTrue("native/bridge.go not found: ${go.absolutePath}",
            go.isFile)

        val kotlinMethods = kotlin.readText()
            .split("\n")
            .mapNotNull { line ->
                Regex("""external\s+fun\s+([A-Za-z_][A-Za-z0-9_]*)""")
                    .find(line)?.groupValues?.get(1)
            }
            .toSet()
        assertTrue("no external methods found in NativeBridge.kt",
            kotlinMethods.isNotEmpty())

        val prefix = "Java_com_manjaniq_NativeBridge_"
        val goExports = go.readText()
            .split("\n")
            .mapNotNull { line ->
                Regex("""//export\s+(\S+)""").find(line)?.groupValues?.get(1)
            }
            .filter { it.startsWith(prefix) }
            .map { it.substring(prefix.length) }
            .toSet()
        assertTrue("no JNI exports found in bridge.go",
            goExports.isNotEmpty())

        if (kotlinMethods != goExports) {
            fail(
                "JNI contract broken.\n" +
                    "Kotlin declares but Go does not export: " +
                    (kotlinMethods - goExports) + "\n" +
                    "Go exports but Kotlin does not declare: " +
                    (goExports - kotlinMethods)
            )
        }
        assertEquals(
            "Kotlin external methods and Go //export directives " +
                "must match one-to-one",
            kotlinMethods, goExports
        )
    }

    @Test
    fun kotlinLoadLibraryNameMatchesLibraryFileNameConvention() {
        val kotlin = resolve(
            "app/src/main/java/com/manjaniq/NativeBridge.kt",
            "src/main/java/com/manjaniq/NativeBridge.kt",
            "../app/src/main/java/com/manjaniq/NativeBridge.kt"
        )
        val text = kotlin.readText()
        val loadMatch = Regex(
            """System\.loadLibrary\("([A-Za-z0-9_]+)"\)"""
        ).find(text)
        assertTrue("System.loadLibrary call not found",
            loadMatch != null)
        assertEquals(
            "lib<name>.so must be the library the CI builds",
            "manjaniq_native",
            loadMatch!!.groupValues[1]
        )
    }
}
