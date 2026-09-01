package com.nagram.usbbridge

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nagram.usbbridge.pro.sync.TransferServiceFacade
import com.nagram.usbbridge.pro.sync.WorkScheduler
import com.nagram.usbbridge.pro.ui.MainViewModel
import com.nagram.usbbridge.pro.ui.ShahadatProApp
import com.nagram.usbbridge.pro.ui.theme.ShahadatProTheme
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("bridge", MODE_PRIVATE) }

    private val usbTreeLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            prefs.edit().putString("tree_uri", uri.toString()).apply()
            Toast.makeText(this, "USB destination saved ✓", Toast.LENGTH_SHORT).show()
        } catch (t: Throwable) {
            Toast.makeText(this, "USB permission could not be saved: ${t.message}", Toast.LENGTH_LONG).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WorkScheduler.ensureScheduled(this)

        // Milestone 0 migration guard: preserve current cleanup behavior and legacy preference names.
        if (!prefs.contains("cleanup_delay_ms")) {
            prefs.edit().putLong("cleanup_delay_ms", 3L * 60L * 1000L).apply()
        }
        prefs.edit()
            .putBoolean("duplicate_guard", true)
            .putInt("configured_parallel_transfers", 2)
            .apply()

        setContent {
            ShahadatProTheme {
                val vm: MainViewModel = viewModel()
                ShahadatProApp(
                    viewModel = vm,
                    distribution = BuildConfig.DISTRIBUTION,
                    onChooseUsb = { usbTreeLauncher.launch(null) },
                    onRequestShizuku = { requestShizukuPermission(); vm.refreshNow() },
                    onStartSync = { startSync(vm) },
                    onPauseSync = { TransferServiceFacade(this).stop(); vm.refreshNow() },
                    onRequestPhoneAccess = { requestPhoneStorageAccess() },
                    onOpenAppSettings = { openAppSettings() }
                )
            }
        }
    }

    private fun requestShizukuPermission() {
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            Toast.makeText(this, "আগে Shizuku চালু করুন", Toast.LENGTH_LONG).show()
            return
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Shizuku permission already granted ✓", Toast.LENGTH_SHORT).show()
        } else {
            Shizuku.requestPermission(7103)
        }
    }

    private fun startSync(vm: MainViewModel) {
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            Toast.makeText(this, "Shizuku চালু করুন", Toast.LENGTH_LONG).show()
            return
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            requestShizukuPermission()
            return
        }
        if (prefs.getString("tree_uri", null).isNullOrBlank()) {
            Toast.makeText(this, "আগে USB destination select করুন", Toast.LENGTH_LONG).show()
            usbTreeLauncher.launch(null)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        TransferServiceFacade(this).start()
        Toast.makeText(this, "Smart Sync started safely", Toast.LENGTH_SHORT).show()
        vm.refreshNow()
    }

    private fun requestPhoneStorageAccess() {
        if (BuildConfig.DISTRIBUTION != "DIRECT") {
            Toast.makeText(this, "Play Store build uses scoped storage access.", Toast.LENGTH_LONG).show()
            return
        }
        if (Build.VERSION.SDK_INT >= 30) {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }
}
