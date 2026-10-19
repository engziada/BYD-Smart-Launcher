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
