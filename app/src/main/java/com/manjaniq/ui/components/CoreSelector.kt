package com.manjaniq.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manjaniq.R
import com.manjaniq.ui.TestTags
import com.manjaniq.core.SelectedCore
import com.manjaniq.ui.theme.AetherCyan
import com.manjaniq.ui.theme.ManjaniqBorder
import com.manjaniq.ui.theme.ManjaniqPurple
import com.manjaniq.ui.theme.RstaGold

@Composable
fun CoreSelector(
    selected: SelectedCore,
    onSelect: (SelectedCore) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TestTags.CORE_SELECTOR),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(R.string.select_core),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.2.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CoreCard(
                selected = selected == SelectedCore.RSTA,
                accent = RstaGold,
                logoRes = R.drawable.ic_rsta_logo,
                logoDesc = stringResource(R.string.cd_rsta_logo),
                name = stringResource(R.string.rsta_name),
                description = stringResource(R.string.rsta_description),
                badgeText = stringResource(R.string.badge_available),
                onClick = { onSelect(SelectedCore.RSTA) },
                modifier = Modifier
                    .weight(1f)
                    .testTag(TestTags.CORE_CARD_RSTA)
            )
            CoreCard(
                selected = selected == SelectedCore.AETHER,
                accent = AetherCyan,
                logoRes = R.drawable.ic_aether_logo,
                logoDesc = stringResource(R.string.cd_aether_logo),
                name = stringResource(R.string.aether_name),
                description = stringResource(R.string.aether_description),
                badgeText = stringResource(R.string.badge_available),
                isComingSoon = false,
                onClick = { onSelect(SelectedCore.AETHER) },
                modifier = Modifier
                    .weight(1f)
                    .testTag(TestTags.CORE_CARD_AETHER)
            )
        }
    }
}

@Composable
fun CoreCard(
    selected: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    logoRes: Int,
    logoDesc: String,
    name: String,
    description: String,
    badgeText: String,
    isComingSoon: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(140.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                if (isComingSoon) MaterialTheme.colorScheme.surface
                else accent.copy(alpha = 0.10f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) accent else ManjaniqBorder
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (selected) 2.dp else 0.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Image(
                        painter = painterResource(id = logoRes),
                        contentDescription = logoDesc,
                        modifier = Modifier.size(32.dp)
                    )
                    Badge(
                        containerColor = if (isComingSoon) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            accent.copy(alpha = 0.18f)
                        },
                        contentColor = if (isComingSoon) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            accent
                        }
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Text(
                    text = name,
                    color = if (selected && !isComingSoon) accent else MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 2
                )
            }

            // Selection indicator
            if (selected) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(
                            color = accent,
                            shape = RoundedCornerShape(2.dp)
                        )
                )
            } else {
                Spacer(modifier = Modifier.height(3.dp))
            }
        }
    }
}
