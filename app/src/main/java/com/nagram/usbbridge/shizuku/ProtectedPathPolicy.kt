package com.nagram.usbbridge.shizuku

import java.io.File

/** Read-only Shizuku scope: no arbitrary shell browser or write operation. */
object ProtectedPathPolicy {
    const val ANDROID_DATA_ROOT = "/storage/emulated/0/Android/data"
    const val ANDROID_OBB_ROOT = "/storage/emulated/0/Android/obb"
    fun isAllowed(path: File): Boolean {
        val item = runCatching { path.canonicalFile }.getOrNull() ?: return false
        return item.isBelow(File(ANDROID_DATA_ROOT)) || item.isBelow(File(ANDROID_OBB_ROOT))
    }
    fun titleFor(path: String): String = when {
        path.startsWith(ANDROID_DATA_ROOT) -> "Android/data" + path.removePrefix(ANDROID_DATA_ROOT)
        path.startsWith(ANDROID_OBB_ROOT) -> "Android/obb" + path.removePrefix(ANDROID_OBB_ROOT)
        else -> "Protected folder"
    }
    private fun File.isBelow(root: File): Boolean { val base = root.canonicalPath; val item = canonicalPath; return item == base || item.startsWith("$base${File.separator}") }
}
