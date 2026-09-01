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
import com.nagram.usbbridge.pro.ui.theme.AppThemeMode
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
    val paused: Boolean = false,
    val shizukuRunning: Boolean = false,
    val shizukuGranted: Boolean = false,
    val usbSelected: Boolean = false,
    val lastStatus: String = "Ready",
    val currentName: String? = null,
    val currentPercent: Int = 0,
    val currentSpeed: Long = 0,
    val currentEta: Long = -1,
    val readyCount: Int = 0,
    val activeTransfers: Int = 0,
    val configuredParallelTransfers: Int = 2,
    val safeMode: Boolean = false,
    val cleanupDelayMinutes: Int = 3,
    val todayFiles: Long = 0,
    val todayBytes: Long = 0,
    val attention: Long = 0,
    val phoneFree: Long = 0,
    val phoneTotal: Long = 0,
    val legacyJournalPresent: Boolean = false,
    val legacyPrefsPresent: Boolean = false,
    val recent: List<RecentTransferUi> = emptyList(),
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("bridge", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        // Legacy Nagram automation is explicitly disabled in the manual file-manager build.
        prefs.edit().putBoolean("running", false).putBoolean("auto_start", false).putBoolean("auto_sync_enabled", false).apply()
        viewModelScope.launch {
            while (isActive) {
                refresh()
                delay(if (_state.value.running) 900L else 2500L)
            }
        }
    }

    fun refreshNow() { viewModelScope.launch { refresh() } }

    fun setCleanupDelayMinutes(minutes: Int) {
        if (minutes !in setOf(1, 3, 5)) return
        prefs.edit().putLong("cleanup_delay_ms", minutes * 60_000L).apply()
        refreshNow()
    }

    fun setThemeMode(mode: AppThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
        _state.value = _state.value.copy(themeMode = mode)
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

        val cleanupMs = prefs.getLong("cleanup_delay_ms", 3L * 60L * 1000L)
        val cleanupMinutes = when ((cleanupMs / 60_000L).toInt()) { 1 -> 1; 5 -> 5; else -> 3 }
        val theme = runCatching { AppThemeMode.valueOf(prefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM") }
            .getOrDefault(AppThemeMode.SYSTEM)
        val manualRunning = prefs.getBoolean("manual_op_running", false)
        val active = prefs.getInt("manual_op_active", 0).coerceAtLeast(0)

        _state.value = HomeUiState(
            running = manualRunning,
            paused = prefs.getBoolean("manual_op_paused", false),
            shizukuRunning = shizukuRunning,
            shizukuGranted = shizukuGranted,
            usbSelected = !prefs.getString("tree_uri", null).isNullOrBlank(),
            lastStatus = prefs.getString("manual_op_status", null) ?: "Manual file manager ready",
            currentName = prefs.getString("manual_op_name", null),
            currentPercent = prefs.getInt("manual_op_progress", 0).coerceIn(0, 100),
            currentSpeed = prefs.getLong("manual_op_speed", 0L),
            currentEta = prefs.getLong("manual_op_eta", -1L),
            readyCount = prefs.getInt("manual_op_ready", 0).coerceAtLeast(0),
            activeTransfers = active,
            configuredParallelTransfers = 2,
            safeMode = active <= 1 && manualRunning,
            cleanupDelayMinutes = cleanupMinutes,
            todayFiles = stats?.files ?: 0,
            todayBytes = stats?.bytes ?: 0,
            attention = stats?.attention ?: 0,
            phoneFree = stat?.availableBytes ?: 0,
            phoneTotal = stat?.totalBytes ?: 0,
            legacyJournalPresent = legacy.journalPresent,
            legacyPrefsPresent = legacy.preferencesPresent,
            recent = recent,
            themeMode = theme
        )
    }
}
