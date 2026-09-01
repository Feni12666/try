package com.nagram.usbbridge;

import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Shizuku UserService restricted to the shared-storage Android/data and Android/obb trees.
 * No fixed app package/folder is hard-coded.
 */
public class RestrictedFileService extends IRestrictedFileService.Stub {
    private static final String DATA = "/storage/emulated/0/Android/data";
    private static final String OBB = "/storage/emulated/0/Android/obb";
    private static final String SEP = "\u0001";

    public RestrictedFileService() {}

    private static String canonical(String p) throws IOException { return new File(p).getCanonicalPath(); }

    private static boolean allowed(String raw) {
        if (raw == null) return false;
        try {
            String p = canonical(raw);
            String data = canonical(DATA);
            String obb = canonical(OBB);
            return p.equals(data) || p.startsWith(data + File.separator) || p.equals(obb) || p.startsWith(obb + File.separator);
        } catch (IOException e) {
            return false;
        }
    }

    @Override public boolean isAllowedPath(String path) { return allowed(path); }

    @Override
    public String[] listEntries(String path) {
        if (!allowed(path)) return new String[0];
        File dir = new File(path);
        File[] files = dir.listFiles();
        if (files == null) return new String[0];
        List<File> list = new ArrayList<>();
        Collections.addAll(list, files);
        list.sort(Comparator.comparing((File f) -> !f.isDirectory()).thenComparing(f -> f.getName().toLowerCase()));
        String[] out = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            File f = list.get(i);
            out[i] = f.getAbsolutePath() + SEP + (f.isDirectory() ? "D" : "F") + SEP +
                    (f.isFile() ? f.length() : 0L) + SEP + f.lastModified() + SEP + f.getName();
        }
        return out;
    }

    @Override public long getFileSize(String path) {
        if (!allowed(path)) return -1L;
        File f = new File(path);
        return f.isFile() ? f.length() : 0L;
    }

    @Override public long getLastModified(String path) {
        if (!allowed(path)) return -1L;
        return new File(path).lastModified();
    }

    @Override public boolean isDirectory(String path) {
        return allowed(path) && new File(path).isDirectory();
    }

    @Override public ParcelFileDescriptor openRead(String path) {
        if (!allowed(path)) return null;
        File f = new File(path);
        if (!f.isFile()) return null;
        try { return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY); }
        catch (FileNotFoundException e) { return null; }
    }

    @Override public ParcelFileDescriptor openWrite(String path) {
        if (!allowed(path)) return null;
        File f = new File(path);
        File parent = f.getParentFile();
        if (parent == null || !allowed(parent.getAbsolutePath()) || !parent.isDirectory()) return null;
        try {
            int mode = ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE | ParcelFileDescriptor.MODE_WRITE_ONLY;
            return ParcelFileDescriptor.open(f, mode);
        } catch (FileNotFoundException e) { return null; }
    }

    @Override public String createFile(String parentPath, String name) {
        if (!allowed(parentPath) || name == null || name.trim().isEmpty() || name.contains("/")) return null;
        File parent = new File(parentPath);
        if (!parent.isDirectory()) return null;
        File target = new File(parent, name.trim());
        if (target.exists()) return null;
        try { return target.createNewFile() ? target.getAbsolutePath() : null; }
        catch (IOException e) { return null; }
    }

    @Override public boolean deletePath(String path) {
        if (!allowed(path)) return false;
        File f = new File(path);
        return deleteRecursively(f);
    }

    private static boolean deleteRecursively(File f) {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File c : children) if (!deleteRecursively(c)) return false;
        }
        return f.delete();
    }

    @Override public boolean renamePath(String path, String newName) {
        if (!allowed(path) || newName == null || newName.trim().isEmpty() || newName.contains("/")) return false;
        File f = new File(path);
        File parent = f.getParentFile();
        if (parent == null || !allowed(parent.getAbsolutePath())) return false;
        File target = new File(parent, newName.trim());
        if (target.exists()) return false;
        return f.renameTo(target);
    }

    @Override public boolean createDirectory(String parentPath, String name) {
        if (!allowed(parentPath) || name == null || name.trim().isEmpty() || name.contains("/")) return false;
        File target = new File(parentPath, name.trim());
        return !target.exists() && target.mkdir();
    }

    @Override public void destroy() { System.exit(0); }
}
