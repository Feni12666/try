package com.shahadat.managefile;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DecimalFormat;

public class MainActivity extends Activity {
    private static final int REQ_TREE = 1001;
    private static final String PREFS = "shahadat_prefs";
    private static final String KEY_TREE_URI = "tree_uri";

    private LinearLayout list;
    private TextView folderText;
    private Uri currentTree;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();

        String saved = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_TREE_URI, null);
        if (!TextUtils.isEmpty(saved)) {
            currentTree = Uri.parse(saved);
            folderText.setText("Selected folder: " + currentTree);
            loadFolder();
        }
    }

    private void buildUi() {
        int pad = dp(16);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(246, 249, 253));

        TextView header = new TextView(this);
        header.setText(getApplicationInfo().loadLabel(getPackageManager()));
        header.setTextSize(22);
        header.setTextColor(Color.WHITE);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(pad, dp(18), pad, dp(18));
        header.setBackgroundColor(Color.rgb(8, 26, 58));
        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(pad, pad, pad, dp(8));

        folderText = new TextView(this);
        folderText.setText("No folder selected");
        folderText.setTextColor(Color.rgb(11, 23, 48));
        folderText.setTextSize(14);
        folderText.setPadding(0, 0, 0, dp(10));
        controls.addView(folderText);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);

        Button choose = new Button(this);
        choose.setText("Choose Folder");
        choose.setOnClickListener(v -> chooseFolder());
        buttons.addView(choose, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button refresh = new Button(this);
        refresh.setText("Refresh");
        refresh.setOnClickListener(v -> loadFolder());
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        rlp.setMargins(dp(8), 0, 0, 0);
        buttons.addView(refresh, rlp);

        controls.addView(buttons);
        root.addView(controls);

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(pad, dp(4), pad, pad);
        scroll.addView(list, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        TextView footer = new TextView(this);
        footer.setText("Shahadat Manage File • SAF folder browser");
        footer.setTextColor(Color.rgb(72, 94, 122));
        footer.setTextSize(12);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(pad, dp(10), pad, dp(12));
        root.addView(footer);

        setContentView(root);
    }

    private void chooseFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION |
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent, REQ_TREE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_TREE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            currentTree = data.getData();
            final int takeFlags = data.getFlags() &
                    (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try {
                getContentResolver().takePersistableUriPermission(currentTree, takeFlags);
            } catch (Exception ignored) {
            }
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_TREE_URI, currentTree.toString()).apply();
            folderText.setText("Selected folder: " + currentTree);
            loadFolder();
        }
    }

    private void loadFolder() {
        list.removeAllViews();
        if (currentTree == null) {
            addMessage("Choose a folder first.");
            return;
        }

        try {
            String treeId = DocumentsContract.getTreeDocumentId(currentTree);
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(currentTree, treeId);

            String[] projection = new String[]{
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID
            };

            int count = 0;
            try (Cursor c = getContentResolver().query(children, projection, null, null, null)) {
                if (c != null) {
                    while (c.moveToNext()) {
                        String name = c.getString(0);
                        String mime = c.getString(1);
                        long size = c.isNull(2) ? -1 : c.getLong(2);
                        boolean folder = DocumentsContract.Document.MIME_TYPE_DIR.equals(mime);
                        addRow(folder ? "📁" : "📄", name, folder ? "Folder" : humanSize(size));
                        count++;
                    }
                }
            }
            if (count == 0) addMessage("This folder is empty.");
        } catch (Exception e) {
            addMessage("Could not read this folder: " + e.getMessage());
            Toast.makeText(this, "Folder access failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void addRow(String icon, String title, String sub) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));
        row.setBackgroundColor(Color.WHITE);

        TextView left = new TextView(this);
        left.setText(icon);
        left.setTextSize(26);
        row.addView(left, new LinearLayout.LayoutParams(dp(42), ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(16);
        t.setTextColor(Color.rgb(11, 23, 48));
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(t);

        TextView s = new TextView(this);
        s.setText(sub);
        s.setTextSize(12);
        s.setTextColor(Color.rgb(90, 110, 135));
        texts.addView(s);

        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(8));
        list.addView(row, lp);
    }

    private void addMessage(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(15);
        v.setTextColor(Color.rgb(72, 94, 122));
        v.setPadding(dp(12), dp(20), dp(12), dp(20));
        list.addView(v);
    }

    private String humanSize(long bytes) {
        if (bytes < 0) return "File";
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return new DecimalFormat("0.0").format(kb) + " KB";
        double mb = kb / 1024.0;
        if (mb < 1024) return new DecimalFormat("0.0").format(mb) + " MB";
        return new DecimalFormat("0.00").format(mb / 1024.0) + " GB";
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
