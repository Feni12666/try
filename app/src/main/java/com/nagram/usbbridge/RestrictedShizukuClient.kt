package com.nagram.usbbridge

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Process-wide client for the optional generic Android/data + Android/obb Shizuku service. */
object RestrictedShizukuClient {
    @Volatile private var remote: IRestrictedFileService? = null
    @Volatile private var binding = false
    @Volatile private var latch: CountDownLatch? = null

    private val args = Shizuku.UserServiceArgs(
        ComponentName("com.nagram.usbbridge", "com.nagram.usbbridge.RestrictedFileService")
    )
        .daemon(false)
        .tag("restricted_file_service")
        .version(1)
        .processNameSuffix("restricted_files")

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            remote = IRestrictedFileService.Stub.asInterface(service)
            binding = false
            latch?.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            remote = null
            binding = false
        }
    }

    fun isReady(): Boolean = runCatching {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun get(timeoutMs: Long = 5000L): IRestrictedFileService? {
        remote?.let { return it }
        if (!isReady()) return null
        val waitOn: CountDownLatch
        synchronized(this) {
            remote?.let { return it }
            if (!binding) {
                binding = true
                latch = CountDownLatch(1)
                runCatching { Shizuku.bindUserService(args, connection) }
                    .onFailure {
                        binding = false
                        latch?.countDown()
                    }
            }
            waitOn = latch ?: CountDownLatch(0)
        }
        runCatching { waitOn.await(timeoutMs, TimeUnit.MILLISECONDS) }
        return remote
    }
}
