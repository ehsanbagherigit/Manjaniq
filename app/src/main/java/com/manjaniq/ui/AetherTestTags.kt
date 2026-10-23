package com.manjaniq.ui

/**
 * Stable test tags for the Aether UI only.
 *
 * They live beside [TestTags] rather than inside it because `TestTags` is shared
 * with the RSTA screens: adding Aether tags there would mean editing a file the
 * rest of the application owns. Tags are plain identifiers with no user-visible
 * effect, and they never replace a runtime assertion.
 *
 * Tags that already existed inline in the Aether UI (`profile_card_<id>`,
 * `profile_settings_<id>`, `aether_profiles_title`) keep their exact strings.
 */
object AetherTestTags {

    /** Root of the Aether region of the home screen. */
    const val PANEL = "aether_panel"

    /** Progressive step 1: the list of Aether routes. */
    const val ROUTE_LIST = "aether_route_list"
    const val ROUTE_TITLE = "aether_profiles_title"

    /** Progressive step 2: the settings of the selected route. */
    fun routeCard(profileId: String) = "profile_card_$profileId"
    fun routeSettings(profileId: String) = "profile_settings_$profileId"
    fun routeToggle(profileId: String) = "profile_toggle_$profileId"

    /** A collapsible settings group inside the selected route. */
    fun section(key: String) = "aether_section_$key"
    fun sectionToggle(key: String) = "aether_section_${key}_toggle"

    /** Individual choices. */
    fun transportChip(id: String) = "aether_transport_$id"
    fun scanChip(id: String) = "aether_scan_$id"
    fun ipChip(id: String) = "aether_ip_$id"
    fun noizeChip(id: String) = "aether_noize_$id"
    const val FRAGMENT_SECTION = "aether_fragment_section"
    const val FRAGMENT_SECTION_TOGGLE = "aether_fragment_section_toggle"
    const val FRAGMENT_TOGGLE = "aether_fragment_enabled"
    const val FRAGMENT_SIZE = "aether_fragment_size"
    const val FRAGMENT_DELAY = "aether_fragment_delay"
    const val FRAGMENT_SNI = "aether_fragment_sni"

    /** The switch inside the fragmenting row: clickable, unlike the row around it. */
    const val FRAGMENT_TOGGLE_SWITCH = "aether_fragment_enabled_switch"
    const val FRAGMENT_SNI_SWITCH = "aether_fragment_sni_switch"

    /** Saves the fragmenting values with the route. */
    const val FRAGMENT_SAVE = "aether_fragment_save"

    /** Shown instead of controls when the transport has no HTTP/2 carrier. */
    const val FRAGMENT_UNAVAILABLE = "aether_fragment_unavailable"

    /** The carrier consequence, shown while fragmenting is on. */
    const val FRAGMENT_CARRIER_NOTE = "aether_fragment_carrier_note"

    /** Save / reset / export / import of the selected route. */
    const val SAVE = "aether_save_settings"
    const val RESET = "aether_reset_settings"

    /** Operational log panel. */
    const val LOG_PANEL = "aether_log_panel"
    const val LOG_TOGGLE = "aether_log_toggle"
    const val LOG_BODY = "aether_log_body"
    const val LOG_EMPTY = "aether_log_empty"
    const val LOG_COUNT = "aether_log_count"
    const val LOG_FOLLOW = "aether_log_follow"
    const val LOG_COPY = "aether_log_copy"
    const val LOG_CLEAR = "aether_log_clear"
    const val LOG_FILTER = "aether_log_filter"
    const val LOG_FILTER_PROBLEMS = "aether_log_filter_problems"
    fun logLine(seq: Long) = "aether_log_line_$seq"
}
