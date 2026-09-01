package com.nagram.usbbridge.pro.data

import android.content.Context
import java.io.File

data class LegacyDataState(
    val preferencesPresent: Boolean,
    val journalPresent: Boolean,
    val journalBytes: Long,
    val proIndexPresent: Boolean
)

object LegacyDataSafety {
    /**
     * Milestone 0 rule: never rename or delete the legacy SharedPreferences file
     * (`bridge.xml`) or the legacy transfer journal (`bridge_journal.db`). The new
     * Compose shell reads the same package data so migration can be incremental.
     */
    fun inspect(context: Context): LegacyDataState {
        val dataDir = File(context.applicationInfo.dataDir)
        val prefs = File(dataDir, "shared_prefs/bridge.xml")
        val journal = context.getDatabasePath("bridge_journal.db")
        val proIndex = context.getDatabasePath("shahadat_pro_index.db")
        return LegacyDataState(
            preferencesPresent = prefs.exists(),
            journalPresent = journal.exists(),
            journalBytes = if (journal.exists()) journal.length() else 0L,
            proIndexPresent = proIndex.exists()
        )
    }
}
