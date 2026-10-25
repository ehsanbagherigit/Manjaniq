package com.manjaniq.core

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap

object AetherSettingsStorage {

    private const val PREFS_NAME = "manjaniq_aether_settings"
    private const val KEY_LISTEN_PORT = "aether_listen_port"
    private const val KEY_SELECTED_SERVER = "aether_selected_server"
    private const val KEY_ACTIVE_PROFILE_GUID = "default_aether"
    private const val KEY_PSIPHON_REGIONS = "aether_psiphon_regions"
    private const val SERVER_PREFIX = "aether_server_"

    @Volatile
    private var prefs: SharedPreferences? = null

    private val memoryStrings = ConcurrentHashMap<String, String>()
    private val memoryProfiles = ConcurrentHashMap<String, ProfileItem>()

    fun init(context: Context) {
        if (prefs == null) {
            synchronized(this) {
                if (prefs == null) {
                    prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                }
            }
        }
    }

    fun getActiveProfile(context: Context? = null): ProfileItem {
        context?.let(::init)
        val guid = getSelectedServer() ?: KEY_ACTIVE_PROFILE_GUID
        return decodeServerConfig(guid) ?: ProfileItem.create().also {
            encodeServerConfig(guid, it)
            setSelectedServer(guid)
        }
    }

    fun saveActiveProfile(context: Context? = null, profile: ProfileItem): String {
        context?.let(::init)
        val guid = getSelectedServer() ?: KEY_ACTIVE_PROFILE_GUID
        encodeServerConfig(guid, profile)
        setSelectedServer(guid)
        return guid
    }

    fun getListenPort(): String? = decodeString(KEY_LISTEN_PORT)

    fun setListenPort(port: String?) {
        encodeString(KEY_LISTEN_PORT, port)
    }

    fun getSelectedServer(): String? = decodeString(KEY_SELECTED_SERVER)

    fun setSelectedServer(guid: String?) {
        encodeString(KEY_SELECTED_SERVER, guid)
    }

    fun getPsiphonRegions(): String = decodeString(KEY_PSIPHON_REGIONS).orEmpty()

    fun putPsiphonRegions(regions: String) {
        encodeString(KEY_PSIPHON_REGIONS, regions)
    }

    fun decodeServerConfig(guid: String): ProfileItem? {
        memoryProfiles[guid]?.let { return it.copy() }
        val raw = prefs?.getString(SERVER_PREFIX + guid, null) ?: return null
        return ProfileItem.fromJsonString(raw)?.also { memoryProfiles[guid] = it }?.copy()
    }

    fun encodeServerConfig(guid: String, profile: ProfileItem) {
        memoryProfiles[guid] = profile.copy()
        prefs?.edit()?.putString(SERVER_PREFIX + guid, profile.toJsonString())?.apply()
    }

    fun removeServer(guid: String) {
        memoryProfiles.remove(guid)
        prefs?.edit()?.remove(SERVER_PREFIX + guid)?.apply()
    }

    fun decodeString(key: String, defaultValue: String? = null): String? {
        memoryStrings[key]?.let { return it }
        val p = prefs ?: return defaultValue
        return p.getString(key, defaultValue)
    }

    fun encodeString(key: String, value: String?) {
        if (value == null) {
            memoryStrings.remove(key)
            prefs?.edit()?.remove(key)?.apply()
        } else {
            memoryStrings[key] = value
            prefs?.edit()?.putString(key, value)?.apply()
        }
    }

    fun clearInMemoryForTest() {
        memoryStrings.clear()
        memoryProfiles.clear()
    }
}
