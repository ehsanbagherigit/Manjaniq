package com.manjaniq.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manjaniq.core.models.AetherProfile
import com.manjaniq.core.models.ProfileCapability
import com.manjaniq.ui.TestTags
import com.manjaniq.ui.AetherAccent
import com.manjaniq.ui.AetherAccentDeep
import com.manjaniq.ui.AetherAccentSoft
import com.manjaniq.ui.theme.ManjaniqBorder

@Composable
fun AetherProfileCard(
    profile: AetherProfile,
    selected: Boolean,
    onSelect: () -> Unit,
    expanded: Boolean,
    modifier: Modifier = Modifier
) {
    // Aether's accent is purple (RSTA's is gold). A selected route differs from an
    // unselected one by border, tint, label colour and elevation, so selection is
    // never colour alone.
    val borderColor = if (selected) AetherAccent else ManjaniqBorder
    val bg = if (selected) AetherAccentDeep else MaterialTheme.colorScheme.surface
    // What this installation can do, not what the table assumes: the Psiphon
    // helper is built and packaged by CI, so its presence is a fact about the
    // installed build (AetherHelpers reads it from the native library directory).
    val capability = profile.capabilityInThisBuild()
    val badgeText = when (capability) {
        ProfileCapability.Available -> stringResource(com.manjaniq.R.string.badge_available)
        ProfileCapability.NeedsConfiguration -> stringResource(com.manjaniq.R.string.badge_needs_config)
        ProfileCapability.RequiresExternalComponent -> stringResource(com.manjaniq.R.string.badge_requires_external)
        ProfileCapability.Unsupported -> stringResource(com.manjaniq.R.string.badge_unsupported)
        ProfileCapability.NotVerified -> stringResource(com.manjaniq.R.string.cap_not_verified)
    }
    val badgeBg = when (capability) {
        ProfileCapability.Available -> AetherAccent.copy(alpha = 0.18f)
        ProfileCapability.RequiresExternalComponent -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Card(
        onClick = onSelect,
        modifier = modifier
            .fillMaxWidth()
            .testTag("profile_card_${profile.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
        border = BorderStroke(
            width = if (selected) 1.6.dp else 1.dp,
            color = borderColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 2.dp else 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(profile.titleRes),
                    color = if (selected) AetherAccentSoft else MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Badge(
                    containerColor = badgeBg,
                    contentColor = if (capability == ProfileCapability.Available) AetherAccentSoft else MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Text(text = badgeText, fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp)
                }
            }
            Text(
                text = stringResource(profile.routeRes),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = stringResource(profile.descriptionRes),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                maxLines = 3
            )
            if (selected) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(
                            if (expanded) com.manjaniq.R.string.profile_collapse_settings
                            else com.manjaniq.R.string.profile_expand_settings
                        ),
                        color = AetherAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (expanded) "▲" else "▼",
                        color = AetherAccent,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileStatusBanner(profile: AetherProfile) {
    if (profile.capabilityInThisBuild() == ProfileCapability.RequiresExternalComponent) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(com.manjaniq.R.string.profile_requires_external),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
