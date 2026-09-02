package com.nagram.usbbridge.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nagram.usbbridge.R
import com.nagram.usbbridge.storage.PersistedFolder
import com.nagram.usbbridge.ui.AppUiState
import com.nagram.usbbridge.ui.components.MetricCell
import com.nagram.usbbridge.ui.components.PhaseBadge
import com.nagram.usbbridge.ui.components.ScreenHeader
import com.nagram.usbbridge.ui.components.SettingSwitchRow
import com.nagram.usbbridge.ui.components.StatusPill
import com.nagram.usbbridge.ui.theme.AccentBlue
import com.nagram.usbbridge.ui.theme.AccentMint
import com.nagram.usbbridge.ui.theme.AccentViolet

@Composable
fun TransferScreen(
    state: AppUiState,
    onChooseSource: () -> Unit,
    onChooseTarget: () -> Unit,
    onRunSync: () -> Unit,
    onCancelSync: () -> Unit,
    onSetAutoSync: (Boolean) -> Unit,
    onSetOnlyNewFiles: (Boolean) -> Unit,
    onSetVerifyAfterCopy: (Boolean) -> Unit,
    onSetRunOnUsbConnect: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScreenHeader(
                    title = stringResource(R.string.transfer_title),
                    subtitle = stringResource(R.string.transfer_subtitle),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {}) { Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings)) }
            }
        }
        item {
            TransferProgressCard(state)
        }
        item {
            RouteCard(
                source = state.syncSourceFolder,
                target = state.syncTargetFolder,
                onChooseSource = onChooseSource,
                onChooseTarget = onChooseTarget,
            )
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = stringResource(R.string.sync_rules), style = MaterialTheme.typography.titleMedium)
                        PhaseBadge()
                    }
                    SettingSwitchRow(
                        title = stringResource(R.string.auto_sync_enabled),
                        checked = state.autoSyncEnabled,
                        onCheckedChange = onSetAutoSync,
                        icon = Icons.Outlined.SwapHoriz,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                    SettingSwitchRow(
                        title = stringResource(R.string.only_new_files),
                        checked = true,
                        onCheckedChange = {},
                        icon = Icons.Outlined.ContentCopy,
                        enabled = false,
                    )
                    SettingSwitchRow(
                        title = stringResource(R.string.verify_after_copy),
                        checked = true,
                        onCheckedChange = {},
                        icon = Icons.Outlined.Security,
                        enabled = false,
                    )
                    SettingSwitchRow(
                        title = stringResource(R.string.run_on_usb_connect),
                        checked = state.runOnUsbConnect,
                        onCheckedChange = onSetRunOnUsbConnect,
                        icon = Icons.Outlined.Usb,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = AccentMint)
                        Text(
                            text = stringResource(R.string.wait_on_disconnect),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        state.syncLastMessage?.let { message ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Text(message, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            val running = state.syncProgress?.isRunning == true
            Button(
                onClick = if (running) onCancelSync else onRunSync,
                modifier = Modifier.fillMaxWidth(),
                enabled = running || (state.autoSyncEnabled && state.syncSourceFolder != null && state.syncTargetFolder != null),
            ) {
                Icon(Icons.Outlined.SwapHoriz, contentDescription = null)
                Text(
                    text = stringResource(if (running) R.string.cancel_sync else R.string.start_smart_sync),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCell("${state.syncProgress?.completedVideos ?: 0}", stringResource(R.string.checked), Modifier.weight(1f))
                MetricCell("${state.syncProgress?.skippedVideos ?: 0}", stringResource(R.string.skipped), Modifier.weight(1f))
                MetricCell("${state.syncProgress?.copiedVideos ?: 0}", stringResource(R.string.new_files), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TransferProgressCard(state: AppUiState) {
    val progress = state.syncProgress
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f), MaterialTheme.colorScheme.surface),
                    ),
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column {
                    Text(
                        text = progress?.let { stringResource(R.string.sync_processed_count, it.completedVideos, it.totalVideos) }
                            ?: stringResource(R.string.ready_to_sync),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = progress?.message ?: stringResource(R.string.sync_select_route_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusPill(text = "${((progress?.fraction ?: 0f) * 100).toInt()}%", color = AccentMint)
            }
            LinearProgressIndicator(
                progress = { progress?.fraction ?: 0f },
                modifier = Modifier.fillMaxWidth(),
                color = AccentMint,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Text(
                text = progress?.currentName ?: stringResource(R.string.only_new_files),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RouteCard(
    source: PersistedFolder?,
    target: PersistedFolder?,
    onChooseSource: () -> Unit,
    onChooseTarget: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RoutePoint(
                title = stringResource(R.string.source_folder),
                path = source?.label ?: stringResource(R.string.choose_source_folder),
                icon = Icons.Outlined.Lock,
                accent = AccentViolet,
                badge = if (source == null) stringResource(R.string.choose) else stringResource(R.string.selected),
                onClick = onChooseSource,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            RoutePoint(
                title = stringResource(R.string.target_folder),
                path = target?.label ?: stringResource(R.string.choose_usb_target_folder),
                icon = Icons.Outlined.Folder,
                accent = AccentBlue,
                badge = if (target == null) stringResource(R.string.choose) else stringResource(R.string.selected),
                onClick = onChooseTarget,
            )
        }
    }
}

@Composable
private fun RoutePoint(
    title: String,
    path: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: androidx.compose.ui.graphics.Color,
    badge: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = accent)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.labelLarge)
            Text(
                text = path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        StatusPill(text = badge, color = accent)
    }
}
