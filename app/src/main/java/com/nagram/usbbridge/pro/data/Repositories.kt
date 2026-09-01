package com.nagram.usbbridge.pro.data

import kotlinx.coroutines.flow.Flow

enum class StorageScope { PHONE, USB }

data class IndexedMediaSummary(
    val totalIndexed: Int,
    val phoneIndexed: Int,
    val usbIndexed: Int
)

interface MediaIndexRepository {
    fun observeAllCount(): Flow<Int>
    fun observePhoneCount(): Flow<Int>
    fun observeUsbCount(): Flow<Int>
    suspend fun upsert(item: MediaIndexEntity): Long
}

class RoomMediaIndexRepository(
    private val dao: MediaIndexDao
) : MediaIndexRepository {
    override fun observeAllCount(): Flow<Int> = dao.observeCount()
    override fun observePhoneCount(): Flow<Int> = dao.observeCountForStorage("PHONE")
    override fun observeUsbCount(): Flow<Int> = dao.observeCountForStorage("USB")
    override suspend fun upsert(item: MediaIndexEntity): Long = dao.upsert(item)
}

interface FileRepository
interface VideoRepository
interface DuplicateRepository
interface SyncRepository
