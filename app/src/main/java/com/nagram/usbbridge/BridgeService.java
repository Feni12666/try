package com.nagram.usbbridge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
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

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import rikka.shizuku.Shizuku;

public class BridgeService extends Service {
    private static final String TAG = "NagramUSBBridge";
    private static final String CHANNEL = "nagram_usb_bridge";
    private static final int NOTIFY_ID = 4102;
    private static final long SCAN_MS = 3000L;
    private static final int STABLE_PASSES = 3;

    private SharedPreferences prefs;
    private ScheduledExecutorService scanner;
    private ExecutorService transferExecutor;
    private final AtomicBoolean transferBusy = new AtomicBoolean(false);
    private final Map<String, Seen> seen = new HashMap<>();
    private final Map<String, Seen> ignoredInitial = new HashMap<>();
    private final Set<String> completedThisRun = new HashSet<>();
    private volatile INagramFileService remote;
    private long serviceStartMs;

    private static class Seen {
        long size;
        long mtime;
        int same;
        Seen(long size, long mtime) { this.size = size; this.mtime = mtime; this.same = 0; }
    }

    private final Shizuku.UserServiceArgs userArgs = new Shizuku.UserServiceArgs(
            new ComponentName("com.nagram.usbbridge", "com.nagram.usbbridge.NagramPrivilegedService"))
            .daemon(false)
            .tag("nagram_usb_file_service")
            .version(1)
            .processNameSuffix("nagram_files");

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            remote = INagramFileService.Stub.asInterface(service);
            setStatus("Shizuku file service connected");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            remote = null;
            setStatus("Shizuku file service disconnected");
        }
    };

    private final Shizuku.OnBinderReceivedListener binderReceived = this::bindRemoteIfPossible;
    private final Shizuku.OnBinderDeadListener binderDead = () -> {
        remote = null;
        setStatus("Shizuku stopped — waiting");
    };

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("bridge", MODE_PRIVATE);
        serviceStartMs = System.currentTimeMillis();
        prefs.edit().putBoolean("running", true).apply();

        createNotificationChannel();
        Notification n = buildNotification("Waiting for Shizuku…");
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFY_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFY_ID, n);
        }

        Shizuku.addBinderReceivedListenerSticky(binderReceived);
        Shizuku.addBinderDeadListener(binderDead);
        bindRemoteIfPossible();

        scanner = Executors.newSingleThreadScheduledExecutor();
        transferExecutor = Executors.newSingleThreadExecutor();
        scanner.scheduleWithFixedDelay(this::scanSafely, 1, 3, TimeUnit.SECONDS);
    }

    private void bindRemoteIfPossible() {
        try {
            if (!Shizuku.pingBinder()) return;
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                setStatus("Shizuku permission missing — open app");
                return;
            }
            Shizuku.bindUserService(userArgs, connection);
        } catch (Throwable t) {
            Log.e(TAG, "bindRemote", t);
            setStatus("Shizuku bind failed: " + shortMessage(t));
        }
    }

    private void scanSafely() {
        try {
            scanOnce();
        } catch (Throwable t) {
            Log.e(TAG, "scan", t);
            setStatus("Scan error: " + shortMessage(t));
        }
    }

    private void scanOnce() throws Exception {
        INagramFileService r = remote;
        if (r == null || transferBusy.get()) return;

        String treeText = prefs.getString("tree_uri", null);
        if (treeText == null) {
            setStatus("Select USB folder in app");
            return;
        }

        String[] files = r.listMediaFiles();
        if (files == null) return;
        boolean moveExisting = prefs.getBoolean("move_existing", false);

        Set<String> now = new HashSet<>();
        for (String path : files) {
            now.add(path);
            if (completedThisRun.contains(path)) continue;

            long size = r.getFileSize(path);
            long mtime = r.getLastModified(path);
            if (size <= 0 || mtime <= 0) continue;

            // By default, don't touch files that were already completed before the bridge was started.
            // Keep their initial signature ignored. If such a file changes after Start (for example it was
            // still downloading), remove it from the ignore set and begin normal stability tracking.
            Seen ignored = ignoredInitial.get(path);
            if (ignored != null) {
                if (ignored.size == size && ignored.mtime == mtime) {
                    continue;
                }
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

            if (s.size == size && s.mtime == mtime) {
                s.same++;
            } else {
                s.size = size;
                s.mtime = mtime;
                s.same = 0;
            }

            if (s.same >= STABLE_PASSES) {
                final long expectedSize = size;
                final long expectedMtime = mtime;
                if (transferBusy.compareAndSet(false, true)) {
                    transferExecutor.execute(() -> {
                        try {
                            transferOne(path, expectedSize, expectedMtime, Uri.parse(treeText));
                        } finally {
                            transferBusy.set(false);
                        }
                    });
                }
                return;
            }
        }

        seen.keySet().retainAll(now);
        ignoredInitial.keySet().retainAll(now);
    }

    private void transferOne(String path, long expectedSize, long expectedMtime, Uri treeUri) {
        INagramFileService r = remote;
        if (r == null) return;

        String name = new File(path).getName();
        setStatus("Copying: " + name);
        updateNotification("Copying: " + name);

        ContentResolver resolver = getContentResolver();
        Uri rootDoc = DocumentTreeUtils.rootDocumentUri(treeUri);
        Uri outDoc = null;
        long copied = 0L;

        try {
            long beforeSize = r.getFileSize(path);
            long beforeMtime = r.getLastModified(path);
            if (beforeSize != expectedSize || beforeMtime != expectedMtime) {
                setStatus("File changed; waiting: " + name);
                return;
            }

            String outName = DocumentTreeUtils.uniqueName(resolver, treeUri, rootDoc, name);
            outDoc = DocumentsContract.createDocument(resolver, rootDoc, DocumentTreeUtils.mimeFor(outName), outName);
            if (outDoc == null) throw new IllegalStateException("USB file could not be created");

            ParcelFileDescriptor pfd = r.openRead(path);
            if (pfd == null) throw new IllegalStateException("Source could not be opened");

            try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(pfd);
                 OutputStream out = resolver.openOutputStream(outDoc, "w")) {
                if (out == null) throw new IllegalStateException("USB output stream unavailable");
                byte[] buf = new byte[1024 * 1024];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    copied += n;
                }
                out.flush();
            }

            long afterSize = r.getFileSize(path);
            long afterMtime = r.getLastModified(path);
            if (copied != expectedSize || afterSize != expectedSize || afterMtime != expectedMtime) {
                try { DocumentsContract.deleteDocument(resolver, outDoc); } catch (Throwable ignored) {}
                setStatus("Source changed during copy; will retry: " + name);
                return;
            }

            boolean deleted = r.deleteIfUnchanged(path, expectedSize, expectedMtime);
            completedThisRun.add(path);
            seen.remove(path);

            if (deleted) {
                setStatus("Moved ✅  " + name + "  (" + human(expectedSize) + ")");
                updateNotification("Moved: " + name);
            } else {
                setStatus("Copied ✅ but source was not deleted: " + name);
                updateNotification("Copied; source kept: " + name);
            }
        } catch (Throwable t) {
            Log.e(TAG, "transfer " + path, t);
            if (outDoc != null) {
                try { DocumentsContract.deleteDocument(resolver, outDoc); } catch (Throwable ignored) {}
            }
            setStatus("Transfer failed; source kept: " + name + " — " + shortMessage(t));
            updateNotification("Transfer failed; source kept");
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Nagram USB Bridge", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Shows Nagram to USB automatic transfer status");
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
        return b.setContentTitle("Nagram USB Bridge")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setOngoing(true)
                .setContentIntent(pi)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(NOTIFY_ID, buildNotification(text));
    }

    private void setStatus(String text) {
        prefs.edit().putString("last_status", text).apply();
    }

    private static String shortMessage(Throwable t) {
        String m = t.getMessage();
        if (m == null || m.trim().isEmpty()) m = t.getClass().getSimpleName();
        if (m.length() > 120) m = m.substring(0, 120);
        return m;
    }

    private static String human(long b) {
        if (b < 1024) return b + " B";
        double kb = b / 1024.0;
        if (kb < 1024) return String.format("%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format("%.1f MB", mb);
        return String.format("%.2f GB", mb / 1024.0);
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
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
