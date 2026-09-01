package com.nagram.usbbridge.pro.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_index",
    indices = [
        Index(value = ["storageId", "uriOrPath"], unique = true),
        Index(value = ["sizeBytes", "durationMs"]),
        Index(value = ["quickHash"]),
        Index(value = ["sha256"])
    ]
)
data class MediaIndexEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val storageId: String,
    val uriOrPath: String,
    val displayName: String,
    val sizeBytes: Long,
    val durationMs: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val modifiedMs: Long,
    val quickHash: String? = null,
    val sha256: String? = null,
    val perceptualHash: String? = null,
    val scannedAtMs: Long = System.currentTimeMillis()
)
