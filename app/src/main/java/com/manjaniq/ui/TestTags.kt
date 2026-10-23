package com.manjaniq.ui

/**
 * Stable semantics test tags for the real UI. They are plain identifiers
 * (no user-visible effect) so instrumentation tests can locate controls
 * without depending on translated wording. Tags never replace runtime
 * assertions: tests still verify what the tagged node does.
 */
object TestTags {
    const val TOP_BAR_MENU = "top_bar_menu"
    const val DRAWER = "app_drawer"
    const val DRAWER_ITEM_PREFIX = "drawer_item_"

    const val HOME_SCREEN = "screen_home"
    const val SETTINGS_SCREEN = "screen_settings"
    const val LOGS_SCREEN = "screen_logs"
    const val ABOUT_SCREEN = "screen_about"

    const val CORE_SELECTOR = "core_selector"
    const val CORE_CARD_RSTA = "core_card_rsta"
    const val CORE_CARD_AETHER = "core_card_aether"
    const val ENGINE_TOGGLE = "engine_toggle_button"
    const val AETHER_DISABLED_BUTTON = "aether_disabled_button"
    const val AETHER_TOGGLE = "aether_toggle_button"
    const val AETHER_SETTINGS = "aether_settings_form"
    const val AETHER_STATUS = "aether_status_card"
    const val CONNECTION_STATUS = "connection_status_card"

    const val LANGUAGE_EN = "language_en"
    const val LANGUAGE_FA = "language_fa"

    fun drawerItem(route: String): String = DRAWER_ITEM_PREFIX + route
}
