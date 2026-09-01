package com.nagram.usbbridge.pro.video

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nagram.usbbridge.pro.files.StorageKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VideoLibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = VideoRepository(app)
    private val _state = MutableStateFlow(VideoLibraryUiState())
    val state: StateFlow<VideoLibraryUiState> = _state.asStateFlow()
    private var allItems: List<VideoItem> = emptyList()

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                loading = true,
                permissionGranted = repo.hasVideoPermission(),
                usbSelected = repo.hasUsb(),
                message = null
            )
            runCatching {
                val phone = if (repo.hasVideoPermission()) repo.loadPhoneVideos() else emptyList()
                val usb = if (repo.hasUsb()) repo.loadUsbVideos() else emptyList()
                phone + usb
            }.onSuccess { all ->
                allItems = all
                rebuild(allItems, loading = false)
            }.onFailure { t ->
                _state.value = _state.value.copy(loading = false, message = t.message ?: "Could not scan videos")
            }
        }
    }

    fun setFilter(filter: VideoStorageFilter) {
        _state.value = _state.value.copy(filter = filter, selectedFolderKey = null)
        rebuild(allItems, loading = false)
    }

    fun setQuery(query: String) {
        _state.value = _state.value.copy(query = query)
        rebuild(allItems, loading = false)
    }

    fun openFolder(key: String) { _state.value = _state.value.copy(selectedFolderKey = key) }
    fun closeFolder() { _state.value = _state.value.copy(selectedFolderKey = null) }

    private fun rebuild(all: List<VideoItem>, loading: Boolean) {
        val state = _state.value
        val filteredStorage = all.filter {
            when (state.filter) {
                VideoStorageFilter.ALL -> true
                VideoStorageFilter.PHONE -> it.storage == StorageKind.PHONE
                VideoStorageFilter.USB -> it.storage == StorageKind.USB
            }
        }
        val q = state.query.trim().lowercase()
        val filtered = if (q.isBlank()) filteredStorage else filteredStorage.filter {
            it.name.lowercase().contains(q) || it.folder.lowercase().contains(q)
        }
        val folders = filtered.groupBy { "${it.storage}:${it.folder}" }
            .map { (key, items) ->
                VideoFolder(key, items.first().folder.substringAfterLast('/').ifBlank { items.first().folder }, items.first().storage, items)
            }
            .sortedWith(compareByDescending<VideoFolder> { it.items.maxOfOrNull { item -> item.modifiedMs } ?: 0L }.thenBy { it.name.lowercase() })
        val selected = state.selectedFolderKey?.takeIf { key -> folders.any { it.key == key } }
        _state.value = state.copy(
            loading = loading,
            permissionGranted = repo.hasVideoPermission(),
            usbSelected = repo.hasUsb(),
            folders = folders,
            selectedFolderKey = selected,
            message = null
        )
    }
}
