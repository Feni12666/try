package com.nagram.usbbridge.shizuku;

import android.os.ParcelFileDescriptor;
import com.nagram.usbbridge.shizuku.RemoteFileEntry;

interface IShizukuFileService {
    List<RemoteFileEntry> listEntries(String absolutePath);
    ParcelFileDescriptor openForRead(String absolutePath);
}
