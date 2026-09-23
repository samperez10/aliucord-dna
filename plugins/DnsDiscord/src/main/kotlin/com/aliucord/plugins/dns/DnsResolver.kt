package com.aliucord.plugins.dns

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

class DnsResolver(var config: DnsConfig) {

    private data class CacheEntry(val addresses: List<InetAddress>, val expiresAt: Long)
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val isResolving = object : ThreadLocal<Boolean>() {
        override fun initialValue(): Boolean = false
    }

    fun lookup(hostname: String): List<InetAddress> {
        if (hostname.isBlank() || !config.enabled) return emptyList()

        // Prevent recursive intercept when the resolver is looking up DoH endpoint
        if (isResolving.get() == true) return emptyList()

        val lowerHost = hostname.lowercase()

        // 1. Check Static Host Mappings from JSON configuration
        config.staticHosts[lowerHost]?.let { staticIp ->
            try {
                return listOf(InetAddress.getByName(staticIp))
            } catch (ignored: Exception) {}
        }

        // 2. Check in-memory DNS cache
        val now = System.currentTimeMillis()
        cache[lowerHost]?.let { entry ->
            if (now < entry.expiresAt && entry.addresses.isNotEmpty()) {
                return entry.addresses
            } else {
                cache.remove(lowerHost)
            }
        }

        // 3. Prevent recursive resolution if resolving the DoH server host itself
        getBootstrapAddress(lowerHost)?.let { bootstrap ->
            return listOf(bootstrap)
        }

        // 4. Resolve according to configured mode
        isResolving.set(true)
        var resolved: List<InetAddress>? = null
        try {
            resolved = when (config.mode) {
                DnsMode.DOH -> resolveDoH(hostname)
                DnsMode.UDP -> resolveUdp(hostname)
                else -> null
            }
        } catch (ignored: Exception) {
        } finally {
            isResolving.set(false)
        }

        // 5. Handle fallback or return resolved addresses
        if (!resolved.isNullOrEmpty()) {
            cache[lowerHost] = CacheEntry(resolved, now + 300_000L) // Cache for 5 minutes
            return resolved
        }

        return emptyList()
    }

    /**
     * Resolves host using DNS-over-HTTPS (DoH) with JSON API
     */
    fun resolveDoH(hostname: String): List<InetAddress> {
        val dohEndpoint = when {
            config.dohUrl.isNotBlank() -> config.dohUrl
            config.preset.dohUrl.isNotBlank() -> config.preset.dohUrl
            else -> DnsPreset.CLOUDFLARE.dohUrl
        }

        val queryUrl = if (dohEndpoint.contains("?")) {
            "$dohEndpoint&name=$hostname&type=A"
        } else {
            "$dohEndpoint?name=$hostname&type=A"
        }

        val url = URL(queryUrl)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 3500
            readTimeout = 3500
            setRequestProperty("Accept", "application/dns-json")
            setRequestProperty("User-Agent", "Aliucord-DnsPlugin/1.0")
        }

        conn.connect()
        if (conn.responseCode != 200) {
            throw UnknownHostException("DoH server returned HTTP ${conn.responseCode}")
        }

        val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(responseBody)
        val result = mutableListOf<InetAddress>()

        if (json.has("Answer")) {
            val answers = json.getJSONArray("Answer")
            for (i in 0 until answers.length()) {
                val record = answers.getJSONObject(i)
                val type = record.optInt("type", 0)
                val data = record.optString("data", "")
                // Type 1 is A (IPv4), Type 28 is AAAA (IPv6)
                if ((type == 1 || type == 28) && data.isNotBlank()) {
                    try {
                        result.add(InetAddress.getByName(data))
                    } catch (ignored: Exception) {}
                }
            }
        }

        return result
    }

    /**
     * Resolves host using direct standard UDP DNS query (Port 53)
     */
    fun resolveUdp(hostname: String): List<InetAddress> {
        val primary = config.primaryDnsIp.ifBlank { config.preset.primaryIp }
        val secondary = config.secondaryDnsIp.ifBlank { config.preset.secondaryIp }

        try {
            val res = queryUdpServer(hostname, primary)
            if (res.isNotEmpty()) return res
        } catch (e: Exception) {
            if (secondary.isNotBlank()) {
                return queryUdpServer(hostname, secondary)
            }
            throw e
        }
        return emptyList()
    }

    private fun queryUdpServer(hostname: String, serverIp: String): List<InetAddress> {
        val queryId = Random.nextInt(0, 0xFFFF)
        val packetData = buildDnsQueryPacket(hostname, queryId)
        val serverAddr = InetAddress.getByName(serverIp)

        DatagramSocket().use { socket ->
            socket.soTimeout = 3000
            val sendPacket = DatagramPacket(packetData, packetData.size, serverAddr, 53)
            socket.send(sendPacket)

            val buffer = ByteArray(512)
            val receivePacket = DatagramPacket(buffer, buffer.size)
            socket.receive(receivePacket)

            return parseDnsResponse(buffer, receivePacket.length, queryId)
        }
    }

    private fun buildDnsQueryPacket(hostname: String, id: Int): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)

        // 12-byte DNS Header
        dos.writeShort(id) // ID
        dos.writeShort(0x0100) // Standard query with recursion desired
        dos.writeShort(1) // QDCOUNT (1 question)
        dos.writeShort(0) // ANCOUNT
        dos.writeShort(0) // NSCOUNT
        dos.writeShort(0) // ARCOUNT

        // Question: QNAME
        val parts = hostname.split(".")
        for (part in parts) {
            val bytes = part.toByteArray(Charsets.US_ASCII)
            dos.writeByte(bytes.size)
            dos.write(bytes)
        }
        dos.writeByte(0) // End of QNAME

        // QTYPE = 1 (A record), QCLASS = 1 (IN)
        dos.writeShort(1)
        dos.writeShort(1)
        dos.flush()

        return baos.toByteArray()
    }

    private fun parseDnsResponse(data: ByteArray, length: Int, expectedId: Int): List<InetAddress> {
        if (length < 12) return emptyList()

        val id = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
        if (id != expectedId) return emptyList()

        val ancount = ((data[6].toInt() and 0xFF) shl 8) or (data[7].toInt() and 0xFF)
        if (ancount == 0) return emptyList()

        // Skip Question section
        var ptr = 12
        while (ptr < length && data[ptr].toInt() != 0) {
            val len = data[ptr].toInt() and 0xFF
            if ((len and 0xC0) == 0xC0) {
                ptr += 2
                break
            } else {
                ptr += 1 + len
            }
        }
        if (ptr < length && data[ptr].toInt() == 0) ptr++ // Skip 0x00 terminator
        ptr += 4 // Skip QTYPE and QCLASS

        val addresses = mutableListOf<InetAddress>()
        for (i in 0 until ancount) {
            if (ptr >= length) break
            // Parse name (may be compressed pointer)
            if ((data[ptr].toInt() and 0xC0) == 0xC0) {
                ptr += 2
            } else {
                while (ptr < length && data[ptr].toInt() != 0) {
                    ptr += 1 + (data[ptr].toInt() and 0xFF)
                }
                if (ptr < length) ptr++
            }

            if (ptr + 10 > length) break
            val type = ((data[ptr].toInt() and 0xFF) shl 8) or (data[ptr + 1].toInt() and 0xFF)
            ptr += 8 // Skip type(2), class(2), ttl(4)
            val rdLength = ((data[ptr].toInt() and 0xFF) shl 8) or (data[ptr + 1].toInt() and 0xFF)
            ptr += 2

            if (type == 1 && rdLength == 4 && ptr + 4 <= length) {
                val ipBytes = byteArrayOf(data[ptr], data[ptr + 1], data[ptr + 2], data[ptr + 3])
                addresses.add(InetAddress.getByAddress(ipBytes))
            }
            ptr += rdLength
        }

        return addresses
    }

    private fun getBootstrapAddress(hostname: String): InetAddress? {
        return when (hostname) {
            "cloudflare-dns.com", "one.one.one.one", "1.1.1.1" -> InetAddress.getByName("1.1.1.1")
            "dns.google", "8.8.8.8" -> InetAddress.getByName("8.8.8.8")
            "dns.adguard-dns.com" -> InetAddress.getByName("94.140.14.14")
            "dns.quad9.net" -> InetAddress.getByName("9.9.9.9")
            else -> null
        }
    }

    fun clearCache() {
        cache.clear()
    }
}
