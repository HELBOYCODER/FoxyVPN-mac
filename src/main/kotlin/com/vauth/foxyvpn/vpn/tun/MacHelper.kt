package com.vauth.foxyvpn.vpn.tun

import com.vauth.foxyvpn.data.AppLogger
import com.vauth.foxyvpn.platform.FoxyPaths
import java.io.File
import java.util.UUID

private const val TAG = "MacHelper"
private const val DAEMON_LABEL = "com.vauth.foxyvpn.helper"

/**
 * A root-owned helper (installed as a LaunchDaemon after one admin prompt) that applies
 * system-wide networking changes the app itself is not entitled to do:
 * - start/stop the sing-box TUN tunnel,
 * - host routes that keep FoxyVPN's own control-plane traffic out of the tunnel,
 * - the macOS SOCKS system proxy.
 *
 * A plain `do shell script ... &` child is killed when the authorization session ends,
 * so the watcher must live under launchd to survive.
 */
object MacHelper {

    private const val DAEMON_PLIST_PATH = "/Library/LaunchDaemons/$DAEMON_LABEL.plist"

    private val dir: File get() = FoxyPaths.helperDir
    private val cmdFile get() = File(dir, "cmd")
    private val doneFile get() = File(dir, "cmd.done")
    private val stopFile get() = File(dir, "stop")
    private val startedFile get() = File(dir, "watcher.started")
    private val scriptFile get() = File(dir, "foxy-helper.sh")
    private val plistStagingFile get() = File(dir, "foxy-helper.plist")
    private val configPathFile get() = File(dir, "current_config")
    private val bypassFile get() = File(dir, "bypass_ips")
    private val proxyPortFile get() = File(dir, "proxy_port")

    @Volatile
    private var promptedForAdmin = false

    @Volatile
    private var shuttingDown = false

    private fun commandNonce(): String = UUID.randomUUID().toString()

    private fun watcherAlive(): Boolean {
        // A user process cannot read the command line of root processes on macOS, so ask
        // launchd directly whether the helper job is running.
        val printOut = runCatching {
            ProcessBuilder("launchctl", "print", "system/$DAEMON_LABEL")
                .redirectErrorStream(true).start().inputStream.bufferedReader().readText()
        }.getOrDefault("")
        if ("state = running" in printOut) return true
        return ProcessHandle.allProcesses()
            .anyMatch { h -> h.info().commandLine().orElse("").contains("foxy-helper.sh") }
    }

    private fun staleWatcherRunning(): Boolean = ProcessHandle.allProcesses()
        .anyMatch { h ->
            val line = h.info().commandLine().orElse("")
            line.contains("/helper.sh") && !line.contains("foxy-helper.sh")
        }

    private fun writeScript() {
        val singBox = FoxyPaths.bundledResource("sing-box")?.absolutePath ?: ""
        scriptFile.writeText(
            """
            #!/bin/sh
            # FoxyVPN privileged helper - generated, do not edit.
            PATH="/usr/bin:/bin:/usr/sbin:/sbin"; export PATH
            DIR="${dir.absolutePath}"
            SINGBOX="$singBox"

            active_service() {
              IF=`route -n get default 2>/dev/null | awk '/interface:/{print ${'$'}2}'`
              [ -z "${'$'}IF" ] && return
              networksetup -listnetworkserviceorder | awk -v ifc="${'$'}IF" '
                /^\([0-9]+\)/ { name=${'$'}0; sub(/^[^)]*\) */, "", name) }
                index(${'$'}0, "Device: " ifc) > 0 { print name; exit }
              '
            }

            apply_bypass() {
              GW=`route -n get default 2>/dev/null | awk '/gateway:/{print ${'$'}2}'`
              [ -z "${'$'}GW" ] && return
              while read -r ip; do
                [ -z "${'$'}ip" ] && continue
                route -n add -host "${'$'}ip" "${'$'}GW" >/dev/null 2>&1
              done < "${'$'}DIR/bypass_ips"
            }

            remove_bypass() {
              [ -f "${'$'}DIR/bypass_ips" ] || return
              while read -r ip; do
                [ -z "${'$'}ip" ] && continue
                route -n delete -host "${'$'}ip" >/dev/null 2>&1
              done < "${'$'}DIR/bypass_ips"
            }

            handle() {
              case "${'$'}1" in
                start_tun)
                  CONFIG=`cat "${'$'}DIR/current_config" 2>/dev/null`
                  [ -f "${'$'}DIR/singbox.pid" ] && kill `cat "${'$'}DIR/singbox.pid"` 2>/dev/null
                  sleep 1
                  apply_bypass
                  if [ -n "${'$'}CONFIG" ] && [ -x "${'$'}SINGBOX" ]; then
                    "${'$'}SINGBOX" run -c "${'$'}CONFIG" >> "${'$'}DIR/singbox.log" 2>&1 &
                    echo ${'$'}! > "${'$'}DIR/singbox.pid"
                    sleep 2
                    if ! route -n get default 2>/dev/null | grep -q utun; then
                      UTUN=`grep -o 'utun[0-9]*' "${'$'}DIR/singbox.log" 2>/dev/null | tail -1`
                      [ -n "${'$'}UTUN" ] && route -n add default -interface "${'$'}UTUN" >/dev/null 2>&1
                    fi
                  fi
                  ;;
                stop_tun)
                  [ -f "${'$'}DIR/singbox.pid" ] && kill `cat "${'$'}DIR/singbox.pid"` 2>/dev/null
                  rm -f "${'$'}DIR/singbox.pid"
                  if ! route -n get default 2>/dev/null | grep -q utun; then
                    for u in utun0 utun1 utun2 utun3 utun4 utun5 utun6; do
                      route -n delete default -interface ${'$'}u >/dev/null 2>&1
                    done
                  fi
                  remove_bypass
                  ;;
                start_proxy)
                  SVC=`active_service`
                  PORT=`cat "${'$'}DIR/proxy_port" 2>/dev/null`
                  [ -n "${'$'}SVC" ] && [ -n "${'$'}PORT" ] || return
                  networksetup -setsocksfirewallproxy "${'$'}SVC" 127.0.0.1 "${'$'}PORT" >/dev/null 2>&1
                  networksetup -setsocksfirewallproxystate "${'$'}SVC" on >/dev/null 2>&1
                  ;;
                stop_proxy)
                  SVC=`active_service`
                  [ -n "${'$'}SVC" ] && networksetup -setsocksfirewallproxystate "${'$'}SVC" off >/dev/null 2>&1
                  ;;
              esac
            }

            uninstall() {
              handle stop_tun
              handle stop_proxy
              rm -f "${'$'}DIR/cmd" "${'$'}DIR/cmd.done" "${'$'}DIR/stop" "${'$'}DIR/watcher.started"
              launchctl bootout system/$DAEMON_LABEL 2>/dev/null
              rm -f "$DAEMON_PLIST_PATH"
              exit 0
            }

            [ -f "${'$'}DIR/stop" ] && uninstall
            touch "${'$'}DIR/watcher.started"
            while :; do
              [ -f "${'$'}DIR/stop" ] && uninstall
              if [ -f "${'$'}DIR/cmd" ] && [ ! -f "${'$'}DIR/cmd.done" ]; then
                nonce=`head -1 "${'$'}DIR/cmd"`
                action=`sed -n 2p "${'$'}DIR/cmd"`
                handle "${'$'}action"
                echo "${'$'}nonce" > "${'$'}DIR/cmd.done"
              fi
              sleep 0.3
            done
            """.trimIndent() + "\n",
        )
        scriptFile.setReadable(true, false)
    }

    private fun writePlist() {
        plistStagingFile.writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
              <key>Label</key><string>$DAEMON_LABEL</string>
              <key>ProgramArguments</key>
              <array><string>/bin/sh</string><string>${scriptFile.absolutePath}</string></array>
              <key>RunAtLoad</key><true/>
              <key>KeepAlive</key><true/>
              <key>StandardOutPath</key><string>${File(dir, "launchd.out").absolutePath}</string>
              <key>StandardErrorPath</key><string>${File(dir, "launchd.err").absolutePath}</string>
            </dict>
            </plist>
            """.trimIndent() + "\n",
        )
    }

    private fun stopStaleWatchers() {
        if (!staleWatcherRunning()) return
        runCatching { stopFile.createNewFile() }
        val deadline = System.currentTimeMillis() + 8_000
        while (System.currentTimeMillis() < deadline && staleWatcherRunning()) {
            Thread.sleep(300)
        }
    }

    private fun ensureWatcher(): Boolean {
        if (watcherAlive()) return true
        promptedForAdmin = true
        stopStaleWatchers()
        if (watcherAlive()) return true

        writeScript()
        writePlist()
        runCatching { stopFile.delete() }
        runCatching { startedFile.delete() }

        val installCommand =
            "cp '${plistStagingFile.absolutePath}' '$DAEMON_PLIST_PATH' && " +
                "chown root:wheel '$DAEMON_PLIST_PATH' && chmod 644 '$DAEMON_PLIST_PATH' && " +
                "launchctl bootout system/$DAEMON_LABEL 2>/dev/null; " +
                "launchctl bootstrap system '$DAEMON_PLIST_PATH'"
        val promptScript =
            "do shell script \"$installCommand\" " +
                "with prompt \"FoxyVPN needs administrator access to create the VPN tunnel and proxy routes.\" " +
                "with administrator privileges"
        val result = runCatching {
            ProcessBuilder("osascript", "-e", promptScript)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start()
                .waitFor()
        }
        if (result.getOrDefault(1) != 0) {
            AppLogger.w(TAG, "the administrator prompt was declined or failed; system-wide modes are unavailable")
            return false
        }
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            if (startedFile.exists() && watcherAlive()) {
                AppLogger.i(TAG, "the privileged helper (LaunchDaemon) is up and running")
                return true
            }
            Thread.sleep(250)
        }
        AppLogger.w(TAG, "the privileged helper did not come up; check ${File(dir, "launchd.err").absolutePath}")
        return watcherAlive()
    }

    private fun sendCommand(action: String, timeoutMs: Long = 45_000): Boolean {
        if (shuttingDown) return false
        dir.mkdirs()
        if (!ensureWatcher()) return false
        runCatching { doneFile.delete() }
        val nonce = commandNonce()
        cmdFile.writeText("$nonce\n$action\n")
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (doneFile.exists() && doneFile.readText().trim() == nonce) {
                runCatching { cmdFile.delete(); doneFile.delete() }
                return true
            }
            Thread.sleep(150)
        }
        AppLogger.w(TAG, "the privileged helper did not answer the '$action' command in time")
        return false
    }

    val isAdminAvailable: Boolean get() = watcherAlive() || !promptedForAdmin

    fun startTun(configPath: String, bypassIps: List<String>): Boolean {
        configPathFile.writeText(configPath)
        bypassFile.writeText(bypassIps.joinToString("\n") + "\n")
        return sendCommand("start_tun")
    }

    fun stopTun(): Boolean = sendCommand("stop_tun")

    fun startSystemProxy(port: Int): Boolean {
        proxyPortFile.writeText(port.toString())
        return sendCommand("start_proxy")
    }

    fun stopSystemProxy(): Boolean = sendCommand("stop_proxy")

    /** Stops the tunnel, removes the daemon and cleans up. Safe to call when nothing runs. */
    fun shutdown() {
        shuttingDown = true
        runCatching { stopFile.createNewFile() }
        val deadline = System.currentTimeMillis() + 6_000
        while (System.currentTimeMillis() < deadline && watcherAlive()) {
            Thread.sleep(250)
        }
    }
}
