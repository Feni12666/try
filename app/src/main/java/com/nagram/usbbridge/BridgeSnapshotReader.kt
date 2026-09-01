package com.nagram.usbbridge

import android.content.Context

data class LegacyBridgeStats(val files: Long, val bytes: Long, val attention: Long)

data class RecentTransferUi(
    val name: String,
    val sizeBytes: Long,
    val status: String,
    val updatedAt: Long
)

object BridgeSnapshotReader {
    fun today(context: Context): LegacyBridgeStats {
        val db = BridgeDatabase(context)
        return try {
            val s = db.todayStats()
            LegacyBridgeStats(s.files, s.bytes, s.attention)
        } finally {
            db.close()
        }
    }

    fun recent(context: Context, limit: Int = 8): List<RecentTransferUi> {
        val db = BridgeDatabase(context)
        return try {
            db.recent(limit).map {
                RecentTransferUi(
                    name = it.sourceName ?: "Unknown",
                    sizeBytes = it.sourceSize,
                    status = it.status ?: "UNKNOWN",
                    updatedAt = it.updatedAt
                )
            }
        } finally {
            db.close()
        }
    }
}
