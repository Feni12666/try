package com.nagram.usbbridge;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.DocumentsContract;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.List;
import java.util.Locale;

final class StorageUtils {
    private StorageUtils() {}

    static String volumeId(Uri treeUri) {
        try {
            String id = DocumentsContract.getTreeDocumentId(treeUri);
            int colon = id.indexOf(':');
            return colon > 0 ? id.substring(0, colon) : id;
        } catch (Throwable ignored) {
            return null;
        }
    }

    static File volumeDirectory(Context context, Uri treeUri) {
        if (Build.VERSION.SDK_INT < 30) return null;
        String id = volumeId(treeUri);
        if (id == null) return null;
        StorageManager sm = context.getSystemService(StorageManager.class);
        if (sm == null) return null;
        List<StorageVolume> volumes = sm.getStorageVolumes();
        for (StorageVolume v : volumes) {
            String uuid = v.getUuid();
            if (uuid != null && uuid.equalsIgnoreCase(id)) return v.getDirectory();
        }
        return null;
    }

    static long availableBytes(Context context, Uri treeUri) {
        try {
            File dir = volumeDirectory(context, treeUri);
            if (dir == null) return -1L;
            return new StatFs(dir.getAbsolutePath()).getAvailableBytes();
        } catch (Throwable ignored) {
            return -1L;
        }
    }

    static String filesystemTypeBestEffort(Context context, Uri treeUri) {
        File dir = volumeDirectory(context, treeUri);
        if (dir == null) return null;
        String target = dir.getAbsolutePath();
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/mounts"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.split("\\s+");
                if (p.length < 3) continue;
                if (target.equals(p[1]) || target.startsWith(p[1] + "/") || p[1].startsWith(target + "/")) {
                    return p[2].toLowerCase(Locale.ROOT);
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    static boolean definitelyFat32(String fsType) {
        if (fsType == null) return false;
        String f = fsType.toLowerCase(Locale.ROOT);
        return f.equals("vfat") || f.equals("fat") || f.equals("fat32") || f.equals("msdos");
    }
}
