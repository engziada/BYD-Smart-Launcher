# P1 — Parent-project / SDK / ADB re-audit (MANDATORY FIRST)

**Owner-approved execution order (2026-10-21):** (1) parent references, SDKs, APIs, ADB commands → (2) live telemetry → (3) vehicle control → (4) real range → (5) blind spot → (6) Telegram. **Automations are deferred**; do not silently re-insert them ahead of the six requested priorities.

This is the **initial source-index and gap register**, NOT a claim that every API has been reverse-engineered or proven on the car. Complete every unchecked audit row before declaring P1 done. Pin commits/releases in PR evidence, and never confuse documentary support on other BYD models with on-car validation of Song Plus 2025 / DiLink 5.0 OS 2602 / Android 12 SDK 32.

## Canonical sources and access rights

| Source | Fixed baseline | Available evidence | Limitations |
| --- | --- | --- | --- |
| BYDMate | [v4.0.1 source](https://github.com/AndyShaman/BYDMate/tree/v4.0.1) and [public current main](https://github.com/AndyShaman/BYDMate); main indexed at `b47ead7fc9f0741ed2d4b2ff61a785da337f0500` at review | Open Kotlin project, `bydauto-stubs/`, tests, helper, FID catalog, `autoservice` and vehicle-control implementation. Owner supplied 4.0.1 APK. | **PolyForm Noncommercial 1.0.0**. Verify file-level notices and noncommercial constraints before any reuse; do not mix main and 4.0.1 APIs without recording differences. |
| Kinex | [lexwah/kinex-dist](https://github.com/lexwah/kinex-dist) and [Kinex site](https://kinex.lexwah.com/); owner-provided Kinex 0.5.3 APK | Public **distribution/README only** (not confirmed application source), documented DiLink 3/limited DiLink 5 support, Seal-U / Song Plus entry, visible launcher features and on-device install/ADB setup. Earlier static assessment enumerated package/manifest, Flutter/native libraries and SDK metadata. | No confirmed source tree, SDK/API contract, or source license to reuse. **Do not present guessed Kinex internal APIs as known**, or extract/repackage its proprietary classes, artwork, 3D models, binaries or signing material. Request source/API documentation/permission from maintainer if necessary. |
| Our implementation | [BYD Smart Launcher](https://github.com/engziada/BYD-Smart-Launcher), v0.2.0+3 | Opt-in ADB loopback handshake; read-only candidate SOC/odometer; manual FID reflection; charging candidate; demo/test rule UI and Telegram TEST. | `helper=not_installed`, `vehicleTelemetry=not_implemented`, no production writes or OEM AVM integration. NOT a functional full-feature MVP. |

**SDK/build compatibility questions:** Kinex 0.5.3 APK static assessment reports min SDK 24 / target SDK 36; BYDMate 4.0.1 min/target 29. We target Android 12 / SDK 32. Distinguish BYD private `android.hardware.bydauto.*` SDK stubs from the standard Android API, and verify SDK ABI, parcel transaction contracts, privilege model, package identity and signature restrictions in the running firmware. Manifest permission requests alone do NOT grant privileged access.

## 1. Parent modules / API map (read docs and code, then test)

| Subsystem | BYDMate specific reference | Kinex reference / boundary | Our next validation |
| --- | --- | --- | --- |
| ADB handshake | `data/autoservice/AdbProtocolClient.kt`, `AdbKeyStore.kt`, `AdbOnDeviceClient.kt`, `AdbVerdictMonitor.kt`, `AdbRestoreSystem.kt` | README install/ADB setup, APK manifest; no verified source client | Version-specific RSA/TLS, port 5555, reconnect after sleep, explicit consent, keys kept private. |
| Firmware SDK / FIDs | `bydauto-stubs/`, `data/nativestack/FidMap.kt`, `FidAddresses.kt`, `FidCatalogManager.kt`, `app/src/test/resources/fid-catalog-songplus.txt` | Manifest privileges and runtime behavior, **not** a public BYD SDK | Compare `BYDAutoFeatureIds` and `BYDAutoConstants` by symbol/value/device on this car; document sentinel, scale, units, source. |
| Native helper / transport | `data/vehicle/HelperBootstrap.kt`, `HelperClient.kt`, `helper/HelperDaemon.kt`, `helper/HelperBinderReceiver.kt` | Kinex includes native runtime; API entry points NOT documented | Single signed APK classpath, shell UID, exclusive helper ownership, version handshake, binder authentication, startup after sleep, logs/errors. |
| Live readings | `data/autoservice/AutoserviceClient.kt`, `data/vehicle/VehicleApi.kt`, `data/vehicle/VehicleApiImpl.kt`, `data/nativestack/ParsReader.kt`, `service/TrackingService.kt` | Kinex UI shows telemetry, but UI does not document its API | Map SOC, odometer, speed, gear, power, AC, tire pressures and timestamps; continuous event vs bounded polling; connected/stale state. |
| Vehicle control | `data/vehicle/WriteAllowlist.kt`, `VehicleApiImpl.kt`, `VehicleApi.kt`, `CommandTranslator.kt`, `HelperClient.kt` and unit tests | Kinex Settings/Controls UI and visible package permissions; no proven callable source API | Enumerate comfort actions, distinguish Leopard 3 validated from **Song Plus verified**; implement fail-closed authorization, readback, parked conditions and per-action consent **before** any dispatch. |
| Range | `domain/calculator/RangeCalculator.kt`, `ConsumptionCalculator.kt`, `OdometerConsumptionBuffer.kt`; BMS/energydata pipeline | Visual range/status UI only | Validate energy counter semantic and units, drive vs DC/AC charging sessions, window selection, trip persistence, scale conversion, confidence. |
| Blind spot | `camera/BlindSpotController.kt`, `BlindSpotDecision.kt`, `data/camera/CameraStateMonitor.kt` | APK `KinexBlindSpotStateReceiver` identified in prior static inspection, but its implementation is proprietary | Model-specific AVM source, turn-signal FID, OEM reverse/360 preemption, overlays teardown and driver distraction guards. |
| Telegram | `data/telegram/TelegramReporter.kt`, `PowerOffArmer.kt`, `helper/offreport/PowerOffAutomaton.kt` | No verified Kinex native Telegram API | Approved status payload, encrypted bot credentials, durable queue, offline retry, distinct verified ON/OFF/charging-complete events. |

## 2. ADB / native command inventory — categorize, never copy blindly

| Command class | Observed/documented usage | Status / constraints |
| --- | --- | --- |
| Loopback ADB handshake | Binary ADB RSA/TLS client to `127.0.0.1:5555`; user permission dialog | **Currently device-proven as transport**. Not telemetry or write authorization. Investigate loss of ADB after reboot on newer BYDMate-supported firmware before opting into any restart automation. |
| Firmware identity | `getprop ro.product.model`, `getprop ro.product.manufacturer`, `getprop ro.build.version.sdk`, `getprop ro.build.fingerprint` | **Read-only**; publish fingerprint **digest only**. A model string is not a rights grant. |
| Binder service presence | `service check autoservice` | Read-only service detection, not an assurance that any FID exists or is authorized. |
| Autoservice GETs | `service call autoservice 5 i32 <device> i32 <fid>` (getInt), `service call autoservice 7 i32 <device> i32 <fid>` (getFloat). BYDMate public read barrier also recognizes transaction 9. | Read-only candidates; commands must be generated internally from a bounded validated address catalog, with typed decode and sentinel rejection. No arbitrary Flutter-provided command strings. |
| One-shot classpath reflection | `CLASSPATH=<installed base.apk> app_process /system/bin <our fixed probe class>` | Currently used for small allowlisted diagnostic FID reflection; **not** BYDMate's persistent helper. APK path must be safely validated and output bounded. |
| BYDMate detached helper | `AdbOnDeviceClient.spawnHelper`, `HelperBootstrap.ensureRunning`; `app_process` detached shell daemon with version/authentication handshake | **NOT installed in our app**. Audit startup, binder ACLs, version mismatch, single-instance locking, reboot behavior and stop/uninstall procedures before porting. |
| Privilege-changing ADB calls | BYDMate source includes permission/app-op setup and secure-settings restoration flow | **Not approved by previous ADB diagnostics**. Any proposed privilege change requires explicit necessity, per-command audit, owner authorization, rollback and isolated parked-car validation. Do not add a generic `adb shell` execution API. |
| Autoservice writes / helper Binder | BYDMate `VehicleApiImpl` with `WriteAllowlist`, `HelperClient` and model-specific source tags | **DISABLED in our APK** until P3 verification. BYDMate's Leopard 3 allowlist and safety thresholds cannot be copied as proof for Song Plus. No lock/unlock, windows, trunk, AC, drive or charging commands based on guesswork. |

`Read-only` status means the command does not intend to change vehicle/system state; it is **not** a guarantee about the OEM implementation or the accessibility of the command on this firmware. Exact BYDMate command implementations and any 5.0/5.1 SDK differences must be pinned and reviewed from source, not reconstructed from prose.

## 3. Audit execution checklist (P1 cannot close without evidence)

- [x] Re-identify both parent references and their source-availability boundary. Kinex public repository is distribution, not confirmed source; BYDMate Kotlin source and `bydauto-stubs` are public.
- [x] Locate key BYDMate classes for ADB transport, private SDK, FID catalog, helper, vehicle API and safety allowlist from public main.
- [x] Cross-reference parent APK manifest/package/SDK findings from the earlier owner-provided binary assessment. Re-run static signature/manifest checks against APK bytes before considering these reverified in this audit.
- [ ] Build **symbol-level** API registry from BYDMate v4.0.1 and current main: `symbol`, device, GET type/transaction, payload type, units/sentinels, write action, safety guard, source file/commit, tested model/firmware, and applicability verdict.
- [ ] Compare Kinex 0.5.3 APK exported components, Android permissions, manifest SDK targets and identifiable intent/Binder interfaces **statically**, with no proprietary binary reuse. Mark any opaque `libkxcore.so` interface as unknown unless owner/developer documentation exists.
- [ ] Compare parent API/ADB command inventory against our `SafeAdbTransport`, `FidReadOnlyDiscovery`, `MainActivity`, and AndroidManifest; record missing or unsafe gaps in a traceable matrix.
- [ ] Audit BYDMate helper binding, process version, shell privilege and restart/permission lifecycle in enough detail to implement a **single APK with no installed second app** safely. Test no stale helper after APK update.
- [ ] Audit all candidate writes for model-specific safety; decide *which are safe to investigate parked* and which are strictly excluded, with owner approval checkpoint. Document exact readback and OFF/rollback behavior.
- [ ] Run static unit tests and a **parked** on-device read-only capability probe. Confirm firmware fingerprint **via hashed value only**; no raw VIN, IMEI, location or keys in repo.
- [ ] Produce approved `P1_SUPPORTED_APIS.md` with individually sourced GET/WRITE/UNAVAILABLE markings and explicit P2/P3 prerequisites. Close P1 only after unresolved SDK and ADB assumptions have either evidence or a documented blocker.

### Audit outcome labels (per function)
`documented_in_parent` · `seen_in_binary` · `implemented_in_our_source` · `ci_tested` · `on_car_read_verified` · `on_car_write_verified` · `blocked_unknown`. Do not collapse them into a generic **supported** badge.

## Licensing and operational boundaries
- Retain BYDMate copyright and PolyForm Noncommercial notices. Noncommercial source visibility does not authorize redistribution into a commercial launcher without required rights.
- Treat Kinex as a **proprietary behavioral/UI reference** unless author supplies source and permission; produce independently authored code/assets. Do not commit the Kinex APK or extracted models/libs.
- Keep factory Home and AVM accessible; no ADAS/drive-safety disabling, no modification of braking systems, traction, speed, safety alerts or charging power protections.
- Do not move P3 ahead of P2 **in execution**, even if driver-facing controls appear more attractive; P3 implementation can begin with a **non-actuating simulator** while P2 stream and firmware gates are being established.

## Audit sources for follow-up
- [Prior local technical assessment](../README.md) for ownership/licensing history (not a substitute for re-verification).
- BYDMate [README.en](https://github.com/AndyShaman/BYDMate/blob/main/README.en.md), [GitHub sources](https://github.com/AndyShaman/BYDMate).
- Kinex [distribution README](https://github.com/lexwah/kinex-dist/blob/main/README.md) and [official site](https://kinex.lexwah.com/).
