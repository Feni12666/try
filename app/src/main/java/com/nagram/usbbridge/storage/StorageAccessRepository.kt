package com.nagram.usbbridge.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.nagram.usbbridge.ui.StorageLocation
import java.io.File
import java.util.Locale

/** Direct Phone access + user-selected persisted SAF grants. */
class StorageAccessRepository(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun hasPhoneStorageAccess(): Boolean = Environment.isExternalStorageManager()
    fun usbFolder(): PersistedFolder? = readFolder(USB_URI_KEY, USB_LABEL_KEY)
    fun syncFolder(role: SyncFolderRole): PersistedFolder? = when (role) {
        SyncFolderRole.SOURCE -> readFolder(SYNC_SOURCE_URI_KEY, SYNC_SOURCE_LABEL_KEY)
        SyncFolderRole.TARGET -> readFolder(SYNC_TARGET_URI_KEY, SYNC_TARGET_LABEL_KEY)
    }
    fun syncPreferences() = SyncPreferences(
        enabled = preferences.getBoolean(SYNC_ENABLED_KEY, true),
        onlyNewFiles = preferences.getBoolean(SYNC_ONLY_NEW_KEY, true),
        verifyAfterCopy = preferences.getBoolean(SYNC_VERIFY_KEY, true),
        runOnUsbConnect = preferences.getBoolean(SYNC_ON_USB_KEY, true),
    )
    fun saveSyncPreferences(value: SyncPreferences) {
        preferences.edit().putBoolean(SYNC_ENABLED_KEY, value.enabled)
            .putBoolean(SYNC_ONLY_NEW_KEY, value.onlyNewFiles)
            .putBoolean(SYNC_VERIFY_KEY, value.verifyAfterCopy)
            .putBoolean(SYNC_ON_USB_KEY, value.runOnUsbConnect).apply()
    }
    fun persistUsbFolder(uri: Uri): PersistedFolder = persistFolder(uri, USB_URI_KEY, USB_LABEL_KEY)
    fun persistSyncFolder(uri: Uri, role: SyncFolderRole): PersistedFolder = when (role) {
        SyncFolderRole.SOURCE -> persistFolder(uri, SYNC_SOURCE_URI_KEY, SYNC_SOURCE_LABEL_KEY)
        SyncFolderRole.TARGET -> persistFolder(uri, SYNC_TARGET_URI_KEY, SYNC_TARGET_LABEL_KEY)
    }

    /** Stores a Shizuku-backed source path. It is intentionally never used as a write target. */
    fun persistProtectedSyncSource(absolutePath: String, label: String): PersistedFolder {
        require(absolutePath == "/storage/emulated/0/Android/data" ||
            absolutePath.startsWith("/storage/emulated/0/Android/data/") ||
            absolutePath == "/storage/emulated/0/Android/obb" ||
            absolutePath.startsWith("/storage/emulated/0/Android/obb/")) {
            "Only Android/data or Android/obb can be saved as a protected sync source."
        }
        val encoded = Uri.encode(absolutePath)
        preferences.edit()
            .putString(SYNC_SOURCE_URI_KEY, "shizuku://protected/$encoded")
            .putString(SYNC_SOURCE_LABEL_KEY, label)
            .apply()
        return PersistedFolder("shizuku://protected/$encoded", label)
    }

    fun listPhoneDirectory(path: String? = null): StorageListing {
        check(hasPhoneStorageAccess()) { "Phone storage access has not been granted." }
        val root = primaryStorageRoot()
        val directory = (path?.let(::File) ?: root).canonicalFile
        require(directory.isInside(root) && directory.isDirectory) { "The selected phone folder is unavailable." }
        return StorageListing(StorageLocation.PHONE, directory.displayPathFrom(root, "Phone"), directory.listFiles()
            ?.sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase(Locale.ROOT) })
            ?.map(::fileEntry).orEmpty())
    }

    fun listUsbDirectory(uriString: String? = null): StorageListing {
        val root = usbFolder() ?: error("Choose a USB or pendrive folder first.")
        check(isUsbGrantUsable()) { "The USB permission is no longer available." }
        val uri = Uri.parse(uriString ?: root.uri)
        val directory = if (uri.toString() == root.uri) DocumentFile.fromTreeUri(context, uri) else DocumentFile.fromSingleUri(context, uri)
            ?: error("The selected USB folder is no longer available.")
        require(directory.isDirectory) { "The selected USB location is not a folder." }
        return StorageListing(StorageLocation.USB, if (uriString == null) root.label else directory.name ?: root.label,
            directory.listFiles().sortedWith(compareByDescending<DocumentFile> { it.isDirectory }.thenBy { it.name.orEmpty().lowercase(Locale.ROOT) }).map(::documentEntry))
    }

    fun isUsbGrantUsable(): Boolean {
        val folder = usbFolder() ?: return false
        val uri = Uri.parse(folder.uri)
        return context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
    }

    private fun persistFolder(uri: Uri, uriKey: String, labelKey: String): PersistedFolder {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val label = DocumentFile.fromTreeUri(context, uri)?.name?.takeIf { it.isNotBlank() }
            ?: DocumentsContract.getTreeDocumentId(uri).substringAfterLast(':').ifBlank { "Selected folder" }
        preferences.edit().putString(uriKey, uri.toString()).putString(labelKey, label).apply()
        return PersistedFolder(uri.toString(), label)
    }
    private fun readFolder(uriKey: String, labelKey: String): PersistedFolder? {
        val uri = preferences.getString(uriKey, null) ?: return null
        return PersistedFolder(uri, preferences.getString(labelKey, null).orEmpty().ifBlank { "Selected folder" })
    }
    @Suppress("DEPRECATION") private fun primaryStorageRoot(): File = Environment.getExternalStorageDirectory().canonicalFile
    private fun fileEntry(file: File) = StorageEntry(file.absolutePath, file.name, file.isDirectory, if (file.isFile) file.length() else 0L, file.lastModified(), canWrite = file.canWrite())
    private fun documentEntry(file: DocumentFile) = StorageEntry(file.uri.toString(), file.name ?: "Unnamed item", file.isDirectory, if (file.isFile) file.length() else 0L, file.lastModified(), file.type, file.canWrite())
    private fun File.isInside(root: File): Boolean { val base = root.canonicalPath; val item = canonicalPath; return item == base || item.startsWith("$base${File.separator}") }
    private fun File.displayPathFrom(root: File, rootLabel: String): String = relativeToOrNull(root)?.path?.takeIf { it.isNotBlank() }?.let { "$rootLabel / $it" } ?: rootLabel
    private companion object {
        const val PREFERENCES_NAME = "storage_access"; const val USB_URI_KEY = "usb_tree_uri"; const val USB_LABEL_KEY = "usb_tree_label"
        const val SYNC_SOURCE_URI_KEY = "sync_source_uri"; const val SYNC_SOURCE_LABEL_KEY = "sync_source_label"; const val SYNC_TARGET_URI_KEY = "sync_target_uri"; const val SYNC_TARGET_LABEL_KEY = "sync_target_label"
        const val SYNC_ENABLED_KEY = "sync_enabled"; const val SYNC_ONLY_NEW_KEY = "sync_only_new"; const val SYNC_VERIFY_KEY = "sync_verify"; const val SYNC_ON_USB_KEY = "sync_on_usb"
    }
}
