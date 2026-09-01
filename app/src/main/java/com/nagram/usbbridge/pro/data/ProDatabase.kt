package com.nagram.usbbridge.pro.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MediaIndexEntity::class],
    version = 1,
    exportSchema = true
)
abstract class ProDatabase : RoomDatabase() {
    abstract fun mediaIndexDao(): MediaIndexDao

    companion object {
        @Volatile private var INSTANCE: ProDatabase? = null

        fun get(context: Context): ProDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                ProDatabase::class.java,
                "shahadat_pro_index.db"
            ).fallbackToDestructiveMigrationOnDowngrade()
             .build()
             .also { INSTANCE = it }
        }
    }
}
