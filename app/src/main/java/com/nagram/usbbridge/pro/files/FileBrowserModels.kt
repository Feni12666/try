package com.nagram.usbbridge.pro.files

enum class StorageKind { PHONE, USB, SHIZUKU }

enum class ManualOperation { COPY, MOVE }

enum class ConflictPolicy { KEEP_BOTH, REPLACE, SKIP }

data class BrowserEntry(
    val id: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val modified: Long,
    val mimeType: String?,
    val storage: StorageKind
)

data class BrowserLocation(
    val storage: StorageKind,
    val id: String,
    val label: String
)

data class ManualOperationUi(
    val running: Boolean = false,
    val paused: Boolean = false,
    val active: Int = 0,
    val ready: Int = 0,
    val name: String? = null,
    val progress: Int = 0,
    val speedBytesPerSecond: Long = 0L,
    val etaSeconds: Long = -1L,
    val status: String = "Ready"
)
