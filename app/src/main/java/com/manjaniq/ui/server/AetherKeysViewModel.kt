package com.manjaniq.ui.server

import android.app.Application
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.manjaniq.R
import com.manjaniq.core.AetherCoreManager
import com.manjaniq.core.AetherExit
import com.manjaniq.core.AetherExitNode
import com.manjaniq.core.AetherFingerprint
import com.manjaniq.core.AetherIdentityManager
import com.manjaniq.core.AetherKey
import com.manjaniq.core.AetherKeyKind
import com.manjaniq.core.AetherKeys
import com.manjaniq.core.AetherKeysSettings
import com.manjaniq.core.ExitNodeOutbound
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

sealed interface AetherKeysNotice {
    data object Renewed : AetherKeysNotice
    data class Invalid(@StringRes val resId: Int, val args: List<String> = emptyList()) : AetherKeysNotice
}

class AetherKeysViewModel @JvmOverloads constructor(
    application: Application,
    private val source: AetherKeysSource = AetherKeysRepository(application),
) : AndroidViewModel(application) {

    var settings by mutableStateOf<AetherKeysSettings?>(null)
        private set

    private val _isCoreAvailable = MutableStateFlow(true)
    val isCoreAvailable: StateFlow<Boolean> = _isCoreAvailable.asStateFlow()

    private val _isRenewing = MutableStateFlow(false)
    val isRenewing: StateFlow<Boolean> = _isRenewing.asStateFlow()

    private val _session = MutableStateFlow<AetherSession?>(null)
    val session: StateFlow<AetherSession?> = _session.asStateFlow()

    private val _exitNodes = MutableStateFlow<List<AetherExitNode>>(emptyList())
    val exitNodes: StateFlow<List<AetherExitNode>> = _exitNodes.asStateFlow()

    private val _notice = MutableStateFlow<AetherKeysNotice?>(null)
    val notice: StateFlow<AetherKeysNotice?> = _notice.asStateFlow()

    private val _log = MutableStateFlow<List<AetherLogEntry>>(emptyList())
    val log: StateFlow<List<AetherLogEntry>> = _log.asStateFlow()

    private val nextLogId = AtomicLong(1L)
    private var activeJob: Job? = null
    private var cancelling = false
    private var lastReportedKeys: Map<String, AetherKey> = emptyMap()

    init {
        viewModelScope.launch {
            _isCoreAvailable.value = source.isCoreAvailable()
            _session.value = source.activeSession()
            _exitNodes.value = source.exitNodes()
            settings = source.loadSettings()
            reportKeys(source.keys())
        }
    }

    fun refreshSession() {
        viewModelScope.launch {
            _session.value = source.activeSession()
            _exitNodes.value = source.exitNodes()
        }
    }

    fun setKind(kind: AetherKeyKind) = updateSettings { copy(kind = kind) }

    fun setEnrollAddress(enrollAddress: String) = updateSettings { copy(enrollAddress = enrollAddress) }

    fun setApiSni(apiSni: String) = updateSettings { copy(apiSni = apiSni) }

    fun setApiHost(apiHost: String) = updateSettings { copy(apiHost = apiHost) }

    fun setUpstreamProxy(upstreamProxy: String) = updateSettings { copy(upstreamProxy = upstreamProxy) }

    fun setExitNode(exitNode: String) = updateSettings { copy(exitNode = exitNode) }

    fun setFragment(fragment: Boolean) = updateSettings { copy(fragment = fragment) }

    fun setFragmentSize(fragmentSize: String) = updateSettings { copy(fragmentSize = fragmentSize) }

    fun setFragmentDelay(fragmentDelay: String) = updateSettings { copy(fragmentDelay = fragmentDelay) }

    fun setEch(ech: Boolean) = updateSettings {
        val dns = if (ech) echDns.ifEmpty { AetherCoreManager.AETHER_ECH_DNS } else echDns
        val domain = if (ech) echDomain.ifEmpty { AetherCoreManager.AETHER_ECH_DOMAIN } else echDomain
        copy(ech = ech, echDns = dns, echDomain = domain)
    }

    fun setEchDns(echDns: String) = updateSettings { copy(echDns = echDns) }

    fun setEchDomain(echDomain: String) = updateSettings { copy(echDomain = echDomain) }

    fun setFingerprint(fingerprint: AetherFingerprint) = updateSettings { copy(fingerprint = fingerprint) }

    fun setFinalMask(finalMask: String) = updateSettings { copy(finalMask = finalMask) }

    fun setDialMode(dialMode: String) = updateSettings { copy(dialMode = dialMode) }

    fun setCommand(command: String) = updateSettings {
        val written = command.trim()
        val built = AetherKeys.builtCommand(this)
        copy(command = if (written.isEmpty() || written == built) "" else command)
    }

    fun useSettings() = updateSettings { copy(command = "") }

    private fun updateSettings(transform: AetherKeysSettings.() -> AetherKeysSettings) {
        if (activeJob?.isActive == true) return
        val current = settings ?: return
        val next = current.transform()
        if (next == current) return
        settings = next
        viewModelScope.launch {
            source.saveSettings(next)
        }
    }

    fun getKeys() {
        if (activeJob?.isActive == true) return
        val current = settings ?: return
        val problem = AetherKeys.validate(current)
        if (problem != null) {
            _notice.value = AetherKeysNotice.Invalid(messageOf(problem))
            return
        }
        val kind = AetherKeys.kindOf(current) ?: return
        val arguments = AetherKeys.arguments(current) ?: return
        val exit = AetherKeys.exitOf(current)
        cancelling = false
        activeJob = viewModelScope.launch {
            if (exit.node.isNotEmpty()) {
                when (val found = source.findExitNode(exit.node)) {
                    is ExitNodeOutbound.Built -> Unit
                    ExitNodeOutbound.NotFound, ExitNodeOutbound.SameName, ExitNodeOutbound.NoOutbound, ExitNodeOutbound.EchUnusable -> {
                        _notice.value = AetherKeysNotice.Invalid(found.message, listOf(exit.node))
                        return@launch
                    }
                }
            }
            val active = source.activeSession()
            _session.value = active
            if (active != null && AetherIdentityManager.sharesKeyWith(kind, active.protocol)) {
                appendResource(Log.WARN, R.string.aether_renew_blocked)
                return@launch
            }
            _isRenewing.value = true
            appendResource(Log.INFO, R.string.aether_log_key_renewing)
            try {
                val renewed = source.renew(kind, arguments, exit) { raw ->
                    val parsed = ServerAetherViewModel.parseCoreLine(raw) ?: return@renew
                    appendEntry(parsed.first, AetherLogText.Raw(parsed.second))
                }
                ensureActive()
                if (renewed != null) {
                    appendResource(Log.INFO, R.string.aether_log_key_renewed)
                    reportKeys(renewed)
                    _notice.value = AetherKeysNotice.Renewed
                } else if (!cancelling) {
                    appendResource(Log.ERROR, R.string.aether_log_key_renew_failed)
                }
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) {
                    reportKeys(source.keys())
                }
                throw cancelled
            } finally {
                cancelling = false
                _isRenewing.value = false
            }
        }
    }

    fun cancel() {
        val job = activeJob ?: return
        if (!job.isActive || cancelling) return
        cancelling = true
        _isRenewing.value = false
        job.cancel()
        activeJob = null
        cancelling = false
        appendResource(Log.WARN, R.string.aether_log_key_renew_cancelled)
    }

    override fun onCleared() {
        activeJob?.cancel()
        activeJob = null
        super.onCleared()
    }

    fun onNoticeShown() {
        _notice.value = null
    }

    private fun reportKeys(keys: List<AetherKey>) {
        keys.forEach { entry ->
            if (lastReportedKeys[entry.file] == entry) return@forEach
            lastReportedKeys = lastReportedKeys + (entry.file to entry)
            appendEntry(Log.INFO, keyLine(entry))
        }
    }

    private fun appendResource(priority: Int, @StringRes resId: Int, vararg args: String) {
        appendEntry(priority, AetherLogText.Resource(resId, args.toList()))
    }

    private fun appendEntry(priority: Int, text: AetherLogText) {
        val entry = AetherLogEntry(
            id = nextLogId.getAndIncrement(),
            priority = priority,
            text = text,
        )
        _log.update { current ->
            if (current.size >= ServerAetherViewModel.LOG_CAPACITY) {
                current.drop(current.size - ServerAetherViewModel.LOG_CAPACITY + 1) + entry
            } else {
                current + entry
            }
        }
    }

    companion object {
        @StringRes
        internal fun messageOf(problem: AetherKeys.Problem): Int = when (problem) {
            AetherKeys.Problem.INVALID_ENROLL_ADDRESS -> R.string.aether_keys_invalid_enroll_address
            AetherKeys.Problem.INVALID_API_SNI -> R.string.aether_keys_invalid_api_sni
            AetherKeys.Problem.INVALID_API_HOST -> R.string.aether_keys_invalid_api_host
            AetherKeys.Problem.INVALID_UPSTREAM -> R.string.aether_invalid_upstream
            AetherKeys.Problem.INVALID_FRAGMENT -> R.string.aether_invalid_fragment
            AetherKeys.Problem.INVALID_ECH_DNS -> R.string.aether_invalid_ech_dns
            AetherKeys.Problem.INVALID_ECH_DOMAIN -> R.string.aether_invalid_ech_domain
            AetherKeys.Problem.INVALID_FINAL_MASK -> R.string.aether_lab_exit_final_mask
            AetherKeys.Problem.INVALID_COMMAND -> R.string.aether_keys_invalid_command
        }

        internal fun keyLine(entry: AetherKey): AetherLogText = when (entry.file) {
            AetherIdentityManager.WIREGUARD_FILE -> ServerAetherViewModel.keyLine(
                entry.identity,
                R.string.aether_log_wireguard_key_ready,
                R.string.aether_log_wireguard_key_missing,
            )
            AetherIdentityManager.WIREGUARD_INNER_FILE -> ServerAetherViewModel.keyLine(
                entry.identity,
                R.string.aether_log_wireguard_inner_key_ready,
                R.string.aether_log_wireguard_inner_key_missing,
            )
            AetherIdentityManager.MASQUE_FILE -> ServerAetherViewModel.keyLine(
                entry.identity,
                R.string.aether_log_masque_key_ready,
                R.string.aether_log_masque_key_missing,
            )
            AetherIdentityManager.MASQUE_INNER_FILE -> ServerAetherViewModel.keyLine(
                entry.identity,
                R.string.aether_log_masque_inner_key_ready,
                R.string.aether_log_masque_inner_key_missing,
            )
            AetherIdentityManager.MASQUE_GOOL_FILE -> ServerAetherViewModel.keyLine(
                entry.identity,
                R.string.aether_log_gool_key_ready,
                R.string.aether_log_gool_key_missing,
            )
            else -> throw IllegalArgumentException("Unknown Aether key file: ${entry.file}")
        }
    }
}
