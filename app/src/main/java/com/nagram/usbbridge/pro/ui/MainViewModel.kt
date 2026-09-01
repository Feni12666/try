package com.nagram.usbbridge.pro.ui

import android.app.Application
import android.content.Context
import android.os.Environment
import android.os.StatFs
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nagram.usbbridge.BridgeSnapshotReader
import com.nagram.usbbridge.RecentTransferUi
import com.nagram.usbbridge.pro.data.LegacyDataSafety
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku


data class HomeUiState(
    val running: Boolean = false,
    val shizukuRunning: Boolean = false,
    val shizukuGranted: Boolean = false,
    val usbSelected: Boolean = false,
    val lastStatus: String = "Starting…",
    val currentName: String? = null,
    val currentPercent: Int = 0,
    val currentSpeed: Long = 0,
    val currentEta: Long = -1,
    val readyCount: Int = 0,
    val todayFiles: Long = 0,
    val todayBytes: Long = 0,
    val attention: Long = 0,
    val phoneFree: Long = 0,
    val phoneTotal: Long = 0,
    val legacyJournalPresent: Boolean = false,
    val legacyPrefsPresent: Boolean = false,
    val recent: List<RecentTransferUi> = emptyList()
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("bridge", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                refresh()
                delay(if (_state.value.running) 1500L else 3500L)
            }
        }
    }

    fun refreshNow() {
        viewModelScope.launch { refresh() }
    }

    private suspend fun refresh() = withContext(Dispatchers.IO) {
        val context = getApplication<Application>()
        val stats = runCatching { BridgeSnapshotReader.today(context) }.getOrNull()
        val recent = runCatching { BridgeSnapshotReader.recent(context, 8) }.getOrDefault(emptyList())
        val legacy = LegacyDataSafety.inspect(context)
        val stat = runCatching { StatFs(Environment.getExternalStorageDirectory().absolutePath) }.getOrNull()
        val shizukuRunning = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val shizukuGranted = shizukuRunning && runCatching {
            Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)

        _state.value = HomeUiState(
            running = prefs.getBoolean("running", false),
            shizukuRunning = shizukuRunning,
            shizukuGranted = shizukuGranted,
            usbSelected = !prefs.getString("tree_uri", null).isNullOrBlank(),
            lastStatus = prefs.getString("last_status", null) ?: "Ready for setup",
            currentName = prefs.getString("current_name", null),
            currentPercent = prefs.getInt("current_percent", 0).coerceIn(0, 100),
            currentSpeed = prefs.getLong("current_speed", 0L),
            currentEta = prefs.getLong("current_eta", -1L),
            readyCount = prefs.getInt("ready_count", 0).coerceAtLeast(0),
            todayFiles = stats?.files ?: 0,
            todayBytes = stats?.bytes ?: 0,
            attention = stats?.attention ?: 0,
            phoneFree = stat?.availableBytes ?: 0,
            phoneTotal = stat?.totalBytes ?: 0,
            legacyJournalPresent = legacy.journalPresent,
            legacyPrefsPresent = legacy.preferencesPresent,
            recent = recent
        )
    }
}
