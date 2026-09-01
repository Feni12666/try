package com.nagram.usbbridge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import rikka.shizuku.Shizuku;

public class BridgeService extends Service {
    private static final String TAG = "NagramUSBBridge";
    private static final String CHANNEL = "nagram_usb_bridge";
    private static final int NOTIFY_ID = 4102;
    private static final int STABLE_PASSES = 3;
    private static final long SCAN_SECONDS = 2L;
    private static final int MAX_PREPARED = 10;
    private static final int MAX_ACTIVE_TRANSFERS = 2;
    private static final int COPY_BUFFER_BYTES = 4 * 1024 * 1024;
    private static final long FAT32_MAX_FILE = 0xFFFFFFFFL;
    private static final long DEFAULT_RESERVE = 1024L * 1024L * 1024L;
    private static final long DEFAULT_CLEANUP_DELAY = 3L * 60L * 1000L;

    private SharedPreferences prefs;
    private BridgeDatabase db;
    private ScheduledExecutorService scanner;
    private ExecutorService transferExecutor;
    private final AtomicInteger activeTransfers = new AtomicInteger(0);
    private final Set<String> activeContentKeys = ConcurrentHashMap.newKeySet();
    private final Map<String, Seen> seen = new ConcurrentHashMap<>();
    private final Map<String, Seen> ignoredInitial = new ConcurrentHashMap<>();
    private final Set<String> completedThisRun = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> retryNotBefore = new ConcurrentHashMap<>();
    private final Map<String, Integer> retryAttempts = new ConcurrentHashMap<>();
    private final Object queueLock = new Object();
    private final ArrayDeque<Prepared> readyQueue = new ArrayDeque<>();
    private final Set<String> queuedKeys = new HashSet<>();
    private volatile INagramFileService remote;
    private long serviceStartMs;
    private long lastCleanupCheckMs;

    private static class Seen {
        long size;
        long mtime;
        int same;
        Seen(long size, long mtime) { this.size = size; this.mtime = mtime; }
    }

    private static class Prepared {
        final String path;
        final long size;
        final long mtime;
        final Uri treeUri;
        final String quickFingerprint;
        Prepared(String path, long size, long mtime, Uri treeUri, String quickFingerprint) {
            this.path = path;
            this.size = size;
            this.mtime = mtime;
            this.treeUri = treeUri;
            this.quickFingerprint = quickFingerprint;
        }
    }

    private final Shizuku.UserServiceArgs userArgs = new Shizuku.UserServiceArgs(
            new ComponentName("com.nagram.usbbridge", "com.nagram.usbbridge.NagramPrivilegedService"))
            .daemon(false)
            .tag("nagram_usb_file_service")
            .version(2)
            .processNameSuffix("nagram_files");

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            remote = INagramFileService.Stub.asInterface(service);
            setStatus("Bridge ready — Shizuku connected");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            remote = null;
            setStatus("Waiting for Shizuku — originals are safe");
        }
    };

    private final Shizuku.OnBinderReceivedListener binderReceived = this::bindRemoteIfPossible;
    private final Shizuku.OnBinderDeadListener binderDead = () -> {
        remote = null;
        setStatus("Shizuku stopped — waiting, originals are safe");
        updateNotification("Waiting for Shizuku");
    };

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("bridge", MODE_PRIVATE);
        db = new BridgeDatabase(this);
        serviceStartMs = System.currentTimeMillis();
        prefs.edit().putBoolean("running", true).putBoolean("duplicate_guard", true).apply();
        createNotificationChannel();

        Notification n = buildNotification("Starting safety engine…");
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFY_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFY_ID, n);
        }

        recoverJournal();
        Shizuku.addBinderReceivedListenerSticky(binderReceived);
        Shizuku.addBinderDeadListener(binderDead);
        bindRemoteIfPossible();

        scanner = Executors.newSingleThreadScheduledExecutor();
        if (!prefs.contains("configured_parallel_transfers")) {
            prefs.edit().putInt("configured_parallel_transfers", MAX_ACTIVE_TRANSFERS).apply();
        }
        transferExecutor = Executors.newFixedThreadPool(MAX_ACTIVE_TRANSFERS);
        scanner.scheduleWithFixedDelay(this::scanSafely, 1, SCAN_SECONDS, TimeUnit.SECONDS);
    }

    private void recoverJournal() {
        try {
            ContentResolver resolver = getContentResolver();
            for (BridgeDatabase.Record r : db.recoverable()) {
                if (r.destUri != null && r.destName != null && r.destName.endsWith(".part")) {
                    try { DocumentsContract.deleteDocument(resolver, Uri.parse(r.destUri)); } catch (Throwable ignored) {}
                }
                db.updateStatus(r.id, "RETRY_PENDING", "Recovered after interruption; source preserved");
            }
        } catch (Throwable t) {
            prefs.edit().putBoolean("cleanup_suspended", true).apply();
            Log.e(TAG, "journal recovery", t);
        }
    }

    private void bindRemoteIfPossible() {
        try {
            if (!Shizuku.pingBinder()) {
                setStatus("Waiting for Shizuku — originals are safe");
                return;
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                setStatus("Shizuku permission needed — originals are safe");
                return;
            }
            Shizuku.bindUserService(userArgs, connection);
        } catch (Throwable t) {
            Log.e(TAG, "bindRemote", t);
            setStatus("Shizuku connection failed — source kept: " + shortMessage(t));
        }
    }

    private void scanSafely() {
        try {
            long now = System.currentTimeMillis();
            if (now - lastCleanupCheckMs >= 10_000L) {
                processCleanupDue();
                lastCleanupCheckMs = now;
            }
            scanOnce();
        } catch (Throwable t) {
            Log.e(TAG, "scan", t);
            setStatus("Scan paused safely: " + shortMessage(t));
        }
    }

    private void scanOnce() throws Exception {
        INagramFileService r = remote;
        if (r == null) return;

        if (!r.isSourceLayoutAvailable()) {
            prefs.edit().putBoolean("cleanup_suspended", true).apply();
            setStatus("Nagram storage layout changed/missing — automation paused safely");
            return;
        }

        String treeText = prefs.getString("tree_uri", null);
        if (treeText == null) {
            setStatus("Select your USB destination folder");
            clearPreparedQueue();
            return;
        }
        Uri treeUri = Uri.parse(treeText);

        // When the 10-file look-ahead queue is already full, do not rescan the whole Nagram tree
        // during an active USB write. This preserves transfer bandwidth and battery.
        if (activeTransfers.get() > 0 && preparedQueueFull()) return;

        String[] files = r.listMediaFiles();
        if (files == null) return;
        boolean moveExisting = prefs.getBoolean("move_existing", false);

        Set<String> now = new HashSet<>();
        for (String path : files) {
            now.add(path);
            if (completedThisRun.contains(path)) continue;
            if (preparedQueueFull()) break;

            Long retryAt = retryNotBefore.get(path);
            if (retryAt != null) {
                if (System.currentTimeMillis() < retryAt) continue;
                retryNotBefore.remove(path);
            }

            long size = r.getFileSize(path);
            long mtime = r.getLastModified(path);
            if (size <= 0 || mtime <= 0) continue;
            if (db.isKnownHandled(path, size, mtime)) continue;
            if (isPreparedOrActive(path, size, mtime)) continue;

            Seen ignored = ignoredInitial.get(path);
            if (ignored != null) {
                if (ignored.size == size && ignored.mtime == mtime) continue;
                ignoredInitial.remove(path);
                seen.put(path, new Seen(size, mtime));
                continue;
            }
            if (!moveExisting && mtime < serviceStartMs - 2000L && !seen.containsKey(path)) {
                ignoredInitial.put(path, new Seen(size, mtime));
                continue;
            }

            Seen s = seen.get(path);
            if (s == null) {
                seen.put(path, new Seen(size, mtime));
                continue;
            }

            if (s.size == size && s.mtime == mtime) s.same++;
            else {
                s.size = size;
                s.mtime = mtime;
                s.same = 0;
            }

            // Download Completion Guard: 3 unchanged observations, now 2 seconds apart (~6 seconds).
            // Once stable, perform only a lightweight source fingerprint and prepare it for the queue.
            // Full duplicate hashing remains candidate-only and never blocks detection of later downloads.
            if (s.same >= STABLE_PASSES) {
                String quickFp = null;
                try { quickFp = FingerprintUtils.quick(r.openRead(path), size); }
                catch (Throwable t) { Log.w(TAG, "prepare fingerprint " + path, t); }
                enqueuePrepared(new Prepared(path, size, mtime, treeUri, quickFp));
            }
        }

        seen.keySet().retainAll(now);
        ignoredInitial.keySet().retainAll(now);
        updateQueuePrefs();
        launchTransfersIfCapacity();
        if (activeTransfers.get() == 0 && preparedCount() == 0) setIdleStatus(files.length);
    }

    private String preparedKey(String path, long size, long mtime) {
        return path + "\n" + size + "\n" + mtime;
    }

    private boolean isPreparedOrActive(String path, long size, long mtime) {
        synchronized (queueLock) {
            return queuedKeys.contains(preparedKey(path, size, mtime));
        }
    }

    private boolean preparedQueueFull() {
        synchronized (queueLock) { return readyQueue.size() >= MAX_PREPARED; }
    }

    private int preparedCount() {
        synchronized (queueLock) { return readyQueue.size(); }
    }

    private void enqueuePrepared(Prepared p) {
        String key = preparedKey(p.path, p.size, p.mtime);
        synchronized (queueLock) {
            if (queuedKeys.contains(key) || readyQueue.size() >= MAX_PREPARED) return;
            readyQueue.addLast(p);
            queuedKeys.add(key);
        }
        updateQueuePrefs();
    }

    private void clearPreparedQueue() {
        synchronized (queueLock) {
            readyQueue.clear();
            queuedKeys.clear();
        }
        updateQueuePrefs();
    }

    private void updateQueuePrefs() {
        prefs.edit()
                .putInt("ready_count", preparedCount())
                .putInt("active_transfer_count", activeTransfers.get())
                .apply();
    }

    private int effectiveMaxActiveTransfers() {
        boolean safer = prefs.getBoolean("cleanup_suspended", false) ||
                prefs.getInt("recent_safety_failures", 0) >= 3;
        if (safer) return 1;
        return Math.max(1, Math.min(MAX_ACTIVE_TRANSFERS,
                prefs.getInt("configured_parallel_transfers", MAX_ACTIVE_TRANSFERS)));
    }

    private synchronized void launchTransfersIfCapacity() {
        while (activeTransfers.get() < effectiveMaxActiveTransfers()) {
            final Prepared next;
            synchronized (queueLock) { next = readyQueue.pollFirst(); }
            if (next == null) break;

            activeTransfers.incrementAndGet();
            updateQueuePrefs();
            transferExecutor.execute(() -> {
                try {
                    transferOne(next.path, next.size, next.mtime, next.treeUri, next.quickFingerprint);
                } finally {
                    synchronized (queueLock) { queuedKeys.remove(preparedKey(next.path, next.size, next.mtime)); }
                    int remaining = activeTransfers.decrementAndGet();
                    if (remaining <= 0) clearProgress();
                    updateQueuePrefs();
                    // Start the next already-prepared file immediately; do not wait for another scan tick.
                    launchTransfersIfCapacity();
                    // Replenish the 10-file look-ahead queue while the other transfer may still be active.
                    try { if (scanner != null && !scanner.isShutdown()) scanner.execute(this::scanSafely); }
                    catch (Throwable ignored) {}
                }
            });
        }
    }

    private void setIdleStatus(int discovered) {
        if (prefs.getBoolean("cleanup_suspended", false)) {
            setStatus("Safer Mode active — automatic cleanup suspended");
        } else {
            setStatus(discovered == 0 ? "All caught up ✓" : "Watching Nagram — waiting for completed files");
        }
    }

    private void transferOne(String path, long expectedSize, long expectedMtime, Uri treeUri, String preparedQuickFp) {
        INagramFileService r = remote;
        if (r == null) return;

        final String name = new File(path).getName();
        final String treeText = treeUri.toString();
        long jobId = db.upsertSource(path, name, expectedSize, expectedMtime, "PREFLIGHT");
        ContentResolver resolver = getContentResolver();
        Uri rootDoc = DocumentTreeUtils.rootDocumentUri(treeUri);
        Uri tempDoc = null;
        String contentKey = null;
        boolean ownsContentKey = false;

        try {
            // Source Mutation Guard — recheck immediately before any destination write.
            if (!sourceUnchanged(r, path, expectedSize, expectedMtime)) {
                db.updateStatus(jobId, "WAITING_FOR_COMPLETION", "Source changed before transfer");
                setStatus("Waiting for download to finish: " + name);
                seen.remove(path);
                return;
            }

            long reserve = prefs.getLong("reserve_bytes", DEFAULT_RESERVE);
            long available = StorageUtils.availableBytes(this, treeUri);
            if (available >= 0 && available < expectedSize + reserve) {
                long need = expectedSize + reserve - available;
                db.updateStatus(jobId, "WAITING_FOR_SPACE", "Need " + human(need) + " more usable USB space");
                setStatus("USB needs " + human(need) + " more usable space");
                updateNotification("USB space needed: " + human(need));
                deferRetryFixed(path, 60_000L);
                return;
            }

            String fs = StorageUtils.filesystemTypeBestEffort(this, treeUri);
            if (expectedSize > FAT32_MAX_FILE && StorageUtils.definitelyFat32(fs)) {
                db.updateStatus(jobId, "DESTINATION_INCOMPATIBLE", "FAT32 cannot store this file size");
                setStatus("USB format cannot store this " + human(expectedSize) + " file — original kept");
                updateNotification("Large file not supported by this USB format");
                deferRetryFixed(path, 10L * 60L * 1000L);
                return;
            }

            db.update(jobId, "PREFLIGHT", treeText, null, null, null, null, 0L, null);

            // Same Video Duplicate Guard is mandatory in V3. Filename is never trusted as identity.
            String quickFp = preparedQuickFp != null ? preparedQuickFp : FingerprintUtils.quick(r.openRead(path), expectedSize);
            if (quickFp == null) throw new IllegalStateException("Could not create a safe source fingerprint");

            // Two transfers may run concurrently, but the same content candidate must never be written twice.
            // If another active job has the same size + quick fingerprint, defer this source briefly; after
            // the first job finalizes, normal duplicate confirmation will find the USB copy and skip it safely.
            contentKey = expectedSize + "\n" + quickFp;
            if (!activeContentKeys.add(contentKey)) {
                db.updateStatus(jobId, "RETRY_PENDING", "Matching content is already being transferred; waiting for duplicate confirmation");
                deferRetryFixed(path, 3_000L);
                return;
            }
            ownsContentKey = true;

            BridgeDatabase.Record dup = findConfirmedDuplicate(r, path, expectedSize, quickFp, treeUri, rootDoc);
            if (dup != null) {
                long delay = prefs.getLong("cleanup_delay_ms", DEFAULT_CLEANUP_DELAY);
                boolean deleteAfter = prefs.getBoolean("delete_after_verified", true);
                if (deleteAfter && !prefs.getBoolean("cleanup_suspended", false)) {
                    db.update(jobId, "CLEANUP_PENDING", treeText, dup.destUri, dup.destName,
                            quickFp, dup.fullHash, System.currentTimeMillis() + delay, "Confirmed same content; copy skipped");
                    setStatus("Duplicate skipped ✓  " + name + " — cleanup pending");
                } else {
                    db.update(jobId, "COMPLETED_SOURCE_KEPT", treeText, dup.destUri, dup.destName,
                            quickFp, dup.fullHash, 0L, prefs.getBoolean("cleanup_suspended", false)
                                    ? "Confirmed same content; cleanup suspended by safety engine"
                                    : "Confirmed same content; source kept by policy");
                    completedThisRun.add(path);
                    setStatus("Duplicate skipped ✓  " + name + " — source kept");
                }
                updateNotification("Duplicate skipped — same video already on USB");
                seen.remove(path);
                clearRetry(path);
                return;
            }

            String tempName = DocumentTreeUtils.safeTempName(name);
            tempDoc = DocumentsContract.createDocument(resolver, rootDoc, "application/octet-stream", tempName);
            if (tempDoc == null) throw new IllegalStateException("USB temporary file could not be created");
            db.update(jobId, "TRANSFERRING", treeText, tempDoc.toString(), tempName, quickFp, null, 0L, null);

            long copied = 0L;
            long started = System.currentTimeMillis();
            long lastUi = 0L;
            ParcelFileDescriptor sourcePfd = r.openRead(path);
            if (sourcePfd == null) throw new IllegalStateException("Source could not be opened");

            ParcelFileDescriptor destPfd = resolver.openFileDescriptor(tempDoc, "w");
            if (destPfd == null) {
                try { sourcePfd.close(); } catch (Throwable ignored) {}
                throw new IllegalStateException("USB output file descriptor unavailable");
            }
            try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(sourcePfd);
                 OutputStream out = new BufferedOutputStream(
                         new ParcelFileDescriptor.AutoCloseOutputStream(destPfd), COPY_BUFFER_BYTES)) {
                byte[] buf = new byte[COPY_BUFFER_BYTES];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    copied += n;
                    long now = System.currentTimeMillis();
                    if (now - lastUi >= 1500L) {
                        updateProgress(name, copied, expectedSize, now - started);
                        lastUi = now;
                    }
                }
                out.flush();
            }

            db.updateStatus(jobId, "VERIFYING", null);
            if (!sourceUnchanged(r, path, expectedSize, expectedMtime)) {
                safeDeleteDocument(resolver, tempDoc);
                db.updateStatus(jobId, "WAITING_FOR_COMPLETION", "Source changed during copy");
                setStatus("Source changed during copy — retrying later, original kept");
                seen.remove(path);
                return;
            }

            long destSize = DocumentTreeUtils.getSize(resolver, tempDoc);
            if (copied != expectedSize || destSize != expectedSize || !DocumentTreeUtils.readable(resolver, tempDoc)) {
                safeDeleteDocument(resolver, tempDoc);
                onSafetyFailure(jobId, "VERIFICATION_FAILED", "Destination verification failed");
                setStatus("Verification failed — original kept: " + name);
                updateNotification("Verification failed — original kept");
                deferRetryBackoff(path);
                return;
            }

            db.updateStatus(jobId, "FINALIZING", null);
            String finalName = DocumentTreeUtils.uniqueName(resolver, treeUri, rootDoc, name);
            Uri finalDoc = DocumentTreeUtils.rename(resolver, tempDoc, finalName);
            if (finalDoc == null) {
                // Do not present the temporary object as a successful transfer if finalization is unsupported.
                // Remove our own incomplete artifact where possible; the source remains untouched.
                safeDeleteDocument(resolver, tempDoc);
                tempDoc = null;
                onSafetyFailure(jobId, "NEEDS_ATTENTION", "USB provider could not safely finalize the temporary file");
                setStatus("USB could not finalize transfer safely — original kept");
                updateNotification("Transfer needs attention — original kept");
                deferRetryBackoff(path);
                return;
            }
            tempDoc = null;

            long finalSize = DocumentTreeUtils.getSize(resolver, finalDoc);
            if (finalSize != expectedSize || !DocumentTreeUtils.readable(resolver, finalDoc)) {
                onSafetyFailure(jobId, "VERIFICATION_FAILED", "Final destination recheck failed");
                setStatus("Final verification failed — original kept");
                deferRetryBackoff(path);
                return;
            }

            long delay = prefs.getLong("cleanup_delay_ms", DEFAULT_CLEANUP_DELAY);
            boolean deleteAfter = prefs.getBoolean("delete_after_verified", true);
            if (deleteAfter && !prefs.getBoolean("cleanup_suspended", false)) {
                db.update(jobId, "CLEANUP_PENDING", treeText, finalDoc.toString(), finalName,
                        quickFp, null, System.currentTimeMillis() + delay, null);
                setStatus("Verified ✓  " + name + " — cleanup in " + humanTime(delay));
                updateNotification("Verified — safe cleanup pending");
            } else {
                db.update(jobId, "COMPLETED_SOURCE_KEPT", treeText, finalDoc.toString(), finalName,
                        quickFp, null, 0L, prefs.getBoolean("cleanup_suspended", false) ? "Cleanup suspended by safety engine" : null);
                completedThisRun.add(path);
                setStatus("Copied + verified ✓  Source kept: " + name);
                updateNotification("Copied + verified — source kept");
            }
            seen.remove(path);
            clearRetry(path);
            resetFailureCounterGradually();
        } catch (Throwable t) {
            Log.e(TAG, "transfer " + path, t);
            if (tempDoc != null) safeDeleteDocument(resolver, tempDoc);
            onSafetyFailure(jobId, "NEEDS_ATTENTION", shortMessage(t));
            setStatus("Transfer failed safely — original kept: " + name + " — " + shortMessage(t));
            updateNotification("Transfer failed — original kept");
            deferRetryBackoff(path);
        } finally {
            if (ownsContentKey && contentKey != null) activeContentKeys.remove(contentKey);
        }
    }

    /**
     * Same Video Duplicate Guard.
     * Exact byte size narrows candidates, sample SHA-256 is a fast fingerprint, and full SHA-256 confirms
     * content before copy is skipped. Filename is deliberately not part of the decision.
     */
    private BridgeDatabase.Record findConfirmedDuplicate(INagramFileService r, String sourcePath, long size,
                                                          String quickFp, Uri treeUri, Uri rootDoc) throws Exception {
        if (quickFp == null) return null;
        ContentResolver resolver = getContentResolver();
        String sourceHash = null;

        // Fast path: previously transferred destination recorded in the persistent journal.
        for (BridgeDatabase.Record old : db.fingerprintCandidates(size, quickFp, treeUri.toString())) {
            if (old.destUri == null) continue;
            Uri u = Uri.parse(old.destUri);
            if (DocumentTreeUtils.getSize(resolver, u) != size || !DocumentTreeUtils.readable(resolver, u)) continue;
            if (sourceHash == null) sourceHash = FingerprintUtils.sha256(r.openRead(sourcePath));
            String destHash = old.fullHash != null ? old.fullHash : FingerprintUtils.sha256Uri(resolver, u);
            if (sourceHash != null && sourceHash.equals(destHash)) {
                old.fullHash = destHash;
                return old;
            }
        }

        // Cold-start path: inspect same-size files already present in the selected USB folder.
        // A generous cap avoids silently missing ordinary duplicate libraries while keeping pathological folders bounded.
        for (DocumentTreeUtils.Child child : DocumentTreeUtils.childrenWithSize(resolver, treeUri, rootDoc, size, 5000)) {
            if (child.name != null && child.name.endsWith(".part")) continue;
            String usbQuick;
            try { usbQuick = FingerprintUtils.quickUri(resolver, child.uri, size); }
            catch (Throwable ignored) { continue; }
            if (!quickFp.equals(usbQuick)) continue;
            if (sourceHash == null) sourceHash = FingerprintUtils.sha256(r.openRead(sourcePath));
            String destHash = FingerprintUtils.sha256Uri(resolver, child.uri);
            if (sourceHash != null && sourceHash.equals(destHash)) {
                BridgeDatabase.Record synthetic = new BridgeDatabase.Record();
                synthetic.destUri = child.uri.toString();
                synthetic.destName = child.name;
                synthetic.fullHash = destHash;
                return synthetic;
            }
        }
        return null;
    }

    private void processCleanupDue() {
        INagramFileService r = remote;
        if (r == null || db == null) return;
        if (!prefs.getBoolean("delete_after_verified", true)) return;
        if (prefs.getBoolean("cleanup_suspended", false)) return;

        List<BridgeDatabase.Record> due = db.dueCleanup(System.currentTimeMillis());
        if (due.isEmpty()) return;
        ContentResolver resolver = getContentResolver();
        String selectedTree = prefs.getString("tree_uri", null);

        for (BridgeDatabase.Record rec : due) {
            try {
                // Cleanup Revalidation: the exact verified destination must still be reachable now.
                if (selectedTree == null || rec.treeUri == null || !selectedTree.equals(rec.treeUri)) {
                    setStatus("Cleanup waiting for the verified USB — original kept");
                    continue;
                }
                if (rec.destUri == null) {
                    db.updateStatus(rec.id, "NEEDS_ATTENTION", "Verified destination reference missing");
                    continue;
                }
                Uri dest = Uri.parse(rec.destUri);
                long destSize = DocumentTreeUtils.getSize(resolver, dest);
                if (destSize != rec.sourceSize || !DocumentTreeUtils.readable(resolver, dest)) {
                    setStatus("Cleanup waiting — verified USB copy is not currently available");
                    continue;
                }
                // Re-prove destination identity at cleanup time, not only its size/readability.
                if (rec.quickFingerprint == null) {
                    db.updateStatus(rec.id, "NEEDS_ATTENTION", "Destination identity proof missing; source preserved");
                    setStatus("Cleanup stopped safely — destination identity could not be proven");
                    continue;
                }
                String liveQuick = FingerprintUtils.quickUri(resolver, dest, rec.sourceSize);
                if (!rec.quickFingerprint.equals(liveQuick)) {
                    db.updateStatus(rec.id, "NEEDS_ATTENTION", "Destination content changed before cleanup; source preserved");
                    setStatus("Cleanup stopped safely — USB copy changed, original kept");
                    continue;
                }
                if (!sourceUnchanged(r, rec.sourcePath, rec.sourceSize, rec.sourceMtime)) {
                    db.updateStatus(rec.id, "NEEDS_ATTENTION", "Source changed before cleanup; source preserved");
                    continue;
                }
                boolean deleted = r.deleteIfUnchanged(rec.sourcePath, rec.sourceSize, rec.sourceMtime);
                if (deleted) {
                    db.updateStatus(rec.id, "COMPLETED", null);
                    completedThisRun.add(rec.sourcePath);
                    setStatus("Moved safely ✓  " + rec.sourceName + "  (" + human(rec.sourceSize) + ")");
                    updateNotification("Safe move completed");
                } else {
                    db.updateStatus(rec.id, "NEEDS_ATTENTION", "Source cleanup refused because source no longer matched");
                    setStatus("USB copy safe, but original was kept because cleanup could not be proven safe");
                }
            } catch (Throwable t) {
                Log.w(TAG, "cleanup revalidation", t);
                // Keep CLEANUP_PENDING so correct USB/recovered permission can be retried later.
                setStatus("Cleanup waiting safely — original kept");
            }
        }
    }

    private boolean sourceUnchanged(INagramFileService r, String path, long size, long mtime) {
        try {
            return r.getFileSize(path) == size && r.getLastModified(path) == mtime;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void onSafetyFailure(long jobId, String state, String message) {
        db.updateStatus(jobId, state, message);
        int failures = prefs.getInt("recent_safety_failures", 0) + 1;
        SharedPreferences.Editor e = prefs.edit().putInt("recent_safety_failures", failures);
        if (failures >= 3) e.putBoolean("cleanup_suspended", true);
        e.apply();
    }

    private void resetFailureCounterGradually() {
        int failures = prefs.getInt("recent_safety_failures", 0);
        if (failures > 0) prefs.edit().putInt("recent_safety_failures", failures - 1).apply();
    }

    private void deferRetryFixed(String path, long delayMs) {
        if (path == null) return;
        retryNotBefore.put(path, System.currentTimeMillis() + Math.max(5_000L, delayMs));
    }

    private void deferRetryBackoff(String path) {
        if (path == null) return;
        int attempt = Math.min(6, retryAttempts.getOrDefault(path, 0) + 1);
        retryAttempts.put(path, attempt);
        long delay = Math.min(5L * 60L * 1000L, 15_000L * (1L << Math.min(4, attempt - 1)));
        retryNotBefore.put(path, System.currentTimeMillis() + delay);
    }

    private void clearRetry(String path) {
        if (path == null) return;
        retryNotBefore.remove(path);
        retryAttempts.remove(path);
    }

    private void safeDeleteDocument(ContentResolver resolver, Uri uri) {
        if (uri == null) return;
        try { DocumentsContract.deleteDocument(resolver, uri); } catch (Throwable ignored) {}
    }

    private void updateProgress(String name, long copied, long total, long elapsedMs) {
        int pct = total <= 0 ? 0 : (int) Math.min(100L, copied * 100L / total);
        double seconds = Math.max(0.1, elapsedMs / 1000.0);
        long bps = (long) (copied / seconds);
        long remain = bps > 0 ? Math.max(0L, (total - copied) / bps) : -1L;
        prefs.edit()
                .putString("current_name", name)
                .putLong("current_done", copied)
                .putLong("current_total", total)
                .putInt("current_percent", pct)
                .putLong("current_speed", bps)
                .putLong("current_eta", remain)
                .apply();
        String text = "Moving " + pct + "% • " + humanRate(bps);
        updateNotification(text);
        setStatus(text + " • " + name);
    }

    private void clearProgress() {
        prefs.edit()
                .remove("current_name")
                .remove("current_done")
                .remove("current_total")
                .remove("current_percent")
                .remove("current_speed")
                .remove("current_eta")
                .apply();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Storage Sync", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Safe phone and USB transfer status");
            nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        return b.setContentTitle("Storage Sync")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setOngoing(true)
                .setContentIntent(pi)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NOTIFY_ID, buildNotification(text));
    }

    private void setStatus(String text) {
        prefs.edit().putString("last_status", text).putLong("last_status_at", System.currentTimeMillis()).apply();
    }

    private static String shortMessage(Throwable t) {
        String m = t.getMessage();
        if (m == null || m.trim().isEmpty()) m = t.getClass().getSimpleName();
        if (m.length() > 140) m = m.substring(0, 140);
        return m;
    }

    static String human(long b) {
        if (b < 1024) return b + " B";
        double kb = b / 1024.0;
        if (kb < 1024) return String.format(java.util.Locale.US, "%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(java.util.Locale.US, "%.1f MB", mb);
        return String.format(java.util.Locale.US, "%.2f GB", mb / 1024.0);
    }

    static String humanRate(long bps) {
        return human(Math.max(0L, bps)) + "/s";
    }

    private static String humanTime(long ms) {
        long min = Math.max(0L, ms / 60000L);
        if (min < 1) return "a moment";
        if (min == 1) return "1 minute";
        return min + " minutes";
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        prefs.edit().putBoolean("running", false).apply();
        try { Shizuku.removeBinderReceivedListener(binderReceived); } catch (Throwable ignored) {}
        try { Shizuku.removeBinderDeadListener(binderDead); } catch (Throwable ignored) {}
        try { Shizuku.unbindUserService(userArgs, connection, true); } catch (Throwable ignored) {}
        if (scanner != null) scanner.shutdownNow();
        if (transferExecutor != null) transferExecutor.shutdownNow();
        remote = null;
        if (db != null) db.close();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
