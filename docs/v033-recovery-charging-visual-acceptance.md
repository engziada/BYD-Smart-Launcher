# v0.3.3+7 — Read-only ADB recovery and original energy-flow visualization

**Scope:** Source-implemented Flutter/Kotlin read-only launcher technical preview for the owner's BYD Song Plus / DiLink 5.0. This update does **not** add verified charging-state telemetry, BYD controls, a native persistent helper, automatic Telegram notifications, OEM camera access or any vehicle writes.

## ADB stream resilience

1. Every manual **Start native read-only stream** opens a new Flutter event subscription. The Android EventChannel first replays its previous status, which may be `stopped` from an old session. The UI now **ignores an initial unarmed `stopped`** until that session's `initializing` or `candidate` arrives. This prevents an obsolete stop event from prematurely terminating a fresh Start.
2. If **an already-started stream unexpectedly loses the actual ADB transport** (`adb_disconnected` or `adb_session_changed`), and the app is still foregrounded, the UI **clears SOC and mileage immediately**, waits 2 seconds, and attempts **at most one** recovery. It invokes the existing ADB handshake and **revalidates firmware identity and FIDs** before displaying new values.
3. A second loss is terminal until a fresh explicit Start. Invalid value, stale-data failure, missing catalog, disallowed firmware, missing permissions, EventChannel error or other exceptions **do not** trigger endless retries.
4. **Operator Disconnect**, explicit Stop, Demo change, app background and Activity stop **never** trigger automatic recovery. The background policy remains strict; there is no head-unit wake lock or hidden polling daemon.
5. The previously implemented native `AdbSessionChangePolicy` still **reuses an already connected ADB session** and preserves first-stop diagnostic reasons; there is no generic shell API or vehicle write path.

### Reproduce while parked

- Start the read-only native stream, confirm timestamp progression at ~5-second intervals, then manually tap **Stop updates**; values disappear and no reconnect occurs.
- Tap **Start** again; it must not abort simply because the previous listener's last event was `stopped`.
- Tap **Connect ADB** with an already connected session while the stream is active; it should **reuse** the connection rather than interrupt sampling.
- In a safely parked environment, where permissions allow, observe an **unexpected** link loss: app clears all candidate values, attempts at most one foreground recovery, and either resumes fresh samples after the firmware/FID gate or stays stopped with a clear reason. **Do not simulate failure by operating the vehicle or disabling its safety systems.**
- Switching to another app or pressing **Disconnect ADB** must stop immediately with **no** reconnect.
- Export sanitized Diagnostics JSON only after tests; it contains stream state, reason and timestamps rather than authorization to control the car. User report is kept private; do not commit raw identifiers.

## Original illustrated vehicle and green cable animation

- The Status dashboard now has a hand-drawn **vector vehicle silhouette**, charger, rear-quarter plug port, and a cable with moving green energy pulses. It uses Flutter CustomPainter only; **no Kinex APK, 3D model, photos or proprietary assets** are reused.
- The only animation trigger is the user's **Preview animated charging cable** switch. The visual explicitly says **DESIGN PREVIEW • NOT LIVE CHARGING**; with preview OFF it instead says **CHARGING STATE NOT VERIFIED**.
- The current charging probe returns `chargeStateCandidate`, unvalidated BMS raw code (observed 15) and `firmwareVerified=false`. **No code path translates that into actual charging animation.** A SOC of 100% is not charge completion. No notification or control action is emitted.
- SOC (when available) is separately labelled **candidate**, and cannot imply the depicted cable is actually connected or flowing.

### Visual acceptance

- On Status, verify the vector scene fits the BYD landscape pad and does not cover the controls.
- Toggle the design preview ON: green light packets move along the rear charging cable, with a **Design Preview** label. Toggle OFF: packets stop and the warning changes back to **charging state not verified**.
- When ADB disconnected, probe missing, BMS code unknown or charging probe says `charging_candidate`, the switch must remain OFF unless **manually** enabled.
- Backgrounding or removing the Status screen disposes/pauses the animation ticker; this drawing has no access to OEM Home, safety cameras or car settings.

## Tests and limitations

- Unit tests: unexpected vs intentional disconnect, retry-count bound, background refusal, and initial stale-stopped event.
- UI tests: design preview is explicitly toggled, and unknown FIDs cannot automatically render charging.
- Release gates: `flutter analyze`, `flutter test`, Kotlin Android JVM tests, signed debug APK and SHA-256 verification via PR CI, then merge to `main` and publish a GitHub **parking-test prerelease**.
- **P1, P2 and P3 are still open**. Production charging animations require owner-device verification of a real charge-active signal (not merely SOC or plug presence), freshness handling and verified state transitions. A polished illustration is not functional vehicle charging detection.
