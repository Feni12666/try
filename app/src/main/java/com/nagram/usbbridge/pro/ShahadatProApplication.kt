package com.nagram.usbbridge.pro

import android.app.Application
import android.content.Context
import com.nagram.usbbridge.pro.data.ProDatabase
import com.nagram.usbbridge.pro.data.RoomMediaIndexRepository

class ShahadatProApplication : Application() {
    val proDatabase: ProDatabase by lazy { ProDatabase.get(this) }
    val mediaIndexRepository: RoomMediaIndexRepository by lazy {
        RoomMediaIndexRepository(proDatabase.mediaIndexDao())
    }

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("bridge", Context.MODE_PRIVATE)
        // Manual transfer service is START_NOT_STICKY. If the process died mid-operation,
        // never assume completion and never perform cleanup from stale state.
        if (prefs.getBoolean("manual_op_running", false)) {
            prefs.edit()
                .putBoolean("manual_op_running", false)
                .putBoolean("manual_op_paused", false)
                .putInt("manual_op_active", 0)
                .putInt("manual_op_ready", 0)
                .putString("manual_op_name", null)
                .putLong("manual_op_speed", 0L)
                .putLong("manual_op_eta", -1L)
                .putString("manual_op_status", "Previous operation was interrupted — originals kept")
                .apply()
        }
    }
}
