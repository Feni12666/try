package com.nagram.usbbridge.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nagram.usbbridge.R
import com.nagram.usbbridge.shizuku.ShizukuAccessStatus
import com.nagram.usbbridge.ui.AppUiState
import com.nagram.usbbridge.ui.components.ScreenHeader
import com.nagram.usbbridge.ui.components.StatusPill
import com.nagram.usbbridge.ui.theme.AccentMint
import com.nagram.usbbridge.ui.theme.AccentViolet

@Composable
fun ShizukuScreen(state: AppUiState, onBack: () -> Unit, onRequestAccess: () -> Unit, onRefreshStatus: () -> Unit, modifier: Modifier = Modifier) {
    val access = state.shizukuAccess; val connected = access.isConnected; val accent = if (connected) AccentMint else AccentViolet
    LazyColumn(modifier, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }; ScreenHeader(stringResource(R.string.shizuku_title), stringResource(R.string.shizuku_subtitle), Modifier.weight(1f)) } }
        item { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = .12f))) { Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Security, null, tint = accent); Column(Modifier.weight(1f)) { Text(stringResource(R.string.shizuku_status), style = MaterialTheme.typography.labelLarge); Text(statusText(access.status), style = MaterialTheme.typography.headlineSmall); Text(access.message, style = MaterialTheme.typography.bodySmall) }; StatusPill(statusText(access.status), color = accent) } } }
        item { Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) { Icon(Icons.Outlined.Info, null, tint = AccentViolet); Column { Text(stringResource(R.string.shizuku_optional_note)); Text(stringResource(R.string.shizuku_read_only_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } }
        item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { when (access.status) {
            ShizukuAccessStatus.PERMISSION_REQUIRED -> { Button(onClick = onRequestAccess, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.grant_shizuku_permission)) }; OutlinedButton(onClick = onRefreshStatus, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.refresh_status)) } }
            ShizukuAccessStatus.CONNECTING -> Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.connecting_shizuku)) }
            else -> OutlinedButton(onClick = onRefreshStatus, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.check_shizuku)) }
        } } }
    }
}
@Composable private fun statusText(status: ShizukuAccessStatus): String = when (status) { ShizukuAccessStatus.CONNECTED -> stringResource(R.string.connected); ShizukuAccessStatus.CONNECTING -> stringResource(R.string.connecting_shizuku); ShizukuAccessStatus.PERMISSION_REQUIRED -> stringResource(R.string.permission_required); ShizukuAccessStatus.DENIED -> stringResource(R.string.permission_denied); ShizukuAccessStatus.ERROR -> stringResource(R.string.error); ShizukuAccessStatus.UNAVAILABLE -> stringResource(R.string.disconnected) }
