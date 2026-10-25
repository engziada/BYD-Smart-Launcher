# BYD Smart Launcher

Landscape-first Android launcher for a **BYD Song Plus Flagship SmartDrive 2025** head unit.

## Current status
**v0.3.3+7 — Technical Preview, not an accepted functional MVP.** The app has an explicit on-device ADB RSA/TLS connection, read-only SOC/odometer candidate sampling, firmware FID discovery, and a **native foreground 5-second candidate event stream** with explicit Start/Stop, strict firmware-address matching, stale-data suppression, **manual raw speed/gear GET-only probes**, idempotent ADB session reuse, one bounded foreground reconnect after unexpected ADB loss, first-stop-reason retention and schema-6 sanitized diagnostics. The Status page also features an **original Flutter-drawn vehicle with a manual green charging-cable animation**; this is strictly labelled a DESIGN PREVIEW and is never activated automatically from unverified charging FIDs. The owner confirmed some SOC/odometer readings against the stock BYD interface; this does **not** validate a live telemetry stream, native helper, vehicle writes, real range, OEM camera pipeline or Telegram automatic events. Unavailable values are not fabricated.

## Current implementation priorities (owner revised 2026-10-21)
1. **Recheck parent-project references, SDKs, APIs and ADB commands** — BYDMate and Kinex. Record exactly which source and version supports each API, and which capabilities are verified for the owner's DiLink 5.0 OS 2602.
2. **Live telemetry** — robust native helper/read-only event stream and firmware-specific validation.
3. **Vehicle control** — only firmware-verified **comfort controls** with explicit operator approval, fresh parked/gear/speed gates, independent readback and fail-closed safety checks.
4. **Real range** — independently validated BMS consumption and trip-history-based estimate.
5. **Blind spot** — left/right camera integration triggered by indicators; OEM reverse/360 overrides all custom camera surfaces.
6. **Telegram** — manual/approved real status and reliable deduplicated verified vehicle events.

**Automations are deferred after these six priorities.** Previous Blind-Spot-first / Telegram-before-controls plans are superseded. Phone and Android Auto remain out of scope.

**Next parked on-car checks:** [ADB recovery and charging-flow visual acceptance](docs/v033-recovery-charging-visual-acceptance.md), then [Native candidate stream acceptance](docs/p2-native-stream-acceptance.md). This does not install a persistent helper or enable real vehicle controls.

**Canonical execution source:** [docs/implementation-plan.md](docs/implementation-plan.md), [parent API/SDK/ADB audit](docs/parent-project-api-adb-audit.md) and [MVP acceptance](docs/mvp-scope.md). Historical issue numbers don't represent the new priority ranking.

## Approach
- Flutter UI, Kotlin Android platform bridge, Kotlin vehicle modules.
- One APK once integrated, with no dependence on a separately installed BYDMate app.
- No proprietary Kinex code, extracted 3D models, APK libraries, or artwork are included.
- BYDMate v4.0.1 provides the licensed ADB handshake implementation; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Kinex's public `kinex-dist` repo provides release/install guidance, **not confirmed source code or an SDK**. Ported source must preserve its [PolyForm Noncommercial 1.0.0 license](https://github.com/AndyShaman/BYDMate/blob/v4.0.1/LICENSE) and required copyright notice. **Commercial reuse requires appropriate permission.**
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
