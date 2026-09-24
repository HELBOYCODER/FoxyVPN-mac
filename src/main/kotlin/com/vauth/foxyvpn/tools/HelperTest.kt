package com.vauth.foxyvpn.tools

import com.vauth.foxyvpn.vpn.tun.HevSocks5Tunnel
import com.vauth.foxyvpn.vpn.tun.HevSocks5TunnelConfig
import com.vauth.foxyvpn.vpn.tun.MacHelper

/**
 * Manual harness: `gradle runHelperTest` drives the privileged helper exactly the way
 * the app does, so the admin prompt, watcher, routes and sing-box can be verified
 * without the UI. Accepts an optional seconds argument to keep the tunnel up.
 */
fun main(args: Array<String>) {
    val holdSeconds = args.getOrNull(0)?.toLongOrNull() ?: 5L
    val config = HevSocks5TunnelConfig.write(
        android.content.Context(),
        1080,
        customDnsServer = null,
        dohEndpointAddresses = listOf("1.1.1.1"),
        bypassIps = listOf("1.1.1.1", "8.8.8.8"),
    )
    println("config written: $config")
    val started = HevSocks5Tunnel.start(config, bypassIps = listOf("1.1.1.1", "8.8.8.8"))
    println("startTun=$started tunRunning=${HevSocks5Tunnel.isRunning()}")
    if (started) {
        Thread.sleep(2_500)
        println("--- route -n get default ---")
        println(run("route", "-n", "get", "default"))
        println("--- ifconfig utun (last) ---")
        println(run("sh", "-c", "ifconfig | grep -A4 '^utun' | tail -20"))
        Thread.sleep(holdSeconds * 1_000)
    }
    println("stopTun=${MacHelper.stopTun()}")
    println("--- route after stop ---")
    println(run("route", "-n", "get", "default"))
}

private fun run(vararg cmd: String): String = runCatching {
    ProcessBuilder(*cmd).redirectErrorStream(true).start().inputStream.bufferedReader().readText()
}.getOrElse { "failed: ${it.message}" }
