package com.manjaniq.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manjaniq.R
import com.manjaniq.core.models.AetherState

@Composable
fun AetherStatusCard(
    state: AetherState,
    socksEndpoint: String,
    endpoint: String,
    version: String,
    lastError: String?,
    isNativeLoaded: Boolean,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = stringResource(R.string.aether_status_title).uppercase(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(
                        when(state) {
                            AetherState.RUNNING -> MaterialTheme.colorScheme.primary
                            AetherState.STARTING, AetherState.STOPPING -> MaterialTheme.colorScheme.secondary
                            AetherState.ERROR -> MaterialTheme.colorScheme.error
                            AetherState.STOPPED -> MaterialTheme.colorScheme.outline
                        }, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(text = when(state) {
                        AetherState.RUNNING -> stringResource(R.string.aether_state_running)
                        AetherState.STOPPED -> stringResource(R.string.aether_state_stopped)
                        AetherState.STARTING -> stringResource(R.string.aether_state_connecting)
                        AetherState.STOPPING -> stringResource(R.string.status_stopping)
                        AetherState.ERROR -> stringResource(R.string.status_error)
                    }, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha=0.5f))
            if (!isNativeLoaded) {
                Text(text = stringResource(R.string.aether_not_native), color = MaterialTheme.colorScheme.error, fontSize=12.sp)
            } else {
                // SOCKS copyable
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(text = "SOCKS5", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize=11.sp)
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(text = "socks5h://${socksEndpoint}", color = MaterialTheme.colorScheme.onSurface, fontSize=13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    OutlinedButton(onClick = { clipboard.setText(AnnotatedString("socks5h://${socksEndpoint}")) }) { Text(stringResource(R.string.aether_proxy_copy), fontSize=11.sp) }
                }
                if (endpoint.isNotEmpty()) {
                    Row {
                        Text(text = "Peer: ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize=12.sp)
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(text = endpoint, color = MaterialTheme.colorScheme.onSurface, fontSize=12.sp)
                        }
                    }
                }
                if (version.isNotEmpty()) {
                    Text(text = "Aether v$version • ${com.manjaniq.AetherBridge.testBridge().take(40)}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize=11.sp)
                }
                if (lastError != null) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(text = lastError, color = MaterialTheme.colorScheme.onErrorContainer, fontSize=12.sp, modifier = Modifier.padding(8.dp))
                    }
                }
                Text(text = stringResource(R.string.aether_proxy_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize=11.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
            }
        }
    }
}
