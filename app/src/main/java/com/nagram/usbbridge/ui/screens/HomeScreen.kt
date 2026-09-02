package com.nagram.usbbridge.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nagram.usbbridge.R
import com.nagram.usbbridge.ui.AppUiState
import com.nagram.usbbridge.ui.components.PhaseBadge
import com.nagram.usbbridge.ui.components.ProtectedAccessBanner
import com.nagram.usbbridge.ui.components.QuickActionBlue
import com.nagram.usbbridge.ui.components.QuickActionCard
import com.nagram.usbbridge.ui.components.QuickActionMint
import com.nagram.usbbridge.ui.components.QuickActionViolet
import com.nagram.usbbridge.ui.components.ScreenHeader
import com.nagram.usbbridge.ui.components.SectionHeader
import com.nagram.usbbridge.ui.components.StorageSummaryCard
import com.nagram.usbbridge.ui.theme.AccentMint

@Composable
fun HomeScreen(
    state: AppUiState,
    onOpenFiles: () -> Unit,
    onOpenVideos: () -> Unit,
    onOpenDuplicates: () -> Unit,
    onOpenTransfer: () -> Unit,
    onOpenShizuku: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScreenHeader(
                    title = stringResource(R.string.home_title),
                    subtitle = stringResource(R.string.home_greeting),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {}) {
                    Icon(Icons.Outlined.Notifications, contentDescription = stringResource(R.string.notifications))
                }
                IconButton(onClick = {}) {
                    Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings))
                }
            }
        }
        item { StorageSummaryCard() }
        item {
            ProtectedAccessBanner(
                connected = state.shizukuConnected,
                onClick = onOpenShizuku,
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionHeader(title = stringResource(R.string.quick_actions), modifier = Modifier.weight(1f))
                PhaseBadge()
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickActionCard(
                    title = stringResource(R.string.smart_transfer),
                    subtitle = stringResource(R.string.smart_transfer_hint),
                    icon = Icons.Outlined.SwapHoriz,
                    accent = QuickActionMint,
                    onClick = onOpenTransfer,
                    modifier = Modifier.weight(1f),
                )
                QuickActionCard(
                    title = stringResource(R.string.browse_files),
                    subtitle = stringResource(R.string.browse_files_hint),
                    icon = Icons.Outlined.FolderOpen,
                    accent = QuickActionBlue,
                    onClick = onOpenFiles,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickActionCard(
                    title = stringResource(R.string.find_duplicates),
                    subtitle = stringResource(R.string.find_duplicates_hint),
                    icon = Icons.Outlined.ContentCopy,
                    accent = QuickActionViolet,
                    onClick = onOpenDuplicates,
                    modifier = Modifier.weight(1f),
                )
                QuickActionCard(
                    title = stringResource(R.string.video_folders),
                    subtitle = stringResource(R.string.video_folders_hint),
                    icon = Icons.Outlined.VideoLibrary,
                    accent = QuickActionBlue,
                    onClick = onOpenVideos,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item { SectionHeader(title = stringResource(R.string.recent_activity), trailing = stringResource(R.string.view_all)) }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = AccentMint)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = stringResource(R.string.recent_transfer_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = stringResource(R.string.recent_transfer_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = AccentMint)
                }
            }
        }
    }
}
