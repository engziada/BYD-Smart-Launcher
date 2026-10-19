# MVP implementation contract — user-visible functional slices

**Target:** imported BYD Song Plus Flagship 2025, DiLink 5.0, Android 12 / SDK 32. A *companion launcher interface* in one APK; do NOT replace BYD OEM HOME while factory camera precedence is unverified.

## Current acceptance evidence

- Owner verified an SOC candidate at **35%** and again at **100%** during a DC charging session against the BYD OEM display.
- Owner also verified a parked odometer value near 41,936 km. Dynamic firmware reflection returned the exact same SOC and mileage FIDs as the fallback reads, and raw odometer scale suggested 0.1 km per tick.
- New owner JSON schema-3 export (2026-10-19 16:26:54 UTC): ADB connected; candidate **SOC 89% / odometer 42,010.6 km**; firmware discovery reading **91% / raw odometer 420,031**, sampled at separate times. Do NOT compare as simultaneous measurements; the diagnostic export stores each section's last sample. Energy counter **7145.9** remains semantically unverified. The owner has not independently confirmed these newest figures against OEM.
- Charging candidate: gun raw **1** maps to "none" according to BYDMate hypotheses; BMS raw **15** is not documented in our limited classifier. **Classify charging as unknown**, not completed. No completion alert or charger operation.
- `transport.vehicleTelemetry=not_implemented`, `helper=not_installed` and `writeEnabled=false`. ADB transport is not the same as a verified continuous CAN/BMS stream.

## v0.2.0+3 — smallest useful dashboard preview (this PR)

- **On Status**, show candidate battery SOC (%) and odometer (km) in large tiles; do not pretend Real Range is available. Demo mode must remain visibly synthetic.
- Add explicit **Start read-only dashboard**, **Stop updates**, **Read once**, and manual **Charging diagnostic once** actions. After user permission / successful ADB pairing, sample the same bounded fixed read-only FIDs every **15 seconds** *while the Flutter UI is foregrounded*. No default-on polling.
- Stop timer on Stop, app pause, demo-mode change or ADB disconnect. Stop after invalid/missing readings; clear candidate figures rather than retaining them as current. Last sample time and stopped status shown. No operating-system service or wake lock.
- Preserve source uncertainty: measured numbers on screen are **candidate diagnostics**, never `VehicleSnapshot.connected=true` or a permission gate for automated events. Background notifications, actual camera overlays, power/charge write commands stay off.
- CI acceptance: Flutter analyze and tests, Android unit tests, APK debug build and signature/package/SHA checks; deterministic widget tests confirm opt-in, 15-second polling, no polling after Stop and after lifecycle pause. APK name includes version/build/commit. On-car validation still required before making a verified-live claim.

## Next bounded MVP slices (separately gated)

1. **Reliable verified read-only stream:** auto-reconnect behavior, bounded stale data, empirically validated ADB/helper longevity and at least two OEM confirmation checks after a real distance change; no safety-dependent actuation.
2. **Real range:** identify physical meaning of cumulative energy counter on the user's specific firmware or use a BMS-confirmed consumption source; reject charging intervals, counter resets and implausible values before showing an estimated range.
3. **Telegram utility:** first manual send of fresh *labelled candidate* status with explicit user tap and encrypted local token. Next, audited/persisted notification-only triggers with independently verified ignition/charging signals and deduplication; no messages from unverified BMS code 15.
4. **Blind spot integration:** only after OEM camera priority and side-indicator FIDs are validated; manual parked checks first. Vehicle controls come much later after separate firmware-specific parked/readback authorization.

**MVP definition:** A visible, useful, read-only vehicle dashboard is a valid first functional preview. **Full original feature-set parity is NOT the MVP** and is not complete at this stage.
