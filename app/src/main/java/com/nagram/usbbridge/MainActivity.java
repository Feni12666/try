package com.nagram.usbbridge;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private static final int REQ_TREE = 2001;
    private static final int REQ_SHIZUKU = 2002;
    private static final int REQ_NOTIFICATIONS = 2003;

    private SharedPreferences prefs;
    private BridgeDatabase db;
    private TextView masterTitle, masterSub, shizukuStatus, folderStatus, autoStatus;
    private TextView currentName, currentMeta, todayStats, recentText, saferMode;
    private ProgressBar progress;
    private Button startStop;
    private CheckBox moveExisting, deleteAfter, duplicateGuard, autoStart;
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            refresh();
            refreshHandler.postDelayed(this, 1200L);
        }
    };

    private final Shizuku.OnBinderReceivedListener binderReceived = this::refresh;
    private final Shizuku.OnBinderDeadListener binderDead = this::refresh;
    private final Shizuku.OnRequestPermissionResultListener permissionResult = (requestCode, grantResult) -> {
        if (requestCode == REQ_SHIZUKU) refresh();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("bridge", MODE_PRIVATE);
        db = new BridgeDatabase(this);
        seedDefaults();
        getWindow().setStatusBarColor(Color.rgb(8, 11, 18));
        getWindow().setNavigationBarColor(Color.rgb(8, 11, 18));
        setContentView(buildUi());

        Shizuku.addBinderReceivedListenerSticky(binderReceived);
        Shizuku.addBinderDeadListener(binderDead);
        Shizuku.addRequestPermissionResultListener(permissionResult);
        refresh();
    }

    private void seedDefaults() {
        if (!prefs.contains("delete_after_verified")) prefs.edit().putBoolean("delete_after_verified", true).apply();
        if (!prefs.contains("duplicate_guard")) prefs.edit().putBoolean("duplicate_guard", true).apply();
        if (!prefs.contains("cleanup_delay_ms")) prefs.edit().putLong("cleanup_delay_ms", 5L * 60L * 1000L).apply();
        if (!prefs.contains("reserve_bytes")) prefs.edit().putLong("reserve_bytes", 1024L * 1024L * 1024L).apply();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(8, 11, 18));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(30));
        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(profileCard());
        root.addView(space(12));
        root.addView(masterCard());
        root.addView(space(12));
        root.addView(transferCard());
        root.addView(space(12));
        root.addView(connectionCard());
        root.addView(space(12));
        root.addView(actionCard());
        root.addView(space(12));
        root.addView(safetyCard());
        root.addView(space(12));
        root.addView(activityCard());
        root.addView(space(12));
        root.addView(footer());
        return scroll;
    }

    private View profileCard() {
        LinearLayout card = card();
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setOrientation(LinearLayout.HORIZONTAL);

        ImageView avatar = new ImageView(this);
        avatar.setImageResource(com.nagram.usbbridge.R.drawable.profile_shahadat);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable oval = new GradientDrawable();
        oval.setShape(GradientDrawable.OVAL);
        oval.setColor(Color.rgb(20, 28, 45));
        oval.setStroke(dp(2), Color.rgb(0, 217, 255));
        avatar.setBackground(oval);
        avatar.setClipToOutline(true);
        card.addView(avatar, new LinearLayout.LayoutParams(dp(72), dp(72)));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(dp(14), 0, 0, 0);
        TextView name = label("SHAHADAT", 23, Color.WHITE, true);
        TextView sub = label("Nagram USB Bridge • Winner Safety Build", 13, Color.rgb(169, 179, 197), false);
        TextView promise = label("If uncertain, keep the original.", 12, Color.rgb(61, 220, 132), false);
        text.addView(name); text.addView(sub); text.addView(promise);
        card.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return card;
    }

    private View masterCard() {
        LinearLayout card = card();
        masterTitle = label("Checking Bridge…", 24, Color.WHITE, true);
        masterSub = label("Reading current safety state", 14, Color.rgb(169, 179, 197), false);
        masterSub.setPadding(0, dp(5), 0, 0);
        saferMode = label("", 13, Color.rgb(255, 176, 32), true);
        saferMode.setPadding(0, dp(8), 0, 0);
        card.addView(masterTitle);
        card.addView(masterSub);
        card.addView(saferMode);
        return card;
    }

    private View transferCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("CURRENT TRANSFER"));
        currentName = label("No active transfer", 16, Color.WHITE, true);
        currentName.setPadding(0, dp(8), 0, dp(8));
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(0);
        card.addView(currentName);
        card.addView(progress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8)));
        currentMeta = label("All caught up", 13, Color.rgb(169, 179, 197), false);
        currentMeta.setPadding(0, dp(8), 0, 0);
        card.addView(currentMeta);
        return card;
    }

    private View connectionCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("SYSTEM STATUS"));
        shizukuStatus = statusRow(card, "Shizuku: checking…");
        folderStatus = statusRow(card, "USB: not selected");
        autoStatus = statusRow(card, "Auto Move: stopped");
        statusRow(card, "Same Video Guard: ON • exact bytes + content fingerprint");
        return card;
    }

    private View actionCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("QUICK ACTIONS"));

        Button grant = primaryButton("Grant Shizuku Permission");
        grant.setOnClickListener(v -> requestShizuku());
        card.addView(grant);

        Button choose = secondaryButton("Choose USB / Pendrive Folder");
        choose.setOnClickListener(v -> chooseUsbFolder());
        card.addView(choose);

        startStop = primaryButton("START AUTO MOVE");
        startStop.setOnClickListener(v -> toggleBridge());
        card.addView(startStop);

        Button test = secondaryButton("Test USB Destination Safely");
        test.setOnClickListener(v -> testDestination());
        card.addView(test);

        Button resetSafety = secondaryButton("Reset Safer Mode After Review");
        resetSafety.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Reset Safer Mode?")
                .setMessage("Only reset this after checking the USB. This does not bypass verification or cleanup revalidation.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Reset", (d, w) -> {
                    prefs.edit().putBoolean("cleanup_suspended", false).putInt("recent_safety_failures", 0).apply();
                    refresh();
                }).show());
        card.addView(resetSafety);
        return card;
    }

    private View safetyCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("SAFE CLEANUP — RECOMMENDED"));
        card.addView(label("Transfer → Verify → 5 minute safety delay → USB revalidation → Source cleanup", 13,
                Color.rgb(169, 179, 197), false));

        moveExisting = check("Process existing Nagram files (migration)", prefs.getBoolean("move_existing", false));
        moveExisting.setOnCheckedChangeListener((b, checked) -> prefs.edit().putBoolean("move_existing", checked).apply());
        card.addView(moveExisting);

        deleteAfter = check("Delete source only after verified safe cleanup", prefs.getBoolean("delete_after_verified", true));
        deleteAfter.setOnCheckedChangeListener((b, checked) -> prefs.edit().putBoolean("delete_after_verified", checked).apply());
        card.addView(deleteAfter);

        duplicateGuard = check("Same Video Duplicate Guard", prefs.getBoolean("duplicate_guard", true));
        duplicateGuard.setOnCheckedChangeListener((b, checked) -> prefs.edit().putBoolean("duplicate_guard", checked).apply());
        card.addView(duplicateGuard);

        autoStart = check("Boot recovery / auto-start Bridge", prefs.getBoolean("auto_start", false));
        autoStart.setOnCheckedChangeListener((b, checked) -> prefs.edit().putBoolean("auto_start", checked).apply());
        card.addView(autoStart);

        TextView explain = label("Duplicate rule: same displayed MB is never enough. Bridge first matches exact bytes, then a content fingerprint, and confirms a full SHA-256 only when a duplicate candidate is found. Filename changes do not decide duplicate status.",
                12, Color.rgb(0, 217, 255), false);
        explain.setPadding(0, dp(8), 0, 0);
        card.addView(explain);
        return card;
    }

    private View activityCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("TODAY & RECENT ACTIVITY"));
        todayStats = label("Today: —", 15, Color.WHITE, true);
        todayStats.setPadding(0, dp(8), 0, dp(10));
        recentText = label("No transfer history yet.", 13, Color.rgb(169, 179, 197), false);
        card.addView(todayStats);
        card.addView(recentText);
        return card;
    }

    private View footer() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        TextView t = label("NAGRAM USB BRIDGE", 12, Color.rgb(0, 217, 255), true);
        TextView p = label("Profile: SHAHADAT • Local-only transfer workflow", 11, Color.rgb(169, 179, 197), false);
        box.addView(t); box.addView(p);
        return box;
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(17, 24, 39));
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), Color.rgb(33, 47, 70));
        l.setBackground(bg);
        l.setElevation(dp(2));
        return l;
    }

    private TextView sectionTitle(String text) {
        return label(text, 12, Color.rgb(0, 217, 255), true);
    }

    private TextView statusRow(LinearLayout root, String text) {
        TextView tv = label(text, 14, Color.rgb(225, 231, 239), false);
        tv.setPadding(0, dp(9), 0, dp(3));
        root.addView(tv);
        return tv;
    }

    private TextView label(String text, float sp, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    private CheckBox check(String text, boolean checked) {
        CheckBox c = new CheckBox(this);
        c.setText(text);
        c.setTextColor(Color.rgb(225, 231, 239));
        c.setTextSize(14);
        c.setChecked(checked);
        c.setPadding(0, dp(6), 0, dp(4));
        return c;
    }

    private Button primaryButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(Color.rgb(4, 13, 20));
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(0, 217, 255));
        bg.setCornerRadius(dp(14));
        b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        lp.setMargins(0, dp(10), 0, 0);
        b.setLayoutParams(lp);
        return b;
    }

    private Button secondaryButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(23, 32, 51));
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), Color.rgb(51, 70, 96));
        b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        lp.setMargins(0, dp(9), 0, 0);
        b.setLayoutParams(lp);
        return b;
    }

    private View space(int dp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(dp)));
        return v;
    }

    private void requestShizuku() {
        if (!Shizuku.pingBinder()) {
            Toast.makeText(this, "Shizuku চালু করুন", Toast.LENGTH_LONG).show();
            return;
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Shizuku permission already granted", Toast.LENGTH_SHORT).show();
        } else {
            Shizuku.requestPermission(REQ_SHIZUKU);
        }
    }

    private void chooseUsbFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent, REQ_TREE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_TREE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            int takeFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try {
                getContentResolver().takePersistableUriPermission(uri, takeFlags);
                prefs.edit().putString("tree_uri", uri.toString()).apply();
                Toast.makeText(this, "USB destination saved", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Folder permission save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
            refresh();
        }
    }

    private void toggleBridge() {
        boolean running = prefs.getBoolean("running", false);
        if (running) {
            stopService(new Intent(this, BridgeService.class));
            prefs.edit().putBoolean("running", false).apply();
            refresh();
            return;
        }
        startBridge();
    }

    private void startBridge() {
        if (!Shizuku.pingBinder()) {
            Toast.makeText(this, "আগে Shizuku চালু করুন", Toast.LENGTH_LONG).show();
            return;
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            requestShizuku();
            return;
        }
        if (prefs.getString("tree_uri", null) == null) {
            Toast.makeText(this, "আগে Pendrive/USB folder select করুন", Toast.LENGTH_LONG).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
        }
        Intent i = new Intent(this, BridgeService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        prefs.edit().putBoolean("running", true).apply();
        refresh();
        Toast.makeText(this, "Auto Move started safely", Toast.LENGTH_SHORT).show();
    }

    private void testDestination() {
        String tree = prefs.getString("tree_uri", null);
        if (tree == null) {
            Toast.makeText(this, "Select USB folder first", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "Testing USB safely…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            Uri test = null;
            try {
                Uri treeUri = Uri.parse(tree);
                Uri root = DocumentTreeUtils.rootDocumentUri(treeUri);
                ContentResolver r = getContentResolver();
                test = DocumentsContract.createDocument(r, root, "application/octet-stream", ".bridge_test_" + System.currentTimeMillis());
                if (test == null) throw new IllegalStateException("Could not create test file");
                byte[] payload = "NAGRAM_USB_BRIDGE_TEST".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                try (OutputStream out = r.openOutputStream(test, "w")) {
                    if (out == null) throw new IllegalStateException("USB is not writable");
                    out.write(payload); out.flush();
                }
                byte[] got = new byte[payload.length];
                try (InputStream in = r.openInputStream(test)) {
                    if (in == null || in.read(got) != payload.length) throw new IllegalStateException("Read-back failed");
                }
                final boolean ok = java.util.Arrays.equals(payload, got);
                runOnUiThread(() -> Toast.makeText(this, ok ? "✓ USB destination is working" : "USB verification failed", Toast.LENGTH_LONG).show());
            } catch (Throwable t) {
                final String msg = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
                runOnUiThread(() -> Toast.makeText(this, "USB test failed: " + msg, Toast.LENGTH_LONG).show());
            } finally {
                if (test != null) {
                    try { DocumentsContract.deleteDocument(getContentResolver(), test); } catch (Throwable ignored) {}
                }
            }
        }).start();
    }

    private void refresh() {
        if (prefs == null) return;
        runOnUiThread(() -> {
            boolean binder = Shizuku.pingBinder();
            boolean granted = false;
            if (binder) {
                try { granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED; } catch (Throwable ignored) {}
            }
            boolean running = prefs.getBoolean("running", false);
            String tree = prefs.getString("tree_uri", null);
            boolean cleanupSuspended = prefs.getBoolean("cleanup_suspended", false);

            shizukuStatus.setText("Shizuku: " + (binder ? (granted ? "Connected + Allowed ✓" : "Permission needed") : "Not running"));
            if (tree == null) folderStatus.setText("USB: destination not selected");
            else {
                String name = null;
                try { name = DocumentTreeUtils.getDisplayName(getContentResolver(), DocumentTreeUtils.rootDocumentUri(Uri.parse(tree))); } catch (Throwable ignored) {}
                folderStatus.setText("USB: " + (name == null ? "Selected destination" : name) + " ✓");
            }
            autoStatus.setText("Auto Move: " + (running ? "ON ✓" : "OFF"));
            startStop.setText(running ? "PAUSE AUTO MOVE" : "START AUTO MOVE");

            String current = prefs.getString("current_name", null);
            if (current != null) {
                int pct = prefs.getInt("current_percent", 0);
                long done = prefs.getLong("current_done", 0L);
                long total = prefs.getLong("current_total", 0L);
                long speed = prefs.getLong("current_speed", 0L);
                long eta = prefs.getLong("current_eta", -1L);
                currentName.setText(current);
                progress.setProgress(pct);
                currentMeta.setText(BridgeService.human(done) + " / " + BridgeService.human(total) + " • " + pct + "% • " +
                        BridgeService.humanRate(speed) + (eta >= 0 ? " • ~" + eta + "s" : ""));
                masterTitle.setText("● Moving to USB");
                masterSub.setText("Safe transfer in progress");
            } else {
                progress.setProgress(0);
                currentName.setText("No active transfer");
                currentMeta.setText(prefs.getString("last_status", "All caught up"));
                if (!binder || !granted) {
                    masterTitle.setText("⚠ Shizuku required");
                    masterSub.setText("Your originals are safe. Start/allow Shizuku to continue.");
                } else if (tree == null) {
                    masterTitle.setText("⏳ USB destination needed");
                    masterSub.setText("Choose your approved Pendrive folder.");
                } else if (cleanupSuspended) {
                    masterTitle.setText("⚠ Safer Mode active");
                    masterSub.setText("Transfers may continue, but automatic source cleanup is suspended.");
                } else if (running) {
                    masterTitle.setText("✓ Bridge Ready");
                    masterSub.setText("Watching Nagram for completed files.");
                } else {
                    masterTitle.setText("Bridge paused");
                    masterSub.setText("Start Auto Move when you are ready.");
                }
            }
            saferMode.setText(cleanupSuspended ? "Original cleanup suspended after repeated safety errors." : "");

            try {
                BridgeDatabase.Stats s = db.todayStats();
                todayStats.setText("Today: " + s.files + " files • " + BridgeService.human(s.bytes) + " • " + s.attention + " attention");
                List<BridgeDatabase.Record> recent = db.recent(6);
                if (recent.isEmpty()) recentText.setText("No transfer history yet.");
                else {
                    StringBuilder sb = new StringBuilder();
                    for (BridgeDatabase.Record r : recent) {
                        sb.append(statusIcon(r.status)).append(' ').append(r.sourceName)
                                .append(" • ").append(BridgeService.human(r.sourceSize))
                                .append("\n   ").append(friendlyStatus(r.status));
                        if (r.error != null && !r.error.isEmpty()) sb.append(" — ").append(r.error);
                        sb.append("\n\n");
                    }
                    recentText.setText(sb.toString().trim());
                }
            } catch (Throwable ignored) {}
        });
    }

    private static String statusIcon(String s) {
        if (s == null) return "•";
        if (s.equals("COMPLETED") || s.equals("COMPLETED_SOURCE_KEPT")) return "✓";
        if (s.equals("CLEANUP_PENDING") || s.equals("VERIFIED") || s.equals("DUPLICATE_VERIFIED")) return "◷";
        if (s.contains("WAITING") || s.equals("RETRY_PENDING")) return "⏳";
        if (s.contains("FAILED") || s.equals("NEEDS_ATTENTION") || s.equals("DESTINATION_INCOMPATIBLE")) return "⚠";
        return "●";
    }

    private static String friendlyStatus(String s) {
        if (s == null) return "Unknown";
        switch (s) {
            case "CLEANUP_PENDING": return "Verified • cleanup pending";
            case "COMPLETED": return "Moved safely • source cleaned";
            case "COMPLETED_SOURCE_KEPT": return "Verified • source kept";
            case "DUPLICATE_VERIFIED": return "Same video already on USB";
            case "WAITING_FOR_COMPLETION": return "Waiting for download to finish";
            case "WAITING_FOR_SPACE": return "Waiting for USB space";
            case "DESTINATION_INCOMPATIBLE": return "USB cannot accept this file safely";
            case "VERIFICATION_FAILED": return "Verification failed • original preserved";
            case "NEEDS_ATTENTION": return "Needs attention • original preserved";
            default: return s.replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        refreshHandler.removeCallbacks(ticker);
        refreshHandler.post(ticker);
    }

    @Override protected void onPause() {
        refreshHandler.removeCallbacks(ticker);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        refreshHandler.removeCallbacks(ticker);
        try { Shizuku.removeBinderReceivedListener(binderReceived); } catch (Throwable ignored) {}
        try { Shizuku.removeBinderDeadListener(binderDead); } catch (Throwable ignored) {}
        try { Shizuku.removeRequestPermissionResultListener(permissionResult); } catch (Throwable ignored) {}
        if (db != null) db.close();
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
