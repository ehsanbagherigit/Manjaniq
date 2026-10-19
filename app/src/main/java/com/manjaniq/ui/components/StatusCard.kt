// Package com.manjaniq.ui.components provides UI composable functions.
package com.manjaniq.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manjaniq.core.models.EngineState
import com.manjaniq.R

/** Colour used for each real engine state. */
fun stateColor(state: EngineState): Color = when (state) {
    EngineState.RUNNING -> Color(0xFF4CAF50)
    EngineState.STARTING, EngineState.STOPPING -> Color(0xFFFFB300)
    EngineState.ERROR -> Color(0xFFE53935)
    EngineState.STOPPED -> Color(0xFF8E8E93)
}

/**
 * Card showing the engine's actual state (never a hardcoded
 * value). The state name is the raw enum constant (always
 * English), which gives UI tests a stable, locale-independent
 * marker of the real engine state.
 */
@Composable
fun StatusCard(engineState: EngineState, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(20.dp)
            .testTag(DashboardTags.ENGINE_STATUS),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.engine_status),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(color = stateColor(engineState), shape = CircleShape)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = engineState.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (subtitle.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
    }
}
