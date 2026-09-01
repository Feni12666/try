package com.nagram.usbbridge.pro

import android.app.Application
import com.nagram.usbbridge.pro.data.ProDatabase
import com.nagram.usbbridge.pro.data.RoomMediaIndexRepository

class ShahadatProApplication : Application() {
    val proDatabase: ProDatabase by lazy { ProDatabase.get(this) }
    val mediaIndexRepository: RoomMediaIndexRepository by lazy {
        RoomMediaIndexRepository(proDatabase.mediaIndexDao())
    }
}
