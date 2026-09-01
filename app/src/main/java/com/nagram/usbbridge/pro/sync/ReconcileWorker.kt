package com.nagram.usbbridge.pro.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * WorkManager owns lightweight reconciliation/scheduling only.
 * The long-running byte transfer stays in BridgeService (foreground service).
 */
class ReconcileWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("bridge", Context.MODE_PRIVATE)
        val shouldRun = prefs.getBoolean("running", false)
        val tree = prefs.getString("tree_uri", null)
        if (shouldRun && tree != null) {
            runCatching { TransferServiceFacade(applicationContext).start() }
                .onFailure { return Result.retry() }
        }
        return Result.success()
    }
}
