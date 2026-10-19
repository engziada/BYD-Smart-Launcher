# BYD Smart Launcher

Landscape-first Android launcher for a **BYD Song Plus Flagship SmartDrive 2025** head unit.

## Current status
**M1 native transport in progress.** The app has opt-in BYDMate-derived ADB RSA/TLS transport and diagnostic read-only SOC, cumulative-energy and odometer **candidate probes**. These fallback FIDs and mileage scale have **not** been validated on the Song Plus head unit. This is source code and a safe, no-vehicle-connected prototype; **not** a functioning DiLink camera/ADB/Telegram integration or a tested APK. Missing vehicle values display as unavailable instead of invented values.

## Priorities
1. BYDMate-inspired turn-signal blind-spot camera integration (factory camera takes priority)
2. Local automation rules (triggers -> conditions -> gated actions)
3. Telegram bot reporting on vehicle on/off (secrets never committed)
4. Real range and consumption from verified vehicle data
5. Verified comfort-only vehicle controls
6. On-device ADB and embedded helper, no second app required (initial permission setup may still be needed)

Phone integration and Android Auto are out of scope for this phase.

## Approach
- Flutter UI, Kotlin Android platform bridge, Kotlin vehicle modules.
- One APK once integrated, with no dependence on a separately installed BYDMate app.
- No proprietary Kinex code, extracted 3D models, APK libraries, or artwork are included.
- BYDMate v4.0.1 provides the licensed ADB handshake implementation; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Ported source must preserve its [PolyForm Noncommercial 1.0.0 license](https://github.com/AndyShaman/BYDMate/blob/v4.0.1/LICENSE) and required copyright notice. **Commercial reuse requires appropriate permission.**
- This repository is **currently public**. Never add Telegram tokens, credentials, vehicle identifiers, or diagnostic dumps containing personal data.

## Build
Install Flutter, Android SDK, JDK 17 and use `flutter pub get && flutter test && flutter build apk --debug`. The Android wrapper may need generation on a new host via `flutter create --platforms=android --project-name byd_smart_launcher --org com.ziada .`; review any generated changes and preserve the project's Kotlin files. The independent native policy tests run with `bash scripts/test-native-core.sh` (requires `kotlinc`).

## Range diagnostic sample (NOT for driving decisions)

In the **Real Range** tab, enter the verified usable battery capacity in kWh. While safely parked, use **Sample vehicle counters** at the start and end of a trip (the app process must stay alive between samples). The app calculates a *candidate* rolling kWh/100km after at least 1 km and a tentative range from SOC × usable capacity / consumption, provided all inputs are plausible.

**Important:** BYDMate's reference odometer FID uses 0.1 km on Leopard 3, while the official Song Plus catalog may use a **different FID and unit scale**. Until both addresses and scale are verified against the OEM cluster and persistent trip logging is added, candidate numbers remain unverified and will NEVER be promoted to live vehicle telemetry.

## Automation status

A **side-effect-free verified-only trigger evaluator** supports SOC threshold edges, power on/off edges, once-per-trip rules, cooldown, and stale/unverified suppression. A Flutter rule editor persists notification-only drafts on device, with a global default-OFF master switch and per-rule toggles. **Even when toggled on, these rules do not dispatch** in the current APK. It is not connected to the unverified ADB FIDs or the Telegram sender. Helper-derived power state, rule journal, background dispatcher and actual automatic notifications still require development and on-car acceptance. The pure BYDMate power-off deduplication policy is included separately, without a live event source.

## Telegram diagnostics

Telegram bot token/chat ID are stored with Android Keystore AES-GCM (app backups disabled). A test message is persisted locally before HTTPS send. A failed test remains in a bounded, deduplicated outbox, with **manual** retry and queue clearing; automatic retry is not implemented. The event is a fixed TEST type only. Never share a bot token, even in issue screenshots.

## Firmware read-only discovery

**Settings → Probe firmware identity** reads fixed Android `getprop` fields and `service check autoservice`, hashes rather than exposes the full firmware fingerprint, and always reports `compatibleAndVerified=false`. This does not unlock any camera or vehicle write capability.

## Safety / acceptance
No vehicle writes exposed until the car/firmware is fingerprinted, allowlist verified, stationary gating passed, and explicit per-action confirmation tested. OEM reversing and 360-degree camera always override aftermarket overlays. Test on a stationary, secured car first.

See `docs/implementation-plan.md` and `docs/phase-evidence.md`.
