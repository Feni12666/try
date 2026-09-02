package com.nagram.usbbridge.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.nagram.usbbridge.BuildConfig
import com.nagram.usbbridge.storage.StorageEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

enum class ShizukuAccessStatus { UNAVAILABLE, PERMISSION_REQUIRED, CONNECTING, CONNECTED, DENIED, ERROR }
data class ShizukuAccessState(val status: ShizukuAccessStatus = ShizukuAccessStatus.UNAVAILABLE, val message: String = "Shizuku is not running.") { val isConnected get() = status == ShizukuAccessStatus.CONNECTED }

class ShizukuFileGateway(context: Context) {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(ShizukuAccessState())
    val state: StateFlow<ShizukuAccessState> = _state.asStateFlow()
    private var service: IShizukuFileService? = null
    private var binding = false
    private val args = Shizuku.UserServiceArgs(ComponentName(appContext.packageName, ShizukuFileUserService::class.java.name))
        .daemon().processNameSuffix("usb-video-files").debuggable(BuildConfig.DEBUG).version(1).tag("usb-video-manager-protected-read")
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = IShizukuFileService.Stub.asInterface(binder); binding = false
            _state.value = ShizukuAccessState(ShizukuAccessStatus.CONNECTED, "Permission granted. Android/data and Android/obb are ready.")
        }
        override fun onServiceDisconnected(name: ComponentName) { service = null; binding = false; _state.value = ShizukuAccessState(ShizukuAccessStatus.UNAVAILABLE, "The Shizuku service disconnected.") }
    }
    private val binderReceived = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDead = Shizuku.OnBinderDeadListener { service = null; binding = false; _state.value = ShizukuAccessState(ShizukuAccessStatus.UNAVAILABLE, "Shizuku stopped. Start it again, then reconnect.") }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { code, result ->
        if (code == REQUEST_CODE) {
            if (result == PackageManager.PERMISSION_GRANTED) bindIfPossible() else _state.value = ShizukuAccessState(ShizukuAccessStatus.DENIED, "Shizuku permission was not granted.")
        }
    }
    init { runCatching { Shizuku.addBinderReceivedListener(binderReceived); Shizuku.addBinderDeadListener(binderDead); Shizuku.addRequestPermissionResultListener(permissionResult) }; refresh() }
    fun refresh() {
        if (!alive()) { service = null; binding = false; _state.value = ShizukuAccessState(ShizukuAccessStatus.UNAVAILABLE, "Install and start Shizuku (or Sui), then return here."); return }
        if (runCatching { Shizuku.checkSelfPermission() }.getOrDefault(PackageManager.PERMISSION_DENIED) == PackageManager.PERMISSION_GRANTED) bindIfPossible()
        else _state.value = ShizukuAccessState(ShizukuAccessStatus.PERMISSION_REQUIRED, "Shizuku is running. Grant protected-folder permission to this app.")
    }
    fun requestAccess() {
        if (!alive()) { refresh(); return }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) bindIfPossible() else { _state.value = ShizukuAccessState(ShizukuAccessStatus.CONNECTING, "Waiting for the Shizuku permission decision…"); Shizuku.requestPermission(REQUEST_CODE) }
    }
    suspend fun listEntries(path: String): List<StorageEntry> = withContext(Dispatchers.IO) {
        checkNotNull(service) { "Connect Shizuku before opening protected folders." }.listEntries(path).map { StorageEntry(it.absolutePath, it.displayName, it.directory, it.sizeBytes, it.lastModified) }
    }
    suspend fun openForRead(path: String) = withContext(Dispatchers.IO) { checkNotNull(service) { "Connect Shizuku before reading protected files." }.openForRead(path) }
    fun close() {
        runCatching { Shizuku.removeBinderReceivedListener(binderReceived) }; runCatching { Shizuku.removeBinderDeadListener(binderDead) }; runCatching { Shizuku.removeRequestPermissionResultListener(permissionResult) }
        if (binding || service != null) runCatching { Shizuku.unbindUserService(args, connection, true) }
        service = null; binding = false
    }
    private fun bindIfPossible() {
        if (service != null) { _state.value = ShizukuAccessState(ShizukuAccessStatus.CONNECTED, "Permission granted. Android/data and Android/obb are ready."); return }
        if (binding) return
        binding = true; _state.value = ShizukuAccessState(ShizukuAccessStatus.CONNECTING, "Connecting to the protected-folder service…")
        runCatching { Shizuku.bindUserService(args, connection) }.onFailure { binding = false; _state.value = ShizukuAccessState(ShizukuAccessStatus.ERROR, it.message ?: "Could not connect to Shizuku.") }
    }
    private fun alive() = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
    private companion object { const val REQUEST_CODE = 42031 }
}
