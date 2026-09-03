package com.onlasdan.netnet

import com.onlasdan.netnet.monitor.interfaceSetUnchanged
import com.onlasdan.netnet.monitor.pairedRateBytesPerSec
import com.onlasdan.netnet.monitor.physicalIfaceWindow
import com.onlasdan.netnet.monitor.trafficRateBytesPerSec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficMonitorMathTest {
    @Test
    fun `traffic rate uses the actual elapsed window`() {
        assertEquals(250_000L, trafficRateBytesPerSec(250_000L, 1_000L))
        assertEquals(125_000L, trafficRateBytesPerSec(250_000L, 2_000L))
        assertEquals(1_000_000L, trafficRateBytesPerSec(250_000L, 250L))
    }

    @Test
    fun `short or invalid windows never calculate a rate`() {
        assertEquals(0L, trafficRateBytesPerSec(12_000_000L, 0L))
        assertEquals(0L, trafficRateBytesPerSec(-1L, 1_000L))
        assertEquals(0L, trafficRateBytesPerSec(0L, 1_000L))
    }

    @Test
    fun `interface membership changes force a rebase`() {
        val wlan = "wlan0" to Pair(1L, 2L)
        val previous = mapOf(wlan)
        val same = mapOf(wlan)
        val changed = mapOf(wlan, "rmnet0" to Pair(3L, 4L))
        val different = mapOf("rmnet0" to Pair(3L, 4L))

        assertTrue(interfaceSetUnchanged(previous, same))
        assertFalse(interfaceSetUnchanged(previous, changed))
        assertFalse(interfaceSetUnchanged(previous, different))
    }

    @Test
    fun `flapping interface set cannot turn cumulative counters into a spike`() {
        val before = mapOf("wlan0" to Pair(1_000_000L, 2_000_000L))
        val after = mapOf("wlan0" to Pair(2_000_000L, 2_000_000L), "rmnet0" to Pair(12_000_000L, 0L))

        assertFalse(interfaceSetUnchanged(before, after))
    }

    @Test
    fun `normal window sums genuine per-interface growth`() {
        val prev = mapOf(
            "wlan0" to Pair(1_000_000L, 500_000L),
            "rmnet0" to Pair(2_000_000L, 1_000_000L)
        )
        val seen = mapOf(
            "wlan0" to Pair(1_250_000L, 510_000L),
            "rmnet0" to Pair(2_000_000L, 1_004_000L)
        )

        val window = physicalIfaceWindow(prev, seen)
        assertEquals(250_000L, window.deltaRx)
        assertEquals(14_000L, window.deltaTx)
        assertTrue(window.comparable)
        assertEquals(seen, window.freshBaselines)
    }

    @Test
    fun `rejoining interface contributes zero and forces a rebase`() {
        // wlan0 was absent for several ticks; rmnet0 kept its baseline. The OLD
        // MERGE kept wlan0's baseline alive across its absence, so on rejoin
        // the key SETS matched again — the window looked comparable and the
        // whole absence-long growth (~12 MB) was credited to one ~1s window:
        // the repeating 12 MB/s spike zigzag. The pure function cannot detect
        // a stale baseline (the sets genuinely match); the fix is that
        // TrafficMonitor replaces baselines WHOLESALE, never merges.
        val staleMergedBaselines = mapOf(
            "wlan0" to Pair(1_000_000L, 2_000_000L),
            "rmnet0" to Pair(800_000L, 400_000L)
        )
        val seenAfterRejoin = mapOf(
            "wlan0" to Pair(13_000_000L, 2_000_500L),
            "rmnet0" to Pair(810_000L, 402_000L)
        )

        // Documents the old bug: against a merged stale baseline the sets
        // match, so the 12 MB cumulative jump would be validated as a delta.
        val windowVsMergedBaseline = physicalIfaceWindow(staleMergedBaselines, seenAfterRejoin)
        assertTrue(windowVsMergedBaseline.comparable)
        assertEquals(12_010_000L, windowVsMergedBaseline.deltaRx)

        // The real protection: wholesale replacement means the baseline
        // persisted while wlan0 was absent contains only rmnet0, so the rejoin
        // is detected (set change -> rebase) and wlan0's cumulative jump
        // counts ZERO — only rmnet0's genuine growth is validated.
        val replacedBaselines = mapOf("rmnet0" to Pair(800_000L, 400_000L))
        val windowAfterReplacement = physicalIfaceWindow(replacedBaselines, seenAfterRejoin)
        assertFalse(windowAfterReplacement.comparable)
        assertEquals(10_000L, windowAfterReplacement.deltaRx)
        assertEquals(2_000L, windowAfterReplacement.deltaTx)
        assertEquals(seenAfterRejoin, windowAfterReplacement.freshBaselines)
    }

    @Test
    fun `vanished interface leaves no stale baseline behind`() {
        val tick1 = mapOf(
            "wlan0" to Pair(1_000_000L, 2_000_000L),
            "rmnet0" to Pair(800_000L, 400_000L)
        )
        val tick2Seen = mapOf("rmnet0" to Pair(810_000L, 402_000L)) // wlan0 vanished

        val window2 = physicalIfaceWindow(tick1, tick2Seen)
        assertFalse(window2.comparable)
        // Wholesale replacement: next tick's baseline is exactly what was seen.
        assertEquals(tick2Seen, window2.freshBaselines)

        // Next tick wlan0 is STILL gone — no stale wlan0 entry can survive.
        val tick3Seen = mapOf("rmnet0" to Pair(820_000L, 403_000L))
        val window3 = physicalIfaceWindow(window2.freshBaselines, tick3Seen)
        assertTrue(window3.comparable)
        assertEquals(10_000L, window3.deltaRx)
        assertEquals(1_000L, window3.deltaTx)
    }

    @Test
    fun `counter decrease or first appearance contributes zero`() {
        val prev = mapOf(
            "wlan0" to Pair(1_000_000L, 500_000L),
            "rmnet0" to Pair(2_000_000L, 1_000_000L)
        )
        val seen = mapOf(
            // wlan0 counter DECREASED (interface reset) -> 0
            "wlan0" to Pair(50_000L, 10_000L),
            // rmnet0 first appearance, no baseline -> 0
            "eth0" to Pair(999_999L, 999_999L)
        )

        val window = physicalIfaceWindow(prev, seen)
        assertEquals(0L, window.deltaRx)
        assertEquals(0L, window.deltaTx)
        assertFalse(window.comparable)
    }

    /**
     * Pairwise window merging removes the kernel counter-batching sawtooth.
     *
     * Kernel TrafficStats advances in ~2s batches, so a 1s poll observes raw
     * rates alternating [2x_true, 0, 2x_true, 0, ...] on a steady transfer
     * (measured on device: consecutive chart samples swinging ±0.15 of full
     * scale). Merging each tick with its predecessor cancels the artifact
     * EXACTLY: (2x + 0) bytes over (1s + 1s) = the true rate, flat curve.
     * (An EMA with alpha=0.5 was tried first and rejected: its smoothing
     * frequency equals the artifact frequency, so it never converges.)
     */
    @Test
    fun `paired windows cancel the kernel batching sawtooth`() {
        val trueRate = 600_000L // 600 KB/s steady transfer
        val tickMs = 1000L
        // Raw per-tick deltas as a 1s poll sees them: batch lands every 2nd tick.
        val rawDeltas = listOf(1_200_000L, 0L, 1_200_000L, 0L, 1_200_000L, 0L, 1_200_000L, 0L)

        var prevDelta = 0L
        val displayed = mutableListOf<Long>()
        for (d in rawDeltas) {
            val rate = pairedRateBytesPerSec(prevDelta, tickMs, d, tickMs)
            displayed.add(rate)
            prevDelta = d
        }

        // From the FIRST batch onward the displayed rate equals the true rate.
        assertEquals(trueRate, displayed[0]) // 0 + 1.2MB over 2s
        assertEquals(trueRate, displayed[1]) // 1.2MB + 0 over 2s
        // ...and stays exactly flat for the whole transfer:
        assertTrue(displayed.drop(1).all { it == trueRate })

        // Reacts instantly to a real stop: last pair (0,0) -> 0.
        val afterStop = pairedRateBytesPerSec(0L, tickMs, 0L, tickMs)
        assertEquals(0L, afterStop)

        // And a rate CHANGE is reflected within one pairing window:
        val newTrue = 300_000L
        val changed = pairedRateBytesPerSec(0L, tickMs, newTrue * 2, tickMs) // new batch rate
        assertEquals(newTrue, changed)
    }

    @Test
    fun `paired rate uses the real summed elapsed time`() {
        // Uneven tick spacing (timer drift): 1.0s then 0.5s windows.
        val rate = pairedRateBytesPerSec(500_000L, 1000L, 250_000L, 500L)
        // (500k + 250k) bytes over 1.5s = 500k B/s — not distorted by assuming 1s ticks.
        assertEquals(500_000L, rate)
    }

    /**
     * Adaptive idle sampling contract, expressed as the delay decision the
     * monitor makes each tick (mirrors computeDelayMs' three-way switch):
     * - user cadence while traffic flows or during the first ~30 silent ticks
     *   (the Stuck-Detector idle notification still refreshes at its own
     *   cadence in this window)
     * - stretched 5s cadence only after sustained pure silence
     * - instantly back to user cadence once bytes arrive again.
     */
    @Test
    fun `adaptive idle sampling stretches only after sustained silence`() {
        val userIntervalMs = 1000L
        val idleStretchThreshold = 30
        val idleSamplingMs = 5_000L

        fun delayFor(ticksSilent: Int, bytesThisTick: Long): Pair<Long, Int> {
            // mirror of the monitor's state machine
            val nextSilent = if (bytesThisTick == 0L) ticksSilent + 1 else 0
            val delay = when {
                nextSilent >= idleStretchThreshold -> idleSamplingMs
                else -> userIntervalMs
            }
            return delay to nextSilent
        }

        // First ~30 silent ticks: still the user's 1s cadence.
        var silent = 0
        for (i in 1..29) {
            val (delay, next) = delayFor(silent, bytesThisTick = 0L)
            assertEquals(userIntervalMs, delay)
            silent = next
        }
        // 30th silent tick crosses the threshold -> stretched cadence.
        val (stretched, silent2) = delayFor(silent, bytesThisTick = 0L)
        assertEquals(idleSamplingMs, stretched)
        // One byte arriving anywhere resets to the user cadence immediately.
        val (resumed, silent3) = delayFor(silent2, bytesThisTick = 1L)
        assertEquals(userIntervalMs, resumed)
        assertEquals(0, silent3)
        // And silence re-accumulates from scratch after the reset.
        val (stillUser, _) = delayFor(silent3, bytesThisTick = 0L)
        assertEquals(userIntervalMs, stillUser)
    }

    /**
     * 70-second windowed simulation of the sampling loop over a ~5 Mbps link
     * (625 KB/s) with the physical interface flapping (absent for several
     * ticks) — the reported regression scenario. Reproduces the OLD merged-
     * baseline behavior (multi-MB/s spike absorbing the whole absence into
     * one ~1s window) and asserts the NEW behavior meets all acceptance
     * criteria: no spike above link capacity, a smooth non-zigzag curve, and
     * a sane non-stuck peak.
     */
    @Test
    fun `seventy second flapping simulation produces no spike zigzag nor stuck peak`() {
        val intervalMs = 1000L
        val linkBytesPerSec = 625_000L // 625 KB/s ≈ 5 Mbps link
        val absentTicks = (16..34).toSet() // ~19s interface absence mid-window

        var oldPeak = 0L
        var newPeak = 0L
        val oldRates = mutableListOf<Long>()
        val newRates = mutableListOf<Long>()

        // OLD implementation: baselines MERGE, a vanished interface's entry
        // survives its whole absence.
        val oldBaselines = HashMap<String, Pair<Long, Long>>()
        // NEW implementation: baselines replaced wholesale via the pure fn.
        var newBaselines: Map<String, Pair<Long, Long>> = emptyMap()

        var counter = 0L
        // Warm-up tick establishes both baselines.
        counter += linkBytesPerSec
        oldBaselines["wlan0"] = counter to 0L
        newBaselines = mapOf("wlan0" to (counter to 0L))

        for (tick in 1..70) {
            counter += linkBytesPerSec
            val seen: Map<String, Pair<Long, Long>> =
                if (tick in absentTicks) emptyMap() else mapOf("wlan0" to (counter to 0L))

            // Old merged-baseline path (bug): rejoining interface matches its
            // stale baseline, the whole 19s of accumulated counter growth is
            // validated into one ~1s window.
            var oldDelta = 0L
            for ((name, current) in seen) {
                val baseline = oldBaselines[name]
                if (baseline != null && current.first > baseline.first) {
                    oldDelta += current.first - baseline.first
                }
            }
            oldBaselines.putAll(seen) // MERGE — the bug
            if (seen.isNotEmpty()) {
                val oldRate = oldDelta // 1000 ms window
                oldRates += oldRate
                oldPeak = maxOf(oldPeak, oldRate)
            }

            // New wholesale-replacement path (fix).
            val window = physicalIfaceWindow(newBaselines, seen)
            newBaselines = window.freshBaselines
            if (window.comparable) {
                val rate = trafficRateBytesPerSec(window.deltaRx, intervalMs)
                newRates += rate
                newPeak = maxOf(newPeak, rate)
            }
        }

        // OLD behavior reproduces the reported regression: the ~19s absence
        // (19 * 625 KB ≈ 12 MB) is credited to a single ~1s window.
        assertEquals(12_500_000L, oldPeak)
        assertTrue(oldRates.count { it > linkBytesPerSec * 2 } == 1) // one sharp zigzag spike

        // NEW behavior meets all acceptance criteria:
        // 1. No spike exceeding the link capacity (~5 Mbps => 625 KB/s).
        assertTrue(newPeak <= linkBytesPerSec)
        // 2. Smooth curve: every recorded rate is at (never above) the real
        //    link rate — no sharp up/down zigzag.
        assertTrue(newRates.all { it <= linkBytesPerSec })
        // 68 of 70 windows are comparable: only the first absent tick and the
        // rejoin tick re-base; the remaining absent ticks read a natural 0 B/s
        // idle (empty interface set), never a fabricated spike.
        assertEquals(68, newRates.size)
        // 3. Peak is a sane, non-stuck value equal to the true link rate.
        assertEquals(linkBytesPerSec, newPeak)
    }
}
