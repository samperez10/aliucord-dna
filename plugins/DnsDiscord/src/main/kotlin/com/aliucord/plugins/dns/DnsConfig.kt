package com.aliucord.plugins.dns

import org.json.JSONObject

object DnsMode {
    const val DOH = "DOH"
    const val UDP = "UDP"
    const val STATIC_ONLY = "STATIC_ONLY"
}

class DnsPreset(
    val name: String,
    val displayName: String,
    val dohUrl: String,
    val primaryIp: String,
    val secondaryIp: String
) {
    companion object {
        val CLOUDFLARE = DnsPreset(
            "CLOUDFLARE",
            "Cloudflare (1.1.1.1)",
            "https://cloudflare-dns.com/dns-query",
            "1.1.1.1",
            "1.0.0.1"
        )
        val GOOGLE = DnsPreset(
            "GOOGLE",
            "Google (8.8.8.8)",
            "https://dns.google/dns-query",
            "8.8.8.8",
            "8.8.4.4"
        )
        val ADGUARD = DnsPreset(
            "ADGUARD",
            "AdGuard DNS (Blocks Ads/Trackers)",
            "https://dns.adguard-dns.com/dns-query",
            "94.140.14.14",
            "94.140.15.15"
        )
        val QUAD9 = DnsPreset(
            "QUAD9",
            "Quad9 (Blocks Malware)",
            "https://dns.quad9.net/dns-query",
            "9.9.9.9",
            "149.112.112.112"
        )
        val CUSTOM = DnsPreset(
            "CUSTOM",
            "Custom Configuration",
            "",
            "",
            ""
        )

        fun fromName(name: String): DnsPreset = when (name.toUpperCase(java.util.Locale.ROOT)) {
            "GOOGLE" -> GOOGLE
            "ADGUARD" -> ADGUARD
            "QUAD9" -> QUAD9
            "CUSTOM" -> CUSTOM
            else -> CLOUDFLARE
        }
    }
}

data class DnsConfig(
    var enabled: Boolean = true,
    var mode: String = DnsMode.DOH,
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
        root.put("mode", mode)
        root.put("preset", preset.name)
        root.put("dohUrl", dohUrl)
        root.put("primaryDnsIp", primaryDnsIp)
        root.put("secondaryDnsIp", secondaryDnsIp)
        root.put("fallbackToSystem", fallbackToSystem)

        val hostsObj = JSONObject()
        val it = staticHosts.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            hostsObj.put(entry.key, entry.value)
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
            if (jsonStr.trim().isEmpty()) return config

            try {
                val root = JSONObject(jsonStr)
                if (root.has("enabled")) config.enabled = root.getBoolean("enabled")
                if (root.has("mode")) {
                    config.mode = root.getString("mode")
                }
                if (root.has("preset")) {
                    config.preset = DnsPreset.fromName(root.getString("preset"))
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
