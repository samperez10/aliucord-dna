package com.aliucord.plugins.dns

import android.content.Context
import com.aliucord.PluginManager
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.Plugin
import com.aliucord.patcher.PreHook
import java.net.InetAddress

@AliucordPlugin(requiresRestart = false)
class DnsPlugin : Plugin() {

    companion object {
        var instance: DnsPlugin? = null
        var resolver: DnsResolver = DnsResolver(DnsConfig())

        fun saveConfig(newConfig: DnsConfig) {
            resolver.config = newConfig
            resolver.clearCache()
            val inst = instance ?: PluginManager.plugins["DnsDiscord"] as? DnsPlugin
            inst?.settings?.setString("config_json", newConfig.toJsonString())
        }
    }

    init {
        settingsTab = SettingsTab(PluginSettings::class.java).withArgs(this)
    }

    override fun start(context: Context) {
        instance = this

        // Load stored configuration from JSON
        val savedJson = settings.getString("config_json", "")
        if (!savedJson.isNullOrBlank()) {
            resolver.config = DnsConfig.fromJson(savedJson)
        }
        resolver.clearCache()

        // Hook java.net.InetAddress.getAllByName(String)
        // This intercepts ALL networking (OkHttp, HttpURLConnection, WebSockets, React Native)
        // using standard Java APIs without depending on obfuscated or missing classes.
        try {
            val getAllByNameMethod = InetAddress::class.java.getDeclaredMethod("getAllByName", String::class.java)
            patcher.patch(
                getAllByNameMethod,
                PreHook { param ->
                    val hostname = param.args[0] as? String ?: return@PreHook
                    if (!resolver.config.enabled) return@PreHook

                    val resolved = resolver.lookup(hostname)
                    if (resolved.isNotEmpty()) {
                        param.result = resolved.toTypedArray()
                    }
                }
            )
            logger.info("Successfully hooked java.net.InetAddress.getAllByName")
        } catch (e: Throwable) {
            logger.error("Failed to patch InetAddress.getAllByName", e)
        }

        logger.info("DNS Discord plugin started successfully! Mode: ${resolver.config.mode}, Enabled: ${resolver.config.enabled}")
    }

    fun saveConfig(newConfig: DnsConfig) {
        Companion.saveConfig(newConfig)
    }

    override fun stop(context: Context) {
        patcher.unpatchAll()
        resolver.clearCache()
        instance = null
    }
}
