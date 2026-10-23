package com.manjaniq

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every engine setting is reachable from the settings screen.
 *
 * The model (`AetherEngineOptions.kt`) and the panel
 * (`AetherEngineOptionsPanel.kt`) are two halves of one promise: a field the model
 * can send but the UI cannot set is a feature the app claims to have and does not,
 * and one the UI sets but the model never sends is a control that does nothing.
 * `AetherEngineOptionsTest` pins the second half (each flag reaches argv); this
 * pins the first, by reading both files and checking that every property of every
 * options data class is written by some `copy(... = ...)` in the panel.
 *
 * The check is on source rather than on a rendered screen on purpose: Compose
 * tests cannot reach a field that has no composable at all, and the question here
 * is exactly whether one exists. Nothing here replaces the instrumentation test
 * that taps a control; it is the cheaper guard that keeps a later field from
 * quietly losing its editor.
 */
class AetherEngineUiCoverageTest {

    @Test
    fun everyEngineOptionIsEditableInThePanel() {
        val model = read("app/src/main/java/com/manjaniq/core/models/AetherEngineOptions.kt")
        val panel = stripComments(
            read("app/src/main/java/com/manjaniq/ui/screens/AetherEngineOptionsPanel.kt")
        )

        val properties = dataClassProperties(model)
        assertTrue("no data classes parsed out of the model", properties.size > 60)

        val missing = properties.filter { name -> !isWritten(panel, name) }
        assertTrue(
            "these engine options have no control in AetherEngineOptionsPanel.kt: " +
                missing.sorted().joinToString(", "),
            missing.isEmpty()
        )
    }

    @Test
    fun everyEditedFieldIsInTheModel() {
        val model = read("app/src/main/java/com/manjaniq/core/models/AetherEngineOptions.kt")
        val panel = stripComments(
            read("app/src/main/java/com/manjaniq/ui/screens/AetherEngineOptionsPanel.kt")
        )
        val known = dataClassProperties(model)

        // Every `copy(x = ...)` the panel writes has to name a property that exists
        // somewhere in the options model. A typo there would not compile, but a
        // group name copied from an older shape would - and would silently drop the
        // edit, because Kotlin would have refused it instead.
        val written = Regex("""\bcopy\(\s*([A-Za-z_][A-Za-z0-9_]*)\s*=""")
            .findAll(panel)
            .map { it.groupValues[1] }
            .toSet()
        // `engine` is AetherConfig's own property, the block these groups live in.
        val groups = setOf("engine", "ech", "carrier", "gool", "mim", "tor", "psiphon",
            "exitLocation", "stats", "tls", "routing", "timing", "api", "logging", "gateway")
        val unknown = written - known - groups
        assertTrue("panel writes fields the model does not have: $unknown", unknown.isEmpty())
        assertTrue("panel writes no fields at all", written.isNotEmpty())
    }

    // ----------------------------------------------------------------- helpers ---

    private fun isWritten(panel: String, name: String): Boolean =
        // `copy(tor = it.tor.copy(bind = ..."`, `AetherEngineOptions(tor = ...` and a
        // plain `bind = ` inside a nested copy all count; the name is what matters.
        Regex("""\b${Regex.escape(name)}\s*=""").containsMatchIn(panel)

    /**
     * Property names of the `data class` blocks in [source].
     *
     * Only `val name: Type` lines directly inside a data class are collected, so
     * local variables and companion constants do not leak in.
     */
    private fun dataClassProperties(source: String): List<String> {
        val withoutComments = stripComments(source)
        val names = mutableListOf<String>()
        var inside = false
        var depth = 0
        for (raw in withoutComments.lineSequence()) {
            val line = raw.trim()
            if (!inside) {
                if (line.startsWith("data class ")) {
                    inside = true
                    depth = 0
                }
                continue
            }
            val isProperty = line.startsWith("val ") && line.contains(":")
            if (isProperty && depth <= 1) {
                names += line.removePrefix("val ").substringBefore(':').trim()
            }
            depth += line.count { it == '{' } - line.count { it == '}' }
            if (depth < 0) {
                inside = false
                depth = 0
            }
        }
        return names
    }

    /**
     * Removes `//` line comments and `/* */` block comments.
     *
     * Needed because both files explain themselves at length, and a field named in
     * prose must not count as an editor.
     */
    private fun stripComments(source: String): String {
        val out = StringBuilder(source.length)
        var i = 0
        var inLine = false
        var inBlock = false
        while (i < source.length) {
            val c = source[i]
            val next = if (i + 1 < source.length) source[i + 1] else '\u0000'
            when {
                inLine -> if (c == '\n') { inLine = false; out.append(c) }
                inBlock -> if (c == '*' && next == '/') { inBlock = false; i++ }
                c == '/' && next == '/' -> { inLine = true; i++ }
                c == '/' && next == '*' -> { inBlock = true; i++ }
                else -> out.append(c)
            }
            i++
        }
        return out.toString()
    }

    /** Reads a repository-relative file, wherever Gradle started the test. */
    private fun read(relative: String): String {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").isFile) {
                val file = File(dir, relative)
                assertNotNull("missing $relative", file.takeIf { it.isFile })
                return file.readText()
            }
            dir = dir.parentFile
        }
        throw AssertionError("could not find the repository root from ${File("").absolutePath}")
    }
}
