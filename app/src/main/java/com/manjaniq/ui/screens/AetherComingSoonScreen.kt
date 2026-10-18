package com.manjaniq.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manjaniq.R
import com.manjaniq.ui.theme.AetherCyan
import com.manjaniq.ui.theme.AetherSurface

@Composable
fun AetherComingSoonContent(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, AetherCyan.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Icon + logo header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_aether_logo),
                    contentDescription = stringResource(R.string.cd_aether_logo),
                    modifier = Modifier.size(48.dp)
                )
                Icon(
                    imageVector = Icons.Filled.HourglassEmpty,
                    contentDescription = null,
                    tint = AetherCyan,
                    modifier = Modifier.size(28.dp)
                )
            }

            Badge(
                containerColor = AetherSurface,
                contentColor = AetherCyan
            ) {
                Text(
                    text = stringResource(R.string.badge_coming_soon),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp
                )
            }

            Text(
                text = stringResource(R.string.aether_coming_soon_title),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            Text(
                text = stringResource(R.string.aether_coming_soon_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FeatureRow("•", "Pluggable transport architecture")
                FeatureRow("•", "Advanced obfuscation pipeline")
                FeatureRow("•", "Multi-core orchestration (planned)")
            }

            Text(
                text = stringResource(R.string.aether_not_available),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun FeatureRow(bullet: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = bullet,
            color = AetherCyan,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(16.dp)
        )
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
    }
}
