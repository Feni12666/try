package com.nagram.usbbridge.ui

import com.nagram.usbbridge.shizuku.ProtectedPathPolicy
import com.nagram.usbbridge.shizuku.ShizukuAccessState
import com.nagram.usbbridge.storage.PersistedFolder
import com.nagram.usbbridge.storage.StorageEntry
import com.nagram.usbbridge.transfer.SyncProgress

enum class StorageLocation {
    PHONE,
    USB,
}

enum class BrowserOrigin {
    PHONE, USB, SHIZUKU_DATA, SHIZUKU_OBB;
    val protectedRoot: String? get() = when (this) {
        SHIZUKU_DATA -> ProtectedPathPolicy.ANDROID_DATA_ROOT
        SHIZUKU_OBB -> ProtectedPathPolicy.ANDROID_OBB_ROOT
        PHONE, USB -> null
    }
}

enum class DuplicateKeepRule {
    NEWEST,
    OLDEST,
}

data class DuplicateFile(
    val id: String,
    val name: String,
    val location: String,
    val sizeBytes: Long,
    val sizeLabel: String,
    val durationSeconds: Long,
    val durationLabel: String,
    val modifiedEpochSeconds: Long,
    val modifiedLabel: String,
)

data class DuplicateDecision(
    val kept: DuplicateFile,
    val selectedForDeletion: DuplicateFile,
)

fun decideDuplicate(files: List<DuplicateFile>, rule: DuplicateKeepRule): DuplicateDecision {
    require(files.size == 2) { "Phase 1 comparison requires exactly two files" }
    val sorted = files.sortedBy { it.modifiedEpochSeconds }
    val kept = when (rule) {
        DuplicateKeepRule.NEWEST -> sorted.last()
        DuplicateKeepRule.OLDEST -> sorted.first()
    }
    return DuplicateDecision(
        kept = kept,
        selectedForDeletion = files.first { it.id != kept.id },
    )
}

data class VideoFolder(
    val name: String,
    val location: StorageLocation,
    val count: Int,
    val sizeLabel: String,
    val isProtected: Boolean = false,
)

data class AppUiState(
    val storageLocation: StorageLocation = StorageLocation.PHONE,
    val browserOrigin: BrowserOrigin = BrowserOrigin.PHONE,
    val currentPathLabel: String = "Phone",
    val canNavigateUp: Boolean = false,
    val browserEntries: List<StorageEntry> = emptyList(),
    val isListingLoading: Boolean = false,
    val browserNotice: String? = null,
    val phoneStorageGranted: Boolean = false,
    val usbFolder: PersistedFolder? = null,
    val usbGrantActive: Boolean = false,
    val shizukuAccess: ShizukuAccessState = ShizukuAccessState(),
    val syncSourceFolder: PersistedFolder? = null,
    val syncTargetFolder: PersistedFolder? = null,
    val autoSyncEnabled: Boolean = true,
    val onlyNewFiles: Boolean = true,
    val verifyAfterCopy: Boolean = true,
    val runOnUsbConnect: Boolean = true,
    val syncProgress: SyncProgress? = null,
    val syncLastMessage: String? = null,
    val keepRule: DuplicateKeepRule = DuplicateKeepRule.NEWEST,
    val deleteDialogVisible: Boolean = false,
) { val shizukuConnected get() = shizukuAccess.isConnected }

val DemoDuplicateFiles = listOf(
    DuplicateFile(
        id = "phone-original",
        name = "VID_20260830_193244.mp4",
        location = "Phone · Android/data",
        sizeBytes = 1_331_445_760L,
        sizeLabel = "1.24 GB",
        durationSeconds = 198L,
        durationLabel = "03:18",
        modifiedEpochSeconds = 1_777_770_800L,
        modifiedLabel = "30 Aug 2026 · 19:32",
    ),
    DuplicateFile(
        id = "usb-copy",
        name = "VID_20260830_193244 (1).mp4",
        location = "USB · Nagram Backup",
        sizeBytes = 1_331_445_760L,
        sizeLabel = "1.24 GB",
        durationSeconds = 198L,
        durationLabel = "03:18",
        modifiedEpochSeconds = 1_777_774_400L,
        modifiedLabel = "30 Aug 2026 · 20:32",
    ),
)
