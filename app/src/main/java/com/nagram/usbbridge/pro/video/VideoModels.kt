package com.nagram.usbbridge.pro.video

import android.net.Uri
import com.nagram.usbbridge.pro.files.StorageKind

data class VideoItem(
    val id: String,
    val name: String,
    val uri: Uri,
    val folder: String,
    val size: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val modifiedMs: Long,
    val storage: StorageKind
)

data class VideoFolder(
    val key: String,
    val name: String,
    val storage: StorageKind,
    val items: List<VideoItem>
) {
    val count: Int get() = items.size
    val totalBytes: Long get() = items.sumOf { it.size }
}

enum class VideoStorageFilter { ALL, PHONE, USB }

data class VideoLibraryUiState(
    val loading: Boolean = false,
    val permissionGranted: Boolean = false,
    val usbSelected: Boolean = false,
    val filter: VideoStorageFilter = VideoStorageFilter.ALL,
    val query: String = "",
    val folders: List<VideoFolder> = emptyList(),
    val selectedFolderKey: String? = null,
    val message: String? = null
) {
    val selectedFolder: VideoFolder? get() = folders.firstOrNull { it.key == selectedFolderKey }
}

data class VideoPlaybackRequest(
    val items: List<VideoItem>,
    val startIndex: Int
)
