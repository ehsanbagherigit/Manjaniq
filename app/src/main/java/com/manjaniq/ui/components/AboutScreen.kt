// Package com.manjaniq.ui.components provides the About screen:
// real app name, version from PackageManager, official logo,
// engine version and acknowledgements. It claims no features
// that are not implemented.
package com.manjaniq.ui.components

import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manjaniq.core.EngineRepository
import com.manjaniq.R

@Composable
fun AboutScreen() {
    val context = LocalContext.current
    val ui by EngineRepository.uiState.collectAsState()
    val versionName by remember {
        mutableStateOf(
            runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull() ?: "unknown"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.ic_manjaniq_logo),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier.size(96.dp),
            contentScale = ContentScale.Fit
        )
        Text(
            text = stringResource(R.string.app_name),
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = stringResource(R.string.about_version, versionName),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )
        if (ui.metrics.version.isNotBlank()) {
            Text(
                text = stringResource(R.string.about_engine, ui.metrics.version),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
        Text(
            text = stringResource(R.string.about_description),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp
        )
        InfoCard(title = stringResource(R.string.about_acknowledgements)) {
            Text(
                text = stringResource(R.string.about_acknowledgements_text),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp
            )
        }
        Text(
            text = stringResource(R.string.about_engine_note),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
    }
}
