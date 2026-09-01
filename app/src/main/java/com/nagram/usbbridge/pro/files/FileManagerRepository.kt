package com.nagram.usbbridge.pro.files

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import com.nagram.usbbridge.RestrictedShizukuClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class FileManagerRepository(private val context: Context) {
    companion object {
        const val SHIZUKU_DATA = "/storage/emulated/0/Android/data"
        const val SHIZUKU_OBB = "/storage/emulated/0/Android/obb"
        private const val SEP = "\u0001"
    }

    private val prefs = context.getSharedPreferences("bridge", Context.MODE_PRIVATE)

    fun phoneRoot(): BrowserLocation {
        val root = Environment.getExternalStorageDirectory()
        return BrowserLocation(StorageKind.PHONE, root.absolutePath, "Phone Storage")
    }

    fun usbRoot(): BrowserLocation? {
        val uri = prefs.getString("tree_uri", null)?.let(Uri::parse) ?: return null
        val doc = DocumentFile.fromTreeUri(context, uri) ?: return null
        return BrowserLocation(StorageKind.USB, doc.uri.toString(), doc.name ?: "USB / SSD")
    }

    fun shizukuDataRoot(): BrowserLocation? = if (RestrictedShizukuClient.isReady()) {
        BrowserLocation(StorageKind.SHIZUKU, SHIZUKU_DATA, "Android/data • Shizuku")
    } else null

    fun shizukuObbRoot(): BrowserLocation? = if (RestrictedShizukuClient.isReady()) {
        BrowserLocation(StorageKind.SHIZUKU, SHIZUKU_OBB, "Android/obb • Shizuku")
    } else null

    fun shizukuReady(): Boolean = RestrictedShizukuClient.isReady()

    suspend fun list(location: BrowserLocation, showHidden: Boolean = false): List<BrowserEntry> = withContext(Dispatchers.IO) {
        val entries = when (location.storage) {
            StorageKind.PHONE -> listPhone(location, showHidden)
            StorageKind.USB -> listUsb(location, showHidden)
            StorageKind.SHIZUKU -> listShizuku(location, showHidden)
        }
        entries.sortedWith(compareBy<BrowserEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
    }

    private fun listPhone(location: BrowserLocation, showHidden: Boolean): List<BrowserEntry> {
        val dir = File(location.id)
        val children = dir.listFiles() ?: return emptyList()
        return children.asSequence()
            .filter { showHidden || !it.name.startsWith('.') }
            .filterNot { isRestrictedAndroidFolder(it) }
            .map {
                BrowserEntry(
                    id = it.absolutePath,
                    name = it.name,
                    isDirectory = it.isDirectory,
                    size = if (it.isFile) it.length() else 0L,
                    modified = it.lastModified(),
                    mimeType = null,
                    storage = StorageKind.PHONE
                )
            }.toList()
    }

    private fun isRestrictedAndroidFolder(file: File): Boolean {
        val p = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
        val root = Environment.getExternalStorageDirectory().absolutePath
        return p == "$root/Android/data" || p == "$root/Android/obb"
    }

    private fun listUsb(location: BrowserLocation, showHidden: Boolean): List<BrowserEntry> {
        val doc = documentFor(location) ?: return emptyList()
        return doc.listFiles().asSequence()
            .filter { showHidden || !(it.name ?: "").startsWith('.') }
            .map {
                BrowserEntry(
                    id = it.uri.toString(),
                    name = it.name ?: "Unnamed",
                    isDirectory = it.isDirectory,
                    size = if (it.isFile) it.length() else 0L,
                    modified = it.lastModified(),
                    mimeType = it.type,
                    storage = StorageKind.USB
                )
            }.toList()
    }

    private fun listShizuku(location: BrowserLocation, showHidden: Boolean): List<BrowserEntry> {
        val service = RestrictedShizukuClient.get() ?: error("Shizuku is not connected")
        return service.listEntries(location.id).orEmpty().mapNotNull { encoded ->
            val parts = encoded.split(SEP, limit = 5)
            if (parts.size < 5) return@mapNotNull null
            val name = parts[4]
            if (!showHidden && name.startsWith('.')) return@mapNotNull null
            BrowserEntry(
                id = parts[0],
                name = name,
                isDirectory = parts[1] == "D",
                size = parts[2].toLongOrNull() ?: 0L,
                modified = parts[3].toLongOrNull() ?: 0L,
                mimeType = null,
                storage = StorageKind.SHIZUKU
            )
        }
    }

    fun childLocation(entry: BrowserEntry): BrowserLocation? {
        if (!entry.isDirectory) return null
        return BrowserLocation(entry.storage, entry.id, entry.name)
    }

    suspend fun createFolder(location: BrowserLocation, name: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(name.isNotBlank())
            when (location.storage) {
                StorageKind.PHONE -> {
                    val target = File(location.id, name.trim())
                    check(!target.exists()) { "A file or folder with this name already exists" }
                    check(target.mkdir()) { "Could not create folder" }
                }
                StorageKind.USB -> {
                    val parent = documentFor(location) ?: error("USB folder is unavailable")
                    check(parent.findFile(name.trim()) == null) { "A file or folder with this name already exists" }
                    check(parent.createDirectory(name.trim()) != null) { "Could not create folder" }
                }
                StorageKind.SHIZUKU -> {
                    val service = RestrictedShizukuClient.get() ?: error("Shizuku is not connected")
                    check(service.createDirectory(location.id, name.trim())) { "Could not create restricted folder" }
                }
            }
        }
    }

    suspend fun rename(entry: BrowserEntry, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(newName.isNotBlank())
            when (entry.storage) {
                StorageKind.PHONE -> {
                    val source = File(entry.id)
                    val target = File(source.parentFile, newName.trim())
                    check(!target.exists()) { "That name already exists" }
                    check(source.renameTo(target)) { "Rename failed" }
                }
                StorageKind.USB -> {
                    val doc = DocumentFile.fromSingleUri(context, Uri.parse(entry.id)) ?: error("USB item unavailable")
                    check(doc.renameTo(newName.trim())) { "Rename failed" }
                }
                StorageKind.SHIZUKU -> {
                    val service = RestrictedShizukuClient.get() ?: error("Shizuku is not connected")
                    check(service.renamePath(entry.id, newName.trim())) { "Restricted rename failed" }
                }
            }
        }
    }

    suspend fun delete(entries: List<BrowserEntry>): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            for (entry in entries) {
                when (entry.storage) {
                    StorageKind.PHONE -> check(deletePhoneRecursively(File(entry.id))) { "Could not delete ${entry.name}" }
                    StorageKind.USB -> {
                        val doc = DocumentFile.fromSingleUri(context, Uri.parse(entry.id)) ?: error("USB item unavailable")
                        check(doc.delete()) { "Could not delete ${entry.name}" }
                    }
                    StorageKind.SHIZUKU -> {
                        val service = RestrictedShizukuClient.get() ?: error("Shizuku is not connected")
                        check(service.deletePath(entry.id)) { "Could not delete ${entry.name}" }
                    }
                }
            }
        }
    }

    private fun deletePhoneRecursively(file: File): Boolean {
        if (file.isDirectory) file.listFiles()?.forEach { if (!deletePhoneRecursively(it)) return false }
        return file.delete()
    }

    private fun documentFor(location: BrowserLocation): DocumentFile? {
        val uri = Uri.parse(location.id)
        val rootText = prefs.getString("tree_uri", null)
        return if (rootText == location.id) DocumentFile.fromTreeUri(context, uri)
        else DocumentFile.fromSingleUri(context, uri)
    }
}
