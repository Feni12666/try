package com.nagram.usbbridge;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {

    private static final int REQ_TREE = 2001;
    private static final int REQ_SHIZUKU = 2002;
    private static final int REQ_NOTIFICATIONS = 2003;

    private SharedPreferences prefs;
    private TextView shizukuStatus;
    private TextView folderStatus;
    private TextView serviceStatus;
    private CheckBox moveExisting;

    private final Shizuku.OnBinderReceivedListener binderReceived = this::refresh;
    private final Shizuku.OnBinderDeadListener binderDead = this::refresh;
    private final Shizuku.OnRequestPermissionResultListener permissionResult = (requestCode, grantResult) -> {
        if (requestCode == REQ_SHIZUKU) refresh();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("bridge", MODE_PRIVATE);

        // Build views before registering the sticky Shizuku listener, because
        // the sticky callback may fire immediately when Shizuku is already running.
        setContentView(buildUi());

        Shizuku.addBinderReceivedListenerSticky(binderReceived);
        Shizuku.addBinderDeadListener(binderDead);
        Shizuku.addRequestPermissionResultListener(permissionResult);

        refresh();
    }

    private View buildUi() {
        int pad = dp(20);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("Nagram → USB Bridge");
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Nagram download complete হলে Shizuku দিয়ে file read করে আপনার selected Pendrive folder-এ সরাসরি copy করবে। Copy verify হওয়ার পরই phone-এর original delete হবে।");
        sub.setTextSize(16);
        sub.setPadding(0, dp(8), 0, dp(20));
        root.addView(sub);

        shizukuStatus = statusLine(root, "Shizuku: checking…");
        folderStatus = statusLine(root, "USB folder: not selected");
        serviceStatus = statusLine(root, "Auto move: stopped");

        Button grant = button("1) Grant Shizuku Permission");
        grant.setOnClickListener(v -> requestShizuku());
        root.addView(grant);

        Button choose = button("2) Choose Pendrive 'ok' Folder");
        choose.setOnClickListener(v -> chooseUsbFolder());
        root.addView(choose);

        moveExisting = new CheckBox(this);
        moveExisting.setText("আগে থেকে থাকা Nagram files-ও move করবে");
        moveExisting.setTextSize(15);
        moveExisting.setChecked(prefs.getBoolean("move_existing", false));
        moveExisting.setPadding(0, dp(8), 0, dp(8));
        moveExisting.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean("move_existing", isChecked).apply());
        root.addView(moveExisting);

        Button start = button("3) START AUTO MOVE");
        start.setOnClickListener(v -> startBridge());
        root.addView(start);

        Button stop = button("STOP");
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, BridgeService.class));
            prefs.edit().putBoolean("running", false).apply();
            refresh();
        });
        root.addView(stop);

        TextView note = new TextView(this);
        note.setText("গুরুত্বপূর্ণ: Pendrive খুলে ফেললে source file delete হবে না। USB আবার লাগালে service চলমান থাকলে transfer আবার চেষ্টা করবে। প্রথমে একটি ছোট video দিয়ে test করুন।");
        note.setTextSize(14);
        note.setPadding(0, dp(18), 0, dp(12));
        root.addView(note);

        return scroll;
    }

    private TextView statusLine(LinearLayout root, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(15);
        tv.setPadding(0, dp(4), 0, dp(4));
        root.addView(tv);
        return tv;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(6), 0, dp(6));
        b.setLayoutParams(lp);
        return b;
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
            } catch (Exception e) {
                Toast.makeText(this, "Folder permission save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                return;
            }
            prefs.edit().putString("tree_uri", uri.toString()).apply();
            refresh();
            Toast.makeText(this, "USB folder saved", Toast.LENGTH_SHORT).show();
        }
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
            Toast.makeText(this, "আগে Pendrive-এর ok folder select করুন", Toast.LENGTH_LONG).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
        }

        Intent i = new Intent(this, BridgeService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        prefs.edit().putBoolean("running", true).apply();
        refresh();
        Toast.makeText(this, "Auto move started", Toast.LENGTH_SHORT).show();
    }

    private void refresh() {
        runOnUiThread(() -> {
            boolean binder = Shizuku.pingBinder();
            boolean granted = false;
            if (binder) {
                try {
                    granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
                } catch (Throwable ignored) {}
            }
            shizukuStatus.setText("Shizuku: " + (binder ? (granted ? "Connected + Allowed ✅" : "Connected, permission needed") : "Not running ❌"));

            String tree = prefs.getString("tree_uri", null);
            if (tree == null) {
                folderStatus.setText("USB folder: not selected ❌");
            } else {
                String name = null;
                try { name = DocumentTreeUtils.getDisplayName(getContentResolver(), DocumentTreeUtils.rootDocumentUri(Uri.parse(tree))); } catch (Throwable ignored) {}
                folderStatus.setText("USB folder: " + (name == null ? tree : name) + " ✅");
            }

            String last = prefs.getString("last_status", "");
            boolean running = prefs.getBoolean("running", false);
            serviceStatus.setText("Auto move: " + (running ? "running ✅" : "stopped") + (last.isEmpty() ? "" : "\n" + last));
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (prefs != null) refresh();
    }

    @Override
    protected void onDestroy() {
        Shizuku.removeBinderReceivedListener(binderReceived);
        Shizuku.removeBinderDeadListener(binderDead);
        Shizuku.removeRequestPermissionResultListener(permissionResult);
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
