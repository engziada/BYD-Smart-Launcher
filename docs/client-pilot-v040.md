# Client Pilot 0.4.0 — Demonstrable functional slice

**Not a completed all-features MVP.** This release contains genuine operational functions, not placeholders promoted as working car controls. It can be presented transparently as a *read-only connected-car pilot*. **Do not market or deploy it commercially under the current BYDMate-derived PolyForm Noncommercial license without resolving commercial rights.** See `THIRD_PARTY_NOTICES.md`.

## Working user flows

1. **Live read-only car dashboard:** On a compatible DiLink 5.0 unit, tap **Status → Start native read-only stream**. SOC and odometer candidates refresh via GET-only `autoservice`, with five-second sampling, firmware FID identity checks, stale suppression and one bounded unexpected ADB reconnect. Other telemetry remains unavailable until proven. OEM launcher stays default.
2. **Actual Telegram message containing current car status:** Configure a Telegram bot and destination chat (encrypted locally with Android Keystore) under **Telegram**. While the native stream is active with a fresh sample, tap **Send current car status**. Read the exact SOC/odometer values and candidate-data warning, then confirm **Send once**. This sends a real HTTPS Telegram message to the saved chat, or returns a clear error without pretending success. **Nothing sends automatically.** Sender refuses samples older than 15 seconds, prior sessions, missing ADB, invalid SOC/mileage or unavailable bot. No queued replay of private stale status; user must re-confirm for another send.
3. **Usable range-planning calculator:** Open **Range Planner**, enter **usable battery capacity** in kWh and your average consumption in kWh/100km. The app uses a fresh, provenance-labelled car SOC candidate if available; otherwise enter SOC manually. Tap **Calculate estimated km**. Formula:
   `rangeKm = (capacityKwh × soc% ÷ 100) ÷ averageKwhPer100Km × 100`.
   Example: 50% SOC, 80 kWh usable capacity and 20 kWh/100km → **200 km planning estimate**. Save capacity and consumption independently for later sessions; no ADB connection required for user-entered calculations. This is **not measured BMS Real Range** or an official BYD prediction.
4. **Animated original vehicle visual:** Status has a Flutter-drawn vehicle and moving cable energy-flow preview, explicitly switched on for design demonstration; unverified BMS codes, SOC=100%, and cable presence do not trigger a false live charge indication.
5. **Telegram infrastructure:** Existing saved bot config, encrypted credentials, diagnostic test send and bounded manual retry remain available.
6. **Rule editor:** Existing rules can be stored/edited locally; dispatch remains disconnected by design until trustworthy events and explicit approval exist.

## Features NOT yet safe or integrated

| Requested capability | Current result | Gate |
| --- | --- | --- |
| Full live telemetry (speed/gear/AC/charging) | SOC and odometer candidate stream only; speed/gear diagnostic raw numbers | OEM validation of FID meanings and native verified event bus |
| Vehicle control (climate/windows/etc.) | **No write API; no fake actuation** | Model-specific permission and command allowlist, trusted parked state, owner authorization, independent readback |
| Measured Real Range | **Manual planning estimate works**, measured consumption unavailable | Verified BMS energy units and charging exclusion, persisted driving samples |
| Blind Spot | **No real side camera feed** | Documented/licensed OEM AVM API and factory reverse/360 precedence |
| Telegram auto power/charge alerts | **Manual status send works**, no automatic events | Independently verified power/charging transitions and durable dispatch |
| Commercial redistribution | **Blocked by BYDMate noncommercial license** | Rightsholder permission or properly relicensed independent implementation |

## Customer presentation flow

**Use a real car only while parked.** Show the app's Overview, operator-enable read-only stream, then navigate to Telegram and send a privately approved current status to a prepared bot chat. Show the message arriving. On Range Planner, calculate a user-parameter estimate, change average consumption and show the numerical change. Optionally toggle the moving green-cable design preview with its **DESIGN PREVIEW** label. Finally, explain that OEM controls, side cameras and fully measured energy range are integration-dependent and therefore **not claimed** in this pilot.

A client presentation in Demo mode may show synthetic sample values but is clearly labelled DEMO and cannot contact the car or Telegram; **do not present it as functioning vehicle integration**.

## Release and acceptance

- Source and tests in `main` after green GitHub Actions Flutter analyze/test, Kotlin JVM tests and signed debug APK package/hash check.
- PR/build success does not prove the owner car's new functionality. Status message requires live on-car ADB and a configured Telegram bot/network.
- This is a **parking-test debug APK** (not signed for public production deployment). Do not replace OEM Home. Use the stock car UI to independently verify battery and mileage.
- For commercial client handoff, address **license**, security review, Android release signing, data privacy consent, and on-car firmware behavior first.
