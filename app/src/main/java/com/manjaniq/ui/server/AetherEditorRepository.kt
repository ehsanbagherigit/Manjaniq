package com.manjaniq.ui.server

import android.content.Context
import android.util.Log
import com.manjaniq.core.AetherCore
import com.manjaniq.core.AetherCoreManager
import com.manjaniq.core.AetherExitNode
import com.manjaniq.core.AetherIdentityManager
import com.manjaniq.core.AetherIdentityStatus
import com.manjaniq.core.AetherKeyKind
import com.manjaniq.core.AetherProtocol
import com.manjaniq.core.AetherPsiphon
import com.manjaniq.core.AetherScanResult
import com.manjaniq.core.AetherScanner
import com.manjaniq.core.AetherSettingsStorage
import com.manjaniq.core.AetherTor
import com.manjaniq.core.ExitNodeOutbound
import com.manjaniq.core.ProfileItem
import com.manjaniq.core.PsiphonServerList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AetherSession(val protocol: AetherProtocol?, val usesWarp: Boolean = true) {

    fun disturbedByScanOf(protocol: AetherProtocol): Boolean =
        this.protocol == null || AetherIdentityManager.sharesIdentity(protocol, this.protocol)

    fun usesKeysOf(kind: AetherKeyKind): Boolean =
        protocol == null || AetherIdentityManager.filesOf(protocol).any { it in AetherIdentityManager.filesOf(kind) }

    fun sharesKeyWith(other: AetherProtocol): Boolean {
        if (!usesWarp) return false
        val current = protocol ?: return true
        return current == other || current.core == other.core ||
            current == AetherProtocol.WG_OVER_MASQUE || other == AetherProtocol.WG_OVER_MASQUE
    }
}

interface AetherEditorSource {
    suspend fun isCoreAvailable(): Boolean
    suspend fun isPsiphonAvailable(): Boolean
    suspend fun isTorTransportsAvailable(): Boolean
    suspend fun activeSession(): AetherSession?
    suspend fun scan(profile: ProfileItem, onOutput: (String) -> Unit): AetherScanResult?
    suspend fun identityStatus(protocol: AetherProtocol): AetherIdentityStatus
    suspend fun missingKeys(files: List<String>): List<String>
    suspend fun clearPsiphonData(): Boolean
    suspend fun psiphonRegions(): List<String>
    suspend fun listenPort(): Int
    suspend fun takenPorts(): Set<Int>
    suspend fun exitNodes(): List<AetherExitNode>
    suspend fun findExitNode(name: String): ExitNodeOutbound
}

class AetherEditorRepository(context: Context) : AetherEditorSource {

    private val appContext = context.applicationContext

    override suspend fun isCoreAvailable(): Boolean = withContext(Dispatchers.IO) {
        AetherCoreManager.hasBinary(appContext)
    }

    override suspend fun isPsiphonAvailable(): Boolean = withContext(Dispatchers.IO) {
        AetherCoreManager.hasPsiphon(appContext)
    }

    override suspend fun isTorTransportsAvailable(): Boolean = withContext(Dispatchers.IO) {
        AetherCoreManager.hasTorTransports(appContext)
    }

    override suspend fun activeSession(): AetherSession? = withContext(Dispatchers.IO) {
        activeSession(appContext)
    }

    override suspend fun scan(
        profile: ProfileItem,
        onOutput: (String) -> Unit,
    ): AetherScanResult? =
        AetherScanner.scan(appContext, profile, onOutput = onOutput)

    override suspend fun identityStatus(protocol: AetherProtocol): AetherIdentityStatus = withContext(Dispatchers.IO) {
        AetherIdentityManager.status(appContext, protocol)
    }

    override suspend fun missingKeys(files: List<String>): List<String> = withContext(Dispatchers.IO) {
        AetherIdentityManager.missing(appContext, files)
    }

    override suspend fun clearPsiphonData(): Boolean = withContext(Dispatchers.IO) {
        AetherCoreManager.clearPsiphonData(appContext)
    }

    override suspend fun psiphonRegions(): List<String> = withContext(Dispatchers.IO) {
        val entries = PsiphonServerList.entriesFile(appContext.filesDir, AetherIdentityManager.workDir(appContext)) { problem ->
            Log.w("AetherEditor", "${PsiphonServerList.PSIPHON_SERVERS_DAT} is not a usable Psiphon list", problem)
        }
        val listed = entries?.let { file ->
            runCatching { PsiphonServerList.regions(file.readText()) }.getOrDefault(emptySet())
        }.orEmpty()
        (listed + PsiphonServerList.remembered()).toSortedSet().toList()
    }

    override suspend fun listenPort(): Int = withContext(Dispatchers.IO) {
        AetherCoreManager.socksPort
    }

    override suspend fun takenPorts(): Set<Int> = withContext(Dispatchers.IO) {
        emptySet()
    }

    override suspend fun exitNodes(): List<AetherExitNode> = withContext(Dispatchers.IO) {
        emptyList()
    }

    override suspend fun findExitNode(name: String): ExitNodeOutbound = withContext(Dispatchers.IO) {
        ExitNodeOutbound.NotFound
    }

    companion object {
        internal fun sessionOf(protocol: AetherProtocol?, processesListed: Boolean, listenerAnswers: () -> Boolean): AetherSession? =
            protocol?.let(::AetherSession) ?: AetherSession(protocol = null).takeIf { !processesListed && listenerAnswers() }

        internal fun activeSession(context: Context): AetherSession? {
            val process = AetherCoreManager.sessionProcess(context)
            if (process != null) {
                val arguments = process.argv.drop(1)
                if (AetherCoreManager.torModeOf(arguments) == AetherTor.ONLY ||
                    AetherCoreManager.psiphonModeOf(arguments) == AetherPsiphon.ONLY
                ) {
                    return AetherSession(protocol = null, usesWarp = false)
                }
                return AetherSession(AetherCoreManager.protocolOf(arguments))
            }
            if (AetherCoreManager.canListProcesses()) return null
            val profile = AetherSettingsStorage.getSelectedServer()?.let(AetherSettingsStorage::decodeServerConfig)
            if (profile != null) {
                val core = AetherCore.of(profile)
                if (!AetherCoreManager.answersSocks(core.port)) return null
                if (AetherCoreManager.torModeOf(core.arguments) == AetherTor.ONLY ||
                    AetherCoreManager.psiphonModeOf(core.arguments) == AetherPsiphon.ONLY
                ) {
                    return AetherSession(protocol = null, usesWarp = false)
                }
                return AetherSession(AetherCoreManager.protocolOf(core.arguments))
            }
            return if (AetherCoreManager.answersSocks(AetherCoreManager.socksPort)) {
                AetherSession(protocol = null)
            } else {
                null
            }
        }
    }
}
