package com.manjaniq.ui.navigation

import androidx.annotation.StringRes
import com.manjaniq.R

sealed class AppDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: String
) {
    object Home : AppDestination("home", R.string.nav_home, "home")
    object Settings : AppDestination("settings", R.string.nav_settings, "settings")
    object Logs : AppDestination("logs", R.string.nav_logs, "logs")
    object About : AppDestination("about", R.string.nav_about, "about")

    companion object {
        val drawerItems = listOf(Home, Settings, Logs, About)
    }
}
