package com.nagram.usbbridge

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nagram.usbbridge.pro.files.FileManagerViewModel
import com.nagram.usbbridge.pro.ui.MainViewModel
import com.nagram.usbbridge.pro.ui.ShahadatProApp
import com.nagram.usbbridge.pro.ui.theme.ShahadatProTheme
import com.nagram.usbbridge.pro.video.VideoLibraryViewModel
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("bridge", MODE_PRIVATE) }
    private var onUsbChanged: (() -> Unit)? = null
    private var onVideoAccessChanged: (() -> Unit)? = null
    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
        onUsbChanged?.invoke()
    }

    private val usbTreeLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            prefs.edit().putString("tree_uri", uri.toString()).apply()
            Toast.makeText(this, "USB / SSD folder saved ✓", Toast.LENGTH_SHORT).show()
            onUsbChanged?.invoke()
            onVideoAccessChanged?.invoke()
        } catch (t: Throwable) {
            Toast.makeText(this, "USB permission could not be saved: ${t.message}", Toast.LENGTH_LONG).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val videoPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        onVideoAccessChanged?.invoke()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { Shizuku.addRequestPermissionResultListener(shizukuPermissionListener) }

        // Manual-first build: no fixed folder, no Nagram watcher, no boot auto-copy.
        prefs.edit()
            .putBoolean("running", false)
            .putBoolean("auto_start", false)
            .putBoolean("auto_sync_enabled", false)
            .putBoolean("duplicate_guard", true)
            .putInt("configured_parallel_transfers", 2)
            .apply()
        if (!prefs.contains("cleanup_delay_ms")) {
            prefs.edit().putLong("cleanup_delay_ms", 3L * 60L * 1000L).apply()
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val mainVm: MainViewModel = viewModel()
            val filesVm: FileManagerViewModel = viewModel()
            val videosVm: VideoLibraryViewModel = viewModel()
            val mainState by mainVm.state.collectAsStateWithLifecycle()
            onUsbChanged = {
                mainVm.refreshNow()
                filesVm.refreshPermissionsAndStorage()
            }
            onVideoAccessChanged = { videosVm.refresh() }

            ShahadatProTheme(mainState.themeMode) {
                ShahadatProApp(
                    viewModel = mainVm,
                    fileManagerViewModel = filesVm,
                    videoLibraryViewModel = videosVm,
                    distribution = BuildConfig.DISTRIBUTION,
                    onChooseUsb = { usbTreeLauncher.launch(null) },
                    onRequestShizuku = { requestShizukuPermission(); mainVm.refreshNow() },
                    onRequestPhoneAccess = { requestPhoneStorageAccess() },
                    onRequestVideoAccess = { requestVideoPermission() },
                    onOpenAppSettings = { openAppSettings() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        onUsbChanged?.invoke()
    }

    override fun onDestroy() {
        runCatching { Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener) }
        super.onDestroy()
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

    private fun requestPhoneStorageAccess() {
        if (BuildConfig.DISTRIBUTION != "DIRECT") {
            Toast.makeText(this, "Play Store build uses policy-compliant scoped access.", Toast.LENGTH_LONG).show()
            return
        }
        if (Build.VERSION.SDK_INT >= 30) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
    }


    private fun requestVideoPermission() {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED ||
            (Build.VERSION.SDK_INT >= 30 && android.os.Environment.isExternalStorageManager())
        ) {
            onVideoAccessChanged?.invoke()
            Toast.makeText(this, "Video access ready ✓", Toast.LENGTH_SHORT).show()
        } else {
            videoPermissionLauncher.launch(permission)
        }
    }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }
}
