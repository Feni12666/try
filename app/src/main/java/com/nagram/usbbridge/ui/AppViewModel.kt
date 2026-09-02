package com.nagram.usbbridge.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nagram.usbbridge.shizuku.ProtectedPathPolicy
import com.nagram.usbbridge.shizuku.ShizukuFileGateway
import com.nagram.usbbridge.storage.StorageAccessRepository
import com.nagram.usbbridge.storage.StorageListing
import com.nagram.usbbridge.storage.SyncFolderRole
import com.nagram.usbbridge.storage.SyncPreferences
import com.nagram.usbbridge.transfer.SmartSyncEngine
import com.nagram.usbbridge.transfer.SyncProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job

private data class BrowserPoint(val id: String, val label: String)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val storage = StorageAccessRepository(application.applicationContext)
    private val shizuku = ShizukuFileGateway(application.applicationContext)
    private val syncEngine = SmartSyncEngine(application.applicationContext)
    private val stacks = BrowserOrigin.entries.associateWith { mutableListOf<BrowserPoint>() }.toMutableMap()
    private var syncJob: Job? = null
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { shizuku.state.collectLatest { access -> _uiState.update { it.copy(shizukuAccess = access) } } }
        refreshStorageAccess(loadActiveLocation = true)
    }

    fun selectStorage(location: StorageLocation) {
        val origin = if (location == StorageLocation.PHONE) BrowserOrigin.PHONE else BrowserOrigin.USB
        _uiState.update { it.copy(storageLocation = location, browserOrigin = origin) }
        loadCurrentDirectory()
    }
    fun onPhoneStorageSettingsReturned() = refreshStorageAccess(loadActiveLocation = true)
    fun onUsbFolderSelected(uri: Uri) = viewModelScope.launch {
        runCatching { withContext(Dispatchers.IO) { storage.persistUsbFolder(uri) } }.onSuccess {
            refreshStorageAccess(false); _uiState.update { state -> state.copy(storageLocation = StorageLocation.USB, browserOrigin = BrowserOrigin.USB, browserNotice = null) }; loadCurrentDirectory()
        }.onFailure { error -> _uiState.update { it.copy(browserNotice = error.message ?: "USB permission could not be saved.") } }
    }
    fun onSyncFolderSelected(uri: Uri, role: SyncFolderRole) = viewModelScope.launch {
        runCatching { withContext(Dispatchers.IO) { storage.persistSyncFolder(uri, role) } }.onSuccess { folder ->
            _uiState.update { if (role == SyncFolderRole.SOURCE) it.copy(syncSourceFolder = folder) else it.copy(syncTargetFolder = folder) }
        }.onFailure { error -> _uiState.update { it.copy(browserNotice = error.message ?: "Folder permission could not be saved.") } }
    }
    fun setCurrentProtectedFolderAsSyncSource() {
        val origin = uiState.value.browserOrigin
        val path = stacks.getValue(origin).lastOrNull()?.id ?: origin.protectedRoot ?: return
        if (!ProtectedPathPolicy.isAllowed(java.io.File(path))) return
        val folder = storage.persistProtectedSyncSource(path, ProtectedPathPolicy.titleFor(path))
        _uiState.update { it.copy(syncSourceFolder = folder, syncLastMessage = "Protected source folder selected.") }
    }
    fun runSmartSync() {
        if (syncJob?.isActive == true) return
        val source = uiState.value.syncSourceFolder ?: run { _uiState.update { it.copy(syncLastMessage = "Choose a source folder first.") }; return }
        val target = uiState.value.syncTargetFolder ?: run { _uiState.update { it.copy(syncLastMessage = "Choose a USB target folder first.") }; return }
        syncJob = viewModelScope.launch {
            _uiState.update { it.copy(syncLastMessage = null, syncProgress = SyncProgress(isRunning = true, message = "Preparing sync…")) }
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    if (source.uri.startsWith(SHIZUKU_SOURCE_PREFIX)) {
                        check(uiState.value.shizukuConnected) { "Connect Shizuku before syncing this protected folder." }
                        val path = Uri.decode(source.uri.removePrefix(SHIZUKU_SOURCE_PREFIX))
                        syncEngine.syncShizuku(path, Uri.parse(target.uri), shizuku, ::publishProgress)
                    } else {
                        syncEngine.syncSaf(Uri.parse(source.uri), Uri.parse(target.uri), ::publishProgress)
                    }
                }
            }
            result.onSuccess { summary ->
                _uiState.update { it.copy(syncProgress = null, syncLastMessage = "Sync complete: ${summary.copiedVideos} copied, ${summary.skippedVideos} already on USB, ${summary.failedVideos} failed.") }
            }.onFailure { error ->
                _uiState.update { it.copy(syncProgress = null, syncLastMessage = error.message ?: "Sync stopped safely. Source files were not deleted.") }
            }
        }
    }
    fun cancelSmartSync() { syncJob?.cancel(); _uiState.update { it.copy(syncProgress = null, syncLastMessage = "Sync cancelled safely. Source files were not deleted.") } }
    fun refreshStorageAccess(loadActiveLocation: Boolean = false) {
        val sync = storage.syncPreferences()
        _uiState.update {
            it.copy(phoneStorageGranted = storage.hasPhoneStorageAccess(), usbFolder = storage.usbFolder(), usbGrantActive = storage.isUsbGrantUsable(),
                syncSourceFolder = storage.syncFolder(SyncFolderRole.SOURCE), syncTargetFolder = storage.syncFolder(SyncFolderRole.TARGET),
                autoSyncEnabled = sync.enabled, onlyNewFiles = sync.onlyNewFiles, verifyAfterCopy = sync.verifyAfterCopy, runOnUsbConnect = sync.runOnUsbConnect)
        }
        if (loadActiveLocation) loadCurrentDirectory()
    }
    fun refreshShizuku() = shizuku.refresh()
    fun requestShizukuAccess() = shizuku.requestAccess()
    fun openProtectedFolder(origin: BrowserOrigin) {
        require(origin == BrowserOrigin.SHIZUKU_DATA || origin == BrowserOrigin.SHIZUKU_OBB)
        if (!uiState.value.shizukuConnected) { _uiState.update { it.copy(browserNotice = "Connect Shizuku before opening protected folders.") }; return }
        _uiState.update { it.copy(storageLocation = StorageLocation.PHONE, browserOrigin = origin, browserNotice = null) }; loadCurrentDirectory()
    }
    fun openEntry(id: String, label: String, isDirectory: Boolean) { if (!isDirectory) return; val origin = uiState.value.browserOrigin; stacks.getValue(origin) += BrowserPoint(id, label); loadCurrentDirectory() }
    fun navigateUp() { val stack = stacks.getValue(uiState.value.browserOrigin); if (stack.isNotEmpty()) { stack.removeAt(stack.lastIndex); loadCurrentDirectory() } }
    fun setAutoSync(enabled: Boolean) = updateSync { it.copy(enabled = enabled) }
    fun setOnlyNewFiles(enabled: Boolean) = updateSync { it.copy(onlyNewFiles = enabled) }
    fun setVerifyAfterCopy(enabled: Boolean) = updateSync { it.copy(verifyAfterCopy = enabled) }
    fun setRunOnUsbConnect(enabled: Boolean) = updateSync { it.copy(runOnUsbConnect = enabled) }
    fun chooseKeepRule(rule: DuplicateKeepRule) { _uiState.update { it.copy(keepRule = rule) } }
    fun requestDelete() { _uiState.update { it.copy(deleteDialogVisible = true) } }
    fun dismissDelete() { _uiState.update { it.copy(deleteDialogVisible = false) } }
    override fun onCleared() { syncJob?.cancel(); shizuku.close(); super.onCleared() }

    private fun loadCurrentDirectory() {
        val origin = uiState.value.browserOrigin
        val path = stacks.getValue(origin).lastOrNull()?.id
        _uiState.update { it.copy(isListingLoading = true, browserNotice = null, canNavigateUp = stacks.getValue(origin).isNotEmpty()) }
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    when (origin) {
                        BrowserOrigin.PHONE -> { check(storage.hasPhoneStorageAccess()) { "Grant Phone storage access to browse shared files." }; storage.listPhoneDirectory(path) }
                        BrowserOrigin.USB -> { check(storage.isUsbGrantUsable()) { "Choose a USB or pendrive folder to begin browsing." }; storage.listUsbDirectory(path) }
                        BrowserOrigin.SHIZUKU_DATA, BrowserOrigin.SHIZUKU_OBB -> {
                            val protectedPath = path ?: requireNotNull(origin.protectedRoot)
                            StorageListing(StorageLocation.PHONE, ProtectedPathPolicy.titleFor(protectedPath), shizuku.listEntries(protectedPath))
                        }
                    }
                }
            }
            if (uiState.value.browserOrigin != origin) return@launch
            result.onSuccess { listing -> _uiState.update { it.copy(storageLocation = listing.location, currentPathLabel = listing.pathLabel, browserEntries = listing.entries, isListingLoading = false, browserNotice = null) } }
                .onFailure { error -> _uiState.update { it.copy(browserEntries = emptyList(), isListingLoading = false, browserNotice = error.message ?: "Could not open this folder.") } }
        }
    }
    private fun updateSync(change: (SyncPreferences) -> SyncPreferences) {
        val now = uiState.value
        val updated = change(SyncPreferences(now.autoSyncEnabled, now.onlyNewFiles, now.verifyAfterCopy, now.runOnUsbConnect))
        storage.saveSyncPreferences(updated)
        _uiState.update { it.copy(autoSyncEnabled = updated.enabled, onlyNewFiles = updated.onlyNewFiles, verifyAfterCopy = updated.verifyAfterCopy, runOnUsbConnect = updated.runOnUsbConnect) }
    }
    private fun publishProgress(progress: SyncProgress) { _uiState.update { it.copy(syncProgress = progress) } }
    private companion object { const val SHIZUKU_SOURCE_PREFIX = "shizuku://protected/" }
}
