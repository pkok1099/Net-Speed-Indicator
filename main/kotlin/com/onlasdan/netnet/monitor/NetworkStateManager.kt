package com.onlasdan.netnet.monitor

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import com.onlasdan.netnet.data.SpeedSettingsRepository
import com.onlasdan.netnet.model.CompleteNetworkState
import com.onlasdan.netnet.model.DetailedNetworkDiagnostics
import com.onlasdan.netnet.model.NetworkInterfaceItem
import com.onlasdan.netnet.model.NetworkType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Centralized NetworkStateManager service.
 * Serves as the single source of truth for ping, latency, and connectivity status across the entire application.
 * Prevents data drift by providing a single unified method [getCompleteNetworkState] / [fetchCompleteNetworkState]
 * and a reactive [networkState] StateFlow, eliminating independent conflicting diagnostic probes.
 */
class NetworkStateManager private constructor(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Default)
    private val settingsRepo = SpeedSettingsRepository.getInstance(context)
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private val stateMutex = Mutex()
    private val _networkState = MutableStateFlow(CompleteNetworkState())
    val networkState: StateFlow<CompleteNetworkState> = _networkState.asStateFlow()

    private var pingJob: Job? = null
    // Mutated from the main thread (screen callbacks, activity lifecycle) and
    // read from Dispatchers.Default/IO coroutines — must be @Volatile so the
    // ping loop and screen-state checks never observe stale values.
    @Volatile private var isScreenOn: Boolean = true
    @Volatile private var isAppInForeground: Boolean = false
    @Volatile private var isBatterySaverMode: Boolean = false
    @Volatile private var currentDiagnosticIntervalMs: Long = 4000L

    // Rolling latency history for jitter computation (max 5 samples)
    private val recentPingSamples = ArrayDeque<Long>(5)

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            refreshConnectivityStateAsync()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            refreshConnectivityStateAsync()
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: android.net.LinkProperties) {
            refreshConnectivityStateAsync()
        }

        override fun onLost(network: Network) {
            refreshConnectivityStateAsync()
        }

        override fun onUnavailable() {
            refreshConnectivityStateAsync()
        }
    }

    init {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connectivityManager?.registerDefaultNetworkCallback(networkCallback)
            } else {
                val request = NetworkRequest.Builder().build()
                connectivityManager?.registerNetworkCallback(request, networkCallback)
            }
        } catch (_: Throwable) {}

        currentDiagnosticIntervalMs = settingsRepo.settings.value.diagnosticPollingIntervalMs.let {
            if (it <= 0L) 0L else it.coerceIn(500L, 60000L)
        }

        // Initialize state immediately
        refreshConnectivityStateSync()

        // Observe settings for battery saver mode and custom diagnostic polling interval
        scope.launch {
            try {
                settingsRepo.settings.collect { settings ->
                    val saverChanged = isBatterySaverMode != settings.isBatterySaverMode
                    val intervalChanged = currentDiagnosticIntervalMs != settings.diagnosticPollingIntervalMs
                    if (saverChanged || intervalChanged) {
                        isBatterySaverMode = settings.isBatterySaverMode
                        // 0 = diagnostics OFF (user-visible option); otherwise clamp.
                        currentDiagnosticIntervalMs = settings.diagnosticPollingIntervalMs.let {
                            if (it <= 0L) 0L else it.coerceIn(500L, 60000L)
                        }
                        updatePingProbeLoop()
                    }
                }
            } catch (_: Throwable) {}
        }
    }

    /**
     * Single synchronous source of truth for complete network state snapshot.
     * Guaranteed to return a valid, unified snapshot with zero data drift.
     */
    fun getCompleteNetworkState(): CompleteNetworkState {
        return _networkState.value
    }

    /**
     * Single suspending method to proactively fetch and synchronize the complete network state.
     * When [forceRefreshPing] is true, actively probes latency before updating the state.
     */
    suspend fun fetchCompleteNetworkState(forceRefreshPing: Boolean = false): CompleteNetworkState = withContext(Dispatchers.IO) {
        val measuredPing = if (forceRefreshPing) {
            measurePingLatencyInternal()
        } else {
            _networkState.value.pingMs
        }
        val computedState = computeComprehensiveNetworkState(measuredPing)
        stateMutex.withLock {
            _networkState.value = computedState
        }
        computedState
    }

    /**
     * Helper to retrieve [DetailedNetworkDiagnostics] directly from the single source of truth.
     */
    fun getDiagnostics(): DetailedNetworkDiagnostics {
        return getCompleteNetworkState().toDetailedDiagnostics()
    }

    /**
     * Controls screen state for battery optimization. Can be invoked from any
     * thread (foreground service receiver runs on the main thread, the monitor
     * coroutine on Dispatchers.Default) — pingJob swaps are small enough that
     * a plain synchronized block is sufficient and avoids a suspend mutex.
     */
    @Synchronized
    fun setScreenState(screenOn: Boolean) {
        if (isScreenOn == screenOn) return
        isScreenOn = screenOn
        if (screenOn) {
            refreshConnectivityStateAsync()
            updatePingProbeLoop()
        } else {
            pingJob?.cancel()
            pingJob = null
        }
    }

    /**
     * Controls whether the UI is in the foreground. Ping probing is activated when in foreground.
     * Synchronized together with [setScreenState]: both swap pingJob.
     */
    @Synchronized
    fun setAppForeground(inForeground: Boolean) {
        if (isAppInForeground == inForeground) return
        isAppInForeground = inForeground
        updatePingProbeLoop()
    }

    /**
     * Controls the diagnostic polling interval directly and persists in settings.
     * 0 turns the diagnostics (ping/jitter probes) OFF entirely.
     */
    fun setDiagnosticPollingInterval(intervalMs: Long) {
        val clamped = if (intervalMs <= 0L) 0L else intervalMs.coerceIn(500L, 60000L)
        settingsRepo.updateSettings { it.copy(diagnosticPollingIntervalMs = clamped) }
    }

    fun getDiagnosticPollingInterval(): Long {
        return currentDiagnosticIntervalMs
    }

    /**
     * Forces an immediate ping measurement and updates the state.
     */
    suspend fun refreshPing(): Long = withContext(Dispatchers.IO) {
        val latency = measurePingLatencyInternal()
        updatePingInState(latency)
        latency
    }

    private fun refreshConnectivityStateAsync() {
        scope.launch(Dispatchers.IO) {
            val currentPing = _networkState.value.pingMs
            val newState = computeComprehensiveNetworkState(currentPing)
            stateMutex.withLock {
                _networkState.value = newState
            }
        }
    }

    private fun refreshConnectivityStateSync() {
        scope.launch(Dispatchers.IO) {
            val newState = computeComprehensiveNetworkState(-1L)
            stateMutex.withLock {
                _networkState.value = newState
            }
            updatePingProbeLoop()
        }
    }

    // Swaps pingJob; called only from the @Synchronized screen/foreground
    // setters and from init coroutines, so pingJob mutations stay serialized.
    // (init paths run on a single-threaded startup sequence before any
    // external caller can race them.)
    private fun updatePingProbeLoop() {
        pingJob?.cancel()
        pingJob = null

        // Ping runs only when: screen ON, app in foreground, Smart Battery Saver
        // OFF, and the user has not set the diagnostics OFF (interval == 0).
        if (isScreenOn && isAppInForeground && !isBatterySaverMode && currentDiagnosticIntervalMs > 0L) {
            val effectiveDelay = currentDiagnosticIntervalMs.coerceIn(500L, 60000L)

            pingJob = scope.launch(Dispatchers.IO) {
                while (isActive) {
                    val latency = measurePingLatencyInternal()
                    updatePingInState(latency)
                    delay(effectiveDelay)
                }
            }
        } else {
            // Keep last known valid ping or mark idle if disconnected
            if (_networkState.value.networkType == NetworkType.OFFLINE) {
                updatePingInState(-1L)
            }
        }
    }

    // Guarded by this object's monitor: updatePingInState can be invoked
    // concurrently by the ping loop, refreshConnectivityStateSync and the
    // probe fetch path; the ArrayDeque is not thread-safe.
    private val updatePingLock = Any()

    private fun updatePingInState(latency: Long) {
        synchronized(updatePingLock) {
            if (latency >= 0) {
                if (recentPingSamples.size >= 5) {
                    recentPingSamples.removeFirst()
                }
                recentPingSamples.addLast(latency)
            }

            val jitter = calculateJitter(recentPingSamples)
            val current = _networkState.value
            _networkState.value = current.copy(
                pingMs = latency,
                jitterMs = jitter,
                timestamp = System.currentTimeMillis()
            )
        }
    }

    private fun calculateJitter(samples: List<Long>): Long {
        if (samples.size < 2) return 0L
        var diffSum = 0L
        for (i in 0 until samples.size - 1) {
            diffSum += abs(samples[i + 1] - samples[i])
        }
        return (diffSum.toDouble() / (samples.size - 1)).roundToInt().toLong()
    }

    private suspend fun measurePingLatencyInternal(): Long = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        // Try Primary target: Google DNS (8.8.8.8:53)
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("8.8.8.8", 53), 1200)
                return@withContext System.currentTimeMillis() - start
            }
        } catch (_: Exception) {}

        // Fallback target: Cloudflare DNS (1.1.1.1:53)
        val startFallback = System.currentTimeMillis()
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("1.1.1.1", 53), 1200)
                return@withContext System.currentTimeMillis() - startFallback
            }
        } catch (_: Exception) {}

        // Fallback target 2: Quad9 (9.9.9.9:53)
        val startQuad9 = System.currentTimeMillis()
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("9.9.9.9", 53), 1200)
                return@withContext System.currentTimeMillis() - startQuad9
            }
        } catch (_: Exception) {
            -1L
        }
    }

    private fun computeComprehensiveNetworkState(currentPing: Long): CompleteNetworkState {
        val cm = connectivityManager ?: return CompleteNetworkState(connectionStatus = "Unavailable")

        val activeNetwork = try { cm.activeNetwork } catch (_: Throwable) { null }
        val caps = activeNetwork?.let {
            try { cm.getNetworkCapabilities(it) } catch (_: Throwable) { null }
        }
        val linkProps = activeNetwork?.let {
            try { cm.getLinkProperties(it) } catch (_: Throwable) { null }
        }

        val activeIfName = linkProps?.interfaceName ?: "Unknown"

        var networkType = NetworkType.OFFLINE
        var networkName = "Disconnected"
        var isVpn = false
        var isValidated = false
        var isConnected = activeNetwork != null && caps != null
        var isMetered = try { cm.isActiveNetworkMetered } catch (_: Throwable) { false }
        var downBandwidthMbps = 0
        var upBandwidthMbps = 0
        var signalDbm: Int? = null
        var signalPercent: Int? = null

        var wifiFreq: Int? = null
        var wifiBand: String? = null
        var wifiStandard: String? = null

        var cellularGen: String? = null
        var cellularCarrier: String? = null

        var connectionStatus = "Disconnected"

        if (caps != null && activeNetwork != null) {
            try {
                isValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                isVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                downBandwidthMbps = (caps.linkDownstreamBandwidthKbps / 1000).coerceAtLeast(0)
                upBandwidthMbps = (caps.linkUpstreamBandwidthKbps / 1000).coerceAtLeast(0)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        val dbm = caps.signalStrength
                        if (dbm != NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED) {
                            signalDbm = dbm
                            signalPercent = (((dbm + 100) / 50f) * 100).toInt().coerceIn(0, 100)
                        }
                    } catch (_: Throwable) {}
                }

                connectionStatus = when {
                    isValidated -> "Connected & Validated"
                    hasInternet -> "Connected (Validating Internet...)"
                    else -> "Local Connection Only (No Internet)"
                }

                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    networkType = NetworkType.WIFI
                    try {
                        val info: WifiInfo? = try { wifiManager?.connectionInfo } catch (_: Throwable) { null }
                        val rawSsid = info?.ssid?.replace("\"", "").orEmpty()
                        networkName = if (rawSsid.isNotEmpty() && rawSsid != "<unknown ssid>") rawSsid else "Wi-Fi Network"

                        if (info != null) {
                            val freq = try { info.frequency } catch (_: Throwable) { 0 }
                            if (freq > 0) {
                                wifiFreq = freq
                                wifiBand = when {
                                    freq in 2400..2500 -> "2.4 GHz (Standard Range)"
                                    freq in 4900..5900 -> "5 GHz (High Throughput)"
                                    freq >= 5925 -> "6 GHz (Wi-Fi 6E/7 Ultra-Wide)"
                                    else -> "$freq MHz"
                                }
                            }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                wifiStandard = try {
                                    when (info.wifiStandard) {
                                        8 -> "Wi-Fi 7 (802.11be)"
                                        6 -> "Wi-Fi 6 (802.11ax)"
                                        5 -> "Wi-Fi 5 (802.11ac)"
                                        4 -> "Wi-Fi 4 (802.11n)"
                                        3 -> "802.11g"
                                        1 -> "802.11a"
                                        2 -> "802.11b"
                                        else -> if (freq > 4900) "Wi-Fi 5/6 (5GHz)" else "Wi-Fi 4 (2.4GHz)"
                                    }
                                } catch (_: Throwable) {
                                    if (freq > 4900) "Wi-Fi 5/6 (5GHz)" else "Wi-Fi 4 (2.4GHz)"
                                }
                            } else {
                                wifiStandard = if (freq > 4900) "802.11ac (5GHz)" else "802.11n (2.4GHz)"
                            }

                            val rssi = try { info.rssi } catch (_: Throwable) { -60 }
                            if (rssi in -120..0) {
                                signalDbm = rssi
                                signalPercent = (((rssi + 100) / 50f) * 100).toInt().coerceIn(0, 100)
                            }
                        }
                    } catch (_: Throwable) {
                        networkName = "Wi-Fi Network"
                    }
                } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    networkType = NetworkType.CELLULAR
                    networkName = "Cellular (Mobile Data)"

                    try {
                        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                        val opName = try {
                            telephonyManager?.networkOperatorName?.ifEmpty {
                                telephonyManager.simOperatorName
                            }
                        } catch (_: Throwable) { null }
                        cellularCarrier = if (!opName.isNullOrEmpty()) opName else "Mobile Carrier"
                        networkName = "$cellularCarrier (Mobile Data)"
                    } catch (_: Throwable) {
                        networkName = "Cellular (Mobile Data)"
                    }

                    cellularGen = when {
                        downBandwidthMbps >= 100 -> "5G NR (High Speed Sub-6/mmWave)"
                        downBandwidthMbps >= 25 -> "4G LTE-Advanced"
                        downBandwidthMbps >= 8 -> "4G LTE"
                        downBandwidthMbps >= 1 -> "3G HSPA+"
                        else -> "Mobile Cellular"
                    }
                } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                    networkType = NetworkType.ETHERNET
                    networkName = "Ethernet (LAN)"
                } else if (isVpn) {
                    networkType = NetworkType.VPN
                    networkName = "Encrypted VPN Tunnel"
                }
            } catch (_: Throwable) {}
        }

        // Extract IP Addresses, Gateway, DNS from LinkProperties
        var ipv4Addr = "127.0.0.1"
        var ipv6Addr: String? = null
        var gateway: String? = null
        val dnsList = mutableListOf<String>()

        try {
            linkProps?.linkAddresses?.forEach { linkAddr ->
                val addr = linkAddr.address
                if (addr is Inet4Address && !addr.isLoopbackAddress) {
                    ipv4Addr = addr.hostAddress ?: ipv4Addr
                } else if (addr is Inet6Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                    if (ipv6Addr == null) {
                        ipv6Addr = addr.hostAddress
                    }
                }
            }

            linkProps?.routes?.forEach { route ->
                val gw = route.gateway
                if (gw != null && !gw.isAnyLocalAddress && route.isDefaultRoute) {
                    gateway = gw.hostAddress
                }
            }

            linkProps?.dnsServers?.forEach { dns ->
                dns.hostAddress?.let { dnsList.add(it) }
            }
        } catch (_: Throwable) {}

        // Enumerate network interfaces
        val ifItems = mutableListOf<NetworkInterfaceItem>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            if (interfaces != null) {
                while (interfaces.hasMoreElements()) {
                    val intf = interfaces.nextElement()
                    val name = intf.name
                    val isLoop = try { intf.isLoopback } catch (_: Throwable) { false }
                    val isUp = try { intf.isUp } catch (_: Throwable) { true }

                    val ifType = when {
                        name.startsWith("wlan") || name.startsWith("swlan") || name.startsWith("wl") -> NetworkType.WIFI
                        name.startsWith("rmnet") || name.startsWith("ccmni") || name.startsWith("pdp") || name.startsWith("wwan") -> NetworkType.CELLULAR
                        name.startsWith("eth") || name.startsWith("en") -> NetworkType.ETHERNET
                        name.startsWith("tun") || name.startsWith("tap") || name.startsWith("ipsec") || name.startsWith("ppp") -> NetworkType.VPN
                        isLoop -> NetworkType.OFFLINE
                        else -> NetworkType.OFFLINE
                    }

                    val isVirt = isVirtualIf(name)
                    val isActiveDef = name == activeIfName

                    val ips = mutableListOf<String>()
                    try {
                        val addrs = intf.inetAddresses
                        while (addrs.hasMoreElements()) {
                            val a = addrs.nextElement()
                            if (!a.isLoopbackAddress) {
                                a.hostAddress?.let { ips.add(it.substringBefore("%")) }
                            }
                        }
                    } catch (_: Throwable) {}

                    val dispName = when (ifType) {
                        NetworkType.WIFI -> "Wi-Fi ($name)"
                        NetworkType.CELLULAR -> "Cellular ($name)"
                        NetworkType.ETHERNET -> "Ethernet ($name)"
                        NetworkType.VPN -> "VPN Tunnel ($name)"
                        else -> if (isLoop) "Loopback ($name)" else "Interface ($name)"
                    }

                    ifItems.add(
                        NetworkInterfaceItem(
                            name = name,
                            displayName = dispName,
                            type = ifType,
                            isUp = isUp,
                            isLoopback = isLoop,
                            isVirtual = isVirt,
                            isActiveDefault = isActiveDef,
                            ipAddresses = ips,
                            mtu = try { intf.mtu } catch (_: Throwable) { 1500 }
                        )
                    )
                }
            }
        } catch (_: Throwable) {}

        val sortedInterfaces = ifItems.sortedWith(
            compareByDescending<NetworkInterfaceItem> { it.isActiveDefault }
                .thenByDescending { it.isUp && !it.isLoopback }
                .thenBy { it.isVirtual }
                .thenBy { it.name }
        )

        val isInternetReachable = isValidated || (currentPing in 0..1500)

        return CompleteNetworkState(
            networkType = networkType,
            networkName = networkName,
            connectionStatus = connectionStatus,
            isConnected = isConnected,
            isValidated = isValidated,
            isInternetReachable = isInternetReachable,
            isVpn = isVpn,
            isMetered = isMetered,
            pingMs = currentPing,
            jitterMs = _networkState.value.jitterMs,
            packetLossPercent = if (isConnected && (currentPing >= 0 || isValidated)) 0 else 100,
            linkSpeedMbps = downBandwidthMbps,
            downstreamBandwidthMbps = downBandwidthMbps,
            upstreamBandwidthMbps = upBandwidthMbps,
            signalStrengthDbm = signalDbm,
            signalLevelPercent = signalPercent,
            wifiFrequencyMhz = wifiFreq,
            wifiBand = wifiBand,
            wifiStandard = wifiStandard,
            cellularGeneration = cellularGen,
            cellularOperatorName = cellularCarrier,
            ipv4Address = ipv4Addr,
            ipv6Address = ipv6Addr,
            gatewayAddress = gateway,
            dnsServers = dnsList.distinct(),
            interfaceList = sortedInterfaces,
            timestamp = System.currentTimeMillis()
        )
    }

    private fun isVirtualIf(name: String): Boolean {
        val lower = name.lowercase(Locale.US)
        return lower.startsWith("tun") ||
                lower.startsWith("tap") ||
                lower.startsWith("p2p") ||
                lower.startsWith("dummy") ||
                lower.startsWith("lo") ||
                lower.startsWith("sit") ||
                lower.startsWith("ipsec") ||
                lower.startsWith("ifb") ||
                lower.startsWith("ppp") ||
                lower.startsWith("vbox") ||
                lower.startsWith("swlan") ||
                lower.contains("vpn")
    }

    companion object {
        @Volatile
        private var INSTANCE: NetworkStateManager? = null

        fun getInstance(context: Context): NetworkStateManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NetworkStateManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
