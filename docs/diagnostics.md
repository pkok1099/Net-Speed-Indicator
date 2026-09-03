# Diagnostics

The Diagnostics tab and Dashboard cards surface four diagnostic tools. All
state is exposed through `MainViewModel` as StateFlows and rendered by the
cards in `ui/components/` (`NetworkInfoCard`, `DiagnosticPollingCard`,
`PingDiagnostic` / `OneTimeDiagnosticCard`, `ProcessResourceDiagnosticsCard`).

## Continuous network state (`NetworkStateManager`)

Single source of truth for connectivity, used by TrafficMonitor and the UI:

- registers a `ConnectivityManager.NetworkCallback`
  (available / capabilities / link properties / lost),
- maintains network type (Wi-Fi / Cellular / Ethernet / VPN / Offline), Wi-Fi
  SSID or carrier name, IPv4 address, link speed, gateway, DNS,
- probes latency in a background loop (interval from
  `diagnosticPollingIntervalMs`; 0 = off, default 4 s), computes **jitter**
  from the sample window, and pauses the probe loop while the screen is off
  or the app is backgrounded,
- exposes `CompleteNetworkState` / `DetailedNetworkDiagnostics` StateFlows;
  `getDiagnostics()` for one-shot reads.

## Ping ladder (`PingDiagnosticRunner`)

A staged latency diagnostic that walks outward from the device:
gateway → DNS servers → public internet endpoints, measuring socket/HTTP
latency with timeouts (1.8–2.2 s per probe). It reports per-target results and
generates **troubleshooting advice** based on where the ladder breaks (e.g.
gateway reachable but internet timing out ⇒ ISP uplink problem). Run/cancel
from the Diagnostics screen; progress streams into
`PingDiagnosticState`.

## Speed burst test (`MainViewModel.runSpeedBurstTest`)

Deliberately simple: downloads ~15 MB from
`https://speed.cloudflare.com/__down?bytes=15000000` on `Dispatchers.IO`
purely to *generate traffic*, then the user watches the live speed indicator
(notification / chart) to verify the meter. It intentionally does not compute
its own throughput number — the point is to exercise TrafficMonitor.

## One-time full diagnostic (`OneTimeDiagnosticRunner`)

A complete audit run on demand:

1. **Ping phase** — reuses the ping ladder (gateway, DNS, internet).
2. **Download benchmark** — tries a fallback ladder of endpoints
   (Cloudflare, jsDelivr, CacheFly, OVH) with proper Referer/Origin headers,
   collecting throughput stats.
3. **Upload benchmark** — `POST` to `https://speed.cloudflare.com/__up`.
4. **Synthesized report** — combines per-endpoint results into an overall
   verdict with concrete suggestions.

Progress/state streams into `OneTimeDiagnosticState`
(`PING_PHASE` → download → upload → done/failed/cancelled).

Note: these tests use raw `HttpURLConnection` — Retrofit/OkHttp were removed
as unused weight (see [building.md](building.md)).

## Process telemetry (`ProcessDiagnosticsHelper`)

A lightweight sampler (every 2 s from `MainViewModel.init`): the app's own
memory / CPU-relevant process usage plus power-management state (is the app
exempt from battery optimizations? is system power-save on?). The
Diagnostics screen offers buttons to request the battery-optimization
exemption and open the relevant settings pages.

## Stuck detector (settings, not diagnostics UI)

`isStuckDetectorEnabled` / `stuckDetectorIntervalSec` control how often a
below-threshold idle notification still refreshes, so the indicator can't
freeze showing stale numbers (see [notifications.md](notifications.md)).
