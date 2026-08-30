package com.nagram.usbbridge;

import android.os.ParcelFileDescriptor;

interface INagramFileService {
    String[] listMediaFiles() = 0;
    long getFileSize(String path) = 1;
    long getLastModified(String path) = 2;
    ParcelFileDescriptor openRead(String path) = 3;
    boolean deleteIfUnchanged(String path, long expectedSize, long expectedModified) = 4;
    void destroy() = 16777114;
}
