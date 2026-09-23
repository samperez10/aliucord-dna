package com.aliucord.plugins.dns

import org.json.JSONObject

enum class DnsMode(val displayName: String) {
    DOH("DNS-over-HTTPS (DoH)"),
    UDP("Direct UDP (Port 53)"),
    STATIC_ONLY("Static Host Mappings Only")
}

enum class DnsPreset(
    val displayName: String,
    val dohUrl: String,
    val primaryIp: String,
    val secondaryIp: String
) {
    CLOUDFLARE(
        "Cloudflare (1.1.1.1)",
        "https://cloudflare-dns.com/dns-query",
        "1.1.1.1",
        "1.0.0.1"
    ),
    GOOGLE(
        "Google (8.8.8.8)",
        "https://dns.google/dns-query",
        "8.8.8.8",
        "8.8.4.4"
    ),
    ADGUARD(
        "AdGuard DNS (Blocks Ads/Trackers)",
        "https://dns.adguard-dns.com/dns-query",
        "94.140.14.14",
        "94.140.15.15"
    ),
    QUAD9(
        "Quad9 (Blocks Malware)",
        "https://dns.quad9.net/dns-query",
        "9.9.9.9",
        "149.112.112.112"
    ),
    CUSTOM(
        "Custom Configuration",
        "",
        "",
        ""
    )
}

data class DnsConfig(
    var enabled: Boolean = true,
    var mode: DnsMode = DnsMode.DOH,
    var preset: DnsPreset = DnsPreset.CLOUDFLARE,
    var dohUrl: String = DnsPreset.CLOUDFLARE.dohUrl,
    var primaryDnsIp: String = DnsPreset.CLOUDFLARE.primaryIp,
    var secondaryDnsIp: String = DnsPreset.CLOUDFLARE.secondaryIp,
    var fallbackToSystem: Boolean = true,
    var staticHosts: MutableMap<String, String> = mutableMapOf()
) {
    fun toJson(): JSONObject {
        val root = JSONObject()
        root.put("enabled", enabled)
        root.put("mode", mode.name)
        root.put("preset", preset.name)
        root.put("dohUrl", dohUrl)
        root.put("primaryDnsIp", primaryDnsIp)
        root.put("secondaryDnsIp", secondaryDnsIp)
        root.put("fallbackToSystem", fallbackToSystem)

        val hostsObj = JSONObject()
        for ((host, ip) in staticHosts) {
            hostsObj.put(host, ip)
        }
        root.put("staticHosts", hostsObj)
        return root
    }

    fun toJsonString(indentSpaces: Int = 2): String {
        return toJson().toString(indentSpaces)
    }

    companion object {
        fun fromJson(jsonStr: String): DnsConfig {
            val config = DnsConfig()
            if (jsonStr.isBlank()) return config

            try {
                val root = JSONObject(jsonStr)
                if (root.has("enabled")) config.enabled = root.getBoolean("enabled")
                if (root.has("mode")) {
                    runCatching { config.mode = DnsMode.valueOf(root.getString("mode")) }
                }
                if (root.has("preset")) {
                    runCatching { config.preset = DnsPreset.valueOf(root.getString("preset")) }
                }
                if (root.has("dohUrl")) config.dohUrl = root.getString("dohUrl")
                if (root.has("primaryDnsIp")) config.primaryDnsIp = root.getString("primaryDnsIp")
                if (root.has("secondaryDnsIp")) config.secondaryDnsIp = root.getString("secondaryDnsIp")
                if (root.has("fallbackToSystem")) config.fallbackToSystem = root.getBoolean("fallbackToSystem")

                if (root.has("staticHosts")) {
                    val hostsObj = root.getJSONObject("staticHosts")
                    val map = mutableMapOf<String, String>()
                    val keys = hostsObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        map[key] = hostsObj.getString(key)
                    }
                    config.staticHosts = map
                }
            } catch (ignored: Exception) {}

            return config
        }
    }
}
