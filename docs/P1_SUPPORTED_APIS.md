# P1 source-backed API/FID decision register (2026-10-21)

**Scope:** first operational re-audit slice for BYDMate v4.0.1 versus Kinex 0.5.3, tailored to the owner's DiLink 5.0 OS 2602. This is **partial P1 evidence**, not certification that *all* private SDK operations are documented or vehicle control is safe.

## Audit evidence / provenance

1. [BYDMate v4.0.1](https://github.com/AndyShaman/BYDMate/tree/v4.0.1), Kotlin public source. The critical files `AdbOnDeviceClient.kt`, `HelperBootstrap.kt`, `FidCatalogManager.kt`, `WriteAllowlist.kt` have **identical Git blob SHAs** against public main when checked on 2026-10-21: `277c6e36c53733b61586f0275c451cfc6586c805`, `9e35740c9a4d7a575819eca213802498f90eadf7`, `b7554d8b08d0ed807a07c04245ac824d8f3a7649`, `a78f3d5f5e76bcf57d17ef59f6e4a09e5a789320`. **No equivalence claim for unchecked files**.
2. BYDMate `data/nativestack/FidMap.kt` (blob `a78133648799ce477b57667fb295481db77370da`), `data/autoservice/AutoserviceClient.kt`, `data/vehicle/VehicleApi.kt` (blob `1d4d418b5e1a2d520172dadf9f558712618c5e77`), plus `app/src/test/resources/fid-catalog-songplus.txt` (blob `15a2b4eecf8dec87045a542d521a52b56bb806e2`). Exact source links follow below.
3. [Kinex distribution README](https://github.com/lexwah/kinex-dist/blob/main/README.md) supports some DiLink 5 / Song Plus vehicles; it is **not** a public SDK or full application source. Prior owner-supplied static APK assessment: `com.lexwah.kinex` / 0.5.3, Flutter AOT, proprietary `libkxcore.so` and `KinexBlindSpotStateReceiver` identifier; public contract unknown. APK sha256 `61571028e8f397bd7d384047cdc5551b2974ae09feeac6d957c74bfc5a5bd471`, compared to owner upload in private environment. **Do not copy assets/code**.
4. Owner's **private** 2026-10-21 on-car schema-3 report: DiLink5.0 Android 12, ADB connected, `autoservicePresent=true`, owner-device reflection SOC FID `1246777400`, odometer FID `1246765072`, statistic device `1014`; candidate SOC `75%`, odometer `42069.9 km` (`420683` raw in a **separately timed** catalog sample). No OEM side-by-side confirmation of this newest sample. `helper=not_installed`, `vehicleTelemetry=not_implemented`, `writeEnabled=false`. Never upload raw diagnostics to public GitHub.

## Owner-uploaded APK manifest string-pool audit (static, 2026-10-21)

The original private APKs were inspected **without installing or executing them**; this pass decoded the binary AndroidManifest.xml **string pools** (which list identifiers appearing in compiled manifest XML) and confirms these declared names, **not** whether Android grants the permissions or whether the services actually function.

| Kinex 0.5.3 APK | BYDMate 4.0.1 APK |
| --- | --- |
| `com.lexwah.kinex`, `KinexBlindSpotStateReceiver`, `KinexDl5BootReceiver`, `KinexBottomBarOverlayService`, `KinexAaReceiverService` in manifest strings. | `com.bydmate.app`, `TrackingService`, `BootReceiver`, `HelperBinderReceiver`, `FidRecorderReceiver` in manifest strings. |
| Permission names present include `CAMERA`, `CAPTURE_VIDEO_OUTPUT`, `WRITE_SECURE_SETTINGS`, `INTERNAL_SYSTEM_WINDOW`, `SYSTEM_ALERT_WINDOW`, `BYDACQUISITION_SEND_BUFFER`, `READ_LOGS`, `DUMP`, `INTERNET`. | Permission names present include `CAMERA`, `SYSTEM_ALERT_WINDOW`, `PACKAGE_USAGE_STATS`, `WRITE_SECURE_SETTINGS`, `READ_LOGS`, `DUMP`, `FOREGROUND_SERVICE`, `WAKE_LOCK`, `INTERNET`. |

**Inference boundary:** Presence in the string pool can indicate a declaration, descriptor or reference; it cannot prove `PackageManager` permission grants, export policy, shell UID access, Binder interface methods or OEM trust. The more precise node/attribute/SDK-target check and on-device tests are still P1 open items. Kinex native `libkxcore.so` API remains opaque and proprietary; never link/copy it without authorization.

## BYD read-only command and SDK map

| Feature | SDK symbol | BYDMate fallback device/FID, GET tx, units | Song Plus reference fixture FID | Owner-car evidence / decision |
| --- | --- | --- | --- | --- |
| SOC | `Statistic.STATISTIC_ELEC_PERCENTAGE` | device 1014, fid **1246777400**, transaction **7** getFloat, percent | **1033543720** | Owner reflection resolves **1246777400**. Earlier OEM-matched 35% / 100%; latest 75% only candidate. Allow **bounded read-only**, never a control gate. |
| Mileage | `Statistic.STATISTIC_TOTAL_MILEAGE` | 1014, fid **1246765072**, tx **5** getInt, fallback scale 0.1 km/tick | **1033543696** | Owner reflection equals fallback FID and OEM-matched parked ~41,936 km; latest candidate 42,069.9 km; separately timed raw 420683. Allow *0.1 km/tick only for this exact already-observed FID*, still candidate. **Never transfer this scale to a differing catalog FID.** |
| Total electric consumption | `Statistic.STATISTIC_TOTAL_ELEC_CONSUMPTION` | 1014, fid **1032871984**, tx **7** getFloat, units unresolved | **1033199632** | Meaning/units/reset vs DC fast charging unverified. **Do not use in automatic real range**. |
| Speed | `Speed.SPEED_AUTO_SPEED` | 1013, fid **-1807745016**, tx **7** float | **-1176502256** | **UNVERIFIED** on owner car; no stationary or safe-write interlocks may rely on it yet. |
| Gear | `Gearbox.GEARBOX_AUTO_MODE_TYPE` | 1011, fid **555745336**, tx **5** enum | **555745336** | **UNVERIFIED** on owner car. Do not authorize vehicle control from name-match alone. |
| Turn indicator | `Light.LIGHT_TURN_SIGNAL_LIGHT` | 1004, fid **950009900**, tx **5** enum | **1564** | Owner reflection resolves fallback FID; event meanings, hazard and OEM AVM override **UNVERIFIED**. |
| Charge gun | `Charging.CHARGING_GUN_CONNECT_STATE` | 1009, fid **876609586**, tx **5** enum | **876611634** | Owner reflection returns fallback. Gun raw `1` observed, BMS raw `15` **unknown**. Do not claim charging completion. |
| BMS phase | `Charging.CHARGING_BATTERRY_DEVICE_STATE` (upstream spelling) | 1009, fid **876609560**, tx **5** enum | **876611608** | Owner value 15 not mapped/verified; no events. |
| Charge type | `Charging.CHARGING_TYPE` | 1009, fid **876609592**, tx **5** raw | **876611640** | Raw 1 seen but semantics not established on owner car. No inference about AC/DC state from this alone. |
| Power, A/C, battery remainder | `Engine.ENGINE_POWER`, `Ac.AC_POWER_STATE`, `Power.POWER_BATTERY_REMAIN_ELECTRICITY` | See upstream `FidMap.kt` with type/scale | May vary | Unproven; **not enabled in v0.3**. |

**Critical firmware trap:** the upstream `fid-catalog-songplus.txt` fixture **differs** from this user's DiLink5.0 reflected SOC/odometer FIDs. An API name, model branding or BYDMate fallback alone is NOT proof of runtime compatibility. First streaming slice therefore accepts only the exact owner-observed device+SOC+odometer address combination. On any mismatch, fail closed and export a reason, with no guessing at odometer scale.

Source refs:
- [BYDMate FidMap.kt](https://github.com/AndyShaman/BYDMate/blob/v4.0.1/app/src/main/kotlin/com/bydmate/app/data/nativestack/FidMap.kt)
- [BYDMate Song Plus test fixture](https://github.com/AndyShaman/BYDMate/blob/v4.0.1/app/src/test/resources/fid-catalog-songplus.txt)
- [BYDMate autoservice decoder](https://github.com/AndyShaman/BYDMate/blob/v4.0.1/app/src/main/kotlin/com/bydmate/app/data/autoservice/AutoserviceClient.kt)
- [BYDMate ADB API](https://github.com/AndyShaman/BYDMate/blob/v4.0.1/app/src/main/kotlin/com/bydmate/app/data/autoservice/AdbOnDeviceClient.kt)

## Follow-up audit: exact manifest component attributes and P2 raw motion GETs (v0.3.1+5)

The original APK **binary AndroidManifest.xml element and attribute data** was decoded, rather than treating strings in the pool as actual permission declarations. See [parent-apk-manifest-evidence.md](parent-apk-manifest-evidence.md), including `KinexBlindSpotStateReceiver exported=false`, `KinexBottomBarOverlayService exported=false`, `KinexDl5BootReceiver enabled=false`, and BYDMate `HelperBinderReceiver exported=true` (whose IPC authentication still requires inspection). **A manifest request grants no privileges to this APK.**

The launcher now supports a **manual one-shot GET-only raw speed/gear probe** from `Speed.SPEED_AUTO_SPEED` (transaction 7 float) and `Gearbox.GEARBOX_AUTO_MODE_TYPE` (transaction 5 int) with dynamic firmware-derived FIDs. It requires the exact observed DiLink owner identity + existing SOC/odometer FID gate and expected device groups SPEED 1013 / GEARBOX 1011. **No unit conversion or enum labels are asserted**; speed and gear are not promoted to live vehicle state or P3 stationary/write approval. See [motion-fid-acceptance.md](motion-fid-acceptance.md). This feature is **in source, awaiting CI and owner-car evidence**.

## ADB commands, privilege and vehicle-control boundary

| Category | Exact permitted structure / upstream reference | Integration decision |
| --- | --- | --- |
| ADB loopback | `AdbProtocolClient` to **127.0.0.1:5555** using RSA/TLS; owner prompt/consent | Already on-car transport tested; NOT privilege to write. |
| Identity/availability | `getprop ro.product.model`, `getprop ro.build.version.sdk`, `getprop ro.build.fingerprint` (hash output), `service check autoservice` | Read only, explicit operator request. |
| Autoservice GET | `service call autoservice 5 i32 <dev> i32 <fid>` for raw int; `... 7 ...` for float. BYDMate's `AdbOnDeviceClient.exec` deliberately permits transactions 5/7/9 and filters other shell commands. | Generate only internally fixed, resolved, validated addresses; decode Binder errors and sentinels. No generic input console. |
| Firmware class reflection | `CLASSPATH=<our signed base.apk> app_process /system/bin com.ziada.bydsmartlauncher.FidReflectionProbe` | One-shot read-only whitelist from installed app; string/path validated and output bounded. |
| BYDMate native daemon | `HelperBootstrap` + `HelperClient`, version handshake, binder auth, shell UID, single-instance | **NOT yet ported.** P2 must inspect version mismatch, IPC integrity, retry/sleep, and stop semantics first. |
| System permission changes | BYDMate ADB client has app-op / secure settings methods in addition to GET commands | **NOT approved or added.** Separate necessity/security/rollback review required. |
| Vehicle writes | `VehicleApiImpl` → `WriteAllowlist` → `HelperClient`; BYDMate source marks some actions **live-leopard3-*** | **DISABLED**. No Song Plus accepted write list. Requires separately authorized P3 step, fresh verified speed/gear, parked gating, exact value range and independent readback. |

Upstream [VehicleApi.kt](https://github.com/AndyShaman/BYDMate/blob/v4.0.1/app/src/main/kotlin/com/bydmate/app/data/vehicle/VehicleApi.kt) lists climate/window/lock/sunshade methods but **interface existence ≠ device support or permission**. [WriteAllowlist.kt](https://github.com/AndyShaman/BYDMate/blob/v4.0.1/app/src/main/kotlin/com/bydmate/app/data/vehicle/WriteAllowlist.kt) contains device bans and value restrictions as well as Leopard 3 validations. Do not import its generic 123-entry competitor-derived dataset as an authorized Song Plus write policy.

## Remaining P1 verification before closing

- [ ] Re-run a full Android binary manifest/resource/component API extraction from both original APKs with an appropriate static disassembler; file hashes alone do not validate permissions/entrypoints.
- [ ] Full, version-pinned source-level inventory of SDK class signatures, helper IPC auth and every relevant candidate command, including `TX_GET_INT/FLOAT` and any other transaction meanings.
- [ ] Prove live helper bootstrapping, stale daemon version, disconnect/restart/reboot, firmware permission model, and stop/rollback on the owner's car (read-only).
- [ ] Prepare explicit exact-allowlist *candidate* comfort controls, separately get owner approval, and test vehicle stationary fresh gear/speed + independent readback before any write.
- [ ] Extend active read-only field map one by one on-device; validate against OEM at identical timestamps. Keep `firmwareVerified=false` for unresolved fields.
- [ ] Kinex proprietary API requires author documentation/permission or stays `blocked_unknown`.

**Implementation decision for the next APK:** start a foreground-only, user-opt-in **native candidate stream**, with symbol reflection and exact owner-address match, no daemon/privilege escalation, with explicit freshness/stop/failure behavior. This is a **P2 implementation slice, not final accepted Live Telemetry**.
