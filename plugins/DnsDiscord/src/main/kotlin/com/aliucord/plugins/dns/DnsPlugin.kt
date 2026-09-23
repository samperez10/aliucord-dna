package com.aliucord.plugins.dns

import android.content.Context
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.Plugin
import com.aliucord.patcher.before
import com.aliucord.patcher.instead
import okhttp3.Dns
import okhttp3.OkHttpClient

@AliucordPlugin(requiresRestart = false)
class DnsPlugin : Plugin() {

    lateinit var resolver: DnsResolver
        private set

    init {
        settingsTab = SettingsTab(PluginSettings::class.java, SettingsTab.Type.PAGE).withArgs(this)
    }

    override fun start(context: Context) {
        // Load stored configuration from JSON
        val savedJson = settings.getString("config_json", "")
        val config = if (savedJson.isNullOrBlank()) {
            DnsConfig()
        } else {
            DnsConfig.fromJson(savedJson)
        }

        resolver = DnsResolver(config)

        // 1. Patch Dns.SYSTEM so all existing and default OkHttp clients use our DNS resolver
        try {
            val systemDnsClass = Dns.SYSTEM.javaClass
            patcher.instead(
                systemDnsClass.getDeclaredMethod("lookup", String::class.java)
            ) { param ->
                val hostname = param.args[0] as String
                resolver.lookup(hostname)
            }
        } catch (e: Throwable) {
            logger.error("Failed to patch Dns.SYSTEM", e)
        }

        // 2. Patch OkHttpClient.Builder.build() to inject our custom Dns instance directly into any new client
        try {
            patcher.before<OkHttpClient.Builder>("build") {
                val builder = this as OkHttpClient.Builder
                builder.dns(resolver)
            }
        } catch (e: Throwable) {
            logger.error("Failed to patch OkHttpClient.Builder.build()", e)
        }

        logger.info("DNS Discord plugin started successfully! Mode: ${config.mode}, Enabled: ${config.enabled}")
    }

    fun saveConfig(newConfig: DnsConfig) {
        resolver.config = newConfig
        resolver.clearCache()
        settings.setString("config_json", newConfig.toJsonString())
    }

    override fun stop(context: Context) {
        patcher.unpatchAll()
        if (::resolver.isInitialized) {
            resolver.clearCache()
        }
    }
}
