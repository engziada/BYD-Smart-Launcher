# BYD DiLink community engineering review — 2026-10-19

Scope: imported BYD Song Plus Flagship SmartDrive 2025, head unit reporting Android 12 / SDK 32. These community references are **not** proof that this exact unit's FIDs, permissions, camera endpoints or startup behavior are supported.

## Relevant Chinese-language and Russian-language sources

| Community reference | Evidence / engineering takeaway | License / treatment |
|---|---|---|
| [DiCar2 (中文)](https://github.com/tsix2019/dicar2) | Describes an ADB shell-identity `app_process` helper that reads `android.hardware.bydauto.*` with `BYDAutoDeviceManager`. The map tracks firmware-specific provenance; do not guess missing fields. | MIT; reference architecture only, not copied here |
| [BYDMate 中文说明](https://github.com/AndyShaman/BYDMate/blob/main/README.zh.md) and [Русский README](https://github.com/AndyShaman/BYDMate/blob/main/README.ru.md) | Firmware-specific FID catalog export is required when moving between BYD models; the Leopard 3 fallback is not Song Plus verification. DiLink 5 ADB may require separate enabling, and newer firmware can close ADB at restart. | PolyForm Noncommercial; preserve notices for any approved port |
| [DiPlay-CN: BYD navigation / ADB probe](https://github.com/serein-morii/DiPlay-CN/blob/main/docs/BYD_NAVIGATION.md) | One-shot read-only shell helper can resolve firmware constants and reject incomplete field checks. Never activate a dependent feature after a partial/failed probe. | Technical reference; review upstream license before reuse |
| [BYD-dashboard (中文 / Русский)](https://github.com/BH4GMI/byd-dashboard) | Documents on-device loopback ADB bootstrapping, user-authorized keys, different DiLink secondary displays and startup limitations. No assumption that cluster projections apply to Song Plus. | GPLv3; no source imported |
| [4PDA: DUDU Launcher Global / BYD owner discussion](https://4pda.to/forum/index.php?showtopic=1089355) | Owners report BYD launcher compatibility differences and stock navigation/widget trade-offs. Preserve original launcher; do not disable OEM HOME during test. | User reports are anecdotal, not API contracts |

## Decisions for this implementation

1. Keep ADB transport state separate from *verified* vehicle state. Android 12 and `autoservicePresent=true` do not authorize reads or writes automatically.
2. Keep current SOC, cumulative-energy and odometer *fallback* values clearly tagged **unverified**; their units and device catalog remain to be confirmed. The owner previously confirmed SOC and odometer matched one parked reading, but that is not a firmware catalog validation. The energy counter candidate was **7126.2 kWh** and its meaning remains unknown.
3. Next technical milestone: an *opt-in read-only one-shot* FID-symbol discovery and validation report, using permissively licensed or appropriately noticed components. Reject collisions, malformed values and unknown scales. Helper absence is real; do not present a fake Install Helper button.
4. Blind-spot overlays must be withheld until exact left/right signal and camera mappings are validated and OEM reverse/360 override wins every conflict. No safety-critical vehicle commands.
5. Vehicle-powered automation and Telegram on/off events remain unavailable until a verified source, durable journal, disconnect handling and restart/power-cycle acceptance exist. Master rule switches currently save preferences only.
6. Do not copy GPLv3 components into differently licensed code or silently expand the existing PolyForm Noncommercial grant.

## Automated test evidence

- PR [#7](https://github.com/engziada/BYD-Smart-Launcher/pull/7), CI [run #53](https://github.com/engziada/BYD-Smart-Launcher/actions/runs/39220577054): Flutter analyzer, Flutter tests including light theme and export bridge, Android native unit tests, debug APK build, signature/package/HOME checks all succeeded.
- CI artifact `byd-smart-launcher-parked-test` includes `app-debug.apk` and `SHA256SUMS.txt`.
- SHA-256 of APK: `1f96a1b133e7b647868e3ef8ef07ab327a97427c50ef3a0b5f1ed4592255e8c1`.
- **No on-vehicle regression test has run on this APK.** Automated build success does not establish readiness for active driving, automation, blind-spot camera overlays or climate control.

## Parked-car final acceptance checklist (manual)

1. Keep factory BYD launcher installed and default. Install the diagnostic APK with vehicle secured in P. Stop on any OEM UI interference or unrecognized authorization prompt.
2. Confirm Settings switches Light/Dark consistently and no live/verified vehicle badge appears solely because ADB is connected.
3. Enable wireless ADB manually if authorized, accept local RSA prompt, run Android-version and read-only firmware-identity probes. Note reconnect behavior after power cycle.
4. While parked, sample SOC and odometer candidate once; compare to *stock* BYD readings. Export the sanitized JSON to Downloads/BYD-Smart-Launcher; inspect it before sharing.
5. Verify the energy counter is *not* promoted to confirmed consumption/range; do not supply a battery capacity unless independently verified.
6. Check Telegram only with operator-supplied token/chat ID and an explicit TEST message. No automatic on/off reports expected. Never export or upload credentials.
7. Check notification rules remain local drafts, controls are unavailable and camera displays remain disabled. No moving-car testing of unverified features.
8. On crash/unwanted behavior, exit the app and use OEM UI; uninstall the prototype if necessary. Removing the app can erase its local ADB key and Telegram settings.

Pending: standalone helper/FID catalog, firmware validation, real camera pipeline, durable dispatch, persisted trip samples, final signed release and on-car acceptance.
