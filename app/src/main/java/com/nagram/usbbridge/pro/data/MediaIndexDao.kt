package com.nagram.usbbridge.pro.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaIndexDao {
    @Query("SELECT COUNT(*) FROM media_index")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM media_index WHERE storageId = :storageId")
    fun observeCountForStorage(storageId: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: MediaIndexEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<MediaIndexEntity>)

    @Query("SELECT * FROM media_index WHERE sizeBytes = :sizeBytes ORDER BY modifiedMs DESC")
    suspend fun candidatesBySize(sizeBytes: Long): List<MediaIndexEntity>

    @Query("SELECT * FROM media_index WHERE sha256 = :sha256 LIMIT 50")
    suspend fun exactBySha256(sha256: String): List<MediaIndexEntity>

    @Query("DELETE FROM media_index WHERE storageId = :storageId")
    suspend fun clearStorage(storageId: String)
}
