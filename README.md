# BYD Smart Launcher

Landscape-first Android launcher for a **BYD Song Plus Flagship SmartDrive 2025** head unit.

## Current status
**M1 foundation in progress.** This is source code and a safe, no-vehicle-connected prototype; **not** a functioning DiLink camera/ADB/Telegram integration or a tested APK. Missing vehicle values display as unavailable instead of invented values.

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
- BYDMate v4.0.1 is an **implementation reference**. Ported source must preserve its [PolyForm Noncommercial 1.0.0 license](https://github.com/AndyShaman/BYDMate/blob/v4.0.1/LICENSE) and required copyright notice. **Commercial reuse requires appropriate permission.**
- This repository is **currently public**. Never add Telegram tokens, credentials, vehicle identifiers, or diagnostic dumps containing personal data.

## Build
Install Flutter, Android SDK, JDK 17 and use `flutter pub get && flutter test && flutter build apk --debug`. The Android wrapper may need generation on a new host via `flutter create --platforms=android --project-name byd_smart_launcher --org com.ziada .`; review any generated changes and preserve the project's Kotlin files. The independent native policy tests run with `bash scripts/test-native-core.sh` (requires `kotlinc`).

## Safety / acceptance
No vehicle writes exposed until the car/firmware is fingerprinted, allowlist verified, stationary gating passed, and explicit per-action confirmation tested. OEM reversing and 360-degree camera always override aftermarket overlays. Test on a stationary, secured car first.

See `docs/implementation-plan.md` and `docs/phase-evidence.md`.
