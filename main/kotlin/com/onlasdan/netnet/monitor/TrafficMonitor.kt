package com.onlasdan.netnet.monitor

import android.content.Context
import android.net.TrafficStats
import android.os.SystemClock
import com.onlasdan.netnet.data.SpeedSettingsRepository
import com.onlasdan.netnet.model.CompleteNetworkState
import com.onlasdan.netnet.model.SpeedPoint
import com.onlasdan.netnet.model.SpeedSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.net.NetworkInterface
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

// ============================================================================
// Concurrency & accuracy contract:
//  1. ALL sampling state (lastRxBytes/lastTxBytes/lastTimestamp/lastBasis/
//     ifaceCounters/accumulators/peaks/history) is confined to code paths
//     serialized by [samplingMutex] on the monitor coroutine (Dispatchers.
//     Default). Public entry points called from other threads (start/stop/
//     setScreenState/resetSession) only flip @Volatile flags or send to a
//     conflated wake channel. A relaunch cancel-and-JOINs the previous job
//     before touching state, so two sampling coroutines never overlap.
//  2. Speed windows use SystemClock.elapsedRealtime() (monotonic clock) —
//     immune to wall-clock adjustments (NTP jumps). Wall-clock time is only
//     used for display/history timestamps. The delta time is ALWAYS the real
//     inter-tick difference, never an assumed 1000 ms.
//  3. The tick schedule is anchored to absolute monotonic timestamps, so the
//     work performed inside a tick never accumulates drift: the real tick
//     period equals the configured interval.
//  4. VPN mode sums per-interface counters, and the set of qualifying
//     interfaces can FLAP between ticks (interface up/down, per-iface stats
//     appearing/disappearing). Deltas are therefore computed PER INTERFACE
//     by [physicalIfaceWindow] against per-interface baselines that are
//     REPLACED WHOLESALE every tick: an interface absent from a tick leaves
//     NO stale baseline behind, so when it (re)joins the set it is treated as
//     first-seen (its cumulative kernel counter is NOT one second of traffic
//     — zero delta, window re-bases), a vanished interface contributes 0,
//     and only genuine per-interface counter growth is summed. This makes
//     the measurement immune to membership flapping — the source of the
//     repeating multi-MB/s spike zigzag. A non-comparable window (basis
//     switch, counter decrease, degenerate < 250 ms window, forced reset)
//     re-bases and carries the previous speeds instead of fabricating a
//     value.
// ============================================================================

/** Minimum valid measurement window; shorter windows are duplicate/early ticks. */
private const val MIN_WINDOW_MS = 250L

/** Floor for the inter-tick delay to prevent busy-looping on misconfigured intervals. */
private const val MIN_DELAY_MS = 50L

/**
 * Adaptive idle sampling: consecutive zero-byte ticks before the sampling
 * cadence stretches (pure 0 B/s only — objective, no user setting needed).
 * At the default 1s cadence this is ~30 seconds of confirmed silence.
 */
private const val IDLE_TICKS_BEFORE_STRETCH = 30

/**
 * Battery-first background ladder. The chip in the status bar is the product;
 * per-second precision while the screen is OFF is not. Cadence stretches
 * aggressively with sustained silence so a backgrounded, idle device pays a
 * handful of counter reads per minute instead of one per second:
 *
 *   silence:      user cadence → 15s (IDLE) → 60s (DEEP) → 120s (ULTRA)
 *   any byte:     instantly back to the user cadence
 *   screen off:   30s (60s if battery low) regardless of silence
 */
internal const val IDLE_SAMPLING_MS = 15_000L
internal const val DEEP_IDLE_SAMPLING_MS = 60_000L
internal const val ULTRA_IDLE_SAMPLING_MS = 120_000L

/** Ticks of sustained 15s-cadence silence before stretching to DEEP. */
internal const val DEEP_IDLE_TICKS = 10

/** Ticks of sustained 60s-cadence silence before stretching to ULTRA. */
internal const val ULTRA_IDLE_TICKS = 10

/** Screen-off sampling cadence (background service only needs to keep the
 *  notification/widget roughly current). Doubled when the battery is low. */
internal const val SCREEN_OFF_SAMPLING_MS = 30_000L
internal const val SCREEN_OFF_BATTERY_LOW_SAMPLING_MS = 60_000L

/**
 * Converts a validated byte counter delta to bytes/second. The elapsed window
 * is the actual monotonic interval, never the configured cadence.
 */
internal fun trafficRateBytesPerSec(deltaBytes: Long, elapsedMs: Long): Long {
    if (deltaBytes <= 0L || elapsedMs <= 0L) return 0L
    return (deltaBytes / (elapsedMs / 1000.0)).toLong()
}

/**
 * Rate computed over a PAIR of consecutive windows (tick N-1 merged with N).
 *
 * WHY: kernel TrafficStats counters do not advance continuously — the kernel
 * accounts rx/tx in batches (qdisc/per-UID accounting coalesces on ~2s
 * intervals depending on SoC). A 1s poll therefore observes raw rates
 * alternating [full-batch, 0, full-batch, 0...]: byte DELTAS remain correct
 * cumulatively, but the per-tick instantaneous rate is a perfect period-2
 * sawtooth (measured on device: consecutive chart samples swinging ±0.15 of
 * full scale during an otherwise steady transfer).
 *
 * Merging each tick with its PREDECESSOR before dividing by the combined real
 * elapsed time cancels the artifact EXACTLY: (batch + 0) bytes over (1s + 1s)
 * = the true average rate, and the displayed curve becomes flat. An EMA was
 * tried first and rejected: with alpha = 0.5 its smoothing frequency equals
 * the artifact frequency, so the truncating integer EMA never converges
 * (simulated: oscillates ±50% forever) — pairwise merging removes the
 * sawtooth at the source instead of damping it.
 *
 * Latency cost: the displayed rate reflects a 2-tick (~2s) moving window,
 * which is imperceptible for a speed indicator and still 5x faster than the
 * notification's own idle-throttle cadence.
 */
internal fun pairedRateBytesPerSec(
    deltaBytesPrev: Long, elapsedMsPrev: Long,
    deltaBytesNow: Long, elapsedMsNow: Long
): Long {
    val totalBytes = deltaBytesPrev + deltaBytesNow
    val totalMs = elapsedMsPrev + elapsedMsNow
    return trafficRateBytesPerSec(totalBytes, totalMs)
}

/**
 * Interface sets are comparable only when exactly the same names are present.
 * Membership changes must re-base; a reappearing interface has no comparable
 * baseline even though its kernel counter is still cumulative.
 */
internal fun interfaceSetUnchanged(previous: Map<String, Pair<Long, Long>>, current: Map<String, Pair<Long, Long>>): Boolean {
    return previous.keys == current.keys
}

/**
 * Result of a physical-interface sampling window: the validated per-interface
 * byte deltas, whether the window is comparable at all, and the fresh
 * baselines the caller must persist for the next tick.
 */
internal class PhysicalIfaceWindow(
    val deltaRx: Long,
    val deltaTx: Long,
    val comparable: Boolean,
    val freshBaselines: Map<String, Pair<Long, Long>>
)

/**
 * Computes the VPN (physical-sum) window deltas PER INTERFACE against the
 * previous tick's baselines.
 *
 * Delta rules (per interface): genuine counter growth counts; a counter
 * decrease (interface reset) and a first appearance both count 0.
 * [comparable] requires the exact same interface SET — any membership change
 * (an interface vanishing or (re)joining) makes the window non-comparable so
 * the caller re-bases instead of computing a rate.
 *
 * [PhysicalIfaceWindow.freshBaselines] is exactly the interfaces seen this
 * tick: baselines must be REPLACED wholesale, never merged. A merged baseline
 * map keeps entries for absent interfaces, so when such an interface
 * rejoins, its baseline is stale by the whole absence and the counter growth
 * accumulated meanwhile is credited to a single ~1s window — the repeating
 * multi-MB/s spike zigzag.
 */
internal fun physicalIfaceWindow(
    prev: Map<String, Pair<Long, Long>>,
    seen: Map<String, Pair<Long, Long>>
): PhysicalIfaceWindow {
    var deltaRx = 0L
    var deltaTx = 0L
    for ((name, current) in seen) {
        val baseline = prev[name] ?: continue
        if (current.first > baseline.first) deltaRx += current.first - baseline.first
        if (current.second > baseline.second) deltaTx += current.second - baseline.second
    }
    return PhysicalIfaceWindow(
        deltaRx = deltaRx,
        deltaTx = deltaTx,
        comparable = interfaceSetUnchanged(prev, seen),
        freshBaselines = seen
    )
}

/**
 * Counter basis used by [TrafficMonitor]. Readings from different bases are
 * NOT comparable — e.g. TrafficStats totals include the VPN tun interface
 * while physical-interface sums do not — so a basis switch re-bases the
 * counters instead of computing a delta.
 */
private enum class CounterBasis { TOTAL, PHYSICAL_IFACE, RAW_HALVED, RAW_TOTAL }

/**
 * One traffic reading: the counters observed right now, plus the byte deltas
 * validated for the current window. [deltaRx]/[deltaTx] are only meaningful
 * when [comparable] is true; otherwise the caller must re-base.
 */
private class CounterReading(
    val basis: CounterBasis,
    val rxTotal: Long,
    val txTotal: Long,
    val deltaRx: Long,
    val deltaTx: Long,
    val comparable: Boolean
)

class TrafficMonitor(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.Default)
    private val settingsRepo = SpeedSettingsRepository.getInstance(context)
    private val networkStateManager = NetworkStateManager.getInstance(context)

    // Relaunch is synchronized because Android may deliver service, widget, and
    // watchdog start calls almost simultaneously after a watchdog restart. The
    // critical section only flips a job reference, so a plain monitor lock is
    // enough — no suspend mutex (calling a suspend Mutex.withLock from this
    // non-suspend entry point does not compile).
    @Volatile private var monitorJob: Job? = null
    private val monitorJobLock = Any()

    /**
     * Serializes every execution of sampleTraffic()/baseline reads. The
     * monitor loop is a single coroutine, but the out-of-band reset path (and
     * any relaunch overlap) goes through this mutex so sampling state is
     * never touched by two coroutines at once.
     */
    private val samplingMutex = Mutex()

    /**
     * Latest-wins wake signal: screen-on, interval/settings change or a reset
     * request pokes the loop so the next sample happens immediately instead of
     * after the currently scheduled delay. CONFLATED so piling signals never
     * build a queue.
     */
    private val wakeChannel = Channel<Unit>(Channel.CONFLATED)

    // ---- Sampling state: ONLY accessed under samplingMutex ----
    private var lastRxBytes: Long = 0L
    private var lastTxBytes: Long = 0L
    private var lastTimestamp: Long = 0L // monotonic (elapsedRealtime)
    private var lastBasis: CounterBasis? = null
    private var prevRxSpeed: Long = 0L
    private var prevTxSpeed: Long = 0L

    /** Previous tick's raw window (bytes + real elapsed ms), for paired-rate
     * sawtooth cancellation — see [pairedRateBytesPerSec]. */
    private var prevDeltaRx: Long = 0L
    private var prevDeltaTx: Long = 0L
    private var prevDtMs: Long = 0L

    /** Consecutive ticks with zero bytes — drives the adaptive idle ladder. */
    private var idleTickCount: Int = 0

    /** Consecutive 15s-cadence silent ticks — drives the DEEP→ULTRA stretch. */
    private var deepIdleTickCount: Int = 0

    /** True while the cadence sits at ULTRA — any traffic resets everything. */
    private var isUltraIdle: Boolean = false

    /** Per-interface previous rx/tx counters for the VPN (physical sum) basis. */
    private val ifaceCounters = HashMap<String, Pair<Long, Long>>()

    private var sessionRxAccumulator: Long = 0L
    private var sessionTxAccumulator: Long = 0L

    private var peakRxSpeed: Long = 0L
    private var peakTxSpeed: Long = 0L

    private val historyPoints = ArrayDeque<SpeedPoint>(60)

    // ---- Control flags (cross-thread safe) ----
    @Volatile private var isScreenOn: Boolean = true
    @Volatile private var currentIntervalMs: Long = 1000L
    @Volatile private var isBatteryLow: Boolean = false
    @Volatile private var appForegroundFlag: Boolean = false
    private val resetRequestCounter = AtomicLong(0L)
    @Volatile private var resetCompletedCounter = 0L

    private val _snapshot = MutableStateFlow(SpeedSnapshot())
    val snapshot: StateFlow<SpeedSnapshot> = _snapshot.asStateFlow()

    private val _history = MutableStateFlow<List<SpeedPoint>>(emptyList())
    val history: StateFlow<List<SpeedPoint>> = _history.asStateFlow()

    init {
        // Collect centralized network state from NetworkStateManager (Single Source of Truth)
        scope.launch {
            try {
                networkStateManager.networkState.collect { netState ->
                    val current = _snapshot.value
                    _snapshot.value = current.copy(
                        networkType = netState.networkType,
                        networkName = netState.networkName,
                        ipAddress = netState.ipv4Address,
                        linkSpeedMbps = netState.linkSpeedMbps,
                        pingMs = netState.pingMs
                    )
                }
            } catch (_: Throwable) {}
        }

        scope.launch {
            try {
                settingsRepo.settings.collect { settings ->
                    currentIntervalMs = settings.updateIntervalMs
                    // Apply interval / battery-saver changes promptly: wake the loop
                    // so it re-evaluates its schedule instead of waiting out the
                    // previously scheduled delay.
                    wakeChannel.trySend(Unit)
                }
            } catch (_: Throwable) {}
        }
    }

    fun start(intervalMs: Long = 1000L) {
        currentIntervalMs = intervalMs
        synchronized(monitorJobLock) {
            val job = monitorJob
            // A cancelling job still reports isActive==true (cancellation is
            // asynchronous), so an isActive-only check would send a wake to a
            // job that is on its way out and leave the monitor permanently
            // stopped — a rapid stop()->start() would silently lose the restart.
            if (job != null && job.isActive && !job.isCancelled) {
                wakeChannel.trySend(Unit)
                return
            }
            relaunchMonitorJobLocked()
        }
    }

    fun stop() {
        synchronized(monitorJobLock) {
            monitorJob?.cancel()
            // Null the reference under the lock so a start() racing this
            // stop() cannot observe the half-cancelled job and skip relaunch.
            monitorJob = null
        }
        // Drop a stale wake so a future relaunch starts on its normal schedule.
        wakeChannel.tryReceive()
    }

    /**
     * Battery Saver: notify monitor and network state manager of screen state
     * changes. Screen-ON wakes the loop for an immediate fresh sample; the
     * sampling itself still happens on the monitor coroutine — this method
     * never touches sampling state from the caller's thread.
     */
    fun setScreenState(screenOn: Boolean) {
        if (isScreenOn == screenOn) return
        isScreenOn = screenOn
        networkStateManager.setScreenState(screenOn)

        if (screenOn) {
            wakeChannel.trySend(Unit)
        }
    }

    /**
     * Android 16 (API 36) BatteryManager.BATTERY_CAPACITY_LEVEL_LOW adaptation:
     * when the system reports a low battery capacity, background sampling slows
     * down (see [computeDelayMs]) and a screen-on tick prompts the loop to
     * re-evaluate its schedule promptly.
     */
    fun setBatteryLow(low: Boolean) {
        if (isBatteryLow == low) return
        isBatteryLow = low
        wakeChannel.trySend(Unit)
    }

    /**
     * App-UI lifecycle state (Activity onStart/onStop). Gates the CHART-history
     * pass only: when the app UI is closed nobody observes the history flow, so
     * skipping the per-tick ArrayDeque append, the toList() copy and the
     * StateFlow emission saves pure waste — the speed counter, notification and
     * widget keep running untouched.
     */
    fun setAppForeground(inForeground: Boolean) {
        if (appForegroundFlag == inForeground) return
        appForegroundFlag = inForeground
        networkStateManager.setAppForeground(inForeground)
    }

    /** Read-only view of the app-foreground lifecycle flag (UI telemetry
     *  pollers use it to pause their work while nobody is looking). */
    val isAppInForeground: Boolean
        get() = appForegroundFlag

    private fun relaunchMonitorJobLocked() {
        val previousJob = monitorJob
        wakeChannel.tryReceive()
        val job = scope.launch {
            // Job.cancel() is asynchronous: a cancelled coroutine can still be
            // executing its final sampleTraffic(). Wait for it to fully finish
            // before this coroutine touches any sampling state.
            try { previousJob?.cancelAndJoin() } catch (_: Throwable) {}

            samplingMutex.withLock {
                // Establish the counter baseline ON THIS COROUTINE so that every
                // read/write of the sampling state happens serialized.
                baselineCountersLocked()
            }

            // Drift-free schedule: each tick is anchored to an absolute monotonic
            // time, so tick work time does not lengthen the period. The loop also
            // wakes early on control signals (screen-on / settings change / reset).
            var nextTickAt = SystemClock.elapsedRealtime()
            while (isActive) {
                val delayMs = computeDelayMs().coerceAtLeast(MIN_DELAY_MS)
                nextTickAt += delayMs
                var wokeEarly = false
                while (true) {
                    val remaining = nextTickAt - SystemClock.elapsedRealtime()
                    if (remaining <= 0L) break
                    if (withTimeoutOrNull(remaining) { wakeChannel.receive() } != null) {
                        wokeEarly = true
                        break
                    }
                }
                if (!isActive) break
                if (wokeEarly) nextTickAt = SystemClock.elapsedRealtime()
                samplingMutex.withLock {
                    if (!isActive) return@withLock
                    sampleTrafficLocked()
                }
            }
        }
        monitorJob = job
    }

    /**
     * Delay actually slept between ticks.
     *
     * Smart Battery Saver is deliberately NOT consulted here: its contract is to
     * disable the NON-core loops (ping probes, watchdog, daily summary) and
     * leave the core speed counter untouched — the sampling cadence must stay
     * exactly at the user's configured interval. Only screen state and a LOW
     * battery capacity (API 36) stretch the screen-off cadence.
     *
     * ADAPTIVE IDLE LADDER (battery-first): user cadence → 15s after ~30s of
     * silence → 60s after ~10 min more → 120s after ~10 min more. A silent
     * counter cannot produce new information, so the extra wake-ups are pure
     * battery waste. Any byte arriving resets the ladder and the very next
     * tick already measures the new traffic, so the reaction delay is bounded
     * by ONE current-ladder tick (≤ 120s worst case, ~15s typical).
     */
    private fun computeDelayMs(): Long {
        return when {
            !isScreenOn -> if (isBatteryLow) SCREEN_OFF_BATTERY_LOW_SAMPLING_MS else SCREEN_OFF_SAMPLING_MS
            isUltraIdle -> ULTRA_IDLE_SAMPLING_MS
            deepIdleTickCount >= DEEP_IDLE_TICKS -> DEEP_IDLE_SAMPLING_MS
            idleTickCount >= IDLE_TICKS_BEFORE_STRETCH -> IDLE_SAMPLING_MS
            else -> currentIntervalMs
        }
    }

    private fun baselineCountersLocked() {
        val netState = networkStateManager.getCompleteNetworkState()
        // A relaunch can happen long after the previous session ended; drop
        // the old per-interface baselines so the fresh baseline never measures
        // against counters from a stale session.
        ifaceCounters.clear()
        val reading = readCountersLocked(netState.isVpn)
        lastRxBytes = reading.rxTotal
        lastTxBytes = reading.txTotal
        lastTimestamp = SystemClock.elapsedRealtime()
        lastBasis = reading.basis
    }

    fun resetSession() {
        val request = resetRequestCounter.incrementAndGet()
        val job = monitorJob
        if (job != null && job.isActive && !job.isCancelled) {
            // Monitor is running: only the monitor coroutine may touch sampling
            // state, so request the reset and wake it for immediate handling.
            wakeChannel.trySend(Unit)
        } else {
            // Monitor is stopped (or finishing after cancel): run the forced-reset
            // sample on the monitor scope, serialized by the sampling mutex and
            // AFTER any still-running sampling coroutine has fully completed.
            scope.launch {
                try { job?.join() } catch (_: Throwable) {}
                samplingMutex.withLock {
                    sampleTrafficLocked()
                    resetCompletedCounter = request
                }
            }
        }
    }

    private fun sampleTrafficLocked() {
        val resetRequested = resetRequestCounter.get() > resetCompletedCounter
        if (resetRequested) resetCompletedCounter = resetRequestCounter.get()
        // Reset always re-bases: the post-reset tick cannot have a comparable
        // history, so the very next sample establishes a fresh baseline and the
        // current tick carries the previous speeds instead of a fabricated 0.
        val rebaseRequested = resetRequested
        if (resetRequested) {
            sessionRxAccumulator = 0L
            sessionTxAccumulator = 0L
            peakRxSpeed = 0L
            peakTxSpeed = 0L
            prevRxSpeed = 0L
            prevTxSpeed = 0L
            prevDeltaRx = 0L
            prevDeltaTx = 0L
            prevDtMs = 0L
            idleTickCount = 0
            deepIdleTickCount = 0
            isUltraIdle = false
            historyPoints.clear()
            _history.value = emptyList()
        }

        val netState = networkStateManager.getCompleteNetworkState()
        val nowMono = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()
        val dtMs = nowMono - lastTimestamp

        // The time window itself must be a real, non-degenerate interval.
        val windowUsable = !resetRequested && !rebaseRequested && lastTimestamp > 0L && dtMs >= MIN_WINDOW_MS

        val reading = readCountersLocked(netState.isVpn)

        if (!windowUsable || !reading.comparable) {
            // Re-base the counters and carry the previous speeds so consumers
            // (notification / widget / UI) do not flicker to a fake value.
            lastRxBytes = reading.rxTotal
            lastTxBytes = reading.txTotal
            lastTimestamp = nowMono
            lastBasis = reading.basis
            emitSnapshotLocked(netState, nowWall, reading.rxTotal, reading.txTotal, prevRxSpeed, prevTxSpeed, appendHistory = false)
            return
        }

        // Smooth only the DISPLAYED rate by merging this tick's window with the
        // previous tick's before dividing (kernel counter batching produces a
        // period-2 sawtooth on the raw instantaneous rate — see
        // [pairedRateBytesPerSec]). Byte accumulators and the usage repository
        // consume the RAW deltas, so totals and data-usage accounting stay
        // exact — only what the chart and notification show is smoothed.
        val rxSpeed = pairedRateBytesPerSec(prevDeltaRx, prevDtMs, reading.deltaRx, dtMs)
        val txSpeed = pairedRateBytesPerSec(prevDeltaTx, prevDtMs, reading.deltaTx, dtMs)
        prevDeltaRx = reading.deltaRx
        prevDeltaTx = reading.deltaTx
        prevDtMs = dtMs

        // Adaptive idle ladder: only pure silence (0 bytes BOTH directions)
        // climbs the cadence ladder; any real traffic resets EVERY rung and
        // the loop returns to the user interval on the NEXT schedule tick.
        //   0..29 silent 1s ticks  -> user cadence (notification still
        //                              refreshes via its own idle throttle)
        //   30th silent tick        -> 15s cadence
        //   10 more silent 15s ticks (~2.5 min) -> 60s cadence
        //   10 more silent 60s ticks (~10 min)  -> 120s cadence
        if (reading.deltaRx == 0L && reading.deltaTx == 0L) {
            idleTickCount++
            if (idleTickCount >= IDLE_TICKS_BEFORE_STRETCH) {
                deepIdleTickCount++
                if (deepIdleTickCount >= DEEP_IDLE_TICKS + ULTRA_IDLE_TICKS) {
                    isUltraIdle = true
                }
            }
        } else {
            idleTickCount = 0
            deepIdleTickCount = 0
            isUltraIdle = false
        }

        sessionRxAccumulator += reading.deltaRx
        sessionTxAccumulator += reading.deltaTx
        // ONE atomic call records both directions. The old split into two
        // recordUsageDelta calls held the repository lock twice per tick and
        // let the 15s flush fire between the rx-update and the tx-update,
        // persisting an inconsistent half-updated snapshot to disk.
        settingsRepo.recordUsageDelta(netState.networkType, reading.deltaRx, reading.deltaTx)

        // Peak tracks the SMOOTHED rate: a raw sawtooth peak is 2x the true
        // transfer rate and would permanently poison the session peak.
        peakRxSpeed = max(peakRxSpeed, rxSpeed)
        peakTxSpeed = max(peakTxSpeed, txSpeed)
        prevRxSpeed = rxSpeed
        prevTxSpeed = txSpeed

        lastRxBytes = reading.rxTotal
        lastTxBytes = reading.txTotal
        lastTimestamp = nowMono
        lastBasis = reading.basis

        emitSnapshotLocked(netState, nowWall, reading.rxTotal, reading.txTotal, rxSpeed, txSpeed, appendHistory = true)
    }

    /**
     * Reads the current counters and computes the validated window deltas.
     * MUST be called under [samplingMutex].
     *
     * Non-VPN: device-wide [TrafficStats] totals (kernel-monotonic).
     * VPN: sum of physical interface counters, with deltas computed
     * PER INTERFACE via [physicalIfaceWindow] against baselines that are
     * replaced wholesale each tick, so a flapping interface membership can
     * neither leave a stale baseline nor fabricate a multi-megabyte delta
     * (see the class contract, point 4).
     */
    private fun readCountersLocked(isVpn: Boolean): CounterReading {
        if (!isVpn) {
            val rx = TrafficStats.getTotalRxBytes()
            val tx = TrafficStats.getTotalTxBytes()
            if (rx != TrafficStats.UNSUPPORTED.toLong() && tx != TrafficStats.UNSUPPORTED.toLong()) {
                val comparable = lastBasis == CounterBasis.TOTAL &&
                        lastRxBytes > 0L && rx >= lastRxBytes && tx >= lastTxBytes
                return CounterReading(
                    basis = CounterBasis.TOTAL,
                    rxTotal = rx,
                    txTotal = tx,
                    deltaRx = if (comparable) rx - lastRxBytes else 0L,
                    deltaTx = if (comparable) tx - lastTxBytes else 0L,
                    comparable = comparable
                )
            }
        }

        // VPN (or totals unsupported): physical per-interface counters.
        try {
            var sumRx = 0L
            var sumTx = 0L
            var hasPhysicalStats = false
            val seen = HashMap<String, Pair<Long, Long>>()

            val interfaces = NetworkInterface.getNetworkInterfaces()
            if (interfaces != null) {
                while (interfaces.hasMoreElements()) {
                    val intf = interfaces.nextElement()
                    val name = intf.name.lowercase()

                    if (intf.isLoopback || !intf.isUp) continue
                    if (isVirtualInterface(name)) continue

                    val rx = TrafficStats.getRxBytes(intf.name)
                    val tx = TrafficStats.getTxBytes(intf.name)
                    if (rx == TrafficStats.UNSUPPORTED.toLong() || tx == TrafficStats.UNSUPPORTED.toLong()) continue

                    hasPhysicalStats = true
                    sumRx += rx
                    sumTx += tx
                    seen[name] = Pair(rx, tx)
                }
            }

            if (hasPhysicalStats) {
                // Per-interface window deltas against the previous tick's
                // baselines, computed by a single pure (unit-tested) function.
                val window = physicalIfaceWindow(ifaceCounters, seen)

                // Replace the baselines WHOLESALE — never merge. The old merge
                // kept baselines for interfaces absent this tick, so a
                // rejoining interface matched its stale baseline and the whole
                // absence-long cumulative growth was credited to one ~1s
                // window: the repeating multi-MB/s spike zigzag.
                ifaceCounters.clear()
                ifaceCounters.putAll(window.freshBaselines)
                return CounterReading(
                    basis = CounterBasis.PHYSICAL_IFACE,
                    rxTotal = sumRx,
                    txTotal = sumTx,
                    deltaRx = window.deltaRx,
                    deltaTx = window.deltaTx,
                    comparable = window.comparable && lastBasis == CounterBasis.PHYSICAL_IFACE
                )
            }
        } catch (_: Exception) {}

        val rawRx = TrafficStats.getTotalRxBytes()
        val rawTx = TrafficStats.getTotalTxBytes()
        return if (isVpn && rawRx > 0) {
            val rx = rawRx / 2
            val tx = rawTx / 2
            val comparable = lastBasis == CounterBasis.RAW_HALVED &&
                    lastRxBytes > 0L && rx >= lastRxBytes && tx >= lastTxBytes
            CounterReading(
                basis = CounterBasis.RAW_HALVED,
                rxTotal = rx,
                txTotal = tx,
                deltaRx = if (comparable) rx - lastRxBytes else 0L,
                deltaTx = if (comparable) tx - lastTxBytes else 0L,
                comparable = comparable
            )
        } else {
            val comparable = lastBasis == CounterBasis.RAW_TOTAL &&
                    lastRxBytes > 0L && rawRx >= lastRxBytes && rawTx >= lastTxBytes
            CounterReading(
                basis = CounterBasis.RAW_TOTAL,
                rxTotal = rawRx,
                txTotal = rawTx,
                deltaRx = if (comparable) rawRx - lastRxBytes else 0L,
                deltaTx = if (comparable) rawTx - lastTxBytes else 0L,
                comparable = comparable
            )
        }
    }

    private fun emitSnapshotLocked(
        netState: CompleteNetworkState,
        nowWall: Long,
        currentRx: Long,
        currentTx: Long,
        rxSpeed: Long,
        txSpeed: Long,
        appendHistory: Boolean
    ) {
        // Chart history is ONLY consumed by the in-app UI. Skip the whole pass
        // (append + toList() copy + StateFlow emission) whenever nobody can see
        // it: screen off, or the app UI is closed/backgrounded — the loop then
        // does nothing beyond the counter read + notification snapshot.
        if (appendHistory && isScreenOn && isAppInForeground) {
            val point = SpeedPoint(nowWall, rxSpeed, txSpeed)
            if (historyPoints.size >= 60) {
                historyPoints.removeFirst()
            }
            historyPoints.addLast(point)
            _history.value = historyPoints.toList()
        }

        val (todayRx, todayTx) = settingsRepo.getTodayUsage()

        _snapshot.value = SpeedSnapshot(
            downloadBytesPerSec = rxSpeed,
            uploadBytesPerSec = txSpeed,
            peakDownloadBytesPerSec = peakRxSpeed,
            peakUploadBytesPerSec = peakTxSpeed,
            sessionRxBytes = sessionRxAccumulator,
            sessionTxBytes = sessionTxAccumulator,
            todayRxBytes = todayRx,
            todayTxBytes = todayTx,
            totalDeviceRxBytes = currentRx,
            totalDeviceTxBytes = currentTx,
            networkType = netState.networkType,
            networkName = netState.networkName,
            ipAddress = netState.ipv4Address,
            linkSpeedMbps = netState.linkSpeedMbps,
            pingMs = netState.pingMs,
            timestamp = nowWall
        )
    }

    private fun isVirtualInterface(name: String): Boolean {
        return InterfaceClassifier.isVirtualInterface(name)
    }

    companion object {
        @Volatile
        private var INSTANCE: TrafficMonitor? = null

        fun getInstance(context: Context): TrafficMonitor {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TrafficMonitor(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
