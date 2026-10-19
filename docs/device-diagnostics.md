# Parked-car acceptance: M1 ADB diagnostics only

**This is a development APK, not the final launcher.** Do not set it as the default HOME app. It does not replace Kinex and does not implement blind-spot cameras or vehicle control yet.

## Installation
1. Open a successful [GitHub Actions](https://github.com/engziada/BYD-Smart-Launcher/actions) run, download the `byd-smart-launcher-diagnostics-debug` artifact ZIP (retention is 7 days), and extract `app-debug.apk`.
2. Install on the BYD central display while parked. Allow APK installation only from a trusted source; do not uninstall the working Kinex.
3. This is a CI debug-signed APK. The signer can change between CI runs; subsequent install may require uninstalling the old debug version. Uninstalling also destroys this prototype's local ADB RSA key, so DiLink may ask for authorization again.

## ADB checks
1. Park securely in P, keep vehicle climate/safety controls under the original BYD UI.
2. Enable the DiLink's local ADB/Wireless Debugging option explicitly (BYDMate also requires it). Confirm classic ADB port `127.0.0.1:5555` is available. These steps do **not** require installing BYDMate as a second APK.
3. Open **BYD Smart Launcher → Settings → Connect ADB**. The native DiLink `Allow USB debugging?` approval may appear; review and allow if you consent. Do **not** approve unknown devices or network ADB clients.
4. Press **Refresh diagnostics** and record the `adb` and `adbFailure` *only*. Expected: `adb: connected` on a compatible setup, else `adb_port_unreachable`, `authorization_rejected_or_timeout`, or `transport_error`. A result of `connected` means ADB transport **only**, not car telemetry access.
5. Press **Probe firmware identity (read-only)**. Compare model/manufacturer/Android SDK with the stock BYD About/System page. The displayed fingerprint hash is a privacy-preserving version marker; **do not post VIN, full fingerprint or any private ID**. `autoservicePresent=true` is not proof of compatible FIDs.
6. Press **Probe Android version**, then **Probe SOC (unverified)**. Compare any candidate SOC against the stock BYD dashboard. No reading or a mismatch is acceptable evidence of an unsupported FID; **do not use candidate values for driving decisions**.
7. Open **Real Range**. Input usable battery capacity only if you know its correct value. For an additional manual read-only test, use **Sample vehicle counters** while parked. Compare SOC and odometer candidate values to the original BYD display before and after any attempt at range estimation. **On Song Plus the BYDMate fallback odometer scale may be wrong**; if so, stop and report the discrepancy rather than calibrating by guesswork.
8. Candidate consumption needs at least two valid readings one kilometre apart and only lasts during the same app process; it does not imply working background trip tracking.
9. Press **Disconnect ADB**; verify UI remains responsive, then exit app. Disable local Wireless ADB after testing if you do not need it.

## Evidence / privacy
- Note whether DiLink requested RSA approval and whether you could connect after a car restart.
- Record firmware label/version and any non-sensitive error codes. Don't include VIN, coordinates, account name, Telegram token, ADB private key, private network details or route history.
- Report in GitHub issue #1 whether local ADB accepted the signature and the SOC candidate agreed with the stock display.
- No moving-car acceptance, no camera overlay acceptance, and no vehicle write tests in this milestone.

## Stop conditions
App crash, unknown authorization prompt, OEM UI interference, unexpected control behavior, or repeatable telemetry mismatch: disconnect, return to the factory launcher and stop testing. The project does not ask to modify safety-critical vehicle systems.

## Automation / Telegram dry-run acceptance
- Open **Automations**. Add a low-SOC rule (25%), power-on and power-off rules. Confirm they are saved after app restart and can be individually toggled/removed. The master switch starts OFF and **never dispatches** in this build.
- Open **Telegram**, save an opt-in test token and chat ID on the device only. The credentials are encrypted at rest. Use **Send test message**; if offline, one diagnostic test should remain pending. Restore connectivity and use **Retry pending test** (respecting backoff), then check pending count returns to zero.
- A repeated Send test while one remains pending should not create multiple queued tests. The delivery system is at-least-once, so rare duplicates are possible after a crash.
- Clear credentials and confirm the pending queue is removed. Don't publish the token, chat ID, BotFather messages, or Telegram logs in GitHub issues.
- Do not assume that creating ON/OFF rules enables car notifications. There is no verified power-state feed or background event dispatcher in the development APK.
