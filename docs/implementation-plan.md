# Implementation backlog

**Target:** BYD Song Plus Flagship 2025, OS 2602, landscape 15-inch screen. One APK. **No phone/Android Auto integration in v1.**

## M1: Native transport foundations (IN PROGRESS)
- [x] Flutter landscape shell and tab architecture (source only)
- [x] Flutter <-> Kotlin read-only channel contract
- [x] Missing-value / demo separation
- [x] Independent JVM range and camera safety policies + 16 smoke checks
- [ ] Build Android/Flutter APK and run Flutter tests in equipped environment
- [ ] Match firmware permissions/capabilities with stationary car diagnostics
- [ ] Port permitted BYDMate v4.0.1 ADB auth, reconnect, helper bootstrap
- [ ] Confirm fresh install, pairing and reboot recovery work with no BYDMate installed

## M2: Blind spot
- [ ] Port BYDMate camera controller, preferences, FID mapping and display overlays
- [ ] Ensure factory reverse and 360 view preempt overlays
- [ ] Unit tests for left/right, hazards, stale data, speed threshold
- [ ] Parked acceptance with indicators; then controlled driving test

## M3: Real range and consumption
- [ ] Integrate BMS/odometer readings and persisted trip measurements
- [ ] Port BYDMate range calculator + consumption smoothing
- [ ] Test unreliable, negative, stale and discontinuous readings
- [ ] Validate on-car with manual trip comparison

## M4: Automations
- [ ] Model WHEN / IF / THEN local rules, migrations, journal and safeguards
- [ ] Implement non-actuating rules first: notifications
- [ ] Verify deduplication, once-per-trip, restart and user kill switch
- [ ] Controlled gating before any comfort writes

## M5: Telegram
- [ ] Add on-device bot settings with protected credential storage
- [ ] Port report, durable queue, deduplication and power-off helper protocol
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
