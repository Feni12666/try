package com.nagram.usbbridge.pro.ui

import android.graphics.Bitmap
import android.os.Build
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nagram.usbbridge.pro.files.StorageKind
import com.nagram.usbbridge.pro.video.VideoFolder
import com.nagram.usbbridge.pro.video.VideoItem
import com.nagram.usbbridge.pro.video.VideoLibraryViewModel
import com.nagram.usbbridge.pro.video.VideoPlaybackRequest
import com.nagram.usbbridge.pro.video.VideoStorageFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

@Composable
fun PremiumVideosScreen(
    viewModel: VideoLibraryViewModel,
    onRequestPermission: () -> Unit,
    onChooseUsb: () -> Unit,
    onPlay: (VideoPlaybackRequest) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }
    val selected = state.selectedFolder

    if (selected != null) {
        VideoFolderScreen(
            folder = selected,
            onBack = viewModel::closeFolder,
            onPlay = { item ->
                val index = selected.items.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
                onPlay(VideoPlaybackRequest(selected.items, index))
            }
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Videos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Text("All accessible video folders in one place", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = viewModel::refresh) { Icon(Icons.Outlined.Refresh, contentDescription = "Refresh videos") }
            }
        }

        if (!state.permissionGranted) {
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Allow video access", fontWeight = FontWeight.ExtraBold)
                        Text("Permission lets the Videos tab show your phone video folders. File Manager access remains separate.")
                        Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) { Text("Grant Video Permission") }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FilterChip(
                    selected = state.filter == VideoStorageFilter.ALL,
                    onClick = { viewModel.setFilter(VideoStorageFilter.ALL) },
                    label = { Text("All") }
                )
                FilterChip(
                    selected = state.filter == VideoStorageFilter.PHONE,
                    onClick = { viewModel.setFilter(VideoStorageFilter.PHONE) },
                    leadingIcon = { Icon(Icons.Outlined.PhoneAndroid, null, modifier = Modifier.size(18.dp)) },
                    label = { Text("Phone") }
                )
                FilterChip(
                    selected = state.filter == VideoStorageFilter.USB,
                    onClick = { viewModel.setFilter(VideoStorageFilter.USB) },
                    leadingIcon = { Icon(Icons.Outlined.Usb, null, modifier = Modifier.size(18.dp)) },
                    label = { Text("USB") }
                )
            }
        }

        if (!state.usbSelected) {
            item {
                Card(shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Usb, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text("USB / SSD videos", fontWeight = FontWeight.Bold)
                            Text("Choose a USB folder once to include its video folders.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(onClick = onChooseUsb) { Text("Choose") }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                placeholder = { Text("Search video or folder") },
                shape = RoundedCornerShape(18.dp)
            )
        }

        if (state.loading) {
            item {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else if (state.folders.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Folder, null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(10.dp))
                        Text("No video folders found", fontWeight = FontWeight.Bold)
                        Text("Grant access, choose USB, or refresh.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(state.folders, key = { it.key }) { folder ->
                VideoFolderCard(folder = folder, onClick = { viewModel.openFolder(folder.key) })
            }
        }

        state.message?.let { msg -> item { Text(msg, color = MaterialTheme.colorScheme.error) } }
    }
}

@Composable
private fun VideoFolderCard(folder: VideoFolder, onClick: () -> Unit) {
    val cover = folder.items.firstOrNull()
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(104.dp, 72.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (cover != null) VideoThumbnail(cover, Modifier.fillMaxSize())
                Icon(Icons.Outlined.PlayArrow, null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(folder.name, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${folder.count} videos • ${humanBytesVideo(folder.totalBytes)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (folder.storage == StorageKind.USB) "USB / SSD" else "Phone", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun VideoFolderScreen(folder: VideoFolder, onBack: () -> Unit, onPlay: (VideoItem) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Back") }
                Column(Modifier.weight(1f)) {
                    Text(folder.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${folder.count} videos • ${if (folder.storage == StorageKind.USB) "USB / SSD" else "Phone"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(folder.items, key = { it.id }) { item ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onPlay(item) },
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(128.dp, 78.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        VideoThumbnail(item, Modifier.fillMaxSize())
                        Icon(Icons.Outlined.PlayArrow, null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            formatDuration(item.durationMs),
                            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.65f), RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 2.dp),
                            color = androidx.compose.ui.graphics.Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.name, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val resolution = if (item.width > 0 && item.height > 0) "${item.width}×${item.height}" else "Video"
                        Text("$resolution • ${humanBytesVideo(item.size)}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoThumbnail(item: VideoItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, item.id) {
        value = if (Build.VERSION.SDK_INT >= 29) {
            withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.loadThumbnail(item.uri, Size(320, 180), null) }.getOrNull()
            }
        } else null
    }
    if (bitmap != null) {
        Image(bitmap!!.asImageBitmap(), contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
    }
}

private fun formatDuration(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
}

private fun humanBytesVideo(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val group = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.lastIndex)
    val value = bytes / 1024.0.pow(group.toDouble())
    return if (group == 0) "$bytes B" else String.format(Locale.US, "%.1f %s", value, units[group])
}
