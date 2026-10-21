# Motion FID (speed + gearbox) research — v0.3.1+5 parked test

**Purpose:** move P1/P2 toward validated real vehicle telemetry by collecting one *on-demand, GET-only* speed and gearbox observation from the owner's DiLink 5.0. **This is a raw numeric diagnostic**, not a speedometer, a parked interlock, a live event subscription or evidence of any vehicle write permission.

## Source / firmware mapping
- [BYDMate v4.0.1 FidMap.kt](https://github.com/AndyShaman/BYDMate/blob/v4.0.1/app/src/main/kotlin/com/bydmate/app/data/nativestack/FidMap.kt) models `Speed.SPEED_AUTO_SPEED` as **autoservice GET 7** (float) on device SPEED (commonly 1013), and `Gearbox.GEARBOX_AUTO_MODE_TYPE` as **GET 5** (int) on device GEARBOX (commonly 1011). This source was validated on another BYD firmware, not necessarily Song Plus 2025.
- **Never use fixed speed/gear FID addresses** from BYDMate Leopard 3 or the Song Plus fixture: the app resolves the FIDs from `android.hardware.bydauto.BYDAutoFeatureIds` and the devices from `BYDAutoConstants` in *this* firmware. It requires exact previously owner-observed **SOC/odometer** device+FID identities, BYD manufacturer/model and SDK 32, plus SPEED=1013 and GEARBOX=1011.
- Native diagnostic performs two GET transactions. The app checks the raw float is finite and within 0..300 and raw gear int 0..30, **without asserting what the units or enum values mean**. A value 0 or gear code 1 does **not** prove vehicle stationary or P. Invalid/sentinel values become unavailable.
- No generic shell, no permission changes, no automations, no controls, no reverse-engineering of Kinex proprietary `libkxcore.so`.

## Parked acceptance checklist
1. **Keep the factory BYD Home/AVM running. Do not make this APK your default Home.**
2. Install v0.3.1+5 test build. On Settings tap Connect ADB if necessary and then **Probe raw speed & gear (GET-only)** with the car safely parked and stock controls untouched. You can also find **Probe raw speed & gear once** on Status when the five-second SOC stream is off.
3. Note the raw values reported, the OEM displayed speed and gear, and whether the vehicle is parked at that instant. Do not drive to gather calibration data during this early test.
4. Export Settings → **Export sanitized diagnostics JSON**. It will use schema **5**, with `motionCandidate` (raw numbers / timestamp / `speedUnitsVerified=false` / `gearEnumVerified=false` / `stationaryStateVerified=false` / `vehicleWriteAuthorized=false`). Firmware FID candidate addresses can be in `fidCatalogCandidate`.
5. If result is `motion_fids_not_present` or `motion_firmware_unverified`, the feature safely refuses; no fallback to another model's FIDs. Bring the sanitized JSON to this conversation privately rather than public GitHub.
6. **P3 control remains OFF**, regardless of result. Before any control, separately prove independent speed/gear meaning across multiple controlled OEM comparisons, fresh source, timeout handling, firmware write allowlist, per-action owner approval and readback.

Known limitation: single raw sample cannot establish calibration, motion direction, gear-letter meanings, or whether any write is safe. Persistent helper and verified live speed/gear are not included in v0.3.1+5.

Related: [P1 API register](P1_SUPPORTED_APIS.md), [binary manifest findings](parent-apk-manifest-evidence.md), [native SOC/odometer stream](p2-native-stream-acceptance.md).
