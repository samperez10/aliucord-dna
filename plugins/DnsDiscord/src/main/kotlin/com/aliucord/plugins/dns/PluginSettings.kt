package com.aliucord.plugins.dns

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.aliucord.Utils
import com.aliucord.fragments.SettingsPage
import com.aliucord.utils.DimenUtils
import com.aliucord.views.Button
import com.aliucord.views.Divider
import com.aliucord.views.TextInput
import com.discord.views.CheckedSetting

class PluginSettings(private val plugin: DnsPlugin? = null) : SettingsPage() {

    private val mainHandler = Handler(Looper.getMainLooper())

    @SuppressLint("SetTextI18n")
    override fun onViewBound(view: View) {
        super.onViewBound(view)

        val ctx = requireContext()
        val config = DnsPlugin.resolver.config
        val dp10 = DimenUtils.dpToPx(10)

        setActionBarTitle("Discord DNS Settings")

        // --- General Section ---
        addHeader(ctx, "General")

        addView(
            Utils.createCheckedSetting(
                ctx,
                CheckedSetting.ViewType.SWITCH,
                "Enable Custom DNS",
                "Route all in-app Discord HTTP/Gateway traffic through custom DNS"
            ).apply {
                isChecked = config.enabled
                setOnCheckedListener {
                    config.enabled = it
                    DnsPlugin.saveConfig(config)
                    Utils.showToast("DNS " + if (it) "Enabled" else "Disabled")
                }
            }
        )

        addView(
            Utils.createCheckedSetting(
                ctx,
                CheckedSetting.ViewType.SWITCH,
                "Fallback to System DNS",
                "Fallback to standard system DNS if custom resolution fails or times out"
            ).apply {
                isChecked = config.fallbackToSystem
                setOnCheckedListener {
                    config.fallbackToSystem = it
                    DnsPlugin.saveConfig(config)
                }
            }
        )

        addDivider(ctx)

        // --- Resolution Mode ---
        addHeader(ctx, "Resolution Protocol")

        val dohSwitch = Utils.createCheckedSetting(
            ctx,
            CheckedSetting.ViewType.SWITCH,
            "DNS-over-HTTPS (DoH)",
            "Encrypted DNS queries via HTTPS (Cloudflare, Google, AdGuard, NextDNS)"
        )
        val udpSwitch = Utils.createCheckedSetting(
            ctx,
            CheckedSetting.ViewType.SWITCH,
            "Direct UDP (Port 53)",
            "Standard DNS datagram queries to upstream DNS server IP"
        )
        val staticSwitch = Utils.createCheckedSetting(
            ctx,
            CheckedSetting.ViewType.SWITCH,
            "Static Host Mappings Only",
            "Only resolve hosts defined in the static JSON overrides table"
        )

        fun updateModeRadios() {
            dohSwitch.isChecked = config.mode == DnsMode.DOH
            udpSwitch.isChecked = config.mode == DnsMode.UDP
            staticSwitch.isChecked = config.mode == DnsMode.STATIC_ONLY
        }

        dohSwitch.setOnCheckedListener {
            if (it) {
                config.mode = DnsMode.DOH
                DnsPlugin.saveConfig(config)
                updateModeRadios()
            }
        }
        udpSwitch.setOnCheckedListener {
            if (it) {
                config.mode = DnsMode.UDP
                DnsPlugin.saveConfig(config)
                updateModeRadios()
            }
        }
        staticSwitch.setOnCheckedListener {
            if (it) {
                config.mode = DnsMode.STATIC_ONLY
                DnsPlugin.saveConfig(config)
                updateModeRadios()
            }
        }

        updateModeRadios()
        addView(dohSwitch)
        addView(udpSwitch)
        addView(staticSwitch)

        addDivider(ctx)

        // --- Presets ---
        addHeader(ctx, "Provider Presets")

        fun applyPreset(preset: DnsPreset) {
            config.preset = preset
            if (preset != DnsPreset.CUSTOM) {
                config.dohUrl = preset.dohUrl
                config.primaryDnsIp = preset.primaryIp
                config.secondaryDnsIp = preset.secondaryIp
            }
            DnsPlugin.saveConfig(config)
            Utils.showToast("Applied preset: ${preset.displayName}")
            reRender()
        }

        val cfButton = Button(ctx).apply {
            text = "Cloudflare (1.1.1.1)"
            setOnClickListener { applyPreset(DnsPreset.CLOUDFLARE) }
        }
        val googleButton = Button(ctx).apply {
            text = "Google (8.8.8.8)"
            setOnClickListener { applyPreset(DnsPreset.GOOGLE) }
        }
        val adguardButton = Button(ctx).apply {
            text = "AdGuard DNS (Block Ads/Trackers)"
            setOnClickListener { applyPreset(DnsPreset.ADGUARD) }
        }
        val quad9Button = Button(ctx).apply {
            text = "Quad9 (Malware Protection)"
            setOnClickListener { applyPreset(DnsPreset.QUAD9) }
        }

        addView(cfButton)
        addView(googleButton)
        addView(adguardButton)
        addView(quad9Button)

        addDivider(ctx)

        // --- Custom Inputs ---
        addHeader(ctx, "Custom Endpoints / Server IPs")

        val dohInput = TextInput(ctx, "DoH Endpoint URL (e.g. https://1.1.1.1/dns-query)", config.dohUrl).apply {
            editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }

        val primaryIpInput = TextInput(ctx, "Primary UDP DNS IP (e.g. 1.1.1.1)", config.primaryDnsIp).apply {
            editText.inputType = InputType.TYPE_CLASS_TEXT
        }

        val secondaryIpInput = TextInput(ctx, "Secondary UDP DNS IP (e.g. 1.0.0.1)", config.secondaryDnsIp).apply {
            editText.inputType = InputType.TYPE_CLASS_TEXT
        }

        val saveEndpointsButton = Button(ctx).apply {
            text = "Save Custom Endpoints"
            setOnClickListener {
                config.dohUrl = dohInput.editText.text.toString().trim()
                config.primaryDnsIp = primaryIpInput.editText.text.toString().trim()
                config.secondaryDnsIp = secondaryIpInput.editText.text.toString().trim()
                config.preset = DnsPreset.CUSTOM
                DnsPlugin.saveConfig(config)
                Utils.showToast("Endpoints saved successfully!")
            }
        }

        addView(dohInput)
        addView(primaryIpInput)
        addView(secondaryIpInput)
        addView(saveEndpointsButton)

        addDivider(ctx)

        // --- Static Host Overrides ---
        addHeader(ctx, "Static Host Overrides (Local /etc/hosts)")

        val staticInfo = TextView(ctx).apply {
            text = "Current static mappings: ${config.staticHosts.size} rules"
            setPadding(0, dp10, 0, dp10)
        }
        addView(staticInfo)

        val addStaticButton = Button(ctx).apply {
            text = "Add / Edit Static Host Mapping"
            setOnClickListener {
                showAddHostDialog(ctx) { host, ip ->
                    config.staticHosts[host.lowercase()] = ip
                    DnsPlugin.saveConfig(config)
                    Utils.showToast("Mapped $host -> $ip")
                    reRender()
                }
            }
        }
        addView(addStaticButton)

        if (config.staticHosts.isNotEmpty()) {
            val clearStaticButton = Button(ctx).apply {
                text = "Clear All Static Mappings (${config.staticHosts.size})"
                setOnClickListener {
                    config.staticHosts.clear()
                    DnsPlugin.saveConfig(config)
                    Utils.showToast("Static mappings cleared!")
                    reRender()
                }
            }
            addView(clearStaticButton)
        }

        addDivider(ctx)

        // --- Tools & JSON Storage ---
        addHeader(ctx, "Diagnostics & JSON Storage")

        val testButton = Button(ctx).apply {
            text = "Test DNS Resolution (discord.com)"
            setOnClickListener {
                it.isEnabled = false
                Utils.showToast("Resolving discord.com...")
                Thread {
                    val start = System.currentTimeMillis()
                    try {
                        val addresses = DnsPlugin.resolver.lookup("discord.com")
                        val elapsed = System.currentTimeMillis() - start
                        val sb = StringBuilder()
                        var a = 0
                        while (a < addresses.size) {
                            if (sb.isNotEmpty()) sb.append(", ")
                            sb.append(addresses[a].hostAddress)
                            a++
                        }
                        val ipList = sb.toString()
                            it.isEnabled = true
                            AlertDialog.Builder(ctx)
                                .setTitle("DNS Test Success")
                                .setMessage("Resolved discord.com in ${elapsed}ms\n\nAddresses:\n$ipList\n\nMode: ${config.mode}")
                                .setPositiveButton("OK", null)
                                .show()
                        }
                    } catch (e: Exception) {
                        val elapsed = System.currentTimeMillis() - start
                        mainHandler.post {
                            it.isEnabled = true
                            AlertDialog.Builder(ctx)
                                .setTitle("DNS Test Failed")
                                .setMessage("Failed after ${elapsed}ms\n\nError: ${e.message}")
                                .setPositiveButton("OK", null)
                                .show()
                        }
                    }
                }.start()
            }
        }
        addView(testButton)

        val jsonEditorButton = Button(ctx).apply {
            text = "View / Edit Raw JSON Storage"
            setOnClickListener {
                showJsonStorageDialog(ctx)
            }
        }
        addView(jsonEditorButton)
    }

    private fun showAddHostDialog(ctx: android.content.Context, onSave: (String, String) -> Unit) {
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val pad = DimenUtils.dpToPx(16)
            setPadding(pad, pad, pad, pad)
        }

        val hostInput = EditText(ctx).apply {
            hint = "Hostname (e.g. gateway.discord.gg)"
            maxLines = 1
        }
        val ipInput = EditText(ctx).apply {
            hint = "Target IP (e.g. 162.159.135.232)"
            maxLines = 1
        }

        layout.addView(hostInput)
        layout.addView(ipInput)

        AlertDialog.Builder(ctx)
            .setTitle("Add Static Host Override")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val host = hostInput.text.toString().trim()
                val ip = ipInput.text.toString().trim()
                if (host.isNotBlank() && ip.isNotBlank()) {
                    onSave(host, ip)
                } else {
                    Utils.showToast("Host and IP cannot be empty")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showJsonStorageDialog(ctx: android.content.Context) {
        val editText = EditText(ctx).apply {
            setText(DnsPlugin.resolver.config.toJsonString(2))
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 12f
            setHorizontallyScrolling(true)
        }

        val pad = DimenUtils.dpToPx(16)
        val container = LinearLayout(ctx).apply {
            setPadding(pad, pad, pad, pad)
            addView(editText, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        AlertDialog.Builder(ctx)
            .setTitle("Raw JSON Storage")
            .setView(container)
            .setPositiveButton("Save JSON") { _, _ ->
                try {
                    val rawText = editText.text.toString()
                    val newConfig = DnsConfig.fromJson(rawText)
                    DnsPlugin.saveConfig(newConfig)
                    Utils.showToast("JSON configuration saved!")
                    reRender()
                } catch (e: Exception) {
                    Utils.showToast("Invalid JSON: ${e.message}")
                }
            }
            .setNeutralButton("Format") { dialog, _ ->
                try {
                    val rawText = editText.text.toString()
                    val formatted = DnsConfig.fromJson(rawText).toJsonString(2)
                    editText.setText(formatted)
                } catch (ignored: Exception) {}
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
