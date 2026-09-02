package com.nagram.usbbridge.transfer

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.nagram.usbbridge.shizuku.ShizukuFileGateway
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * Copies only new videos from one selected folder to one selected target folder.
 * Every copied file is read back and SHA-256 verified before it is counted as done.
 * It never deletes a source file.
 */
class SmartSyncEngine(private val context: Context) {
    private val resolver = context.contentResolver

    suspend fun syncSaf(
        sourceTree: Uri,
        targetTree: Uri,
        onProgress: (SyncProgress) -> Unit,
    ): SyncResult {
        val source = requireNotNull(DocumentFile.fromTreeUri(context, sourceTree)) { "The source folder is unavailable." }
        val videos = mutableListOf<SyncVideo>()
        collectSafVideos(source, videos)
        return copyToTarget(videos, targetTree, onProgress)
    }

    suspend fun syncShizuku(
        sourcePath: String,
        targetTree: Uri,
        shizuku: ShizukuFileGateway,
        onProgress: (SyncProgress) -> Unit,
    ): SyncResult {
        val videos = mutableListOf<SyncVideo>()
        collectShizukuVideos(sourcePath, shizuku, videos)
        return copyToTarget(videos, targetTree, onProgress)
    }

    private suspend fun collectSafVideos(folder: DocumentFile, output: MutableList<SyncVideo>) {
        coroutineContext.ensureActive()
        folder.listFiles().forEach { entry ->
            if (entry.isDirectory) collectSafVideos(entry, output)
            else if (entry.isFile && isVideo(entry.name, entry.type)) {
                output += SyncVideo(
                    id = entry.uri.toString(),
                    name = entry.name ?: "video",
                    sizeBytes = entry.length(),
                    openInput = { requireNotNull(resolver.openInputStream(entry.uri)) { "Could not read ${entry.name}" } },
                )
            }
        }
    }

    private suspend fun collectShizukuVideos(
        folderPath: String,
        shizuku: ShizukuFileGateway,
        output: MutableList<SyncVideo>,
    ) {
        coroutineContext.ensureActive()
        shizuku.listEntries(folderPath).forEach { entry ->
            if (entry.isDirectory) collectShizukuVideos(entry.id, shizuku, output)
            else if (isVideo(entry.displayName, entry.mimeType)) {
                output += SyncVideo(
                    id = entry.id,
                    name = entry.displayName,
                    sizeBytes = entry.sizeBytes,
                    openInput = {
                        val descriptor: ParcelFileDescriptor = shizuku.openForRead(entry.id)
                        ParcelFileDescriptor.AutoCloseInputStream(descriptor)
                    },
                )
            }
        }
    }

    private suspend fun copyToTarget(
        videos: List<SyncVideo>,
        targetTree: Uri,
        onProgress: (SyncProgress) -> Unit,
    ): SyncResult {
        val target = requireNotNull(DocumentFile.fromTreeUri(context, targetTree)) { "The USB target folder is unavailable." }
        check(target.canWrite()) { "The selected USB folder is read-only." }
        val existingBySize = mutableMapOf<Long, MutableList<DocumentFile>>()
        collectTargetVideos(target, existingBySize)

        var copied = 0
        var skipped = 0
        var failed = 0
        videos.forEachIndexed { index, video ->
            coroutineContext.ensureActive()
            val base = SyncProgress(
                totalVideos = videos.size,
                completedVideos = index,
                copiedVideos = copied,
                skippedVideos = skipped,
                failedVideos = failed,
                currentName = video.name,
                currentTotalBytes = video.sizeBytes,
                message = "Checking ${video.name}",
                isRunning = true,
            )
            onProgress(base)
            try {
                val candidates = existingBySize[video.sizeBytes].orEmpty()
                val sourceHash = sha256(video.openInput(), video.sizeBytes) { bytes ->
                    onProgress(base.copy(currentBytes = bytes, message = "Checking ${video.name}"))
                }
                var duplicate = false
                for (candidate in candidates) {
                    if (sha256(requireNotNull(resolver.openInputStream(candidate.uri)), candidate.length()) {} == sourceHash) {
                        duplicate = true
                        break
                    }
                }
                if (duplicate) {
                    skipped += 1
                } else {
                    val copiedFile = copyVerified(target, video, sourceHash) { bytes ->
                        onProgress(base.copy(currentBytes = bytes, message = "Copying ${video.name}"))
                    }
                    existingBySize.getOrPut(copiedFile.length()) { mutableListOf() } += copiedFile
                    copied += 1
                }
            } catch (_: Exception) {
                failed += 1
            }
            onProgress(
                base.copy(
                    completedVideos = index + 1,
                    copiedVideos = copied,
                    skippedVideos = skipped,
                    failedVideos = failed,
                    currentBytes = video.sizeBytes,
                    message = "Processed ${index + 1} of ${videos.size}",
                ),
            )
        }
        return SyncResult(copied, skipped, failed)
    }

    private suspend fun collectTargetVideos(folder: DocumentFile, output: MutableMap<Long, MutableList<DocumentFile>>) {
        coroutineContext.ensureActive()
        folder.listFiles().forEach { entry ->
            if (entry.isDirectory) collectTargetVideos(entry, output)
            else if (entry.isFile && isVideo(entry.name, entry.type)) output.getOrPut(entry.length()) { mutableListOf() } += entry
        }
    }

    private suspend fun copyVerified(
        target: DocumentFile,
        video: SyncVideo,
        sourceHash: String,
        onBytes: (Long) -> Unit,
    ): DocumentFile {
        val temporaryName = ".${video.name}.usb-video-manager-part"
        val temporary = target.createFile(mimeFor(video.name), temporaryName)
            ?: error("Could not create a temporary file on the USB drive.")
        try {
            val copiedHash = copyAndHash(video.openInput(), requireNotNull(resolver.openOutputStream(temporary.uri, "w")), video.sizeBytes, onBytes)
            check(copiedHash == sourceHash) { "The copied file hash did not match its source." }
            val targetHash = sha256(requireNotNull(resolver.openInputStream(temporary.uri)), temporary.length()) {}
            check(targetHash == sourceHash) { "The USB verification hash did not match its source." }

            val finalName = uniqueName(target, video.name)
            if (temporary.renameTo(finalName)) return temporary

            val final = target.createFile(mimeFor(video.name), finalName)
                ?: error("Could not finalize the copied video on the USB drive.")
            try {
                val finalHash = copyAndHash(requireNotNull(resolver.openInputStream(temporary.uri)), requireNotNull(resolver.openOutputStream(final.uri, "w")), temporary.length()) {}
                check(finalHash == sourceHash) { "The finalized file hash did not match its source." }
                check(temporary.delete()) { "Could not clean up the temporary USB file." }
                return final
            } catch (error: Exception) {
                final.delete()
                throw error
            }
        } catch (error: Exception) {
            temporary.delete()
            throw error
        }
    }

    private fun uniqueName(parent: DocumentFile, desired: String): String {
        if (parent.findFile(desired) == null) return desired
        val dot = desired.lastIndexOf('.')
        val stem = if (dot > 0) desired.substring(0, dot) else desired
        val extension = if (dot > 0) desired.substring(dot) else ""
        var index = 1
        while (parent.findFile("$stem ($index)$extension") != null) index += 1
        return "$stem ($index)$extension"
    }

    private fun mimeFor(name: String): String = MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase(Locale.ROOT)) ?: "video/*"

    private fun isVideo(name: String?, mime: String?): Boolean = mime?.startsWith("video/") == true ||
        name?.substringAfterLast('.', "").lowercase(Locale.ROOT) in VIDEO_EXTENSIONS

    private suspend fun sha256(input: InputStream, total: Long, onBytes: (Long) -> Unit): String =
        BufferedInputStream(input).use { stream ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(BUFFER_SIZE)
            var copied = 0L
            while (true) {
                coroutineContext.ensureActive()
                val count = stream.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
                copied += count
                onBytes(copied.coerceAtMost(total))
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }

    private suspend fun copyAndHash(input: InputStream, output: java.io.OutputStream, total: Long, onBytes: (Long) -> Unit): String =
        BufferedInputStream(input).use { source ->
            BufferedOutputStream(output).use { destination ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(BUFFER_SIZE)
                var copied = 0L
                while (true) {
                    coroutineContext.ensureActive()
                    val count = source.read(buffer)
                    if (count < 0) break
                    destination.write(buffer, 0, count)
                    digest.update(buffer, 0, count)
                    copied += count
                    onBytes(copied.coerceAtMost(total))
                }
                destination.flush()
                digest.digest().joinToString("") { "%02x".format(it) }
            }
        }

    private data class SyncVideo(
        val id: String,
        val name: String,
        val sizeBytes: Long,
        val openInput: suspend () -> InputStream,
    )

    private companion object {
        const val BUFFER_SIZE = 256 * 1024
        val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "mov", "m4v", "3gp", "avi", "ts", "flv")
    }
}
