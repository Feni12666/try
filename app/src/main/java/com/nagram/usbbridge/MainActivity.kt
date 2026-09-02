package com.nagram.usbbridge

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.nagram.usbbridge.storage.SyncFolderRole
import com.nagram.usbbridge.ui.AppViewModel
import com.nagram.usbbridge.ui.UsbVideoManagerApp
import com.nagram.usbbridge.ui.theme.UsbVideoManagerTheme

private enum class FolderPickerPurpose { USB, SYNC_SOURCE, SYNC_TARGET }

class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels()
    private var folderPickerPurpose = FolderPickerPurpose.USB
    private val allFilesAccessLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { appViewModel.onPhoneStorageSettingsReturned() }
    private val folderTreeLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@registerForActivityResult
        when (folderPickerPurpose) {
            FolderPickerPurpose.USB -> appViewModel.onUsbFolderSelected(uri)
            FolderPickerPurpose.SYNC_SOURCE -> appViewModel.onSyncFolderSelected(uri, SyncFolderRole.SOURCE)
            FolderPickerPurpose.SYNC_TARGET -> appViewModel.onSyncFolderSelected(uri, SyncFolderRole.TARGET)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        setContent { UsbVideoManagerTheme { UsbVideoManagerApp(appViewModel, ::openAllFilesAccessSettings, { openFolderPicker(FolderPickerPurpose.USB) }, { openFolderPicker(FolderPickerPurpose.SYNC_SOURCE) }, { openFolderPicker(FolderPickerPurpose.SYNC_TARGET) }) } }
    }
    override fun onResume() { super.onResume(); appViewModel.onPhoneStorageSettingsReturned(); appViewModel.refreshShizuku() }
    private fun openAllFilesAccessSettings() {
        val appIntent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
        runCatching { allFilesAccessLauncher.launch(appIntent) }.getOrElse { allFilesAccessLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
    }
    private fun openFolderPicker(purpose: FolderPickerPurpose) { folderPickerPurpose = purpose; folderTreeLauncher.launch(null) }
}
