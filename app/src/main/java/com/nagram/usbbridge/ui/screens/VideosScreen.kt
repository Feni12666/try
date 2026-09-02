package com.nagram.usbbridge.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nagram.usbbridge.R
import com.nagram.usbbridge.ui.components.FolderRow
import com.nagram.usbbridge.ui.components.ScreenHeader
import com.nagram.usbbridge.ui.theme.AccentBlue
import com.nagram.usbbridge.ui.theme.AccentMint
import com.nagram.usbbridge.ui.theme.AccentViolet

@Composable
fun VideosScreen(
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(
                title = stringResource(R.string.videos_title),
                subtitle = stringResource(R.string.videos_subtitle),
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = true, onClick = {}, label = { Text(stringResource(R.string.filter_all)) })
                FilterChip(selected = false, onClick = {}, label = { Text(stringResource(R.string.storage_phone)) })
                FilterChip(selected = false, onClick = {}, label = { Text("USB") })
            }
        }
        item {
            FolderRow(
                title = stringResource(R.string.folder_nagram),
                subtitle = pluralStringResource(R.plurals.video_count_size, 128, 128, "47.2 GB"),
                icon = Icons.Outlined.Lock,
                accent = AccentViolet,
                badge = "Shizuku",
                onClick = onOpenPlayer,
            )
        }
        item {
            FolderRow(
                title = stringResource(R.string.folder_usb_backup),
                subtitle = pluralStringResource(R.plurals.video_count_size, 216, 216, "92.8 GB"),
                icon = Icons.Outlined.Usb,
                accent = AccentBlue,
                badge = "USB",
                onClick = onOpenPlayer,
            )
        }
        item {
            FolderRow(
                title = stringResource(R.string.folder_camera),
                subtitle = pluralStringResource(R.plurals.video_count_size, 74, 74, "21.6 GB"),
                icon = Icons.Outlined.CameraAlt,
                accent = AccentMint,
                onClick = onOpenPlayer,
            )
        }
        item {
            FolderRow(
                title = stringResource(R.string.folder_downloads),
                subtitle = pluralStringResource(R.plurals.video_count_size, 53, 53, "13.4 GB"),
                icon = Icons.Outlined.Download,
                accent = AccentBlue,
                onClick = onOpenPlayer,
            )
        }
        item {
            FolderRow(
                title = "Edited videos",
                subtitle = pluralStringResource(R.plurals.video_count_size, 31, 31, "8.1 GB"),
                icon = Icons.Outlined.Folder,
                accent = AccentMint,
                onClick = onOpenPlayer,
            )
        }
    }
}
