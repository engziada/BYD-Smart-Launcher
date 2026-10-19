# Implementation backlog

**Target:** BYD Song Plus Flagship 2025, OS 2602, landscape 15-inch screen. One APK. **No phone/Android Auto integration in v1.**

## M1: Native transport foundations (IN PROGRESS)
- [x] Flutter landscape shell and tab architecture (source only)
- [x] Flutter <-> Kotlin read-only channel contract
- [x] Missing-value / demo separation
- [x] Independent JVM range and camera safety policies + 16 smoke checks
- [x] Build Android/Flutter diagnostic APK and run Flutter tests in CI
- [ ] Match firmware permissions/capabilities with stationary car diagnostics
- [x] Port permitted BYDMate v4.0.1 RSA/TLS ADB handshake, explicit connect/disconnect and read-only probes
- [x] Add opt-in firmware identity + autoservice presence probe, hash (not expose) build fingerprint
- [x] Port BYDMate firmware FID catalog text parser and ambiguity rejection tests (no helper ingestion yet)
- [x] One-shot read-only firmware FID reflection and SOC/odometer catalog-address probes, with OEM matching observations (not a continuous live stream)
- [x] Versioned APK filename in CI and version bump to 0.1.1+2
- [x] Implement manual charging FID discovery and charge-gun/BMS-state **candidate** sampling, default-off for all automatic events
- [ ] Validate charging-state candidate enums against the Song Plus OEM display, at disconnected, charging and finished states
- [ ] Port helper bootstrap, firmware-resolved FID probes and reconnect monitoring
- [ ] Confirm fresh install, pairing and reboot recovery work with no BYDMate installed

## M2: Blind spot
- [ ] Port BYDMate camera controller, preferences, FID mapping and display overlays
- [ ] Ensure factory reverse and 360 view preempt overlays
- [ ] Unit tests for left/right, hazards, stale data, speed threshold
- [ ] Parked acceptance with indicators; then controlled driving test

## M3: Real range and consumption
- [x] Implement opt-in read-only fallback BMS/odometer candidate sampling (firmware NOT verified)
- [ ] Integrate **verified** BMS/odometer addresses, autonomous tracking and persisted trip measurements
- [ ] Port BYDMate range calculator + consumption smoothing
- [x] Test float sentinels, malformed parcels, counter resets/jumps and short windows
- [ ] Test live firmware data quality and SOC confirmation on Song Plus
- [ ] Validate on-car with manual trip comparison

## M4: Automations
- [x] Implement a pure, verified-only trigger evaluator (SOC threshold / power edges, cooldown, once-per-trip), native tests
- [x] Create local notification-rule editor and versioned rule definitions with default-off master enable and per-rule toggles
- [ ] Port full durable rule journal, verified-input dispatcher, conditions, migrations and audit trail
- [ ] Implement non-actuating rules first: notifications
- [ ] Verify deduplication, once-per-trip, restart and user kill switch
- [ ] Controlled gating before any comfort writes

## M5: Telegram
- [x] Add on-device bot settings with Android Keystore AES-GCM encryption and explicit HTTPS test
- [x] Port BYDMate's pure power-off transition/deduplication policy and JVM tests (no live events)
- [x] Add single-message persisted diagnostic-test outbox, bounded manual retry, duplicate-request collapse and queue clearing
- [ ] Add helper-driven power on/off reports, durable event outbox, automatic retry and verified event deduplication
- [ ] Separate OEM-confirmed charging completion from mere 100% SOC / plug-connected state before any automated Telegram alert
- [ ] Validate offline/retry/reboot/power cycle end-to-end
- [ ] Never log tokens, chat identifiers or private messages

## M6: Comfort-only vehicle control
- [ ] Port firmware-aware BYDMate vehicle API with STRICT allowlist
- [ ] Verify vehicle parked, operation safe, and readback of resulting state
- [ ] Confirm actions before dispatch; diagnostic log without secrets
- [ ] Keep safety and drive/charging commands disabled pending explicit separate review

## M7: Release
- [ ] Verify single APK, cold start, resource limits and no other-app dependencies
- [ ] Device test on BYD; separate test profile and rollback
- [ ] Verify no confidential files in repository and licensing notices
- [ ] Sign release with private signing key outside GitHub

Source of implementation reference: https://github.com/AndyShaman/BYDMate/tree/v4.0.1
