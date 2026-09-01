package com.nagram.usbbridge;

import android.os.ParcelFileDescriptor;

interface IRestrictedFileService {
    String[] listEntries(String path) = 0;
    long getFileSize(String path) = 1;
    long getLastModified(String path) = 2;
    boolean isDirectory(String path) = 3;
    ParcelFileDescriptor openRead(String path) = 4;
    ParcelFileDescriptor openWrite(String path) = 5;
    String createFile(String parentPath, String name) = 6;
    boolean deletePath(String path) = 7;
    boolean renamePath(String path, String newName) = 8;
    boolean createDirectory(String parentPath, String name) = 9;
    boolean isAllowedPath(String path) = 10;
    void destroy() = 16777114;
}
