package com.nagram.usbbridge;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;

final class FingerprintUtils {
    private static final int SAMPLE = 256 * 1024;

    private FingerprintUtils() {}

    static String quick(ParcelFileDescriptor pfd, long size) throws Exception {
        if (pfd == null || size < 0) return null;
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(ByteBuffer.allocate(8).putLong(size).array());
        try (FileInputStream in = new FileInputStream(pfd.getFileDescriptor());
             FileChannel ch = in.getChannel()) {
            long[] offsets = new long[]{0L, Math.max(0L, size / 2L - SAMPLE / 2L), Math.max(0L, size - SAMPLE)};
            ByteBuffer buf = ByteBuffer.allocate(SAMPLE);
            for (long off : offsets) {
                buf.clear();
                ch.position(off);
                int remaining = (int) Math.min(SAMPLE, Math.max(0L, size - off));
                buf.limit(remaining);
                while (buf.hasRemaining() && ch.read(buf) > 0) {}
                md.update(buf.array(), 0, buf.position());
            }
        } finally {
            try { pfd.close(); } catch (Throwable ignored) {}
        }
        return hex(md.digest());
    }

    static String quickUri(ContentResolver resolver, Uri uri, long size) throws Exception {
        ParcelFileDescriptor pfd = resolver.openFileDescriptor(uri, "r");
        if (pfd == null) return null;
        return quick(pfd, size);
    }

    static String sha256(ParcelFileDescriptor pfd) throws Exception {
        if (pfd == null) return null;
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(pfd)) {
            byte[] buf = new byte[1024 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) md.update(buf, 0, n);
        }
        return hex(md.digest());
    }

    static String sha256Uri(ContentResolver resolver, Uri uri) throws Exception {
        ParcelFileDescriptor pfd = resolver.openFileDescriptor(uri, "r");
        if (pfd == null) return null;
        return sha256(pfd);
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x & 0xff));
        return sb.toString();
    }
}
