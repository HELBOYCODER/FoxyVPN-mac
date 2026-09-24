package com.vauth.foxyvpn.vpn.tun

import android.content.Context
import com.vauth.foxyvpn.platform.FoxyPaths
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.File

/**
 * Desktop replacement for the hev-socks5-tunnel YAML config: generates an equivalent
 * sing-box configuration (TUN in + SOCKS5 out + fakeip, mirroring the Android mapdns).
 */
object HevSocks5TunnelConfig {
    const val FILE_NAME = "singbox-tun.json"

    const val TUN_ADDRESS = "10.8.0.2"

    const val TUN_MTU = 9000

    const val MAPDNS_ADDRESS = "198.18.0.2"

    private const val FAKEIP_NETWORK = "100.64.0.0/10"

    private const val MAPDNS_NETWORK_BITS = 100L shl 24 or (64L shl 16)
    private const val MAPDNS_NETMASK_BITS = 255L shl 24 or (192L shl 16)

    fun isFakeDnsAddress(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4) return false
        var packed = 0L
        for (part in parts) {
            if (part.isEmpty() || part.length > 3) return false
            val octet = part.toIntOrNull() ?: return false
            if (octet !in 0..255) return false
            packed = (packed shl 8) or octet.toLong()
        }
        return (packed and MAPDNS_NETMASK_BITS) == MAPDNS_NETWORK_BITS
    }

    private fun dohHostName(endpoint: String): String =
        endpoint.substringAfter("://").substringBefore('/')

    fun write(
        context: Context,
        socksPort: Int,
        customDnsServer: String?,
        dohEndpointAddresses: List<String>,
        bypassIps: List<String>,
    ): String {
        val bypass = buildList {
            addAll(bypassIps)
            add("127.0.0.0/8")
        }.distinct()

        val dns = buildJsonObject {
            put(
                "servers",
                buildJsonArray {
                    if (customDnsServer == null) {
                        add(
                            buildJsonObject {
                                put("type", "fakeip")
                                put("tag", "dns-fakeip")
                                put("inet4_range", FAKEIP_NETWORK)
                            },
                        )
                    }
                    add(
                        if (customDnsServer != null) {
                            buildJsonObject {
                                put("type", "udp")
                                put("tag", "dns-upstream")
                                put("server", customDnsServer)
                                put("detour", "tun-socks")
                            }
                        } else {
                            buildJsonObject {
                                put("type", "https")
                                put("tag", "dns-upstream")
                                put("server", "https://1.1.1.1/dns-query")
                                put(
                                    "server_ip",
                                    JsonArray((dohEndpointAddresses.ifEmpty { listOf("1.1.1.1", "8.8.8.8") }).map { JsonPrimitive(it) }),
                                )
                                put("detour", "tun-socks")
                            }
                        },
                    )
                },
            )
            put(
                "rules",
                buildJsonArray {
                    if (customDnsServer == null) {
                        add(
                            buildJsonObject {
                                put("action", "route")
                                put("server", "dns-fakeip")
                                put("query_type", buildJsonArray { add(JsonPrimitive("A")); add(JsonPrimitive("AAAA")) })
                            },
                        )
                    }
                },
            )
            put("final", "dns-upstream")
            put("independent_cache", true)
        }

        val config = buildJsonObject {
            putJsonObject("log") {
                put("level", "info")
                put("timestamp", true)
                put("output", File(FoxyPaths.helperDir, "singbox.log").absolutePath)
            }
            put("dns", dns)
            put(
                "inbounds",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", "tun")
                            put("tag", "tun-in")
                            put("interface_name", "utun")
                            put("address", buildJsonArray { add(JsonPrimitive("$TUN_ADDRESS/24")) })
                            put("mtu", TUN_MTU)
                            put("auto_route", true)
                            put("strict_route", false)
                            put("stack", "system")
                        },
                    )
                },
            )
            putJsonObject("route") {
                put(
                    "rules",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("action", "route")
                                put("protocol", buildJsonArray { add(JsonPrimitive("dns")) })
                                put("outbound", "dns-out")
                            },
                        )
                        add(
                            buildJsonObject {
                                put("action", "route")
                                put(
                                    "ip_cidr",
                                    JsonArray(bypass.map { JsonPrimitive(if (it.contains('/')) it else "$it/32") }),
                                )
                                put("outbound", "direct-bypass")
                            },
                        )
                    },
                )
                put("final", "tun-socks")
                put("auto_detect_interface", true)
            }
            putJsonObject("experimental") {
                putJsonObject("cache_file") {
                    put("enabled", true)
                    put("path", File(FoxyPaths.helperDir, "singbox-cache.db").absolutePath)
                }
            }
            put(
                "outbounds",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", "socks")
                            put("tag", "tun-socks")
                            put("server", "127.0.0.1")
                            put("server_port", socksPort)
                            put("version", "5")
                        },
                    )
                    add(
                        buildJsonObject {
                            put("type", "direct")
                            put("tag", "direct-bypass")
                        },
                    )
                    add(
                        buildJsonObject {
                            put("type", "dns")
                            put("tag", "dns-out")
                        },
                    )
                },
            )
        }

        val file = File(FoxyPaths.helperDir, FILE_NAME)
        file.parentFile?.mkdirs()
        file.writeText(config.toString())
        return file.absolutePath
    }
}
