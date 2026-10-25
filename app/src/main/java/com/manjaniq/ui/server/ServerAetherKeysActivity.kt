package com.manjaniq.ui.server

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.manjaniq.R
import com.manjaniq.core.AetherCoreManager
import com.manjaniq.core.AetherFingerprint
import com.manjaniq.core.AetherIdentityManager
import com.manjaniq.core.AetherKeyKind
import com.manjaniq.core.AetherKeys
import com.manjaniq.core.AetherKeysSettings
import com.manjaniq.ui.theme.ManjaniqTheme

class ServerAetherKeysActivity : ComponentActivity() {

    private val viewModel: AetherKeysViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ManjaniqTheme {
                AetherKeysScreen(
                    viewModel = viewModel,
                    onBack = ::finish,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AetherKeysScreen(
    viewModel: AetherKeysViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val settings = viewModel.settings
    val isCoreAvailable by viewModel.isCoreAvailable.collectAsState()
    val isRenewing by viewModel.isRenewing.collectAsState()
    val session by viewModel.session.collectAsState()
    val notice by viewModel.notice.collectAsState()
    val log by viewModel.log.collectAsState()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshSession()
    }

    LaunchedEffect(notice) {
        when (val current = notice) {
            AetherKeysNotice.Renewed -> {
                Toast.makeText(context, R.string.aether_log_key_renewed, Toast.LENGTH_SHORT).show()
                viewModel.onNoticeShown()
            }
            is AetherKeysNotice.Invalid -> {
                val text = if (current.args.isEmpty()) {
                    context.getString(current.resId)
                } else {
                    context.getString(current.resId, *current.args.toTypedArray())
                }
                Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                viewModel.onNoticeShown()
            }
            null -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.aether_keys_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.close),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (settings != null) {
            AetherKeysContent(
                settings = settings,
                isCoreAvailable = isCoreAvailable,
                isRenewing = isRenewing,
                session = session,
                log = log,
                onKindChange = viewModel::setKind,
                onEnrollAddressChange = viewModel::setEnrollAddress,
                onApiSniChange = viewModel::setApiSni,
                onApiHostChange = viewModel::setApiHost,
                onUpstreamProxyChange = viewModel::setUpstreamProxy,
                onFragmentChange = viewModel::setFragment,
                onFragmentSizeChange = viewModel::setFragmentSize,
                onFragmentDelayChange = viewModel::setFragmentDelay,
                onEchChange = viewModel::setEch,
                onEchDnsChange = viewModel::setEchDns,
                onEchDomainChange = viewModel::setEchDomain,
                onFingerprintChange = viewModel::setFingerprint,
                onCommandChange = viewModel::setCommand,
                onUseSettings = viewModel::useSettings,
                onGetKeys = viewModel::getKeys,
                onCancel = viewModel::cancel,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding(),
            )
        }
    }
}

@Composable
private fun AetherKeysContent(
    settings: AetherKeysSettings,
    isCoreAvailable: Boolean,
    isRenewing: Boolean,
    session: AetherSession?,
    log: List<AetherLogEntry>,
    onKindChange: (AetherKeyKind) -> Unit,
    onEnrollAddressChange: (String) -> Unit,
    onApiSniChange: (String) -> Unit,
    onApiHostChange: (String) -> Unit,
    onUpstreamProxyChange: (String) -> Unit,
    onFragmentChange: (Boolean) -> Unit,
    onFragmentSizeChange: (String) -> Unit,
    onFragmentDelayChange: (String) -> Unit,
    onEchChange: (Boolean) -> Unit,
    onEchDnsChange: (String) -> Unit,
    onEchDomainChange: (String) -> Unit,
    onFingerprintChange: (AetherFingerprint) -> Unit,
    onCommandChange: (String) -> Unit,
    onUseSettings: () -> Unit,
    onGetKeys: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val protocolValues = stringArrayResource(R.array.aether_protocol_values).toList()
    val protocolEntries = stringArrayResource(R.array.aether_protocol_entries).toList()
    val kindValues = remember { AetherKeyKind.entries.map { it.type } }
    val allLabel = stringResource(R.string.aether_keys_kind_all)
    val kindEntries = remember(protocolValues, protocolEntries, allLabel) {
        val byValue = protocolValues.zip(protocolEntries).toMap()
        AetherKeyKind.entries.map { kind ->
            if (kind == AetherKeyKind.ALL) allLabel else byValue[kind.type] ?: kind.type
        }
    }

    val fingerprintEntries = stringArrayResource(R.array.aether_fingerprint_entries).toList()
    val fingerprintValues = stringArrayResource(R.array.aether_fingerprint_values).toList()
    val apiNameOptions = stringArrayResource(R.array.aether_api_name_options).toList()
    val upstreamOptions = stringArrayResource(R.array.aether_upstream_options).toList()
    val echDnsOptions = stringArrayResource(R.array.aether_ech_dns_options).toList()
    val echDomainOptions = stringArrayResource(R.array.aether_ech_domain_options).toList()

    val isCustomCommand = remember(settings) { AetherKeys.isCustom(settings) }
    val displayedCommand = remember(settings) {
        if (isCustomCommand) settings.command else AetherKeys.builtCommand(settings)
    }
    val effectiveKind = remember(settings) { AetherKeys.kindOf(settings) }
    val sessionSharesKey = session != null &&
        (effectiveKind == null || AetherIdentityManager.sharesKeyWith(effectiveKind, session.protocol))
    val fieldsEnabled = !isRenewing && !isCustomCommand

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (!isCoreAvailable) {
            Text(
                text = stringResource(R.string.aether_unsupported_abi),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            )
        }

        FormDropdownField(
            label = stringResource(R.string.aether_keys_lab_kind),
            selectedValue = settings.kind.type,
            options = kindValues,
            optionLabels = kindEntries,
            enabled = fieldsEnabled,
            onValueChange = { onKindChange(AetherKeyKind.fromString(it)) },
        )

        FormTextField(
            label = stringResource(R.string.aether_keys_lab_enroll_address),
            value = settings.enrollAddress,
            onValueChange = onEnrollAddressChange,
            placeholder = AetherKeys.DEFAULT_ENROLL_ADDRESS,
            supportingText = stringResource(R.string.aether_keys_hint_enroll_address),
            keyboardType = KeyboardType.Uri,
            enabled = fieldsEnabled,
        )

        FormComboField(
            label = stringResource(R.string.aether_keys_lab_api_sni),
            value = settings.apiSni,
            options = apiNameOptions,
            onValueChange = onApiSniChange,
            supportingText = stringResource(R.string.aether_keys_hint_api_sni),
            enabled = fieldsEnabled,
        )

        FormComboField(
            label = stringResource(R.string.aether_keys_lab_api_host),
            value = settings.apiHost,
            options = apiNameOptions,
            onValueChange = onApiHostChange,
            supportingText = stringResource(R.string.aether_keys_hint_api_host),
            enabled = fieldsEnabled,
        )

        FormComboField(
            label = stringResource(R.string.aether_lab_upstream),
            value = settings.upstreamProxy,
            options = upstreamOptions,
            onValueChange = onUpstreamProxyChange,
            supportingText = stringResource(R.string.aether_hint_upstream),
            enabled = fieldsEnabled,
        )

        FormDropdownField(
            label = stringResource(R.string.aether_lab_fingerprint),
            selectedValue = settings.fingerprint.type,
            options = fingerprintValues,
            optionLabels = fingerprintEntries,
            enabled = fieldsEnabled,
            onValueChange = { onFingerprintChange(AetherFingerprint.fromString(it)) },
        )

        SettingsSwitch(
            title = stringResource(R.string.aether_lab_fragment),
            summary = stringResource(R.string.aether_keys_hint_fragment),
            checked = settings.fragment,
            enabled = fieldsEnabled,
            onCheckedChange = onFragmentChange,
        )

        if (settings.fragment) {
            FormTextField(
                label = stringResource(R.string.aether_lab_fragment_size),
                value = settings.fragmentSize,
                onValueChange = onFragmentSizeChange,
                supportingText = stringResource(R.string.aether_hint_fragment_size),
                keyboardType = KeyboardType.Ascii,
                enabled = fieldsEnabled,
            )

            FormTextField(
                label = stringResource(R.string.aether_lab_fragment_delay),
                value = settings.fragmentDelay,
                onValueChange = onFragmentDelayChange,
                supportingText = stringResource(R.string.aether_hint_fragment_delay),
                keyboardType = KeyboardType.Ascii,
                enabled = fieldsEnabled,
            )
        }

        SettingsSwitch(
            title = stringResource(R.string.aether_lab_ech),
            summary = stringResource(R.string.aether_keys_hint_ech),
            checked = settings.ech,
            enabled = fieldsEnabled,
            onCheckedChange = onEchChange,
        )

        if (settings.ech) {
            FormComboField(
                label = stringResource(R.string.aether_lab_ech_dns),
                value = settings.echDns.ifEmpty { AetherCoreManager.AETHER_ECH_DNS },
                options = echDnsOptions,
                onValueChange = onEchDnsChange,
                enabled = fieldsEnabled,
            )

            FormComboField(
                label = stringResource(R.string.aether_lab_ech_domain),
                value = settings.echDomain.ifEmpty { AetherCoreManager.AETHER_ECH_DOMAIN },
                options = echDomainOptions,
                onValueChange = onEchDomainChange,
                imeAction = ImeAction.Done,
                enabled = fieldsEnabled,
            )
        }

        SectionDivider()
        SectionHeader(title = stringResource(R.string.aether_lab_other_settings))

        FormTextField(
            label = stringResource(R.string.aether_lab_command),
            value = displayedCommand,
            onValueChange = onCommandChange,
            singleLine = false,
            minLines = 2,
            maxLines = 6,
            keyboardType = KeyboardType.Ascii,
            fontFamily = FontFamily.Monospace,
            supportingText = if (isCustomCommand) stringResource(R.string.aether_command_custom) else null,
            enabled = !isRenewing,
        )

        if (isCustomCommand) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onUseSettings,
                    enabled = !isRenewing,
                ) {
                    Text(stringResource(R.string.aether_action_use_settings))
                }
            }
        }

        if (sessionSharesKey) {
            Text(
                text = stringResource(R.string.aether_renew_blocked),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(
                onClick = onGetKeys,
                enabled = isCoreAvailable && !isRenewing && !sessionSharesKey,
                modifier = Modifier.weight(1f),
            ) {
                if (isRenewing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.aether_action_renewing_key))
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.aether_action_get_keys))
                }
            }

            if (isRenewing) {
                OutlinedButton(onClick = onCancel) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        AetherLogPanel(
            entries = log,
            emptyResId = R.string.aether_keys_log_empty,
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}
