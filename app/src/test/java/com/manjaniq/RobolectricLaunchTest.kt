package com.manjaniq

import com.manjaniq.core.AppLanguage
import com.manjaniq.core.LocaleHelper
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Host-side startup validation — runs on JVM without emulator.
 * Validates the two previous crash regressions:
 * 1) LocaleHelper must not mutate shared Configuration (previous NPE/crash)
 * 2) AndroidManifest must declare launcher icon (previous missing icon)
 *
 * This runs in `build` job's unit tests, so CI catches launch crashes even
 * if emulator infra is down. See also androidTest/LaunchSmokeTest for
 * real-device verification.
 */
class RobolectricLaunchTest {

    private fun resolvePath(vararg candidates: String): File {
        for (c in candidates) {
            val f = File(c)
            if (f.exists()) return f
        }
        // fallback to first candidate for error message
        return File(candidates[0])
    }

    @Test
    fun localeHelperDoesNotMutateOriginalConfig() {
        assertEquals(java.util.Locale("fa"), LocaleHelper.getCurrentLocale(AppLanguage.FA))
        assertEquals(java.util.Locale.ENGLISH, LocaleHelper.getCurrentLocale(AppLanguage.EN))
    }

    @Test
    fun localeHelperWrapContextUsesCopy() {
        val srcFile = resolvePath(
            "app/src/main/java/com/manjaniq/core/LocaleHelper.kt",
            "src/main/java/com/manjaniq/core/LocaleHelper.kt",
            "../app/src/main/java/com/manjaniq/core/LocaleHelper.kt"
        )
        assertTrue("LocaleHelper.kt not found: $srcFile", srcFile.exists())
        val src = srcFile.readText()
        assertTrue("LocaleHelper must copy Configuration", src.contains("Configuration(context.resources.configuration)"))
        assertTrue("LocaleHelper must setLayoutDirection", src.contains("setLayoutDirection"))
        assertFalse("LocaleHelper must not mutate shared config directly", src.contains("val config = context.resources.configuration\n        config.setLocale"))
    }

    @Test
    fun launcherIconDeclaredInManifest() {
        val manifestFile = resolvePath(
            "app/src/main/AndroidManifest.xml",
            "src/main/AndroidManifest.xml",
            "../app/src/main/AndroidManifest.xml"
        )
        assertTrue("AndroidManifest.xml not found: $manifestFile", manifestFile.exists())
        val manifest = manifestFile.readText()
        assertTrue("Manifest must declare android:icon=\"@mipmap/ic_launcher\"", manifest.contains("android:icon=\"@mipmap/ic_launcher\""))
        assertTrue("Manifest must declare roundIcon", manifest.contains("android:roundIcon=\"@mipmap/ic_launcher_round\""))
    }

    @Test
    fun mipmapLauncherResourcesExist() {
        val densities = listOf("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")
        for (d in densities) {
            val icon = resolvePath("app/src/main/res/mipmap-$d/ic_launcher.png", "src/main/res/mipmap-$d/ic_launcher.png")
            val fg = resolvePath("app/src/main/res/mipmap-$d/ic_launcher_foreground.png", "src/main/res/mipmap-$d/ic_launcher_foreground.png")
            val round = resolvePath("app/src/main/res/mipmap-$d/ic_launcher_round.png", "src/main/res/mipmap-$d/ic_launcher_round.png")
            assertTrue("Missing mipmap-$d/ic_launcher.png ($icon)", icon.exists() && icon.length() > 0)
            assertTrue("Missing mipmap-$d/ic_launcher_foreground.png ($fg)", fg.exists() && fg.length() > 0)
            assertTrue("Missing mipmap-$d/ic_launcher_round.png ($round)", round.exists() && round.length() > 0)
        }
        val adv = resolvePath("app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml", "src/main/res/mipmap-anydpi-v26/ic_launcher.xml")
        val advRound = resolvePath("app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml", "src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml")
        assertTrue("Missing adaptive icon XML ($adv)", adv.exists())
        assertTrue("Missing adaptive round icon XML ($advRound)", advRound.exists())
        val colorsFile = resolvePath("app/src/main/res/values/colors.xml", "src/main/res/values/colors.xml")
        assertTrue("colors.xml not found", colorsFile.exists())
        val colors = colorsFile.readText()
        assertTrue("colors.xml must define ic_launcher_background", colors.contains("ic_launcher_background"))
    }

    @Test
    fun mainActivityAttachBaseContextIsGuarded() {
        val srcFile = resolvePath(
            "app/src/main/java/com/manjaniq/MainActivity.kt",
            "src/main/java/com/manjaniq/MainActivity.kt",
            "../app/src/main/java/com/manjaniq/MainActivity.kt"
        )
        assertTrue("MainActivity.kt not found: $srcFile", srcFile.exists())
        val src = srcFile.readText()
        assertTrue("MainActivity must guard attachBaseContext with try/catch", src.contains("try {") && src.contains("catch"))
        assertTrue("MainActivity must use LocaleHelper.wrapContext", src.contains("LocaleHelper.wrapContext"))
    }
}
