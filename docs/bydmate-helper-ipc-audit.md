# P1 source audit — BYDMate helper process, Binder IPC and restart boundaries

**Reviewed upstream version:** [BYDMate v4.0.1](https://github.com/AndyShaman/BYDMate/tree/v4.0.1), source-based review conducted 2026-10-21. **No BYDMate helper was installed, spawned, killed or called on the owner's BYD.** Kinex helper internals remain proprietary and unknown.

## Source-backed control-plane facts

| Property | Evidence | Required architectural decision |
| --- | --- | --- |
| Process identity | `helper/HelperDaemon.kt` starts under ADB **shell UID** with app UID argument, not under BYDMate's ordinary app UID. It checks `Binder.getCallingUid() == expectedUid` and `data.enforceInterface(HelperBinderProtocol.DESCRIPTOR)` on Binder transactions. | An independently owned helper must implement strict Binder UID+interface checks; never treat the ability to reach an exported receiver as permission to issue vehicle operations. |
| Fallback announcement | `helper/HelperBinderReceiver.kt` is exported because the shell daemon broadcasts its Binder. The holder **checks expected spawn token before touching the incoming Binder**, then verifies interface descriptor and refuses replacement when already holding a live Binder. | Exported receiver is not a generic external API. Avoid Binder spoofing or exposing private IPC tokens; do not call BYDMate's receiver from our app. |
| Spawn authentication | `data/vehicle/HelperBootstrap.kt` generates a per-spawn token and arms it before executing `spawnHelper`. `data/autoservice/AdbOnDeviceClient.kt` checks token is alphanumeric (16–64 chars) before interpolation into shell command line. | Per-spawn secret, strict shell quoting, private storage and no token in logs/diagnostics. Tokens from BYDMate do not belong to our app. |
| Version freshness | `HelperBootstrap.kt` queries the running daemon using `TX_GET_VERSION` and compares it with the installed app version. A previously detached `app_process` daemon can outlive an APK update. | Implement version handshake and stale daemon cleanup *before* any persistent helper. A bare Binder ping is insufficient. |
| Duplicate prevention | `HelperDaemon.kt` uses a nonblocking exclusive file lock (`/data/local/tmp/bydmate_helper.lock` in BYDMate's implementation). `HelperBootstrap.kt` checks process heartbeat and waits for stale process exit before spawning. | Separate names/file-lock paths per app, no interference with BYDMate/Kinex, bounded kill-confirm/rollback and exclusive owner guarantee. |
| API definition | `helper/HelperBinderProtocol.kt` enumerates **different** transactions for reads, writes, helper version, display/system permissions, subscription and event callbacks. | Our native IPC must use a separately reviewed, minimum **read-only** protocol; never expose blanket helper/Binder raw transaction or SET command passthrough. |
| Shell privilege lifecycle | BYDMate's `AdbOnDeviceClient.kt` includes permission-grant and helper-kill/spawn code beyond GET-only read diagnostics. | **Our current APK does not use those calls.** Any proposed permission/system command requires separate P1 review, user approval and proof of reversibility. |

## References pinned to source

- [HelperBinderReceiver.kt](https://github.com/AndyShaman/BYDMate/blob/v4.0.1/app/src/main/kotlin/com/bydmate/app/helper/HelperBinderReceiver.kt) (blob `0380e2ed5c5de2dc6b2ed78ca090c7a9eb5fbc4d`)
- [HelperDaemon.kt](https://github.com/AndyShaman/BYDMate/blob/v4.0.1/app/src/main/kotlin/com/bydmate/app/helper/HelperDaemon.kt) (blob `0107c130bcca7604ada5f1890c156600937a0bb8`)
- [HelperBinderProtocol.kt](https://github.com/AndyShaman/BYDMate/blob/v4.0.1/app/src/main/kotlin/com/bydmate/app/helper/HelperBinderProtocol.kt) (blob `4f67be1d4616506f8a91628be78af30a81f5860c`)
- [HelperBootstrap.kt](https://github.com/AndyShaman/BYDMate/blob/v4.0.1/app/src/main/kotlin/com/bydmate/app/data/vehicle/HelperBootstrap.kt) (blob `9e35740c9a4d7a575819eca213802498f90eadf7`)
- [AdbOnDeviceClient.kt](https://github.com/AndyShaman/BYDMate/blob/v4.0.1/app/src/main/kotlin/com/bydmate/app/data/autoservice/AdbOnDeviceClient.kt) (blob `277c6e36c53733b61586f0275c451cfc6586c805`)

## Not yet proven

- Native IPC permission behavior, version handshake and reannouncement on **the owner's DiLink 5.0 firmware**.
- Shell UID vs OEM binder permissions for all requested GETs and any candidate comfort actions.
- Whether a persistent helper is necessary and permitted or a foreground-only, GET-only native stream can suffice for P2 acceptance.
- Implementation under BYDMate's **PolyForm Noncommercial 1.0.0** source license (must confirm permitted redistribution/use before porting).
- Safe, bounded restart/kill strategy that cannot affect any other app's daemon or OEM processes.
- Independent, OEM-matched speed and gear interpretation: the new v0.3.1 raw probe is observational only.

**Gate remains CLOSED** for P3 vehicle controls and for any privileged permission/system operation. No runtime trust, app signature, helper authority, confirmed motion semantics or write authorization can be inferred from this source review alone.
