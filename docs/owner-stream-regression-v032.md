# v0.3.2+6 — Live telemetry session regression and owner evidence

## Observations (2026-10-22)

Owner screenshots around **14:47–14:48 Cairo** display **84% SOC**, **42,261.1 km** odometer, and **raw speed 0.0 / raw gearbox code 1**. The latter two values are **unverified**, not evidence of PARK, km/h units or authorization to operate any vehicle control. Another screen reports a native stream interruption due to **adb_session_changed**; the OEM media rail has no active media session, and the real range / blind spot / controls remain unavailable.

**Chronology:** the private schema-5 JSON export preceded the screenshots: **2026-10-22 11:43:43Z = 14:43:43 Cairo**, with a telemetry sample taken at 14:43:13 local time. Therefore empty motionCandidate/chargingCandidate in the earlier export, and its operator_stopped reason, do not prove that values were lost or reasons changed later. Never infer a software defect from an older report vs newer screenshots without considering timestamps. The owner's full private JSON and hashed build identifier are NOT checked in.

## Independently confirmed source defects

- MainActivity used to stop the native stream on **every** adbConnect, even when ADB was already connected.
- NativeReadOnlyStream.stop() could publish a stopped state on **initial ADB connect before any stream was started**, which a newly subscribed EventChannel could then replay.
- Flutter cleanup could replace an external first stop reason with operator_stopped.
- Every ADB Connect used to clear prior motion/charging observations even if the underlying session was reused.
- Range UI labelled the raw cumulative energy counter kWh, though unit and meaning are not verified on this firmware.

## Fixed behavior

1. The native stream survives a duplicate Connect ADB request when the actual ADB session is alive. Only a true reconnect/disconnect stops it.
2. An idle stream stays idle until the user starts it. Stop/cancel/dispose preserves the FIRST genuine terminal reason in the native export.
3. Export schema **6** includes stop time and last accepted candidate time/sequence; motion/charging observations carry a sessionValid flag. A true reconnect marks historical data invalid while retaining the timestamped observation for debugging. UI never presents historical observations as fresh.
4. Real Range does not show derived kWh/100km or kilometers from an uncalibrated energy counter. The counter is labelled RAW with unknown units.
5. No extra permissions, helper daemon, write command, unlock, remote control or camera access are enabled.

## Parked test procedure

1. Start Native read-only stream in Status. Wait for SOC/odometer updates while parked.
2. Open Settings and press Connect ADB *again while already connected*. The stream should NOT terminate. If ADB genuinely disconnects, it should stop.
3. Stop manually; the state should say stopped. Restart and confirm another stream can start without restarting the tablet.
4. While stationary, probe raw speed and gear; probe charging if desired. Then export **a NEW schema-6 diagnostics report AFTER those actions**.
5. Compare exported timestamps and the screenshot capture time; sessionValid tells whether the raw probe belongs to the current ADB session.
6. Use OEM instruments for any real decisions. Do not treat a zero raw speed + raw gear 1 as proven PARK or safe authorization for controls.

**P1/P2/P3 remain incomplete:** live full-firmware telemetry, authorized comfort writes and OEM field/gear validation still require separate on-car verification. This build is a diagnostic technical preview only.
