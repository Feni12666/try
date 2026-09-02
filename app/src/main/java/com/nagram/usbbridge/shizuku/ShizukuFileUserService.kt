package com.nagram.usbbridge.shizuku

import android.os.ParcelFileDescriptor
import java.io.File

/** User-approved shell/root service; it only exposes protected paths for reading. */
class ShizukuFileUserService : IShizukuFileService.Stub() {
    override fun listEntries(absolutePath: String): MutableList<RemoteFileEntry> {
        val folder = allowedFile(absolutePath)
        require(folder.isDirectory) { "The protected path is not a folder." }
        return folder.listFiles()?.sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
            ?.map { RemoteFileEntry(it.absolutePath, it.name, it.isDirectory, if (it.isFile) it.length() else 0L, it.lastModified()) }?.toMutableList() ?: mutableListOf()
    }
    override fun openForRead(absolutePath: String): ParcelFileDescriptor {
        val file = allowedFile(absolutePath)
        require(file.isFile) { "Only files can be opened for transfer." }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }
    @Suppress("unused") fun destroy() = Unit
    private fun allowedFile(path: String): File {
        val file = File(path).canonicalFile
        check(ProtectedPathPolicy.isAllowed(file)) { "Only Android/data and Android/obb are available through this service." }
        check(file.exists() && file.canRead()) { "The requested protected path is unavailable." }
        return file
    }
}
