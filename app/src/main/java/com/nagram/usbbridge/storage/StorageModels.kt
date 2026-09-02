package com.nagram.usbbridge.storage

import com.nagram.usbbridge.ui.StorageLocation

data class StorageEntry(
    val id: String,
    val displayName: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val mimeType: String? = null,
    val canWrite: Boolean = false,
)

data class StorageListing(
    val location: StorageLocation,
    val pathLabel: String,
    val entries: List<StorageEntry>,
)

data class PersistedFolder(val uri: String, val label: String)

enum class SyncFolderRole { SOURCE, TARGET }

data class SyncPreferences(
    val enabled: Boolean = true,
    val onlyNewFiles: Boolean = true,
    val verifyAfterCopy: Boolean = true,
    val runOnUsbConnect: Boolean = true,
)
