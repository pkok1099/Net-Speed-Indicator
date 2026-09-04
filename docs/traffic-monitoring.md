# Traffic monitoring engine

`monitor/TrafficMonitor.kt` is the sampling core. It reads the kernel's
cumulative traffic counters once per tick, converts counter deltas into
bytes/second, and publishes a `SpeedSnapshot` + rolling 60-point history as
StateFlows.

## Concurrency contract

All sampling state (last counters, timestamps, per-interface baselines,
accumulators, peaks, history) is confined to the single coroutine serialized
by `samplingMutex` on `Dispatchers.Default`. Cross-thread callers
(`start`, `stop`, `setScreenState`, `resetSession`) only flip `@Volatile`
flags or send to a **conflated** wake channel — they never touch sampling
state directly. A relaunch `cancelAndJoin`s the previous job before touching
anything, so two sampling coroutines never overlap.

## Clock discipline

- Speed windows use `SystemClock.elapsedRealtime()` (monotonic) — immune to
  NTP/wall-clock jumps. Wall-clock time is only used for display/history
  timestamps.
- Delta time is **always the real inter-tick difference**, never the assumed
  interval (default 1000 ms).
- The tick schedule is anchored to absolute monotonic timestamps
  (`nextTickAt += delay`), so per-tick work time does not accumulate drift.

## Counter bases

`TrafficStats` totals include the VPN `tun` interface, so when a VPN is
active the monitor switches to summing **per-interface** counters
(`NetworkInterface`), and `RAW_HALVED` / `RAW_TOTAL` bases handle further
edge cases. Deltas are only comparable when computed on the *same* basis; a
basis switch re-bases instead of computing a rate. See the `CounterBasis`
enum in the source.

## VPN / flapping-interface handling

Interface-set membership can flap between ticks (interfaces going up/down).
To stay accurate:

- Deltas are computed **per interface** against per-interface baselines.
- Baselines are **replaced wholesale every tick** (`freshBaselines`) — an
  interface absent from a tick leaves no stale baseline behind.
- A reappearing interface is treated as first-seen: its cumulative kernel
  counter is *not* credited as one second of traffic (zero delta, window
  re-bases).
- Any membership change makes the window non-comparable → re-base and carry
  the previous speeds instead of fabricating a value.

This eliminates the repeating multi-MB/s spike zigzag; it is regression-tested
in `TrafficMonitorMathTest`.

## Sawtooth cancellation (paired-rate)

**Problem:** the kernel accounts rx/tx in batches (qdisc/per-UID accounting
coalesces on ~2s intervals depending on SoC). A 1s poll observes raw rates
alternating `[full-batch, 0, full-batch, 0, ...]` — a perfect period-2
sawtooth even during a steady transfer (measured on device: chart samples
swinging ±0.15 of full scale).

**Solution:** `pairedRateBytesPerSec` merges each tick with its *predecessor*
before dividing by the combined elapsed time:

```
rate = (deltaBytes_prev + deltaBytes_now) / (elapsedMs_prev + elapsedMs_now)
```

`(batch + 0) bytes over (1s + 1s)` = the true average rate; the sawtooth is
cancelled *exactly*, not damped.

**Rejected alternative:** an EMA with alpha = 0.5 has its smoothing
frequency equal to the artifact frequency, so the truncating integer EMA
never converges (simulated: oscillates ±50% forever). Pairwise merging
removes the artifact at the source.

**Cost:** the displayed rate reflects a ~2s moving window — imperceptible
for a speed indicator.

Only the **displayed** rate is smoothed. Byte accumulators and the usage
repository consume the *raw* deltas, so session totals and data-usage
accounting stay exact.

## Adaptive sampling cadence (battery-first ladder)

`computeDelayMs()` decides the sleep between ticks:

| State | Cadence |
|---|---|
| Screen off | 30 s (60 s if battery low) |
| Screen on, sustained pure silence (~30 zero-byte ticks) | 15 s |
| Screen on, ~10 more silent 15 s ticks | 60 s |
| Screen on, ~10 more silent 60 s ticks | 120 s |
| Screen on, normal | user interval (default 1 s) |

Any byte arriving resets the whole ladder; the very next tick already
measures the new traffic, so reaction delay is bounded by one current-rung
tick (≤ 120 s worst case, ~15 s typical). Smart Battery Saver deliberately
does *not* touch the core cadence — it only disables non-core loops (ping
probes, watchdog, daily summary).

## Non-comparable windows

A window re-bases (no rate computed; previous speeds carried) when:

- the counter basis switched (e.g. VPN toggled),
- a counter decreased (interface reset),
- the window is degenerate (< 250 ms — duplicate/early tick),
- a forced reset was requested.

## Pure math functions (unit-testable)

These are `internal` top-level functions precisely so JVM tests can exercise
them without Android:

- `trafficRateBytesPerSec(deltaBytes, elapsedMs)` — validated delta → B/s
- `pairedRateBytesPerSec(prev..., now...)` — sawtooth-cancelled rate
- `interfaceSetUnchanged(prev, current)` — membership equality
- `physicalIfaceWindow(prev, seen)` — per-interface deltas + fresh baselines

They are tested in `test/.../TrafficMonitorMathTest.kt` with backtick test
names.

## What's in a SpeedSnapshot

Emitted every tick (model/NetworkModels.kt): current/peak up+down speeds,
session bytes, today's bytes, total device bytes, network type/name/IP/link
speed, ping, timestamp — plus derived `sessionTotalBytes` / `todayTotalBytes`.
