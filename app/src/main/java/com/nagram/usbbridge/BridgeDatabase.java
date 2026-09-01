package com.nagram.usbbridge;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

final class BridgeDatabase extends SQLiteOpenHelper {
    static final String DB_NAME = "bridge_journal.db";
    private static final int DB_VERSION = 1;

    static final class Record {
        long id;
        String sourcePath;
        String sourceName;
        long sourceSize;
        long sourceMtime;
        String treeUri;
        String destUri;
        String destName;
        String status;
        String quickFingerprint;
        String fullHash;
        long cleanupAfter;
        String error;
        long createdAt;
        long updatedAt;
    }

    static final class Stats {
        long files;
        long bytes;
        long attention;
    }

    BridgeDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE transfers (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "source_path TEXT NOT NULL," +
                "source_name TEXT NOT NULL," +
                "source_size INTEGER NOT NULL," +
                "source_mtime INTEGER NOT NULL," +
                "tree_uri TEXT," +
                "dest_uri TEXT," +
                "dest_name TEXT," +
                "status TEXT NOT NULL," +
                "quick_fp TEXT," +
                "full_hash TEXT," +
                "cleanup_after INTEGER DEFAULT 0," +
                "last_error TEXT," +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL," +
                "UNIQUE(source_path, source_size, source_mtime) ON CONFLICT IGNORE" +
                ")");
        db.execSQL("CREATE INDEX idx_transfers_status ON transfers(status)");
        db.execSQL("CREATE INDEX idx_transfers_fp ON transfers(source_size, quick_fp)");
        db.execSQL("CREATE INDEX idx_transfers_updated ON transfers(updated_at DESC)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Version 1. Future migrations must preserve active transfer state.
    }

    synchronized long upsertSource(String path, String name, long size, long mtime, String status) {
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        ContentValues v = new ContentValues();
        v.put("source_path", path);
        v.put("source_name", name);
        v.put("source_size", size);
        v.put("source_mtime", mtime);
        v.put("status", status);
        v.put("created_at", now);
        v.put("updated_at", now);
        db.insertWithOnConflict("transfers", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        try (Cursor c = db.rawQuery(
                "SELECT id FROM transfers WHERE source_path=? AND source_size=? AND source_mtime=? LIMIT 1",
                new String[]{path, String.valueOf(size), String.valueOf(mtime)})) {
            if (c.moveToFirst()) return c.getLong(0);
        }
        return -1L;
    }

    synchronized void update(long id, String status, String treeUri, String destUri, String destName,
                             String quickFp, String fullHash, long cleanupAfter, String error) {
        if (id <= 0) return;
        ContentValues v = new ContentValues();
        if (status != null) v.put("status", status);
        if (treeUri != null) v.put("tree_uri", treeUri);
        if (destUri != null) v.put("dest_uri", destUri);
        if (destName != null) v.put("dest_name", destName);
        if (quickFp != null) v.put("quick_fp", quickFp);
        if (fullHash != null) v.put("full_hash", fullHash);
        v.put("cleanup_after", cleanupAfter);
        if (error == null) v.putNull("last_error"); else v.put("last_error", error);
        v.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("transfers", v, "id=?", new String[]{String.valueOf(id)});
    }

    synchronized void updateStatus(long id, String status, String error) {
        if (id <= 0) return;
        ContentValues v = new ContentValues();
        v.put("status", status);
        if (error == null) v.putNull("last_error"); else v.put("last_error", error);
        v.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("transfers", v, "id=?", new String[]{String.valueOf(id)});
    }

    synchronized List<Record> dueCleanup(long now) {
        List<Record> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM transfers WHERE status='CLEANUP_PENDING' AND cleanup_after<=? ORDER BY cleanup_after ASC LIMIT 20",
                new String[]{String.valueOf(now)})) {
            while (c.moveToNext()) out.add(read(c));
        }
        return out;
    }

    synchronized List<Record> recoverable() {
        List<Record> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM transfers WHERE status IN ('PREFLIGHT','TRANSFERRING','VERIFYING','FINALIZING') ORDER BY updated_at ASC",
                null)) {
            while (c.moveToNext()) out.add(read(c));
        }
        return out;
    }

    synchronized boolean isKnownHandled(String path, long size, long mtime) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT status FROM transfers WHERE source_path=? AND source_size=? AND source_mtime=? LIMIT 1",
                new String[]{path, String.valueOf(size), String.valueOf(mtime)})) {
            if (!c.moveToFirst()) return false;
            String st = c.getString(0);
            return st != null && (st.equals("COMPLETED") || st.equals("COMPLETED_SOURCE_KEPT") ||
                    st.equals("CLEANUP_PENDING") || st.equals("VERIFIED") || st.equals("DUPLICATE_VERIFIED"));
        }
    }

    synchronized List<Record> fingerprintCandidates(long size, String quickFp, String treeUri) {
        List<Record> out = new ArrayList<>();
        if (quickFp == null || treeUri == null) return out;
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM transfers WHERE source_size=? AND quick_fp=? AND tree_uri=? " +
                        "AND dest_uri IS NOT NULL AND status IN ('VERIFIED','CLEANUP_PENDING','COMPLETED','DUPLICATE_VERIFIED','COMPLETED_SOURCE_KEPT') " +
                        "ORDER BY updated_at DESC LIMIT 20",
                new String[]{String.valueOf(size), quickFp, treeUri})) {
            while (c.moveToNext()) out.add(read(c));
        }
        return out;
    }

    synchronized List<Record> recent(int limit) {
        List<Record> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM transfers ORDER BY updated_at DESC LIMIT " + Math.max(1, Math.min(limit, 200)), null)) {
            while (c.moveToNext()) out.add(read(c));
        }
        return out;
    }

    synchronized Stats todayStats() {
        long now = System.currentTimeMillis();
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(now);
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        long start = cal.getTimeInMillis();
        Stats s = new Stats();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*), COALESCE(SUM(source_size),0) FROM transfers WHERE updated_at>=? AND status IN ('COMPLETED','COMPLETED_SOURCE_KEPT')",
                new String[]{String.valueOf(start)})) {
            if (c.moveToFirst()) {
                s.files = c.getLong(0);
                s.bytes = c.getLong(1);
            }
        }
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM transfers WHERE updated_at>=? AND status IN ('NEEDS_ATTENTION','VERIFICATION_FAILED','DESTINATION_INCOMPATIBLE')",
                new String[]{String.valueOf(start)})) {
            if (c.moveToFirst()) s.attention = c.getLong(0);
        }
        return s;
    }

    private Record read(Cursor c) {
        Record r = new Record();
        r.id = c.getLong(c.getColumnIndexOrThrow("id"));
        r.sourcePath = c.getString(c.getColumnIndexOrThrow("source_path"));
        r.sourceName = c.getString(c.getColumnIndexOrThrow("source_name"));
        r.sourceSize = c.getLong(c.getColumnIndexOrThrow("source_size"));
        r.sourceMtime = c.getLong(c.getColumnIndexOrThrow("source_mtime"));
        r.treeUri = c.getString(c.getColumnIndexOrThrow("tree_uri"));
        r.destUri = c.getString(c.getColumnIndexOrThrow("dest_uri"));
        r.destName = c.getString(c.getColumnIndexOrThrow("dest_name"));
        r.status = c.getString(c.getColumnIndexOrThrow("status"));
        r.quickFingerprint = c.getString(c.getColumnIndexOrThrow("quick_fp"));
        r.fullHash = c.getString(c.getColumnIndexOrThrow("full_hash"));
        r.cleanupAfter = c.getLong(c.getColumnIndexOrThrow("cleanup_after"));
        r.error = c.getString(c.getColumnIndexOrThrow("last_error"));
        r.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
        r.updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at"));
        return r;
    }
}
