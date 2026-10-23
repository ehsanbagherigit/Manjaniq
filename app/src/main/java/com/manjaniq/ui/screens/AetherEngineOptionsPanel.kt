package com.manjaniq.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manjaniq.R
import com.manjaniq.core.models.AetherConfig
import com.manjaniq.core.models.AetherEngineOptions
import com.manjaniq.core.models.AetherPsiphonMode
import com.manjaniq.core.models.AetherStartMode
import com.manjaniq.core.models.AetherTorBridges
import com.manjaniq.core.models.AetherTorMode
import com.manjaniq.ui.AetherAccentSoft
import com.manjaniq.ui.AetherTestTags
import com.manjaniq.ui.components.AetherOption
import com.manjaniq.ui.components.AetherOptionGroup
import com.manjaniq.ui.components.AetherSection

/**
 * The remaining engine flags, edited where they are stored.
 *
 * Everything here maps onto one field of [AetherEngineOptions], which in turn is
 * `third_party/Aether/aether/src/cli.rs` rendered as data: each control writes
 * exactly one flag, and a value left blank emits nothing so the core keeps its
 * own default. Nothing is translated twice - `AetherConfig.toAetherArgs` is what
 * the service passes to `aether_core_start`.
 *
 * These flags only take effect on the full-engine start path, because the
 * granular FFI payloads have no field for them (`ffi.rs:215-260`). The section
 * says so instead of letting a saved value look applied, and every route shows
 * the same information through `AetherConfig.commandLineOnlyFeatures()`.
 *
 * Secrets (the Zero Trust client secret and access token) are ordinary text
 * fields here for exactly as long as they are being edited; they are never
 * logged, and `AetherLogSanitizer` redacts them at the logging boundary. They do
 * reach the app's own configuration store, which is private to the app - the
 * same place the identity key already lives.
 */
@Composable
fun AetherEngineOptionsSection(
    base: AetherConfig,
    onChange: (AetherConfig) -> Unit,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val engine = base.engine

    /** Applies one change to the engine block and hands back the whole config. */
    fun edit(block: (AetherEngineOptions) -> AetherEngineOptions) {
        onChange(base.copy(engine = block(engine)))
    }

    AetherSection(
        title = stringResource(R.string.aether_engine_section),
        summary = engineOptionsSummary(engine),
        expanded = expanded,
        onToggle = onToggle,
        tag = AetherTestTags.ENGINE_OPTIONS
    ) {
        Text(
            text = stringResource(R.string.aether_engine_section_desc),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )

        // ------------------------------------------------------------- Tor ---
        EngineGroupTitle(R.string.section_tor)
        EngineChoice(
            labelRes = R.string.aether_engine_tor_mode,
            options = TOR_MODES,
            selected = engine.tor.mode,
            tagPrefix = "aether_engine_tor_mode_",
            onSelect = { id -> edit { it.copy(tor = it.tor.copy(mode = id)) } }
        )
        EngineChoice(
            labelRes = R.string.aether_engine_tor_bridges,
            options = TOR_BRIDGES,
            selected = engine.tor.bridges,
            tagPrefix = "aether_engine_tor_bridges_",
            onSelect = { id -> edit { it.copy(tor = it.tor.copy(bridges = id)) } }
        )
        EngineText(
            labelRes = R.string.aether_engine_tor_bridge_lines,
            hintRes = R.string.aether_engine_tor_bridge_lines_hint,
            value = engine.tor.bridgeLines.joinToString("\n"),
            tag = "aether_engine_tor_lines",
            minLines = 3,
            onValueChange = { text ->
                edit {
                    it.copy(
                        tor = it.tor.copy(
                            bridgeLines = text.lines().map { line -> line.trim() }
                                .filter { line -> line.isNotEmpty() }
                        )
                    )
                }
            }
        )
        EngineText(
            labelRes = R.string.aether_engine_tor_relays,
            hintRes = R.string.aether_engine_tor_relays_hint,
            value = engine.tor.relays,
            tag = "aether_engine_tor_relays",
            onValueChange = { text -> edit { it.copy(tor = it.tor.copy(relays = text)) } }
        )
        EngineText(
            labelRes = R.string.aether_engine_tor_pt,
            hintRes = R.string.aether_engine_tor_pt_hint,
            value = engine.tor.pt.joinToString("\n"),
            tag = "aether_engine_tor_pt",
            onValueChange = { text ->
                edit {
                    it.copy(
                        tor = it.tor.copy(
                            pt = text.lines().map { line -> line.trim() }
                                .filter { line -> line.isNotEmpty() }
                        )
                    )
                }
            }
        )
        EngineText(
            labelRes = R.string.aether_engine_tor_dir,
            value = engine.tor.dir,
            tag = "aether_engine_tor_dir",
            onValueChange = { text -> edit { it.copy(tor = it.tor.copy(dir = text)) } }
        )

        // --------------------------------------------------------- Psiphon ---
        EngineGroupTitle(R.string.section_psiphon)
        EngineChoice(
            labelRes = R.string.aether_engine_psiphon_mode,
            options = PSIPHON_MODES,
            selected = engine.psiphon.mode,
            tagPrefix = "aether_engine_psiphon_mode_",
            onSelect = { id -> edit { it.copy(psiphon = it.psiphon.copy(mode = id)) } }
        )
        EngineText(
            labelRes = R.string.aether_engine_psiphon_region,
            hintRes = R.string.aether_engine_psiphon_region_hint,
            value = engine.psiphon.region,
            tag = "aether_engine_psiphon_region",
            onValueChange = { text -> edit { it.copy(psiphon = it.psiphon.copy(region = text)) } }
        )
        EngineText(
            labelRes = R.string.aether_engine_psiphon_cdn_mode,
            hintRes = R.string.aether_engine_psiphon_cdn_mode_hint,
            value = engine.psiphon.cdnMode,
            tag = "aether_engine_psiphon_cdn_mode",
            onValueChange = { text -> edit { it.copy(psiphon = it.psiphon.copy(cdnMode = text)) } }
        )
        EngineText(
            labelRes = R.string.aether_engine_psiphon_cdn_ips,
            hintRes = R.string.aether_engine_psiphon_cdn_ips_hint,
            value = engine.psiphon.cdnIps,
            tag = "aether_engine_psiphon_cdn_ips",
            onValueChange = { text -> edit { it.copy(psiphon = it.psiphon.copy(cdnIps = text)) } }
        )
        EngineText(
            labelRes = R.string.aether_engine_psiphon_cdn_sni,
            value = engine.psiphon.cdnSni,
            tag = "aether_engine_psiphon_cdn_sni",
            onValueChange = { text -> edit { it.copy(psiphon = it.psiphon.copy(cdnSni = text)) } }
        )

        // ------------------------------------------------------ Zero Trust ---
        EngineGroupTitle(R.string.aether_engine_zt_team)
        EngineText(
            labelRes = R.string.aether_engine_zt_team,
            value = engine.api.team,
            tag = "aether_engine_zt_team",
            onValueChange = { text -> edit { it.copy(api = it.api.copy(team = text)) } }
        )
        EngineText(
            labelRes = R.string.aether_engine_zt_access_id,
            value = engine.api.accessId,
            tag = "aether_engine_zt_access_id",
            onValueChange = { text -> edit { it.copy(api = it.api.copy(accessId = text)) } }
        )
        EngineText(
            labelRes = R.string.aether_engine_zt_access_secret,
            value = engine.api.accessSecret,
            tag = "aether_engine_zt_access_secret",
            onValueChange = { text -> edit { it.copy(api = it.api.copy(accessSecret = text)) } }
        )
        EngineText(
            labelRes = R.string.aether_engine_zt_access_email,
            hintRes = R.string.aether_engine_zt_access_email_hint,
            value = engine.api.accessEmail,
            tag = "aether_engine_zt_access_email",
            onValueChange = { text -> edit { it.copy(api = it.api.copy(accessEmail = text)) } }
        )
        EngineText(
            labelRes = R.string.aether_engine_zt_access_token,
            value = engine.api.accessToken,
            tag = "aether_engine_zt_access_token",
            onValueChange = { text -> edit { it.copy(api = it.api.copy(accessToken = text)) } }
        )
        EngineSwitch(
            titleRes = R.string.aether_engine_zt_gateway,
            descRes = R.string.aether_engine_zt_gateway_desc,
            checked = engine.gateway.enabled,
            tag = "aether_engine_zt_gateway",
            onCheckedChange = { on -> edit { it.copy(gateway = it.gateway.copy(enabled = on)) } }
        )

        // ---------------------------------------------------------- Routing ---
        EngineGroupTitle(R.string.aether_engine_route_block)
        EngineText(
            labelRes = R.string.aether_engine_route_block,
            hintRes = R.string.aether_engine_route_block_hint,
            value = engine.routing.block,
            tag = "aether_engine_route_block",
            minLines = 2,
            onValueChange = { text -> edit { it.copy(routing = it.routing.copy(block = text)) } }
        )
        EngineText(
            labelRes = R.string.aether_engine_route_direct,
            hintRes = R.string.aether_engine_route_block_hint,
            value = engine.routing.direct,
            tag = "aether_engine_route_direct",
            minLines = 2,
            onValueChange = { text -> edit { it.copy(routing = it.routing.copy(direct = text)) } }
        )
        EngineText(
            labelRes = R.string.aether_engine_route_file,
            hintRes = R.string.aether_engine_route_file_hint,
            value = engine.routing.routesFile,
            tag = "aether_engine_route_file",
            onValueChange = { text -> edit { it.copy(routing = it.routing.copy(routesFile = text)) } }
        )

        // -------------------------------------------------------- TLS / ECH ---
        EngineGroupTitle(R.string.aether_engine_tls_ciphers)
        EngineText(
            labelRes = R.string.aether_engine_tls_ciphers,
            value = engine.tls.ciphers,
            tag = "aether_engine_tls_ciphers",
            onValueChange = { text -> edit { it.copy(tls = it.tls.copy(ciphers = text)) } }
        )
        EngineText(
            labelRes = R.string.aether_engine_tls_groups,
            value = engine.tls.groups,
            tag = "aether_engine_tls_groups",
            onValueChange = { text -> edit { it.copy(tls = it.tls.copy(groups = text)) } }
        )
        EngineSwitch(
            titleRes = R.string.aether_engine_tls_grease,
            descRes = R.string.aether_engine_tls_grease_desc,
            checked = engine.tls.disableGrease,
            tag = "aether_engine_tls_grease",
            onCheckedChange = { on -> edit { it.copy(tls = it.tls.copy(disableGrease = on)) } }
        )
        EngineText(
            labelRes = R.string.aether_engine_ech_value,
            hintRes = R.string.aether_engine_ech_value_hint,
            value = engine.ech.value,
            tag = "aether_engine_ech_value",
            onValueChange = { text -> edit { it.copy(ech = it.ech.copy(value = text)) } }
        )
        EngineText(
            labelRes = R.string.aether_engine_ech_dns,
            hintRes = R.string.aether_engine_ech_dns_hint,
            value = engine.ech.dns,
            tag = "aether_engine_ech_dns",
            onValueChange = { text -> edit { it.copy(ech = it.ech.copy(dns = text)) } }
        )
        EngineText(
            labelRes = R.string.aether_engine_ech_domain,
            value = engine.ech.domain,
            tag = "aether_engine_ech_domain",
            onValueChange = { text -> edit { it.copy(ech = it.ech.copy(domain = text)) } }
        )

        // ----------------------------------------------- Reporting / routing ---
        EngineGroupTitle(R.string.aether_engine_misc)
        EngineText(
            labelRes = R.string.aether_engine_mark,
            hintRes = R.string.aether_engine_mark_hint,
            value = engine.timing.mark,
            tag = "aether_engine_mark",
            onValueChange = { text -> edit { it.copy(timing = it.timing.copy(mark = text)) } }
        )
        EngineText(
            labelRes = R.string.aether_engine_exit_loc,
            hintRes = R.string.aether_engine_exit_loc_hint,
            value = engine.exitLocation.filter,
            tag = "aether_engine_exit_loc",
            onValueChange = { text ->
                edit { it.copy(exitLocation = it.exitLocation.copy(filter = text)) }
            }
        )
        EngineSwitch(
            titleRes = R.string.aether_engine_stats,
            descRes = R.string.aether_engine_stats_desc,
            checked = engine.stats.enabled,
            tag = "aether_engine_stats",
            onCheckedChange = { on -> edit { it.copy(stats = it.stats.copy(enabled = on)) } }
        )
        EngineText(
            labelRes = R.string.aether_engine_stats_secs,
            value = engine.stats.intervalSecs,
            tag = "aether_engine_stats_secs",
            onValueChange = { text -> edit { it.copy(stats = it.stats.copy(intervalSecs = text)) } }
        )
        EngineText(
            labelRes = R.string.aether_engine_register,
            hintRes = R.string.aether_engine_register_hint,
            value = engine.api.register,
            tag = "aether_engine_register",
            onValueChange = { text -> edit { it.copy(api = it.api.copy(register = text)) } }
        )

        Text(
            text = stringResource(R.string.aether_engine_helper_note),
            color = AetherAccentSoft,
            fontSize = 11.sp
        )
    }
}

// ------------------------------------------------------------------ pieces ---

private val TOR_MODES: List<Pair<String, Int>> = listOf(
    AetherTorMode.OFF to R.string.aether_engine_off,
    AetherTorMode.CHAIN to R.string.aether_engine_chain,
    AetherTorMode.REVERSE to R.string.aether_engine_reverse,
    AetherTorMode.ONLY to R.string.aether_engine_only
)

private val TOR_BRIDGES: List<Pair<String, Int>> = listOf(
    AetherTorBridges.DEFAULT to R.string.aether_engine_bridges_default,
    AetherTorBridges.AUTO to R.string.aether_engine_bridges_auto,
    AetherTorBridges.OFF to R.string.aether_engine_bridges_off,
    AetherTorBridges.CUSTOM to R.string.aether_engine_bridges_custom
)

private val PSIPHON_MODES: List<Pair<String, Int>> = listOf(
    AetherPsiphonMode.OFF to R.string.aether_engine_off,
    AetherPsiphonMode.CHAIN to R.string.aether_engine_chain,
    AetherPsiphonMode.REVERSE to R.string.aether_engine_reverse,
    AetherPsiphonMode.ONLY to R.string.aether_engine_only
)

@Composable
private fun EngineGroupTitle(labelRes: Int) {
    Text(
        text = stringResource(labelRes).uppercase(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.1.sp
    )
}

@Composable
private fun EngineChoice(
    labelRes: Int,
    options: List<Pair<String, Int>>,
    selected: String,
    tagPrefix: String,
    onSelect: (String) -> Unit
) {
    Text(
        text = stringResource(labelRes),
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurface
    )
    AetherOptionGroup(
        options = options.map { (id, label) ->
            AetherOption(id = id, label = stringResource(label))
        },
        selectedId = selected,
        onSelect = onSelect,
        tagPrefix = tagPrefix
    )
}

@Composable
private fun EngineText(
    labelRes: Int,
    value: String,
    tag: String,
    onValueChange: (String) -> Unit,
    hintRes: Int? = null,
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = stringResource(labelRes), fontSize = 12.sp) },
        singleLine = minLines == 1,
        minLines = minLines,
        supportingText = {
            if (hintRes != null) {
                Text(text = stringResource(hintRes), fontSize = 10.sp)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
    )
}

@Composable
private fun EngineSwitch(
    titleRes: Int,
    descRes: Int,
    checked: Boolean,
    tag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag)
        )
        Column(
            modifier = Modifier
                .padding(start = 8.dp)
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = stringResource(titleRes),
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(descRes),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
    }
}

/**
 * One line for the collapsed header: which engine families this route actually
 * asks for. Values are not repeated here - a secret must never be summarised
 * into a card.
 */
@Composable
private fun engineOptionsSummary(engine: AetherEngineOptions): String {
    val parts = mutableListOf<String>()
    if (engine.tor.isEnabled()) parts += "Tor ${engine.tor.mode}"
    if (engine.psiphon.isEnabled()) parts += "Psiphon ${engine.psiphon.mode}"
    if (engine.api.team.isNotBlank()) parts += "Zero Trust"
    if (engine.gateway.enabled) parts += "gateway"
    if (engine.routing.block.isNotBlank() || engine.routing.direct.isNotBlank() ||
        engine.routing.routesFile.isNotBlank()
    ) {
        parts += "routing"
    }
    if (engine.tls.ciphers.isNotBlank() || engine.tls.groups.isNotBlank() ||
        engine.tls.disableGrease
    ) {
        parts += "TLS"
    }
    if (engine.ech.value.isNotBlank()) parts += "ECH"
    if (engine.stats.enabled) parts += "stats"
    return if (parts.isEmpty()) {
        stringResource(R.string.aether_section_summary_default)
    } else {
        parts.joinToString(" · ")
    }
}

/** True when [base] runs the core's command line, i.e. when the flags above apply. */
fun engineOptionsApply(base: AetherConfig): Boolean =
    AetherStartMode.isFullEngine(base.startMode)
