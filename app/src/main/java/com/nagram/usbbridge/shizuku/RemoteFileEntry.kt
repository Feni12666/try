package com.nagram.usbbridge.shizuku

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class RemoteFileEntry(
    val absolutePath: String,
    val displayName: String,
    val directory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
) : Parcelable
