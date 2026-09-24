package com.vauth.foxyvpn.vpn.tun

import com.vauth.foxyvpn.data.AppLogger
import com.vauth.foxyvpn.platform.FoxyPaths
import java.io.File

private const val TAG = "TunBackend"

/**
 * Desktop stand-in for the Android native tun2socks binding. The heavy lifting moved to
 * sing-box, driven through [MacHelper]; this object keeps a process handle on the sing-box
 * child so status and statistics stay available without a native library.
 */
object HevSocks5Tunnel {

    private val pidFile get() = File(FoxyPaths.helperDir, "singbox.pid")

    fun start(configPath: String, tunFd: Int = -1, bypassIps: List<String>): Boolean =
        runCatching { MacHelper.startTun(configPath, bypassIps) }
            .onFailure { AppLogger.e(TAG, "failed to start the sing-box tun2socks tunnel", it) }
            .getOrDefault(false)

    fun stop(): Boolean =
        runCatching { MacHelper.stopTun() }
            .onFailure { AppLogger.w(TAG, "failed to stop tun2socks tunnel", it) }
            .getOrDefault(false)

    fun isRunning(): Boolean {
        val pid = pidFile.takeIf { it.exists() }?.readText()?.trim()?.toLongOrNull() ?: return false
        return ProcessHandle.of(pid).map { it.isAlive }.orElse(false)
    }
}
