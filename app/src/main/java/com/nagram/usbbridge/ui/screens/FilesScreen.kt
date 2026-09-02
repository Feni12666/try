package com.nagram.usbbridge.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nagram.usbbridge.R
import com.nagram.usbbridge.storage.StorageEntry
import com.nagram.usbbridge.ui.AppUiState
import com.nagram.usbbridge.ui.BrowserOrigin
import com.nagram.usbbridge.ui.StorageLocation
import com.nagram.usbbridge.ui.components.FolderRow
import com.nagram.usbbridge.ui.components.ProtectedAccessBanner
import com.nagram.usbbridge.ui.components.ScreenHeader
import com.nagram.usbbridge.ui.components.StorageSwitcher
import com.nagram.usbbridge.ui.theme.AccentBlue
import com.nagram.usbbridge.ui.theme.AccentMint
import com.nagram.usbbridge.ui.theme.AccentViolet
import java.util.Locale

@Composable
fun FilesScreen(
    state: AppUiState,
    onStorageSelected: (StorageLocation) -> Unit,
    onRequestPhoneStorageAccess: () -> Unit,
    onChooseUsbFolder: () -> Unit,
    onRefresh: () -> Unit,
    onNavigateUp: () -> Unit,
    onOpenEntry: (String, String, Boolean) -> Unit,
    onOpenProtectedData: () -> Unit,
    onOpenProtectedObb: () -> Unit,
    onSetProtectedSyncSource: () -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenShizuku: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScreenHeader(stringResource(R.string.files_title), stringResource(R.string.files_subtitle), Modifier.weight(1f))
                IconButton(onClick = onRefresh) { Icon(Icons.Outlined.Refresh, stringResource(R.string.refresh)) }
                IconButton(onClick = {}) { Icon(Icons.Outlined.GridView, stringResource(R.string.change_layout)) }
            }
        }
        item { StorageSwitcher(state.storageLocation, onStorageSelected) }
        item { AccessCard(state, onRequestPhoneStorageAccess, onChooseUsbFolder, onNavigateUp) }
        if (state.browserOrigin == BrowserOrigin.SHIZUKU_DATA || state.browserOrigin == BrowserOrigin.SHIZUKU_OBB) {
            item {
                OutlinedButton(onClick = onSetProtectedSyncSource, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Lock, contentDescription = null)
                    Text(stringResource(R.string.use_as_sync_source), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        if (state.storageLocation == StorageLocation.PHONE && state.browserOrigin == BrowserOrigin.PHONE) {
            item { ProtectedAccessBanner(state.shizukuConnected, onOpenShizuku) }
            if (state.shizukuConnected) {
                item { FolderRow(stringResource(R.string.browse_android_data), stringResource(R.string.protected_folder_hint), Icons.Outlined.Lock, accent = AccentViolet, badge = "Shizuku", onClick = onOpenProtectedData) }
                item { FolderRow(stringResource(R.string.browse_android_obb), stringResource(R.string.protected_folder_hint), Icons.Outlined.Lock, accent = AccentViolet, badge = "Shizuku", onClick = onOpenProtectedObb) }
            }
        }
        state.browserNotice?.let { item { NoticeCard(it) } }
        if (state.isListingLoading) item { Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp); Text(stringResource(R.string.loading_files), Modifier.padding(start = 10.dp)) } }
        else if (state.browserEntries.isNotEmpty()) items(state.browserEntries, key = { it.id }) { entry -> BrowserRow(entry) { if (entry.isDirectory) onOpenEntry(entry.id, entry.displayName, true) else onOpenPlayer() } }
        else if (state.browserNotice == null) item { NoticeCard(stringResource(R.string.empty_folder)) }
    }
}

@Composable private fun AccessCard(state: AppUiState, requestPhone: () -> Unit, chooseUsb: () -> Unit, up: () -> Unit) {
    val phone = state.storageLocation == StorageLocation.PHONE
    val protected = state.browserOrigin == BrowserOrigin.SHIZUKU_DATA || state.browserOrigin == BrowserOrigin.SHIZUKU_OBB
    val granted = if (protected) true else if (phone) state.phoneStorageGranted else state.usbGrantActive
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f))) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.canNavigateUp) IconButton(onClick = up) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.go_up)) }
                Icon(if (protected) Icons.Outlined.Lock else if (phone) Icons.Outlined.Storage else Icons.Outlined.Usb, null, tint = if (phone) AccentViolet else AccentBlue)
                Column(Modifier.weight(1f)) { Text(state.currentPathLabel, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(if (protected) stringResource(R.string.protected_read_only) else if (granted) stringResource(R.string.storage_access_active) else stringResource(if (phone) R.string.phone_storage_access_needed else R.string.usb_folder_needed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (!granted) Button(onClick = if (phone) requestPhone else chooseUsb, modifier = Modifier.fillMaxWidth()) { Text(stringResource(if (phone) R.string.grant_phone_storage_access else R.string.choose_usb_folder)) }
            else if (!phone && !protected) OutlinedButton(onClick = chooseUsb, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.change_usb_folder)) }
        }
    }
}
@Composable private fun BrowserRow(entry: StorageEntry, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .18f))) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(if (entry.isDirectory) Icons.Outlined.Folder else Icons.Outlined.Description, null, tint = if (entry.isDirectory) AccentBlue else AccentMint)
            Column(Modifier.weight(1f)) { Text(entry.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall); Text(if (entry.isDirectory) stringResource(R.string.folder) else bytes(entry.sizeBytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
@Composable private fun NoticeCard(message: String) { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AccentViolet.copy(alpha = .10f))) { Text(message, Modifier.padding(14.dp)) } }
private fun bytes(value: Long): String { if (value < 1024) return "$value B"; val units = arrayOf("KB", "MB", "GB", "TB"); var x = value.toDouble(); var i = -1; do { x /= 1024; i++ } while (x >= 1024 && i < units.lastIndex); return String.format(Locale.US, "%.1f %s", x, units[i]) }
