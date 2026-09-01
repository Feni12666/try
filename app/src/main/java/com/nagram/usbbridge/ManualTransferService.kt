package com.nagram.usbbridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import com.nagram.usbbridge.pro.files.ConflictPolicy
import com.nagram.usbbridge.pro.files.StorageKind
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

/**
 * User-initiated manual transfer engine.
 *
 * Important invariants:
 *  - It never watches Nagram or any fixed source folder.
 *  - Source items and destination folder are explicitly chosen by the user.
 *  - MOVE = copy -> SHA-256 verify -> selected cleanup delay -> destination revalidation -> source delete.
 *  - COPY never deletes the source.
 *  - Two user-selected files may copy concurrently; pending items are reported as a ready queue.
 */
class ManualTransferService : Service() {
    companion object {
        const val ACTION_START = "com.nagram.usbbridge.manual.START"
        const val ACTION_PAUSE = "com.nagram.usbbridge.manual.PAUSE"
        const val ACTION_RESUME = "com.nagram.usbbridge.manual.RESUME"
        const val ACTION_CANCEL = "com.nagram.usbbridge.manual.CANCEL"

        const val EXTRA_SOURCE_KIND = "source_kind"
        const val EXTRA_SOURCE_IDS = "source_ids"
        const val EXTRA_SOURCE_NAMES = "source_names"
        const val EXTRA_SOURCE_DIRS = "source_dirs"
        const val EXTRA_DEST_KIND = "dest_kind"
        const val EXTRA_DEST_ID = "dest_id"
        const val EXTRA_MOVE = "move"
        const val EXTRA_CONFLICT = "conflict"

        private const val CHANNEL = "manual-transfer"
        private const val NOTIFY_ID = 4021
        private const val MAX_ACTIVE = 2
        private const val BUFFER = 4 * 1024 * 1024

        fun action(context: Context, action: String): Intent =
            Intent(context, ManualTransferService::class.java).setAction(action)
    }

    private val prefs by lazy { getSharedPreferences("bridge", MODE_PRIVATE) }
    private val cancelled = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)
    private val pauseLock = Object()
    private val active = AtomicInteger(0)
    private val pending = AtomicInteger(0)
    private val cleanupPending = AtomicInteger(0)
    private val copiedBytes = AtomicLong(0L)
    private val totalBytes = AtomicLong(0L)
    private val startedAt = AtomicLong(0L)
    private val finishedSubmitting = AtomicBoolean(false)
    private var workers = Executors.newFixedThreadPool(MAX_ACTIVE)
    private var cleanupScheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> {
                paused.set(true)
                updatePrefs(status = "Paused by you")
                updateNotification("Transfer paused")
                return START_NOT_STICKY
            }
            ACTION_RESUME -> {
                paused.set(false)
                synchronized(pauseLock) { pauseLock.notifyAll() }
                updatePrefs(status = "Resuming safely…")
                updateNotification("Resuming transfer")
                return START_NOT_STICKY
            }
            ACTION_CANCEL -> {
                cancelled.set(true)
                paused.set(false)
                synchronized(pauseLock) { pauseLock.notifyAll() }
                updatePrefs(status = "Cancelled — originals kept")
                updateNotification("Cancelling safely…")
                maybeFinish(force = true)
                return START_NOT_STICKY
            }
            ACTION_START -> intent?.let(::startNewOperation)
        }
        return START_NOT_STICKY
    }

    private fun startNewOperation(intent: Intent) {
        if (prefs.getBoolean("manual_op_running", false)) return
        val sourceKind = runCatching { StorageKind.valueOf(intent.getStringExtra(EXTRA_SOURCE_KIND) ?: "") }.getOrNull() ?: return
        val destKind = runCatching { StorageKind.valueOf(intent.getStringExtra(EXTRA_DEST_KIND) ?: "") }.getOrNull() ?: return
        val ids = intent.getStringArrayListExtra(EXTRA_SOURCE_IDS) ?: return
        val names = intent.getStringArrayListExtra(EXTRA_SOURCE_NAMES) ?: return
        val dirs = intent.getBooleanArrayExtra(EXTRA_SOURCE_DIRS) ?: BooleanArray(ids.size)
        val destId = intent.getStringExtra(EXTRA_DEST_ID) ?: return
        val move = intent.getBooleanExtra(EXTRA_MOVE, false)
        val conflict = runCatching { ConflictPolicy.valueOf(intent.getStringExtra(EXTRA_CONFLICT) ?: "KEEP_BOTH") }
            .getOrDefault(ConflictPolicy.KEEP_BOTH)

        if (ids.isEmpty() || ids.size != names.size) return
        cancelled.set(false)
        paused.set(false)
        finishedSubmitting.set(false)
        copiedBytes.set(0L)
        totalBytes.set(0L)
        startedAt.set(System.currentTimeMillis())
        pending.set(ids.size)

        val specs = ids.indices.map { i -> SourceSpec(sourceKind, ids[i], names[i], dirs.getOrElse(i) { false }) }
        totalBytes.set(specs.sumOf { calculateSize(it) })

        prefs.edit()
            .putBoolean("manual_op_running", true)
            .putBoolean("manual_op_paused", false)
            .putInt("manual_op_active", 0)
            .putInt("manual_op_ready", minOf(10, specs.size))
            .putInt("manual_op_progress", 0)
            .putLong("manual_op_speed", 0L)
            .putLong("manual_op_eta", -1L)
            .putString("manual_op_status", if (move) "Moving selected items safely" else "Copying selected items")
            .apply()

        val initial = notification(if (move) "Preparing safe move…" else "Preparing copy…")
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFY_ID, initial, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else startForeground(NOTIFY_ID, initial)

        for (spec in specs) {
            workers.execute {
                active.incrementAndGet()
                pending.decrementAndGet()
                updateQueuePrefs(spec.name)
                try {
                    if (!cancelled.get()) {
                        copyTopLevel(spec, DestSpec(destKind, destId), move, conflict)
                    }
                } catch (t: Throwable) {
                    updatePrefs(status = "${spec.name}: ${t.message ?: "transfer failed"} — original kept")
                } finally {
                    active.decrementAndGet()
                    updateQueuePrefs(null)
                    maybeFinish()
                }
            }
        }
        finishedSubmitting.set(true)
        maybeFinish()
    }

    private fun copyTopLevel(source: SourceSpec, dest: DestSpec, move: Boolean, conflict: ConflictPolicy) {
        awaitIfPaused()
        if (cancelled.get()) return
        if (source.isDirectory) {
            val created = ensureDestinationDirectory(dest, source.name, conflict) ?: return
            listChildren(source).forEach { child ->
                if (cancelled.get()) return
                copyTopLevel(child, created, move, conflict)
            }
            if (move) scheduleDirectoryCleanup(source)
        } else {
            copySingleFile(source, dest, move, conflict)
        }
    }

    private fun copySingleFile(source: SourceSpec, dest: DestSpec, move: Boolean, conflict: ConflictPolicy) {
        awaitIfPaused()
        if (cancelled.get()) return
        val sourceBytes = sourceSize(source)
        val sourceMtime = sourceModified(source)
        if (sourceBytes < 0) error("Source is unavailable")

        // Anti-duplicate: exact bytes -> quick sampled fingerprint -> full SHA-256 only for candidates.
        val sourceQuickCache = arrayOfNulls<String>(1)
        val sourceHashCache = arrayOfNulls<String>(1)
        val duplicate = destinationChildren(dest)
            .asSequence()
            .filter { !it.isDirectory && it.size == sourceBytes }
            .firstOrNull { candidate ->
                val srcQuick = sourceQuickCache[0] ?: quickSource(source, sourceBytes).also { sourceQuickCache[0] = it }
                val dstQuick = quickDestination(candidate, sourceBytes)
                if (srcQuick == null || dstQuick == null || srcQuick != dstQuick) return@firstOrNull false
                val srcHash = sourceHashCache[0] ?: sha256Source(source).also { sourceHashCache[0] = it }
                srcHash == sha256Destination(candidate)
            }
        if (duplicate != null) {
            updatePrefs(status = "Duplicate skipped: ${source.name}")
            if (move) {
                val hash = sourceHashCache[0] ?: sha256Source(source)
                scheduleFileCleanup(source, sourceBytes, sourceMtime, duplicate, hash)
            }
            return
        }

        val existing = destinationChildren(dest).firstOrNull { it.name == source.name }
        if (existing != null && conflict == ConflictPolicy.SKIP) {
            updatePrefs(status = "Skipped existing name: ${source.name} — original kept")
            return
        }
        val finalName = when {
            existing == null -> source.name
            conflict == ConflictPolicy.KEEP_BOTH -> uniqueName(dest, source.name)
            else -> source.name
        }
        val tempName = uniqueTempName(dest, "$finalName.part")
        val temp = createDestinationFile(dest, tempName, sourceMime(source)) ?: error("Could not create destination")

        val digest = MessageDigest.getInstance("SHA-256")
        var localCopied = 0L
        try {
            openSource(source).use { input ->
                openDestinationOutput(temp).use { output ->
                    val buffer = ByteArray(BUFFER)
                    while (true) {
                        awaitIfPaused()
                        if (cancelled.get()) throw CancelledException()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        localCopied += count
                        copiedBytes.addAndGet(count.toLong())
                        updateProgress(source.name)
                    }
                    output.flush()
                }
            }
            val sourceHash = hex(digest.digest())
            if (destinationSize(temp) != sourceBytes) error("Size verification failed")
            val destHash = sha256Destination(temp)
            if (sourceHash != destHash) error("Hash verification failed")

            // Source mutation guard: source must still match the original size/mtime before finalization.
            if (sourceSize(source) != sourceBytes || (sourceMtime > 0 && sourceModified(source) != sourceMtime)) {
                error("Source changed during copy")
            }

            if (existing != null && conflict == ConflictPolicy.REPLACE) {
                // Existing destination is kept until the replacement has been fully verified.
                check(deleteDestination(existing)) { "Could not replace existing destination" }
            }
            check(renameDestination(temp, finalName)) { "Could not finalize destination" }
            val finalEntry = findDestinationByName(dest, finalName) ?: error("Final destination missing")
            if (destinationSize(finalEntry) != sourceBytes || sha256Destination(finalEntry) != sourceHash) {
                error("Final revalidation failed")
            }

            updatePrefs(status = if (move) "Verified • cleanup pending: ${source.name}" else "Copied & verified: ${source.name}")
            if (move) scheduleFileCleanup(source, sourceBytes, sourceMtime, finalEntry, sourceHash)
        } catch (c: CancelledException) {
            deleteDestination(temp)
            updatePrefs(status = "Cancelled — original kept")
        } catch (t: Throwable) {
            deleteDestination(temp)
            // Adjust aggregate bytes because this item may retry only by explicit user action.
            if (localCopied > 0) copiedBytes.addAndGet(-localCopied)
            throw t
        }
    }

    private fun scheduleFileCleanup(source: SourceSpec, size: Long, modified: Long, destination: DestEntry, hash: String) {
        cleanupPending.incrementAndGet()
        val delay = prefs.getLong("cleanup_delay_ms", 3L * 60_000L).coerceIn(60_000L, 5L * 60_000L)
        cleanupScheduler.schedule({
            try {
                val safeToDelete = !cancelled.get() &&
                    sourceSize(source) == size &&
                    (modified <= 0 || sourceModified(source) == modified) &&
                    destinationSize(destination) == size &&
                    sha256Destination(destination) == hash &&
                    sha256Source(source) == hash
                if (safeToDelete && deleteSource(source)) {
                    updatePrefs(status = "Moved safely: ${source.name}")
                } else {
                    updatePrefs(status = "Cleanup held safely: ${source.name} — original kept")
                }
            } catch (_: Throwable) {
                updatePrefs(status = "Cleanup held safely: ${source.name} — original kept")
            } finally {
                cleanupPending.decrementAndGet()
                maybeFinish()
            }
        }, delay, TimeUnit.MILLISECONDS)
    }

    private fun scheduleDirectoryCleanup(source: SourceSpec) {
        cleanupPending.incrementAndGet()
        val delay = prefs.getLong("cleanup_delay_ms", 3L * 60_000L).coerceIn(60_000L, 5L * 60_000L) + 10_000L
        cleanupScheduler.schedule({
            try { deleteSourceDirectoryIfEmpty(source) } catch (_: Throwable) {}
            finally { cleanupPending.decrementAndGet(); maybeFinish() }
        }, delay, TimeUnit.MILLISECONDS)
    }

    private fun awaitIfPaused() {
        while (paused.get() && !cancelled.get()) {
            prefs.edit().putBoolean("manual_op_paused", true).apply()
            synchronized(pauseLock) { pauseLock.wait(500L) }
        }
        prefs.edit().putBoolean("manual_op_paused", false).apply()
    }

    private fun updateProgress(name: String) {
        val done = copiedBytes.get().coerceAtLeast(0L)
        val total = totalBytes.get().coerceAtLeast(1L)
        val elapsed = max(1L, System.currentTimeMillis() - startedAt.get())
        val speed = done * 1000L / elapsed
        val remaining = (total - done).coerceAtLeast(0L)
        val eta = if (speed > 0) remaining / speed else -1L
        val percent = ((done * 100L) / total).toInt().coerceIn(0, 100)
        prefs.edit()
            .putString("manual_op_name", name)
            .putInt("manual_op_progress", percent)
            .putLong("manual_op_speed", speed)
            .putLong("manual_op_eta", eta)
            .apply()
        updateNotification("$percent% • $name")
    }

    private fun updateQueuePrefs(name: String?) {
        val edit = prefs.edit()
            .putInt("manual_op_active", active.get())
            .putInt("manual_op_ready", minOf(10, pending.get().coerceAtLeast(0)))
        if (name != null) edit.putString("manual_op_name", name)
        edit.apply()
    }

    private fun updatePrefs(status: String) {
        prefs.edit().putString("manual_op_status", status).apply()
    }

    private fun maybeFinish(force: Boolean = false) {
        if (!force && (!finishedSubmitting.get() || active.get() > 0 || cleanupPending.get() > 0)) return
        prefs.edit()
            .putBoolean("manual_op_running", false)
            .putBoolean("manual_op_paused", false)
            .putInt("manual_op_active", 0)
            .putInt("manual_op_ready", 0)
            .putString("manual_op_name", null)
            .putLong("manual_op_speed", 0L)
            .putLong("manual_op_eta", -1L)
            .apply()
        updateNotification(if (cancelled.get()) "Cancelled — originals kept" else "Operation complete")
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    override fun onDestroy() {
        paused.set(false)
        synchronized(pauseLock) { pauseLock.notifyAll() }
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "File transfers", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 10, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pauseAction = if (paused.get()) ACTION_RESUME else ACTION_PAUSE
        val pauseLabel = if (paused.get()) "Resume" else "Pause"
        val pause = PendingIntent.getService(
            this, 11, action(this, pauseAction), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cancel = PendingIntent.getService(
            this, 12, action(this, ACTION_CANCEL), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val progress = prefs.getInt("manual_op_progress", 0).coerceIn(0, 100)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_app)
            .setContentTitle("Video & Storage Pro")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(prefs.getBoolean("manual_op_running", false))
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, false)
            .addAction(0, pauseLabel, pause)
            .addAction(0, "Cancel", cancel)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFY_ID, notification(text))
    }

    // ---- Storage abstraction helpers ----

    private data class SourceSpec(val kind: StorageKind, val id: String, val name: String, val isDirectory: Boolean)
    private data class DestSpec(val kind: StorageKind, val id: String)
    private data class DestEntry(val kind: StorageKind, val id: String, val name: String, val isDirectory: Boolean, val size: Long)
    private class CancelledException : RuntimeException()

    private fun calculateSize(source: SourceSpec): Long {
        if (!source.isDirectory) return sourceSize(source).coerceAtLeast(0L)
        return listChildren(source).sumOf { calculateSize(it) }
    }

    private fun sourceSize(source: SourceSpec): Long = when (source.kind) {
        StorageKind.PHONE -> File(source.id).let { if (it.isFile) it.length() else 0L }
        StorageKind.USB -> DocumentFile.fromSingleUri(this, Uri.parse(source.id))?.let { if (it.isFile) it.length() else 0L } ?: -1L
        StorageKind.SHIZUKU -> RestrictedShizukuClient.get()?.getFileSize(source.id) ?: -1L
    }

    private fun sourceModified(source: SourceSpec): Long = when (source.kind) {
        StorageKind.PHONE -> File(source.id).lastModified()
        StorageKind.USB -> DocumentFile.fromSingleUri(this, Uri.parse(source.id))?.lastModified() ?: -1L
        StorageKind.SHIZUKU -> RestrictedShizukuClient.get()?.getLastModified(source.id) ?: -1L
    }

    private fun sourceMime(source: SourceSpec): String = when (source.kind) {
        StorageKind.PHONE -> contentResolver.getType(Uri.fromFile(File(source.id))) ?: "application/octet-stream"
        StorageKind.USB -> contentResolver.getType(Uri.parse(source.id)) ?: "application/octet-stream"
        StorageKind.SHIZUKU -> "application/octet-stream"
    }

    private fun openSource(source: SourceSpec): InputStream = when (source.kind) {
        StorageKind.PHONE -> FileInputStream(File(source.id))
        StorageKind.USB -> contentResolver.openInputStream(Uri.parse(source.id)) ?: error("Cannot read source")
        StorageKind.SHIZUKU -> {
            val pfd = RestrictedShizukuClient.get()?.openRead(source.id) ?: error("Cannot read restricted source")
            ParcelFileDescriptor.AutoCloseInputStream(pfd)
        }
    }

    private fun listChildren(source: SourceSpec): List<SourceSpec> = when (source.kind) {
        StorageKind.PHONE -> File(source.id).listFiles()?.map { SourceSpec(StorageKind.PHONE, it.absolutePath, it.name, it.isDirectory) } ?: emptyList()
        StorageKind.USB -> DocumentFile.fromSingleUri(this, Uri.parse(source.id))?.listFiles()?.map {
            SourceSpec(StorageKind.USB, it.uri.toString(), it.name ?: "Unnamed", it.isDirectory)
        } ?: emptyList()
        StorageKind.SHIZUKU -> RestrictedShizukuClient.get()?.listEntries(source.id).orEmpty().mapNotNull { encoded ->
            val parts = encoded.split("\u0001", limit = 5)
            if (parts.size < 5) null else SourceSpec(StorageKind.SHIZUKU, parts[0], parts[4], parts[1] == "D")
        }
    }

    private fun deleteSource(source: SourceSpec): Boolean = when (source.kind) {
        StorageKind.PHONE -> File(source.id).delete()
        StorageKind.USB -> DocumentFile.fromSingleUri(this, Uri.parse(source.id))?.delete() == true
        StorageKind.SHIZUKU -> RestrictedShizukuClient.get()?.deletePath(source.id) == true
    }

    private fun deleteSourceDirectoryIfEmpty(source: SourceSpec) {
        if (!source.isDirectory) return
        val empty = listChildren(source).isEmpty()
        if (empty) deleteSource(source)
    }

    private fun destinationDocument(dest: DestSpec): DocumentFile? =
        DocumentFile.fromSingleUri(this, Uri.parse(dest.id)) ?: DocumentFile.fromTreeUri(this, Uri.parse(dest.id))

    private fun destinationChildren(dest: DestSpec): List<DestEntry> = when (dest.kind) {
        StorageKind.PHONE -> File(dest.id).listFiles()?.map { DestEntry(StorageKind.PHONE, it.absolutePath, it.name, it.isDirectory, if (it.isFile) it.length() else 0L) } ?: emptyList()
        StorageKind.USB -> destinationDocument(dest)?.listFiles()?.map {
            DestEntry(StorageKind.USB, it.uri.toString(), it.name ?: "Unnamed", it.isDirectory, if (it.isFile) it.length() else 0L)
        } ?: emptyList()
        StorageKind.SHIZUKU -> RestrictedShizukuClient.get()?.listEntries(dest.id).orEmpty().mapNotNull { encoded ->
            val parts = encoded.split("\u0001", limit = 5)
            if (parts.size < 5) null else DestEntry(
                StorageKind.SHIZUKU,
                parts[0],
                parts[4],
                parts[1] == "D",
                parts[2].toLongOrNull() ?: 0L
            )
        }
    }

    private fun findDestinationByName(dest: DestSpec, name: String): DestEntry? = destinationChildren(dest).firstOrNull { it.name == name }

    private fun ensureDestinationDirectory(dest: DestSpec, name: String, conflict: ConflictPolicy): DestSpec? {
        val existing = destinationChildren(dest).firstOrNull { it.name == name }
        if (existing != null) {
            if (existing.isDirectory) return DestSpec(existing.kind, existing.id)
            if (conflict == ConflictPolicy.SKIP) return null
        }
        val finalName = if (existing != null && conflict == ConflictPolicy.KEEP_BOTH) uniqueName(dest, name) else name
        if (existing != null && conflict == ConflictPolicy.REPLACE) deleteDestination(existing)
        return when (dest.kind) {
            StorageKind.PHONE -> {
                val dir = File(dest.id, finalName)
                if (!dir.exists() && !dir.mkdir()) error("Could not create destination folder")
                DestSpec(StorageKind.PHONE, dir.absolutePath)
            }
            StorageKind.USB -> {
                val parent = destinationDocument(dest) ?: error("USB destination unavailable")
                val child = parent.createDirectory(finalName) ?: error("Could not create destination folder")
                DestSpec(StorageKind.USB, child.uri.toString())
            }
            StorageKind.SHIZUKU -> {
                val service = RestrictedShizukuClient.get() ?: error("Shizuku is not connected")
                check(service.createDirectory(dest.id, finalName)) { "Could not create restricted destination folder" }
                DestSpec(StorageKind.SHIZUKU, File(dest.id, finalName).absolutePath)
            }
        }
    }

    private fun createDestinationFile(dest: DestSpec, name: String, mime: String): DestEntry? {
        return when (dest.kind) {
            StorageKind.PHONE -> {
                val file = File(dest.id, name)
                file.parentFile?.mkdirs()
                if (!file.createNewFile()) return null
                DestEntry(StorageKind.PHONE, file.absolutePath, file.name, false, 0L)
            }
            StorageKind.USB -> {
                val parent = destinationDocument(dest) ?: return null
                val doc = parent.createFile(mime, name) ?: return null
                DestEntry(StorageKind.USB, doc.uri.toString(), doc.name ?: name, false, 0L)
            }
            StorageKind.SHIZUKU -> {
                val service = RestrictedShizukuClient.get() ?: return null
                val path = service.createFile(dest.id, name) ?: return null
                DestEntry(StorageKind.SHIZUKU, path, name, false, 0L)
            }
        }
    }

    private fun openDestinationOutput(entry: DestEntry): OutputStream = when (entry.kind) {
        StorageKind.PHONE -> FileOutputStream(File(entry.id), false)
        StorageKind.USB -> contentResolver.openOutputStream(Uri.parse(entry.id), "w") ?: error("Cannot write destination")
        StorageKind.SHIZUKU -> {
            val pfd = RestrictedShizukuClient.get()?.openWrite(entry.id) ?: error("Cannot write restricted destination")
            ParcelFileDescriptor.AutoCloseOutputStream(pfd)
        }
    }

    private fun destinationSize(entry: DestEntry): Long = when (entry.kind) {
        StorageKind.PHONE -> File(entry.id).length()
        StorageKind.USB -> DocumentFile.fromSingleUri(this, Uri.parse(entry.id))?.length() ?: -1L
        StorageKind.SHIZUKU -> RestrictedShizukuClient.get()?.getFileSize(entry.id) ?: -1L
    }

    private fun openDestinationInput(entry: DestEntry): InputStream = when (entry.kind) {
        StorageKind.PHONE -> FileInputStream(File(entry.id))
        StorageKind.USB -> contentResolver.openInputStream(Uri.parse(entry.id)) ?: error("Cannot verify destination")
        StorageKind.SHIZUKU -> {
            val pfd = RestrictedShizukuClient.get()?.openRead(entry.id) ?: error("Cannot verify restricted destination")
            ParcelFileDescriptor.AutoCloseInputStream(pfd)
        }
    }

    private fun renameDestination(entry: DestEntry, newName: String): Boolean = when (entry.kind) {
        StorageKind.PHONE -> {
            val file = File(entry.id)
            file.renameTo(File(file.parentFile, newName))
        }
        StorageKind.USB -> DocumentFile.fromSingleUri(this, Uri.parse(entry.id))?.renameTo(newName) == true
        StorageKind.SHIZUKU -> RestrictedShizukuClient.get()?.renamePath(entry.id, newName) == true
    }

    private fun deleteDestination(entry: DestEntry): Boolean = when (entry.kind) {
        StorageKind.PHONE -> File(entry.id).deleteRecursively()
        StorageKind.USB -> DocumentFile.fromSingleUri(this, Uri.parse(entry.id))?.delete() == true
        StorageKind.SHIZUKU -> RestrictedShizukuClient.get()?.deletePath(entry.id) == true
    }

    private fun uniqueName(dest: DestSpec, requested: String): String {
        val existing = destinationChildren(dest).map { it.name }.toHashSet()
        if (!existing.contains(requested)) return requested
        val dot = requested.lastIndexOf('.')
        val base = if (dot > 0) requested.substring(0, dot) else requested
        val ext = if (dot > 0) requested.substring(dot) else ""
        var i = 1
        while (existing.contains("$base ($i)$ext")) i++
        return "$base ($i)$ext"
    }

    private fun uniqueTempName(dest: DestSpec, requested: String): String {
        var name = requested
        var i = 1
        val existing = destinationChildren(dest).map { it.name }.toHashSet()
        while (existing.contains(name)) name = "$requested.$i".also { i++ }
        return name
    }

    private fun quickSource(source: SourceSpec, size: Long): String? {
        val pfd = when (source.kind) {
            StorageKind.PHONE -> runCatching { ParcelFileDescriptor.open(File(source.id), ParcelFileDescriptor.MODE_READ_ONLY) }.getOrNull()
            StorageKind.USB -> runCatching { contentResolver.openFileDescriptor(Uri.parse(source.id), "r") }.getOrNull()
            StorageKind.SHIZUKU -> runCatching { RestrictedShizukuClient.get()?.openRead(source.id) }.getOrNull()
        } ?: return null
        return runCatching { FingerprintUtils.quick(pfd, size) }.getOrNull()
    }

    private fun quickDestination(entry: DestEntry, size: Long): String? {
        val pfd = when (entry.kind) {
            StorageKind.PHONE -> runCatching { ParcelFileDescriptor.open(File(entry.id), ParcelFileDescriptor.MODE_READ_ONLY) }.getOrNull()
            StorageKind.USB -> runCatching { contentResolver.openFileDescriptor(Uri.parse(entry.id), "r") }.getOrNull()
            StorageKind.SHIZUKU -> runCatching { RestrictedShizukuClient.get()?.openRead(entry.id) }.getOrNull()
        } ?: return null
        return runCatching { FingerprintUtils.quick(pfd, size) }.getOrNull()
    }

    private fun sha256Source(source: SourceSpec): String = openSource(source).use(::sha256)
    private fun sha256Destination(entry: DestEntry): String = openDestinationInput(entry).use(::sha256)

    private fun sha256(input: InputStream): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(1024 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            md.update(buffer, 0, n)
        }
        return hex(md.digest())
    }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { String.format(Locale.US, "%02x", it.toInt() and 0xff) }
}
