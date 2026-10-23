package com.manjaniq.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manjaniq.R
import com.manjaniq.core.models.AetherReach
import com.manjaniq.ui.AetherAccent
import com.manjaniq.ui.AetherAccentDeep
import com.manjaniq.ui.AetherAccentSoft
import com.manjaniq.ui.theme.ManjaniqBorder

/**
 * Building blocks of the progressive Aether flow.
 *
 * They are Aether-specific on purpose: the Aether screens keep their own
 * components instead of reaching into the shared ones, so nothing here can
 * change how another part of the application looks or behaves.
 */

/** One selectable choice in an Aether option group. */
data class AetherOption(
    val id: String,
    val label: String,
    val detail: String = "",
    val enabled: Boolean = true,
    /** Why the choice cannot be made here, shown instead of the detail line. */
    val note: String? = null
)

/**
 * A collapsible settings group.
 *
 * Collapsed it is one line: the group name plus what is currently chosen, so the
 * whole route stays scannable. Expanding it reveals only that group's settings.
 */
@Composable
fun AetherSection(
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(tag),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (expanded) AetherAccent.copy(alpha = 0.5f) else ManjaniqBorder)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Card(
                onClick = onToggle,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("${tag}_toggle"),
                shape = RoundedCornerShape(0.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title.uppercase(),
                            color = if (expanded) AetherAccentSoft else MaterialTheme.colorScheme.onSurface,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.1.sp
                        )
                        if (summary.isNotEmpty()) {
                            Text(
                                text = summary,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                maxLines = 2
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (expanded) "▲" else "▼",
                        color = AetherAccent,
                        fontSize = 10.sp
                    )
                }
            }
            if (expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    content()
                }
            }
        }
    }
}

/**
 * A list of mutually exclusive choices, drawn the way the Aether CLI lists them:
 * one line each, the reason beside the name.
 */
@Composable
fun AetherOptionGroup(
    options: List<AetherOption>,
    selectedId: String,
    onSelect: (String) -> Unit,
    tagPrefix: String,
    modifier: Modifier = Modifier,
    help: String? = null
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { option ->
            AetherOptionRow(
                option = option,
                selected = option.id == selectedId,
                onClick = { onSelect(option.id) },
                tag = "$tagPrefix${option.id}"
            )
        }
        if (help != null) {
            Text(
                text = help,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
    }
}

/** One choice. Selected, unselected and unavailable are visually distinct. */
@Composable
fun AetherOptionRow(
    option: AetherOption,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier
) {
    if (option.enabled) {
        Card(
            onClick = onClick,
            modifier = modifier
                .fillMaxWidth()
                .testTag(tag),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (selected) AetherAccentDeep
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ),
            border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) AetherAccent else ManjaniqBorder)
        ) { AetherOptionRowBody(option, selected) }
    } else {
        // A choice that cannot be made here keeps its own card so the reason stays
        // readable, and it is deliberately not clickable.
        Card(
            modifier = modifier
                .fillMaxWidth()
                .alpha(0.7f)
                .testTag(tag),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            ),
            border = BorderStroke(1.dp, ManjaniqBorder)
        ) { AetherOptionRowBody(option, selected) }
    }
}

@Composable
private fun AetherOptionRowBody(option: AetherOption, selected: Boolean) {
    val blocked = !option.enabled
    val noteShown = blocked && option.note != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (selected) "\u25CF" else "\u25CB",
            color = if (selected && !blocked) AetherAccent else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.width(18.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = option.label,
                color = when {
                    blocked -> MaterialTheme.colorScheme.onSurfaceVariant
                    selected -> AetherAccentSoft
                    else -> MaterialTheme.colorScheme.onSurface
                },
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
            val detail = if (noteShown) option.note.orEmpty() else option.detail
            if (detail.isNotEmpty()) {
                Text(
                    text = detail,
                    color = if (noteShown) AetherAccentSoft else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }
        }
        if (blocked) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .background(AetherAccent.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    text = stringResource(R.string.aether_choice_unavailable),
                    color = AetherAccentSoft,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * A short, honest line about how far a setting reaches in this build.
 * Only shown for settings that are not fully applied, so the panel does not
 * grow a disclaimer under every control.
 */
@Composable
fun AetherReachNote(reach: AetherReach, reasonRes: Int, modifier: Modifier = Modifier) {
    if (reach == AetherReach.Applied) return
    val prefix = stringResource(
        if (reach == AetherReach.Partial) R.string.aether_choice_partial
        else R.string.aether_choice_not_forwarded
    )
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .width(6.dp)
                .height(6.dp)
                .background(
                    if (reach == AetherReach.Partial) AetherAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                    RoundedCornerShape(3.dp)
                )
        )
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = prefix,
                color = if (reach == AetherReach.Partial) AetherAccentSoft else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp
            )
            Text(
                text = stringResource(reasonRes),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                lineHeight = 13.sp
            )
        }
    }
}
