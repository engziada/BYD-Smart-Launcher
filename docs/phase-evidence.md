# Evidence register

| Milestone | Status | Evidence | Pending |
|---|---|---|---|
| M1 Flutter launcher shell | Validated prototype | Flutter analyze/test and Android debug compile succeeded on commit `207ca2a` ([CI](https://github.com/engziada/BYD-Smart-Launcher/actions/runs/39171979998)) | No vehicle integration at that stage |
| M1 ADB handshake | Ported; compilation tested, car test pending | BYDMate v4.0.1 ADB protocol/keypair/TLS classes under third-party license; user-initiated 127.0.0.1:5555 connect/disconnect and read-only version/SOC probes; test source | Confirm Android build and actual ADB pairing on DiLink OS 2602 |
| M1 helper and live telemetry | Not implemented | Native code explicitly returns `source=unavailable`; helper remains uninstalled | Port helper, firmware catalog, FID discovery, BYD-only data bridge |
| M2 blind spot | Decision logic ported; camera NOT implemented | BYDMate v4.0.1 pure decision and backoff sources, Android JVM tests; no camera surface access | Port actual AVM capture/displays after M1 helper and accept OEM override on-car |
| M3 real range | Candidate read-only prototype | On-demand FID reads for SOC, cumulative energy and odometer; bounded 25-km consumption trend; operator-entered capacity and native tests | FID catalog resolution and on-car verification, persistent history, calibrated range, automated tracking |
| M4 automations | Rules editor and native persistence, NO dispatch | Verified-only SOC threshold/power edge evaluator; local CRUD/toggles and default-off master switch; native/Flutter tests in latest CI | Native helper, live verified vehicle events, actual rule execution, journal, restart recovery |
| M5 Telegram | Manual test queue and encrypted bot settings, NO auto events | Encrypted credentials; persisted single TEST message, manual bounded retry and queue control; BYDMate power-off deduplication policy; latest CI pending | Verified vehicle power events, automatic outbox processing, persistence across full ignition shutdown, no-device tests |
| M6 vehicle control | Not implemented | Writes unavailable | Strict per-firmware allowlist, stationary confirmation, readback |

## Firmware identity and trust boundary
- User-invoked read-only ADB commands: `getprop ro.product.model`, `getprop ro.product.manufacturer`, `getprop ro.build.version.sdk`, `getprop ro.build.fingerprint`, `service check autoservice`.
- Build fingerprint returned as a short SHA-256 digest only; never used as permission to issue writes.
- `autoservicePresent` means the Binder service is detected, **not** that a vehicle model or any FID is validated.
- The automation rules can be toggled on as saved preferences while `executionConnected=false`. Nothing runs, and no Telegram auto-event is sent.
- The diagnostic Telegram outbox is an *at-least-once* local test, manual retry only. It can duplicate after a process crash between Telegram acceptance and local acknowledgement. It is **not yet** a powered-off sender.

## Notes
- `adb.connected` confirms transport only; it **never** marks vehicle telematics as live.
- SOC probe uses BYDMate's Leopard 3 *fallback* address and is marked UNVERIFIED for Song Plus OS 2602 until device-catalog validation.
- A prior Android CI build on `7ce2850` failed during BouncyCastle duplicate META-INF packaging; new exclusion config applied.
- No production APK or on-car acceptance completed. No keys/tokens/vehicle ID uploaded.

## 2026-10-19 on-car diagnostic feedback and UI patch
- User reported ADB connected after permission; Android 12 / SDK 32, BYD Auto, autoservice present; firmware compatible/verified remains false and helper not installed.
- User compared SOC candidate (46%) and odometer candidate with OEM display; both matched in that sample. Cumulative energy candidate read 7126.2 kWh; meaning, unit and FID still require verification.
- Status was empty and top bar displayed Vehicle disconnected despite ADB connectivity. UI now distinguishes ADB transport from unverified vehicle telemetry and exposes parked-only explicit candidate sampling in Status. This does NOT enable verified live snapshots.
- Light theme, helper installation, blind-spot camera, rule dispatch, automatic Telegram events and controls remain pending.
- On-device re-test and Flutter analyze/test/build are pending; no APK acceptance claimed.

## 2026-10-19 parked-car re-test: owner-confirmed SOC and odometer
- Diagnostic JSON exported at 2026-10-19 14:06:02 UTC reported ADB `connected`, Android 12 / SDK 32, model `DiLink5.0 For BYD AUTO`, `autoservicePresent=true`, `helper=not_installed`, `compatibleAndVerified=false`, `vehicleTelemetry=not_implemented`, and `writeEnabled=false`.
- Read-only fallback sample: `socCandidatePercent=35`, `odometerCandidateKm=41936.1`, `energyCandidateKwh=7134.80029296875`, `sampleCount=1`, `windowDistanceKm=0`, `firmwareVerified=false`, `reason=requires_firmware_validation`.
- **Owner directly confirmed that 35% SOC and approximately 41,936 km odometer matched the factory BYD display at the time of that sample.** This validates two readings in that single parked observation only; the odometer fallback scaling assumption still references Leopard 3 and must be verified more broadly.
- Energy counter meaning, units, scale, and whether it can be used for consumption remain **unverified**. A single sample at zero window distance cannot yield a real-range estimate.
- CI run [#53](https://github.com/engziada/BYD-Smart-Launcher/actions/runs/39220577054) succeeded with Flutter analysis/tests, Android native unit tests, APK build, signing/package verification and confirmation the app does not register as the OEM HOME launcher. **Successful CI and matched stationary values do not establish real-time telemetry, automatic Telegram dispatch, functional blind-spot camera, or vehicle-control safety.**
- Next required technical gate: opt-in firmware-specific FID catalog discovery with read-only helper/probe, repeated independent comparisons to OEM display, safe restart/reconnect tests, and evidence before enabling any automated camera/rule/control behavior. No private identifiers, keys, or raw diagnostic fingerprints are committed.

## 2026-10-19 DiLink 5.0 on-car FID catalog discovery (schema 2)
- Owner-supplied export at **2026-10-19 15:10:03 UTC** reports `ADB=connected`, Android 12 / SDK 32, `autoservicePresent=true`, `helper=not_installed`, `vehicleTelemetry=not_implemented`, `writeEnabled=false`. Firmware model remains `DiLink5.0 For BYD AUTO`, and its fingerprint digest was unchanged from the earlier owner's report (digest deliberately omitted).
- **One-shot firmware reflection succeeded**: `status=catalog_candidate`, `catalogReadsOk=true`, four symbol addresses and two device constants. The discovered STATISTIC device 1014 and SOC / odometer / total electrical consumption FIDs (`1246777400`, `1246765072`, `1032871984`) **equal the existing BYDMate fallback addresses**. LIGHT device 1004 and turn-signal FID 950009900 were **discovered only**, not live-probed or validated for the Song Plus.
- Catalog SOC candidate `100%` equals fallback SOC candidate `100%` in this export, but this is **NOT two independent vehicle sources** because the discovered SOC FID matches the fallback FID. **Owner subsequently confirmed the OEM BYD dashboard showed 100% at the time of this sample, and the vehicle was on a DC fast charger.** This adds an independently confirmed *charging-context* SOC observation to the earlier owner-confirmed **35%** / **41,936.1 km** parked comparison. The owner did not specify whether 100% was shown before, during, or just at the end of the DC charging session; do not infer charge-completion events or state-machine behavior.
- Catalog odometer raw integer `419361` and fallback odometer `41936.1 km` indicate **0.1 km per tick** for this matched FID in this one stationary observation. Do not automatically extrapolate this scale to different firmware or a different catalog FID.
- Energy counter candidate `7134.89990234375` versus the preceding reported `7134.80029296875`: **meaning, unit and usage remain unverified**. The newest observation occurred in a DC fast-charging context, and the reported odometer stayed at `41936.1 km`. A ~`0.1` difference between rounded readings **cannot** identify this counter as charge energy, trip consumption, cumulative energy, or its units; avoid interpreting or feeding it into a real-range calculation. `windowDistanceKm=0` and `sampleCount=1`; no energy-per-distance consumption or real range is established.
- All `firmwareVerified`, `odometerCatalogScaleVerified`, and `vehicleWriteAuthorized` flags remain false. One-shot read-only FID resolution is a successful **diagnostic integration milestone**, not a persistent helper, event subscription, camera access, comfort-control grant or verified live telemetry.
- Regression acceptance still requires repeated OEM dashboard comparisons after a charge/discharge and at least one odometer change, firmware-specific device/catalog checking after cold start, and a deliberate reconnect/power-cycle test. Do not promote source to `live` or enable rules/vehicle writes on this evidence.

## 2026-10-19 charging-state diagnostic implementation (0.1.1+2, on-car acceptance pending)
- Added bounded, user-invoked **read-only** firmware reflection of charging device and `Charging.CHARGING_GUN_CONNECT_STATE`, `Charging.CHARGING_BATTERRY_DEVICE_STATE`, and optional `Charging.CHARGING_TYPE` symbols. If required firmware symbols are absent, **no fallback reads** are performed.
- A manual sample makes at most three read-only transaction-5 GET calls. Pure classifier reports raw values and guarded AC/DC + BMS phase **hypotheses** derived from BYDMate v4.0.1 Leopard 3 evidence, **not** verified for this Song Plus. Unknown, disconnected, paused and contradictory values cannot become completion alerts.
- The sanitized schema-3 export adds `chargingCandidate`; the UI has an explicit Settings button and prints candidate-only status. `automaticEventEligible=false`, `completionConfirmed=false`, `vehicleWriteAuthorized=false` by construction.
- **No on-car charging FID values have been collected for this version yet.** Prior owner confirmation of 100% SOC while on a DC charger does not reveal whether charging was active or completed.
- CI and parked acceptance instructions: `docs/dilink5-charging-candidate-test.md`. This diagnostic does not implement a persistent helper, event subscription, Telegram automatic send, charging-complete alarm, or vehicle control.

## 2026-10-21 — P1 source audit and P2 native candidate stream (v0.3.0+4, no on-car acceptance)
- BYDMate v4.0.1 source/FidMap and Song Plus fixture were re-examined; the fixture's SOC/odometer symbols resolve to **different values** than the owner's reflected DiLink5.0 FIDs. Kinex distribution repo is not a public source SDK. [P1 decision register](P1_SUPPORTED_APIS.md). Full SDK/manifest/helper audit and P1 acceptance still OPEN.
- New code implements [NativeReadOnlyStream](../android/app/src/main/kotlin/com/ziada/bydsmartlauncher/NativeReadOnlyStream.kt), [CandidateStreamPolicy](../android/app/src/main/kotlin/com/ziada/bydsmartlauncher/CandidateStreamPolicy.kt), runtime firmware reflection and two GET-only FID reads every five seconds while operator-initiated, foregrounded and subscribed; Android EventChannel and Flutter Status support lifecycle Stop and fail-closed stale/invalid readings. No native shell helper installed, no extra permissions or vehicle writes.
- Schema-4 sanitized diagnostics include native stream state and last accepted candidate sample. This is NOT final verified Live Telemetry: no independently validated speed/gear/AC/BMS/turn indicator, no background/reboot continuity, and no write authorization. `firmwareVerified=false` and `vehicleWriteAuthorized=false`.
- CI results and owner parked acceptance must be attached before claiming even this candidate stream works on the target car; [acceptance instructions](p2-native-stream-acceptance.md). **P1/P2/P3 remain incomplete**.
