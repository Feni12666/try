package com.nagram.usbbridge.pro.video

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.nagram.usbbridge.pro.files.StorageKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class VideoRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("bridge", Context.MODE_PRIVATE)

    fun hasVideoPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= 33) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED ||
                (Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager())
        }
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED ||
            (Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager())
    }

    fun hasUsb(): Boolean = !prefs.getString("tree_uri", null).isNullOrBlank()

    suspend fun loadPhoneVideos(): List<VideoItem> = withContext(Dispatchers.IO) {
        if (!hasVideoPermission()) return@withContext emptyList()
        val resolver = context.contentResolver
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = buildList {
            add(MediaStore.Video.Media._ID)
            add(MediaStore.Video.Media.DISPLAY_NAME)
            add(MediaStore.Video.Media.SIZE)
            add(MediaStore.Video.Media.DURATION)
            add(MediaStore.Video.Media.WIDTH)
            add(MediaStore.Video.Media.HEIGHT)
            add(MediaStore.Video.Media.DATE_MODIFIED)
            if (Build.VERSION.SDK_INT >= 29) add(MediaStore.Video.Media.RELATIVE_PATH)
            else add(MediaStore.Video.Media.DATA)
        }.toTypedArray()
        val out = ArrayList<VideoItem>()
        resolver.query(
            collection,
            projection,
            null,
            null,
            "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val durationCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val widthCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val modifiedCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
            val pathCol = if (Build.VERSION.SDK_INT >= 29) c.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH) else c.getColumnIndex(MediaStore.Video.Media.DATA)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val uri = ContentUris.withAppendedId(collection, id)
                val rawPath = if (pathCol >= 0) c.getString(pathCol).orEmpty() else ""
                val folder = if (Build.VERSION.SDK_INT >= 29) {
                    rawPath.trim('/').ifBlank { "Phone Storage" }
                } else {
                    File(rawPath).parentFile?.absolutePath?.removePrefix(Environment.getExternalStorageDirectory().absolutePath)?.trim('/')?.ifBlank { "Phone Storage" } ?: "Phone Storage"
                }
                out += VideoItem(
                    id = "phone:$id",
                    name = c.getString(nameCol) ?: "Video",
                    uri = uri,
                    folder = folder,
                    size = c.getLong(sizeCol).coerceAtLeast(0L),
                    durationMs = c.getLong(durationCol).coerceAtLeast(0L),
                    width = c.getInt(widthCol).coerceAtLeast(0),
                    height = c.getInt(heightCol).coerceAtLeast(0),
                    modifiedMs = c.getLong(modifiedCol).coerceAtLeast(0L) * 1000L,
                    storage = StorageKind.PHONE
                )
            }
        }
        out
    }

    suspend fun loadUsbVideos(): List<VideoItem> = withContext(Dispatchers.IO) {
        val tree = prefs.getString("tree_uri", null)?.let(Uri::parse) ?: return@withContext emptyList()
        val root = DocumentFile.fromTreeUri(context, tree) ?: return@withContext emptyList()
        val out = ArrayList<VideoItem>()
        walkUsb(root, root.name ?: "USB / SSD", out)
        out.sortedByDescending { it.modifiedMs }
    }

    private fun walkUsb(dir: DocumentFile, relative: String, out: MutableList<VideoItem>) {
        val children = runCatching { dir.listFiles() }.getOrDefault(emptyArray())
        for (child in children) {
            if (child.isDirectory) {
                val childName = child.name ?: "Folder"
                walkUsb(child, "$relative/$childName", out)
            } else if (child.isFile && isVideo(child)) {
                val meta = readMetadata(child.uri)
                out += VideoItem(
                    id = "usb:${child.uri}",
                    name = child.name ?: "Video",
                    uri = child.uri,
                    folder = relative,
                    size = child.length().coerceAtLeast(0L),
                    durationMs = meta.first,
                    width = meta.second,
                    height = meta.third,
                    modifiedMs = child.lastModified().coerceAtLeast(0L),
                    storage = StorageKind.USB
                )
            }
        }
    }

    private fun isVideo(file: DocumentFile): Boolean {
        val type = file.type.orEmpty().lowercase(Locale.US)
        if (type.startsWith("video/")) return true
        val n = file.name.orEmpty().lowercase(Locale.US)
        return n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".mov") || n.endsWith(".webm") ||
            n.endsWith(".avi") || n.endsWith(".m4v") || n.endsWith(".3gp") || n.endsWith(".ts")
    }

    private fun readMetadata(uri: Uri): Triple<Long, Int, Int> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            Triple(
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            )
        } catch (_: Throwable) {
            Triple(0L, 0, 0)
        } finally {
            runCatching { retriever.release() }
        }
    }
}
