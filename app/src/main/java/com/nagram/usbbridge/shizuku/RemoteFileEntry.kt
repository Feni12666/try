package com.nagram.usbbridge.shizuku

import android.os.Parcel
import android.os.Parcelable

class RemoteFileEntry(
    val absolutePath: String,
    val displayName: String,
    val directory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
) : Parcelable {
    private constructor(parcel: Parcel) : this(
        absolutePath = parcel.readString().orEmpty(),
        displayName = parcel.readString().orEmpty(),
        directory = parcel.readInt() != 0,
        sizeBytes = parcel.readLong(),
        lastModified = parcel.readLong(),
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(absolutePath)
        parcel.writeString(displayName)
        parcel.writeInt(if (directory) 1 else 0)
        parcel.writeLong(sizeBytes)
        parcel.writeLong(lastModified)
    }

    override fun describeContents() = 0

    companion object CREATOR : Parcelable.Creator<RemoteFileEntry> {
        override fun createFromParcel(parcel: Parcel) = RemoteFileEntry(parcel)
        override fun newArray(size: Int): Array<RemoteFileEntry?> = arrayOfNulls(size)
    }
}
