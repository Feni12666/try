package com.nagram.usbbridge.pro.files

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nagram.usbbridge.ManualTransferService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch


data class FileManagerUiState(
    val storage: StorageKind = StorageKind.PHONE,
    val stack: List<BrowserLocation> = emptyList(),
    val entries: List<BrowserEntry> = emptyList(),
    val selected: Set<String> = emptySet(),
    val loading: Boolean = false,
    val message: String? = null,
    val showHidden: Boolean = false,
    val phoneAccessGranted: Boolean = false,
    val usbAvailable: Boolean = false,
    val shizukuReady: Boolean = false,
    val destinationMode: ManualOperation? = null,
    val destinationStorage: StorageKind? = null,
    val destinationStack: List<BrowserLocation> = emptyList(),
    val destinationEntries: List<BrowserEntry> = emptyList(),
    val conflictPolicy: ConflictPolicy = ConflictPolicy.KEEP_BOTH
) {
    val current: BrowserLocation? get() = stack.lastOrNull()
    val destinationCurrent: BrowserLocation? get() = destinationStack.lastOrNull()
}

class FileManagerViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = FileManagerRepository(app)
    private val _state = MutableStateFlow(FileManagerUiState())
    val state: StateFlow<FileManagerUiState> = _state.asStateFlow()

    init { selectStorage(StorageKind.PHONE) }

    fun refreshPermissionsAndStorage() {
        val current = _state.value
        _state.value = current.copy(
            phoneAccessGranted = Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager(),
            usbAvailable = repo.usbRoot() != null,
            shizukuReady = repo.shizukuReady()
        )
        refresh()
    }

    fun selectStorage(kind: StorageKind) {
        val root = when (kind) {
            StorageKind.PHONE -> repo.phoneRoot()
            StorageKind.USB -> repo.usbRoot()
            StorageKind.SHIZUKU -> repo.shizukuDataRoot()
        }
        _state.value = _state.value.copy(
            storage = kind,
            stack = if (root != null) listOf(root) else emptyList(),
            selected = emptySet(),
            message = when {
                root == null && kind == StorageKind.USB -> "Choose a USB / SSD folder first"
                root == null && kind == StorageKind.SHIZUKU -> "Connect and grant Shizuku first"
                else -> null
            },
            phoneAccessGranted = Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager(),
            usbAvailable = repo.usbRoot() != null,
            shizukuReady = repo.shizukuReady()
        )
        refresh()
    }

    fun openShizukuData() {
        val root = repo.shizukuDataRoot()
        if (root == null) {
            _state.value = _state.value.copy(message = "Connect and grant Shizuku first", shizukuReady = false)
            return
        }
        _state.value = _state.value.copy(storage = StorageKind.SHIZUKU, stack = listOf(root), selected = emptySet(), shizukuReady = true, message = null)
        refresh()
    }

    fun openShizukuObb() {
        val root = repo.shizukuObbRoot()
        if (root == null) {
            _state.value = _state.value.copy(message = "Connect and grant Shizuku first", shizukuReady = false)
            return
        }
        _state.value = _state.value.copy(storage = StorageKind.SHIZUKU, stack = listOf(root), selected = emptySet(), shizukuReady = true, message = null)
        refresh()
    }

    fun refresh() {
        val location = _state.value.current ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            runCatching { repo.list(location, _state.value.showHidden) }
                .onSuccess { _state.value = _state.value.copy(entries = it, loading = false, message = null) }
                .onFailure { _state.value = _state.value.copy(entries = emptyList(), loading = false, message = it.message ?: "Could not open folder") }
        }
    }

    fun open(entry: BrowserEntry) {
        val location = repo.childLocation(entry) ?: return
        _state.value = _state.value.copy(stack = _state.value.stack + location, selected = emptySet())
        refresh()
    }

    fun up() {
        if (_state.value.stack.size <= 1) return
        _state.value = _state.value.copy(stack = _state.value.stack.dropLast(1), selected = emptySet())
        refresh()
    }

    fun jumpTo(index: Int) {
        val stack = _state.value.stack
        if (index !in stack.indices) return
        _state.value = _state.value.copy(stack = stack.take(index + 1), selected = emptySet())
        refresh()
    }

    fun toggleSelection(entry: BrowserEntry) {
        val set = _state.value.selected.toMutableSet()
        if (!set.add(entry.id)) set.remove(entry.id)
        _state.value = _state.value.copy(selected = set)
    }

    fun clearSelection() { _state.value = _state.value.copy(selected = emptySet()) }

    fun setShowHidden(enabled: Boolean) {
        _state.value = _state.value.copy(showHidden = enabled)
        refresh()
    }

    fun createFolder(name: String) {
        val location = _state.value.current ?: return
        viewModelScope.launch {
            repo.createFolder(location, name)
                .onSuccess { _state.value = _state.value.copy(message = "Folder created") ; refresh() }
                .onFailure { _state.value = _state.value.copy(message = it.message ?: "Folder creation failed") }
        }
    }

    fun renameSelected(newName: String) {
        val entry = _state.value.entries.firstOrNull { _state.value.selected.contains(it.id) } ?: return
        if (_state.value.selected.size != 1) return
        viewModelScope.launch {
            repo.rename(entry, newName)
                .onSuccess { _state.value = _state.value.copy(selected = emptySet(), message = "Renamed") ; refresh() }
                .onFailure { _state.value = _state.value.copy(message = it.message ?: "Rename failed") }
        }
    }

    fun deleteSelected() {
        val entries = _state.value.entries.filter { _state.value.selected.contains(it.id) }
        if (entries.isEmpty()) return
        viewModelScope.launch {
            repo.delete(entries)
                .onSuccess { _state.value = _state.value.copy(selected = emptySet(), message = "Deleted") ; refresh() }
                .onFailure { _state.value = _state.value.copy(message = it.message ?: "Delete failed") }
        }
    }

    /** Starts a destination browser with NO automatic/default destination. */
    fun beginDestination(operation: ManualOperation) {
        if (_state.value.selected.isEmpty()) return
        _state.value = _state.value.copy(
            destinationMode = operation,
            destinationStorage = null,
            destinationStack = emptyList(),
            destinationEntries = emptyList(),
            conflictPolicy = ConflictPolicy.KEEP_BOTH,
            message = null
        )
    }

    fun chooseDestinationStorage(kind: StorageKind) {
        val root = when (kind) {
            StorageKind.PHONE -> repo.phoneRoot()
            StorageKind.USB -> repo.usbRoot()
            StorageKind.SHIZUKU -> repo.shizukuDataRoot()
        }
        if (root == null) {
            _state.value = _state.value.copy(message = if (kind == StorageKind.SHIZUKU) "Connect and grant Shizuku first" else "Choose a USB / SSD folder first")
            return
        }
        _state.value = _state.value.copy(destinationStorage = kind, destinationStack = listOf(root), message = null)
        refreshDestination()
    }


    fun chooseRestrictedDestinationData() {
        val root = repo.shizukuDataRoot() ?: run {
            _state.value = _state.value.copy(message = "Connect and grant Shizuku first")
            return
        }
        _state.value = _state.value.copy(destinationStorage = StorageKind.SHIZUKU, destinationStack = listOf(root), message = null)
        refreshDestination()
    }

    fun chooseRestrictedDestinationObb() {
        val root = repo.shizukuObbRoot() ?: run {
            _state.value = _state.value.copy(message = "Connect and grant Shizuku first")
            return
        }
        _state.value = _state.value.copy(destinationStorage = StorageKind.SHIZUKU, destinationStack = listOf(root), message = null)
        refreshDestination()
    }

    fun openDestination(entry: BrowserEntry) {
        if (!entry.isDirectory) return
        val location = repo.childLocation(entry) ?: return
        _state.value = _state.value.copy(destinationStack = _state.value.destinationStack + location)
        refreshDestination()
    }

    fun destinationUp() {
        if (_state.value.destinationStack.size <= 1) return
        _state.value = _state.value.copy(destinationStack = _state.value.destinationStack.dropLast(1))
        refreshDestination()
    }

    private fun refreshDestination() {
        val location = _state.value.destinationCurrent ?: return
        viewModelScope.launch {
            runCatching { repo.list(location, _state.value.showHidden).filter { it.isDirectory } }
                .onSuccess { _state.value = _state.value.copy(destinationEntries = it, message = null) }
                .onFailure { _state.value = _state.value.copy(destinationEntries = emptyList(), message = it.message) }
        }
    }

    fun setConflictPolicy(policy: ConflictPolicy) { _state.value = _state.value.copy(conflictPolicy = policy) }

    fun cancelDestination() {
        _state.value = _state.value.copy(
            destinationMode = null,
            destinationStorage = null,
            destinationStack = emptyList(),
            destinationEntries = emptyList()
        )
    }

    fun confirmDestination() {
        val state = _state.value
        val op = state.destinationMode ?: return
        val dest = state.destinationCurrent ?: return
        val sources = state.entries.filter { state.selected.contains(it.id) }
        if (sources.isEmpty()) return

        val intent = Intent(getApplication(), ManualTransferService::class.java).apply {
            action = ManualTransferService.ACTION_START
            putExtra(ManualTransferService.EXTRA_SOURCE_KIND, state.storage.name)
            putStringArrayListExtra(ManualTransferService.EXTRA_SOURCE_IDS, ArrayList(sources.map { it.id }))
            putStringArrayListExtra(ManualTransferService.EXTRA_SOURCE_NAMES, ArrayList(sources.map { it.name }))
            putExtra(ManualTransferService.EXTRA_SOURCE_DIRS, BooleanArray(sources.size) { sources[it].isDirectory })
            putExtra(ManualTransferService.EXTRA_DEST_KIND, dest.storage.name)
            putExtra(ManualTransferService.EXTRA_DEST_ID, dest.id)
            putExtra(ManualTransferService.EXTRA_MOVE, op == ManualOperation.MOVE)
            putExtra(ManualTransferService.EXTRA_CONFLICT, state.conflictPolicy.name)
        }
        ContextCompat.startForegroundService(getApplication(), intent)
        _state.value = state.copy(
            selected = emptySet(),
            destinationMode = null,
            destinationStorage = null,
            destinationStack = emptyList(),
            destinationEntries = emptyList(),
            message = if (op == ManualOperation.MOVE) "Safe move started" else "Copy started"
        )
    }

    fun pauseTransfer() = startServiceAction(ManualTransferService.ACTION_PAUSE)
    fun resumeTransfer() = startServiceAction(ManualTransferService.ACTION_RESUME)
    fun cancelTransfer() = startServiceAction(ManualTransferService.ACTION_CANCEL)

    private fun startServiceAction(action: String) {
        val intent = ManualTransferService.action(getApplication(), action)
        getApplication<Application>().startService(intent)
    }
}
