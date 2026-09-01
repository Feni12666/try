package com.nagram.usbbridge.pro.sync

import android.content.Context
import android.content.Intent
import android.os.Build
import com.nagram.usbbridge.BridgeService

class TransferServiceFacade(private val context: Context) {
    private val prefs = context.getSharedPreferences("bridge", Context.MODE_PRIVATE)

    fun isRunning(): Boolean = prefs.getBoolean("running", false)

    fun start() {
        val intent = Intent(context, BridgeService::class.java)
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
        prefs.edit().putBoolean("running", true).apply()
    }

    fun stop() {
        context.stopService(Intent(context, BridgeService::class.java))
        prefs.edit().putBoolean("running", false).apply()
    }
}
