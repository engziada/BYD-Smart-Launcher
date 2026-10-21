# Parent APK AndroidManifest evidence — 2026-10-21

## Method and scope

The user-provided **original APK files** were inspected **offline** as ZIP containers. Their binary AndroidManifest.xml structures (ResXMLTree string pool, start-element nodes and typed attributes) were decoded, rather than merely searching for strings. No code was installed or executed, and no asset, proprietary class, signed binary or owner-private identifier was committed.

- Kinex 0.5.3 original APK: SHA-256 `61571028e8f397bd7d384047cdc5551b2974ae09feeac6d957c74bfc5a5bd471`, package `com.lexwah.kinex`, versionCode `1`, **minSdk 24 / targetSdk 36**.
- BYDMate 4.0.1 original APK: SHA-256 `d0c251f446bafb209f4c8e13b96599223234c797eb80cea1fecb5db7b3f90d20`, package `com.bydmate.app`, versionCode `508`, **minSdk 29 / targetSdk 29**.

This is factual **manifest-declaration evidence**, not evidence that a custom BYD permission was actually *granted* on the owner's DiLink 5.0. Android signature permissions and vendor Binder capabilities need separate on-device verification.

## Kinex manifest findings

| Component | Exported / enabled | Implication |
| --- | --- | --- |
| `com.lexwah.kinex.listeners.KinexBlindSpotStateReceiver` | **exported=false** | **Not an externally callable public receiver API**; cannot use this as an inter-app blind-spot hook. Internal behavior and source unavailable. |
| `com.lexwah.kinex.services.KinexBottomBarOverlayService` | exported=false | Internal overlay service only; avoid assumptions about window/camera APIs. |
| `com.lexwah.kinex.listeners.KinexDl5BootReceiver` | exported=true, **enabled=false** | Not a default automatic boot integration on this APK's manifest. |
| `com.lexwah.kinex.listeners.KinexDl5HomeAdminReceiver` | exported=true, requires `android.permission.BIND_DEVICE_ADMIN` | Device administration is not authorization for another launcher to replace BYD OEM HOME; do not emulate or launch it. |
| `com.lexwah.kinex.services.KinexFullscreenAccessibilityService` | exported=true, binds `android.permission.BIND_ACCESSIBILITY_SERVICE` | Special user consent/service binding, not a public vehicle SDK. |
| `com.lexwah.kinex.aa.KinexAaReceiverService` | exported=false | Android Auto is separate/deferred. |
| `com.lexwah.kinex.MainActivity` | exported=true, **enabled=false** | Manifest alone does not establish a usable external entry point; only vendor-relevant activities should be examined if specifically needed. |

Manifest **requests** include `android.permission.BYDACQUISITION_SEND_BUFFER`, `BYDACQUISITION_SEND_FILE`, `CAMERA`, `CAPTURE_VIDEO_OUTPUT`, `INTERNAL_SYSTEM_WINDOW`, `WRITE_SECURE_SETTINGS`, `SYSTEM_ALERT_WINDOW`, `INJECT_EVENTS`, `DUMP` (indirect string references alone not declarations), plus ordinary Internet/Bluetooth/network permissions. These privileged/vendor operations are **not available to our app merely because Kinex requests them**.

## BYDMate manifest findings

| Component | Exported | Implication |
| --- | --- | --- |
| `com.bydmate.app.service.TrackingService` | false | BYDMate's internal tracking service is not a generic public telemetry API. |
| `com.bydmate.app.helper.HelperBinderReceiver` | **true** | A declared exported broadcast receiver is **not evidence of an authorized open Binder contract**; inspect explicit package targets, authenticator/UID checks and helper lifecycle before considering any interaction. |
| `com.bydmate.app.data.push.FidRecorderReceiver` | **true** | Exported declaration; cannot assume it is safe to call or implements our vehicle subscription API. |
| `com.bydmate.app.service.BootReceiver` | true | Possible own-app boot integration; requires on-car lifecycle validation for a single-APK implementation. |
| `com.bydmate.app.cluster.SteeringWheelKeyService` | false, `BIND_ACCESSIBILITY_SERVICE` | Special service permission and explicit user action; not granted to us. |

BYDMate **requests** `SYSTEM_ALERT_WINDOW`, `WRITE_SECURE_SETTINGS`, `READ_LOGS`, `PACKAGE_USAGE_STATS`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`, `CAMERA` and others. **Our APK only needs INTERNET for its current loopback ADB connection** and did not inherit these parent privileges.

## Architecture decisions

1. **P1:** Do not reuse Kinex internals, `libkxcore.so`, private camera data, copyrighted assets, or an exported receiver name as an SDK. Its actual camera surface integration remains `blocked_unknown` until documented/licensed.
2. **P2:** Prefer an independently owned, version/authenticated native read-only bridge based on reviewed BYDMate source, not inter-app broadcasts to the BYDMate APK.
3. **P3:** No Android permission/ADB escalation by imitation. Comfort controls remain `vehicleWriteAuthorized=false` and `stationaryStateVerified=false` until real speed/gear data, parked state, individual write permission and independent readback are validated on **this** firmware.
4. **P5:** Future blind-spot camera integration must independently obtain an authorized OEM camera surface API and prove factory reverse/360 precedence. Kinex's receiver is not a direct integration path.

**Unfinished checks:** actual Kinex native `libkxcore.so` API documentation/permission, intent-filter semantics and exported receiver authorization validation, BYDMate HelperBinderReceiver message authentication/UID/version handling, and on-device permission grants. Thus **P1 remains OPEN**.

Additional source references: [BYDMate v4.0.1 source](https://github.com/AndyShaman/BYDMate/tree/v4.0.1), [Kinex distribution](https://github.com/lexwah/kinex-dist), [P1_SUPPORTED_APIS.md](P1_SUPPORTED_APIS.md).
