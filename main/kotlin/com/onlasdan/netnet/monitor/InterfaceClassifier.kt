package com.onlasdan.netnet.monitor

/**
 * Shared physical/virtual interface classifier.
 *
 * Previously duplicated (with subtle divergence risk) in TrafficMonitor
 * (VPN counter basis) and NetworkStateManager (interface list diagnostics) —
 * both must classify the SAME set of names as virtual, otherwise a tun/tap
 * interface could be counted as physical traffic in one place and reported
 * as virtual in the other.
 */
internal object InterfaceClassifier {

    private val VIRTUAL_PREFIXES = arrayOf(
        "tun", "tap", "p2p", "dummy", "lo", "sit", "ipsec", "ifb", "ppp", "vbox", "swlan"
    )

    fun isVirtualInterface(name: String): Boolean {
        val lower = name.lowercase()
        if ("vpn" in lower) return true
        return VIRTUAL_PREFIXES.any { lower.startsWith(it) }
    }
}
