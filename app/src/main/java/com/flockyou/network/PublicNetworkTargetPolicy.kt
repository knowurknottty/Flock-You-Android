package com.flockyou.network

import java.net.IDN
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Fail-closed policy for external Internet enrichment targets.
 *
 * This class performs no DNS or network I/O. Hostnames are only syntax-checked here;
 * callers must resolve them and require [allResolvedAddressesPublic] immediately before
 * an external request. This prevents a MAC/BSSID/private address from being treated as
 * a Shodan host and protects against mixed/private DNS answers.
 */
object PublicNetworkTargetPolicy {
    enum class Kind { PUBLIC_IP, HOSTNAME, REJECTED }

    data class Decision(
        val kind: Kind,
        val normalized: String? = null,
        val reason: String? = null
    )

    fun classify(raw: String): Decision {
        val value = raw.trim().removeSurrounding("[", "]")
        if (value.isEmpty()) return rejected("Target is blank")
        if (value.contains('%')) return rejected("Scoped IPv6 addresses are not public enrichment targets")

        parseIpv4(value)?.let { address ->
            return if (isPublicAddress(address)) Decision(Kind.PUBLIC_IP, address.hostAddress)
            else rejected("IP address is private, local, reserved, or non-routable")
        }
        if (looksLikeIpv4(value)) return rejected("Invalid IPv4 address")

        if (value.contains(':')) {
            val address = try { InetAddress.getByName(value) } catch (_: Exception) { null }
                ?: return rejected("Invalid IPv6 address")
            if (address !is Inet6Address && address !is Inet4Address) return rejected("Invalid IP address")
            return if (isPublicAddress(address)) Decision(Kind.PUBLIC_IP, address.hostAddress)
            else rejected("IP address is private, local, reserved, or non-routable")
        }

        val hostname = normalizeHostname(value) ?: return rejected("Invalid or local-only hostname")
        return Decision(Kind.HOSTNAME, hostname)
    }

    fun allResolvedAddressesPublic(addresses: List<InetAddress>): Boolean =
        addresses.isNotEmpty() && addresses.all(::isPublicAddress)

    fun isPublicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress) return false
        return when (address) {
            is Inet4Address -> isPublicIpv4(address.address)
            is Inet6Address -> isPublicIpv6(address.address)
            else -> false
        }
    }

    private fun parseIpv4(value: String): Inet4Address? {
        if (!looksLikeIpv4(value)) return null
        val parts = value.split('.')
        if (parts.size != 4) return null
        val bytes = ByteArray(4)
        for (i in parts.indices) {
            if (parts[i].isEmpty() || (parts[i].length > 1 && parts[i].startsWith('0'))) return null
            val n = parts[i].toIntOrNull() ?: return null
            if (n !in 0..255) return null
            bytes[i] = n.toByte()
        }
        return InetAddress.getByAddress(bytes) as Inet4Address
    }

    private fun looksLikeIpv4(value: String): Boolean =
        value.isNotEmpty() && value.all { it.isDigit() || it == '.' }

    private fun isPublicIpv4(bytes: ByteArray): Boolean {
        val a = bytes[0].toInt() and 0xff
        val b = bytes[1].toInt() and 0xff
        val c = bytes[2].toInt() and 0xff
        return when {
            a == 0 || a == 10 || a == 127 -> false
            a == 100 && b in 64..127 -> false // carrier-grade NAT
            a == 169 && b == 254 -> false
            a == 172 && b in 16..31 -> false
            a == 192 && b == 0 && c == 0 -> false
            a == 192 && b == 0 && c == 2 -> false // documentation
            a == 192 && b == 168 -> false
            a == 198 && b in 18..19 -> false // benchmark networks
            a == 198 && b == 51 && c == 100 -> false // documentation
            a == 203 && b == 0 && c == 113 -> false // documentation
            a >= 224 -> false
            else -> true
        }
    }

    private fun isPublicIpv6(bytes: ByteArray): Boolean {
        if (bytes.size != 16) return false
        val b0 = bytes[0].toInt() and 0xff
        val b1 = bytes[1].toInt() and 0xff
        if (b0 and 0xfe == 0xfc) return false // unique local fc00::/7
        if (b0 == 0xff) return false // multicast
        if (b0 == 0xfe && b1 and 0xc0 == 0x80) return false // link-local fe80::/10
        if (b0 == 0x20 && b1 == 0x01 && (bytes[2].toInt() and 0xff) == 0x0d &&
            (bytes[3].toInt() and 0xff) == 0xb8) return false // documentation 2001:db8::/32
        return true
    }

    private fun normalizeHostname(raw: String): String? {
        val ascii = try { IDN.toASCII(raw.trimEnd('.'), IDN.USE_STD3_ASCII_RULES).lowercase() }
        catch (_: IllegalArgumentException) { return null }
        if (ascii.isEmpty() || ascii.length > 253 || !ascii.contains('.')) return null
        val localSuffixes = listOf(".local", ".localhost", ".internal", ".home.arpa", ".lan", ".localdomain")
        if (ascii == "localhost" || localSuffixes.any(ascii::endsWith)) return null
        val labels = ascii.split('.')
        if (labels.any { it.isEmpty() || it.length > 63 || it.startsWith('-') || it.endsWith('-') ||
                    it.any { ch -> !(ch.isLetterOrDigit() || ch == '-') } }) return null
        return ascii
    }

    private fun rejected(reason: String) = Decision(Kind.REJECTED, reason = reason)
}
