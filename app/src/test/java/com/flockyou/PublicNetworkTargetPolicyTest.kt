package com.flockyou.network

import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress

class PublicNetworkTargetPolicyTest {
    @Test fun `public IPv4 literal is accepted`() {
        val d = PublicNetworkTargetPolicy.classify("8.8.8.8")
        assertEquals(PublicNetworkTargetPolicy.Kind.PUBLIC_IP, d.kind)
        assertEquals("8.8.8.8", d.normalized)
    }

    @Test fun `private and special IPv4 literals are rejected`() {
        listOf("10.0.0.1", "172.16.1.1", "192.168.1.1", "127.0.0.1", "169.254.1.2",
            "100.64.0.1", "192.0.2.10", "198.51.100.2", "203.0.113.9", "224.0.0.1")
            .forEach { assertEquals(it, PublicNetworkTargetPolicy.Kind.REJECTED, PublicNetworkTargetPolicy.classify(it).kind) }
    }

    @Test fun `private and documentation IPv6 literals are rejected`() {
        listOf("::1", "fe80::1", "fc00::1", "fd12::1", "ff02::1", "2001:db8::1")
            .forEach { assertEquals(it, PublicNetworkTargetPolicy.Kind.REJECTED, PublicNetworkTargetPolicy.classify(it).kind) }
    }

    @Test fun `normal hostname is accepted but local-only names are rejected`() {
        assertEquals(PublicNetworkTargetPolicy.Kind.HOSTNAME, PublicNetworkTargetPolicy.classify("Example.COM.").kind)
        listOf("localhost", "printer.local", "router.home.arpa", "host.internal")
            .forEach { assertEquals(it, PublicNetworkTargetPolicy.Kind.REJECTED, PublicNetworkTargetPolicy.classify(it).kind) }
    }

    @Test fun `all resolved addresses must be public`() {
        val public = InetAddress.getByAddress(byteArrayOf(8, 8, 8, 8))
        val private = InetAddress.getByAddress(byteArrayOf(10, 0, 0, 1))
        assertTrue(PublicNetworkTargetPolicy.allResolvedAddressesPublic(listOf(public)))
        assertFalse(PublicNetworkTargetPolicy.allResolvedAddressesPublic(listOf(public, private)))
        assertFalse(PublicNetworkTargetPolicy.allResolvedAddressesPublic(emptyList()))
    }
}
