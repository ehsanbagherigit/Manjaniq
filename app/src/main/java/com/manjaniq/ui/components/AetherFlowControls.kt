package com.manjaniq.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val detail: String = ""
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

/** One choice: the selected one is tinted, the others are neutral. */
@Composable
fun AetherOptionRow(
    option: AetherOption,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier
) {
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
}

@Composable
private fun AetherOptionRowBody(option: AetherOption, selected: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (selected) "\u25CF" else "\u25CB",
            color = if (selected) AetherAccent else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.width(18.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = option.label,
                color = if (selected) AetherAccentSoft else MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
            if (option.detail.isNotEmpty()) {
                Text(
                    text = option.detail,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }
        }
    }
}
