package com.nagram.usbbridge;

import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Runs as a Shizuku UserService and is restricted to Nagram's external media tree. */
public class NagramPrivilegedService extends INagramFileService.Stub {
    private static final String BASE = "/storage/emulated/0/Android/data/xyz.nextalone.nagram/files";
    private static final String[] DIRS = {
            "videos", "documents", "images", "audios", "stories", "emojis"
    };

    public NagramPrivilegedService() {}

    private static boolean isAllowed(String rawPath) {
        if (rawPath == null) return false;
        try {
            String canonical = new File(rawPath).getCanonicalPath();
            for (String dir : DIRS) {
                String root = new File(BASE, dir).getCanonicalPath();
                if (canonical.equals(root) || canonical.startsWith(root + File.separator)) return true;
            }
        } catch (IOException ignored) {}
        return false;
    }

    @Override
    public String[] listMediaFiles() {
        List<File> files = new ArrayList<>();
        for (String dirName : DIRS) collect(new File(BASE, dirName), files, 0);
        Collections.sort(files, Comparator.comparingLong(File::lastModified));
        String[] out = new String[files.size()];
        for (int i = 0; i < files.size(); i++) out[i] = files.get(i).getAbsolutePath();
        return out;
    }

    private static void collect(File dir, List<File> out, int depth) {
        if (depth > 2) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File f : children) {
            if (f.isDirectory()) {
                collect(f, out, depth + 1);
                continue;
            }
            if (!f.isFile()) continue;
            String n = f.getName().toLowerCase();
            if (n.endsWith(".part") || n.endsWith(".tmp") || n.endsWith(".temp") || n.endsWith(".crdownload")) continue;
            out.add(f);
        }
    }

    @Override
    public long getFileSize(String path) {
        if (!isAllowed(path)) return -1L;
        File f = new File(path);
        return f.isFile() ? f.length() : -1L;
    }

    @Override
    public long getLastModified(String path) {
        if (!isAllowed(path)) return -1L;
        File f = new File(path);
        return f.isFile() ? f.lastModified() : -1L;
    }

    @Override
    public ParcelFileDescriptor openRead(String path) {
        if (!isAllowed(path)) return null;
        File f = new File(path);
        if (!f.isFile()) return null;
        try {
            return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (FileNotFoundException ignored) {
            return null;
        }
    }

    @Override
    public boolean deleteIfUnchanged(String path, long expectedSize, long expectedModified) {
        if (!isAllowed(path)) return false;
        File f = new File(path);
        if (!f.isFile()) return false;
        if (f.length() != expectedSize) return false;
        if (expectedModified > 0 && f.lastModified() != expectedModified) return false;
        return f.delete();
    }

    @Override
    public boolean isSourceLayoutAvailable() {
        File base = new File(BASE);
        if (!base.isDirectory()) return false;
        int present = 0;
        for (String dir : DIRS) if (new File(base, dir).isDirectory()) present++;
        return present >= 3;
    }

    @Override
    public void destroy() {
        System.exit(0);
    }
}
