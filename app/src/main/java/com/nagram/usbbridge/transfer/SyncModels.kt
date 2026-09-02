package com.nagram.usbbridge.transfer

data class SyncProgress(
    val totalVideos: Int = 0,
    val completedVideos: Int = 0,
    val copiedVideos: Int = 0,
    val skippedVideos: Int = 0,
    val failedVideos: Int = 0,
    val currentName: String? = null,
    val currentBytes: Long = 0L,
    val currentTotalBytes: Long = 0L,
    val message: String = "Preparing sync…",
    val isRunning: Boolean = false,
) {
    val fraction: Float
        get() = if (totalVideos == 0) 0f else completedVideos.toFloat() / totalVideos.toFloat()
}

data class SyncResult(
    val copiedVideos: Int,
    val skippedVideos: Int,
    val failedVideos: Int,
)
