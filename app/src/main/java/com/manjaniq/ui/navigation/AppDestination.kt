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
        /**
         * Must be lazy. An eager `listOf(Home, …)` here runs during the
         * base class's static initialisation, which is triggered by the
         * first access to any subclass object (e.g. `AppDestination.Home`
         * in ManjaniqApp). At that moment `Home.INSTANCE` is still null,
         * so the list silently contained a null and the drawer crashed
         * with a NullPointerException on first composition.
         */
        val drawerItems: List<AppDestination> by lazy { listOf(Home, Settings, Logs, About) }
    }
}
