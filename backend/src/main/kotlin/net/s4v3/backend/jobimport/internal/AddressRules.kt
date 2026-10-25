package net.s4v3.backend.jobimport.internal

import java.net.InetAddress

/** Whether an address is a public, routable one. Everything else is never connected to. */
internal object AddressRules {

    private val NAT64_PREFIX = listOf(0x00, 0x64, 0xff, 0x9b, 0, 0, 0, 0, 0, 0, 0, 0)

    fun isPublic(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress
        ) return false
        val b = address.address.map { it.toInt() and 0xff }
        return if (b.size == 4) isPublicV4(b) else isPublicV6(b)
    }

    private fun isPublicV4(b: List<Int>): Boolean = when {
        b[0] == 0 || b[0] == 10 || b[0] == 127 -> false
        b[0] >= 224 -> false // multicast and reserved
        b[0] == 100 && b[1] in 64..127 -> false // CGNAT
        b[0] == 169 && b[1] == 254 -> false // link-local, cloud metadata
        b[0] == 172 && b[1] in 16..31 -> false
        b[0] == 192 && b[1] == 168 -> false
        b[0] == 192 && b[1] == 0 && b[2] == 0 -> false
        b[0] == 198 && b[1] in 18..19 -> false // benchmarking
        else -> true
    }

    private fun isPublicV6(b: List<Int>): Boolean {
        if (b.take(10).all { it == 0 } && b[10] == 0xff && b[11] == 0xff) return isPublicV4(b.drop(12)) // IPv4-mapped
        if (b.take(12) == NAT64_PREFIX) return isPublicV4(b.drop(12))
        if (b.all { it == 0 }) return false
        if (b.take(15).all { it == 0 } && b[15] == 1) return false
        if (b[0] and 0xfe == 0xfc) return false // unique-local fc00::/7
        if (b[0] == 0xfe && b[1] and 0xc0 == 0x80) return false // link-local
        if (b[0] == 0xfe && b[1] and 0xc0 == 0xc0) return false // site-local
        if (b[0] == 0xff) return false
        return true
    }
}
