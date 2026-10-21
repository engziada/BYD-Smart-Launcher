# P2 — Native read-only candidate telemetry stream (v0.3.0+4)

**Status:** implemented in source; automated CI and owner-on-car acceptance must be reported separately. This is **not** a fully verified live telemetry/helper integration. It is a usable event-stream foundation for Priority 2 after the initial Priority 1 evidence review.

## User-facing difference versus v0.2.0

- Status has **Start native read-only stream / Stop updates** rather than a Flutter-side 15-second read loop. Actual sampling is owned by Kotlin via a native Android EventChannel on the **foreground Activity**, every **5 seconds**.
- Before first sampling, code performs one-shot allowlisted `BYDAutoFeatureIds`/device ID reflection through its own signed APK using existing authorized loopback ADB. It **requires the exact SOC, odometer, and device addresses observed in this owner's DiLink 5.0 report**. It declines unknown addresses instead of reusing inaccurate Song Plus/Leopard 3 assumptions.
- Once accepted, each native tick issues **two** internally constructed GET-only `service call autoservice` queries: SOC transaction 7 float and odometer transaction 5 raw int. No energy/charge/control commands. Mileage scale `0.1 km/tick` is applied ONLY to the exact owner-observed FID pair; unknown firmware fails closed.
- Each event includes `streamState` (`initializing`/`candidate`/`stale`/`stopped`), timestamp, monotonic sequence number, source, explicit `firmwareVerified=false`, `vehicleWriteAuthorized=false` and `automaticEventEligible=false`. No native helper installation, root, app-op changes or continuous background service.
- An invalid or too-old event immediately clears the displayed sample. After **three** consecutive failures the stream stops; reconnect is **never automatic**. The stream also stops on operator Stop, EventChannel listener cancellation, ADB reconnect/disconnect, Android Activity background/stop, app dispose or switching to Demo.
- Exported **schema-4** diagnostics add `nativeStreamCandidate` (state/quality only) and sanitized last accepted `telemetryCandidate` readings. Export may contain an *older* last accepted sample; compare timestamps instead of assuming the export's wall clock is the sample time.

## Parked acceptance (owner test only)

1. Install side-by-side/over the existing app using the same signing key. Do NOT make the new app the OEM Home. Park safely and keep factory reverse/360 camera reachable.
2. On **Status**, press **Start native read-only stream** and approve ADB if prompted. The first native event should be `initializing`, followed by a new SOC/mileage sample when the symbol catalog matches.
3. Compare the displayed SOC and odometer with the **stock BYD interface at the same time**. Note the two values; then let it run for at least one minute. Watch for fresh timestamps/approximately 5-second sequence increments, no freezes or disruptive ADB authorization pop-ups. Do NOT test by intentionally changing the car's drive or safety state.
4. Press Stop: numbers disappear, native stream becomes stopped, no more packets. Start once more to verify reattachment. Tap Demo mode: no car reads.
5. While parked, switch apps or let the head unit sleep. The stream must stop and **not** silently restart when foreground returns. Explicit new Start required.
6. Export **Settings → Export sanitized diagnostics JSON** *after* exercising the native stream; submit JSON privately. Verify `nativeStreamCandidate` and `telemetryCandidate` timestamps/reasons; do not post private diagnostic dumps to public GitHub.
7. If `firmware_fids_or_scale_unvalidated` or `firmware_catalog_unavailable`: leave unsupported, collect sanitized evidence and update P1 audit. Never use control writes or a different odometer scale to force a successful screen.

## Safety and known gaps

- **Only SOC and odometer** currently have owner-observed and partially OEM-checked readings. This is a 5-second *read-only candidate stream*, not a continuously verified full vehicle/ADAS feed.
- `snapshot().connected` stays `false`, `source=unavailable`; helper remains `not_installed`. There is no speed/gear/power/TPMS/indicator validated feed, automatic device re-pairing/reboot recovery, persistent daemon or control permission.
- This P2 slice **does not close P1 or P2**. Next: finish binary SDK/ADB auditing, helper auth/version/rollback review; then model-specific speed and gear GETs with OEM comparisons before any P3 control candidate.
- No `vehicleWrite` method, arbitrary ADB shell, permission change, control UI activation, automatic Telegram or automation executor was introduced.

Source guidance: [P1 SDK/API register](P1_SUPPORTED_APIS.md), [parent audit](parent-project-api-adb-audit.md), [implementation plan](implementation-plan.md).
