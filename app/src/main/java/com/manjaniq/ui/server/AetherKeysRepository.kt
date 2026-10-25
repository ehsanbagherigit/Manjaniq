package com.manjaniq.ui.server

import android.content.Context
import com.manjaniq.core.AetherCoreManager
import com.manjaniq.core.AetherExit
import com.manjaniq.core.AetherExitNode
import com.manjaniq.core.AetherIdentityManager
import com.manjaniq.core.AetherKey
import com.manjaniq.core.AetherKeyKind
import com.manjaniq.core.AetherKeys
import com.manjaniq.core.AetherKeysSettings
import com.manjaniq.core.ExitNodeOutbound
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface AetherKeysSource {
    suspend fun isCoreAvailable(): Boolean
    suspend fun activeSession(): AetherSession?
    suspend fun loadSettings(): AetherKeysSettings
    suspend fun saveSettings(settings: AetherKeysSettings)
    suspend fun keys(): List<AetherKey>
    suspend fun renew(
        kind: AetherKeyKind,
        arguments: List<String>,
        exit: AetherExit = AetherExit.PLAIN,
        onOutput: (String) -> Unit,
    ): List<AetherKey>?
    suspend fun exitNodes(): List<AetherExitNode>
    suspend fun findExitNode(name: String): ExitNodeOutbound
}

class AetherKeysRepository(context: Context) : AetherKeysSource {

    private val appContext = context.applicationContext

    override suspend fun isCoreAvailable(): Boolean = withContext(Dispatchers.IO) {
        AetherCoreManager.hasBinary(appContext)
    }

    override suspend fun activeSession(): AetherSession? = withContext(Dispatchers.IO) {
        AetherEditorRepository.activeSession(appContext)
    }

    override suspend fun loadSettings(): AetherKeysSettings = withContext(Dispatchers.IO) {
        AetherKeys.load()
    }

    override suspend fun saveSettings(settings: AetherKeysSettings) = withContext(Dispatchers.IO) {
        AetherKeys.save(settings)
    }

    override suspend fun keys(): List<AetherKey> = withContext(Dispatchers.IO) {
        AetherIdentityManager.keys(appContext)
    }

    override suspend fun renew(
        kind: AetherKeyKind,
        arguments: List<String>,
        exit: AetherExit,
        onOutput: (String) -> Unit,
    ): List<AetherKey>? =
        AetherIdentityManager.renew(appContext, kind, arguments, exit, onOutput = onOutput)

    override suspend fun exitNodes(): List<AetherExitNode> = withContext(Dispatchers.IO) {
        emptyList()
    }

    override suspend fun findExitNode(name: String): ExitNodeOutbound = withContext(Dispatchers.IO) {
        ExitNodeOutbound.NotFound
    }
}
