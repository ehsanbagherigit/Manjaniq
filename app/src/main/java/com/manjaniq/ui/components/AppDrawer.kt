package com.manjaniq.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manjaniq.R
import com.manjaniq.ui.TestTags
import com.manjaniq.ui.navigation.AppDestination
import com.manjaniq.ui.theme.ManjaniqBorder
import com.manjaniq.ui.theme.ManjaniqPurple

@Composable
fun AppDrawerContent(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    onClose: () -> Unit,
    versionName: String
) {
    ModalDrawerSheet(
        modifier = Modifier.testTag(TestTags.DRAWER),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerContentColor = MaterialTheme.colorScheme.onSurface
    ) {
        // Header with branding
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.ic_manjaniq_logo),
                    contentDescription = stringResource(R.string.cd_manjaniq_logo),
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.drawer_subtitle),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }
        }

        HorizontalDivider(color = ManjaniqBorder, thickness = 1.dp)

        Spacer(modifier = Modifier.height(12.dp))

        AppDestination.drawerItems.forEach { dest ->
            val selected = currentRoute == dest.route
            val icon = when (dest.route) {
                "home" -> Icons.Filled.Home
                "settings" -> Icons.Filled.Settings
                "logs" -> Icons.Filled.Description
                "about" -> Icons.Filled.Info
                else -> Icons.Filled.Home
            }
            NavigationDrawerItem(
                icon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (selected) ManjaniqPurple else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                label = {
                    Text(
                        text = stringResource(dest.labelRes),
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                },
                selected = selected,
                onClick = {
                    onNavigate(dest.route)
                    onClose()
                },
                modifier = Modifier
                    .padding(NavigationDrawerItemDefaults.ItemPadding)
                    .testTag(TestTags.drawerItem(dest.route)),
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    selectedTextColor = ManjaniqPurple,
                    selectedIconColor = ManjaniqPurple,
                    unselectedContainerColor = MaterialTheme.colorScheme.surface,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurface,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        HorizontalDivider(color = ManjaniqBorder, thickness = 1.dp)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = stringResource(R.string.version) + " $versionName",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun DrawerIconForRoute(route: String): ImageVector = when (route) {
    "home" -> Icons.Filled.Home
    "settings" -> Icons.Filled.Settings
    "logs" -> Icons.Filled.Description
    "about" -> Icons.Filled.Info
    else -> Icons.Filled.Home
}
