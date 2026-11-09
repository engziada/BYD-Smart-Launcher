# BYD Smart Launcher

Landscape-first Android launcher for a **BYD Song Plus Flagship SmartDrive 2025** head unit.

## Current status
**v0.4.7+15 — integrated Song Plus build.** **Connect ADB & start live readings** starts the foreground SOC and odometer stream and loads climate and window controls. The owner's firmware profile now supports A/C on/off, fan level, Celsius setpoint and four window positions from touch and offline Arabic/English voice. The profile matches the owner diagnostics and the reference APK registry. Window values are percent open (0 = closed, 100 = open). Each request still requires an individual confirmation, fresh Park and zero-speed checks, and an independent readback. The owner's confirmed on-car write evidence covers fan 1 → 2 → 1. The other writes are enabled for parked acceptance on the same exact firmware.

For a combined parked test, compare live battery and mileage to the factory display, test fan and A/C, move the setpoint by one degree and restore it, then try a small opening on each window one at a time and close it. Try one of the commands by voice. The diagnostics export captures up to 24 write results from the current app session, so one export covers the run. Range is based on manually measured trips. The blind-spot camera feed, automatic BMS-based range, and car start/stop or charge Telegram notifications still need their corresponding live vehicle sources.

**v0.4.6+14 — bundled offline speech recognition.** If the head unit lacks Android's offline speech service or language, the microphone uses the bundled English and Arabic Whisper model. Tap the microphone, allow microphone permission, wait for “Listening,” and speak a short command in the language selected in Settings. Try “Set the fan to two” or «ظبط سرعة المروحة على اتنين» while parked with ADB connected and the factory fan at 1. Vehicle writes still require confirmation. The reply appears on screen. The Android text-to-speech engine can also speak it when it has an installed offline voice for that language.

**v0.4.5+13 — parked A/C power write test.** On November 8, 2026, the Song Plus owner reported that v0.4.4 successfully changed the fan from 1 to 2 and back to 1 on the matching Dylan 5 firmware. The result covers those two fan levels. This build adds A/C power as a test on the same firmware. Each write requires individual confirmation, fresh Park and zero-speed checks, and a valid readback. Windows and set temperature are read-only.

While parked with the factory A/C on, connect ADB and open **Vehicle Controls → Read climate and window state → A/C power**. Review target **0** to turn it off. Compare the factory display with the launcher readback. If both show Off, review target **1** to restore On and compare again. You can also say “Turn off the A/C” and “Turn on the A/C,” or «اطفي التكييف» and «شغل التكييف». Each voice command opens the same confirmation dialog. The diagnostics export records the most recent command and its readback.

**v0.4.2+10 — sensor and integration commissioning build.**

- **Voice and sensors.** Connect local ADB, then ask “tyre pressure,” “cabin temperature,” or “outside temperature.” Arabic alternatives include «ضغط الكاوتش» and «درجة الحرارة بره العربية». Only readings returned by the firmware are spoken, with candidate provenance. Settings can export these samples along with the reflected raw energy counter and indicator state.
- **Vehicle controls.** The Controls page discovers named command and independent read-back addresses for A/C power, fan level, temperature and four window positions. It sends GETs to the state addresses. To commission writes, compare the reported readbacks with the factory UI and establish the actual Park and zero-speed encodings on this firmware.
- **Blind spot.** The Blind Spot page detects the AVM surface API and captures the raw indicator state without opening a camera. Feed ownership, left/right routing and factory reverse/360 priority still require on-car evidence.
- **Range.** The Range Planner includes persistent, distance-weighted history from manually entered EV-only measured trips. Enter measured battery energy and distance for a trip without engine use or charging, then enter the current OEM SOC and usable capacity for a trip-based estimate. The raw BMS counter remains available as a separate research reading.
- **Telegram and rules.** Background retry is separately opt-in for queued diagnostic messages. Manual status continues to require confirmation. Power, charging and SOC alert routing requires a native verified event source. The current candidate stream supplies no eligible events, so saved vehicle rules cannot send yet.

For on-car commissioning while parked, connect ADB, capture **Probe raw speed & gear**, **Probe climate and window mappings**, **Probe AVM and indicator**, and **Read tyre and temperature candidates**, then export the sanitized JSON. Include the matching factory display values when reporting results. See the [Assistant NG source revision](https://github.com/3freet/BYD-Assistant-NG/tree/05832b3f704d1f321262d154b2ee2bc037419285) for the upstream typed tyre getters and comfort symbol pairs.

**v0.4.1+9 — offline voice and read-only helper milestone.** Use the microphone in the header or type a command in Settings. Select Arabic or English, start the read-only stream, and ask for the battery level or odometer. The assistant labels current samples as unverified and refuses stale ones. Speech recognition and playback use installed Android offline services. To try the optional steering-wheel shortcut, enable **BYD steering-wheel voice shortcut** in Android Accessibility Settings if your head unit exposes that screen. It reacts to the BYD short-press or Android voice-assist key only when the launcher has focus. Key delivery requires on-car verification.

In **Settings**, connect ADB and select **Probe vehicle helper and firmware FIDs**. Diagnostics distinguish ADB authorization, a completed one-shot helper invocation from the launcher APK, and a response to a Binder liveness check. A Binder response does not verify sensor values or unlock vehicle writes. To test voice on the target head unit, disconnect internet while leaving local ADB available, select Arabic, and say «نسبة البطارية» or «قراءة العداد». Compare the reply to the current Status card and the OEM display. Repeat after stopping the stream to verify that the assistant rejects old readings.

**v0.4.0+8 — Functional read-only Client Pilot, NOT an accepted all-features MVP or production release.** The app has an explicit on-device ADB RSA/TLS connection, read-only SOC/odometer candidate sampling, firmware FID discovery, and a **native foreground 5-second candidate event stream** with explicit Start/Stop, strict firmware-address matching, stale-data suppression, **manual raw speed/gear GET-only probes**, idempotent ADB session reuse, one bounded foreground reconnect after unexpected ADB loss, first-stop-reason retention and schema-6 sanitized diagnostics. The Status page also features an **original Flutter-drawn vehicle with a manual green charging-cable animation**; this is strictly labelled a DESIGN PREVIEW and is never activated automatically from unverified charging FIDs. The owner confirmed some SOC/odometer readings against the stock BYD interface; this does **not** validate a live telemetry stream, native helper, vehicle writes, real range, OEM camera pipeline or Telegram automatic events. Unavailable values are not fabricated.

**New usable customer journeys:** (1) send a genuine **manual private Telegram status** containing the current fresh read-only SOC/odometer candidates, only after user confirmation and to a separately configured encrypted bot chat; (2) calculate and persist a user-parameter **Range Planner** estimate from capacity, average consumption and fresh-car/manual SOC. The calculator is not measured BMS Real Range. See [Client Pilot usage and honest capability matrix](docs/client-pilot-v040.md). All car controls and native side-camera feeds remain unavailable. **Commercial deployment requires resolving BYDMate's PolyForm Noncommercial license**.

## Current implementation priorities (owner revised 2026-10-21)
1. **Recheck parent-project references, SDKs, APIs and ADB commands** — BYDMate and Kinex. Record exactly which source and version supports each API, and which capabilities are verified for the owner's DiLink 5.0 OS 2602.
2. **Live telemetry** — robust native helper/read-only event stream and firmware-specific validation.
3. **Vehicle control** — only firmware-verified **comfort controls** with explicit operator approval, fresh parked/gear/speed gates, independent readback and fail-closed safety checks.
4. **Real range** — independently validated BMS consumption and trip-history-based estimate.
5. **Blind spot** — left/right camera integration triggered by indicators; OEM reverse/360 overrides all custom camera surfaces.
6. **Telegram** — manual/approved real status and reliable deduplicated verified vehicle events.

**Automations are deferred after these six priorities.** Previous Blind-Spot-first / Telegram-before-controls plans are superseded. Phone and Android Auto remain out of scope.

**Next parked on-car checks:** [ADB recovery and charging-flow visual acceptance](docs/v033-recovery-charging-visual-acceptance.md), then [Native candidate stream acceptance](docs/p2-native-stream-acceptance.md). This does not install a persistent helper or enable real vehicle controls.

**Canonical execution source:** [docs/implementation-plan.md](docs/implementation-plan.md), [parent API/SDK/ADB audit](docs/parent-project-api-adb-audit.md) and [MVP acceptance](docs/mvp-scope.md). Historical issue numbers don't represent the new priority ranking.

## Approach
- Flutter UI, Kotlin Android platform bridge, Kotlin vehicle modules.
- One APK once integrated, with no dependence on a separately installed BYDMate app.
- No proprietary Kinex code, extracted 3D models, APK libraries, or artwork are included.
- BYDMate v4.0.1 provides the licensed ADB handshake implementation; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Kinex's public `kinex-dist` repo provides release/install guidance, **not confirmed source code or an SDK**. Ported source must preserve its [PolyForm Noncommercial 1.0.0 license](https://github.com/AndyShaman/BYDMate/blob/v4.0.1/LICENSE) and required copyright notice. **Commercial reuse requires appropriate permission.**
- This repository is **currently public**. Never add Telegram tokens, credentials, vehicle identifiers, or diagnostic dumps containing personal data.

## Build
Install Flutter, Android SDK and JDK 17. Run `flutter pub get && flutter test`, then `bash scripts/prepare-offline-voice.sh` to fetch and verify the speech runtime and model before `flutter build apk --debug`. The prepared files stay local to the checkout and are included in the APK. The Android wrapper may need generation on a new host via `flutter create --platforms=android --project-name byd_smart_launcher --org com.ziada .`. Review any generated changes and preserve the project's Kotlin files. The independent native policy tests run with `bash scripts/test-native-core.sh` (requires `kotlinc`).

## Range Planner and raw research counters (NOT for driving decisions)

In the **Real Range** tab, enter the verified usable battery capacity in kWh. While safely parked, use **Sample vehicle counters** at the start and end of a trip (the app process must stay alive between samples). The app calculates a *candidate* rolling kWh/100km after at least 1 km and a tentative range from SOC × usable capacity / consumption, provided all inputs are plausible.

**Important:** BYDMate's reference odometer FID uses 0.1 km on Leopard 3, while the official Song Plus catalog may use a **different FID and unit scale**. Until both addresses and scale are verified against the OEM cluster and persistent trip logging is added, candidate numbers remain unverified and will NEVER be promoted to live vehicle telemetry.

## Automation status

A **side-effect-free verified-only trigger evaluator** supports SOC threshold edges, power on/off edges, once-per-trip rules, cooldown, and stale/unverified suppression. A Flutter rule editor persists notification-only drafts on device, with a global default-OFF master switch and per-rule toggles. **Even when toggled on, these rules do not dispatch** in the current APK. It is not connected to the unverified ADB FIDs or the Telegram sender. Helper-derived power state, rule journal, background dispatcher and actual automatic notifications still require development and on-car acceptance. The pure BYDMate power-off deduplication policy is included separately, without a live event source.

## Telegram (manual car status and diagnostic delivery)

Telegram bot token/chat ID are stored with Android Keystore AES-GCM (app backups disabled). A test message is persisted locally before HTTPS send. A failed test remains in a bounded, deduplicated outbox, with **manual** retry and queue clearing; automatic retry is not implemented. There is also a user-confirmed live read-only **manual SOC/odometer status** request when the native stream is fresh; it sends over HTTPS once, without queuing/automatic retries of private car readings. No ON/OFF or charging alerts are automatic. Never share a bot token, even in issue screenshots.

## Firmware read-only discovery

**Settings → Probe firmware identity** reads fixed Android `getprop` fields and `service check autoservice`, hashes rather than exposes the full firmware fingerprint, and always reports `compatibleAndVerified=false`. This does not unlock any camera or vehicle write capability.

## Safety / acceptance
No vehicle writes exposed until the car/firmware is fingerprinted, allowlist verified, stationary gating passed, and explicit per-action confirmation tested. OEM reversing and 360-degree camera always override aftermarket overlays. Test on a stationary, secured car first.

See `docs/implementation-plan.md` and `docs/phase-evidence.md`.
