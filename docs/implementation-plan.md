# Implementation backlog — owner-approved priority order

**Updated:** 2026-10-21. **Target:** BYD Song Plus Flagship SmartDrive 2025 (Chinese DiLink 5.0 / OS 2602 / Android 12 SDK 32), single APK, 15-inch landscape. No phone integration / Android Auto in this delivery.

**This sequence supersedes the earlier M1–M6 ordering and the previous Blind-Spot-first MVP plan. The six priorities are EXACTLY:**

1. **Recheck ALL relevant references, SDKs, APIs and ADB commands in the two parent projects: BYDMate and Kinex.**
2. **Live telemetry.**
3. **Vehicle control.**
4. **Real range.**
5. **Blind spot.**
6. **Telegram.**

**Automations are deferred after priority 6** (maintain existing rule-editor code but do not divert implementation resources from the requested six). Release hygiene and testing run **across** all six stages; they are not a competing seventh feature.

## Current baseline (do not inflate)
- [x] Flutter landscape shell, Status / Range / Controls / Blind Spot / Telegram screens and light-theme switch (UI is not proof of integrations).
- [x] Kotlin ↔ Flutter channel, explicit demo isolation and bounded read-only ADB 127.0.0.1:5555 handshake.
- [x] On-car ADB/DiLink 5 identity and read-only firmware FID reflection; SOC and odometer have some owner-confirmed OEM samples.
- [x] v0.2.0+3 **diagnostic dashboard preview**: operator-started two-FID candidate polling every 15 seconds while foregrounded (superseded).\n- [x] v0.3.0+4 **native stream implemented in source**: firmware-gated two-FID GET-only candidate samples every 5 seconds when foreground, EventChannel, stale/dropout bounds, explicit stop and schema-4 diagnostics. **Owner on-car validation still pending.**
- [x] Pure testable camera/rule logic, draft local automation settings, encrypted Telegram bot settings and manually triggered TEST outbox.
- [ ] Integrated persistent BYD helper, model-verified general telemetry event bus, actual permitted vehicle controls, validated real range, native blind-spot camera and automatic Telegram alerts. **All outstanding.**

---

## P1 — Parent-project SDK/API/ADB audit (DO FIRST; blocking P2 and P3)

**References:** [BYDMate v4.0.1](https://github.com/AndyShaman/BYDMate/tree/v4.0.1), [BYDMate current source](https://github.com/AndyShaman/BYDMate), [Kinex distribution](https://github.com/lexwah/kinex-dist), owner-uploaded Kinex 0.5.3 / BYDMate 4.0.1 APK static assessment. **Detailed inventory and checklists: [parent-project-api-adb-audit.md](parent-project-api-adb-audit.md).**

- [x] Identify sources, accessible SDK stubs/implementation classes and IP limits. Kinex public repository is distribution-only; do not pretend to have its proprietary source.
- [x] Locate BYDMate handshake, daemon/helper, `autoservice`, FID catalog, vehicle-control allowlist, BMS/range, camera and Telegram reference modules.
- [ ] **Re-review source code and versions**: compare frozen v4.0.1 vs current BYDMate (pin exact commit); separately revalidate Kinex 0.5.3 APK package/manifest/SDK/runtime contracts, without copying opaque code or models.
- [ ] Produce exact `SDK/API/FID/ADB-command → source → type/transaction → units/sentinels → verified car model → risk → our implementation` matrix; classify actual capabilities vs claims.
- [ ] Review DiLink 5 permission/ADB modes, `app_process` helper lifecycle, Binder authentication and version/reconnect behavior; verify OS 2602 read-only diagnostics.
- [ ] Audit *candidate write controls* under default-deny; require model/firmware-specific safety, fresh stationary state, explicit confirmation and readback. No guessed permissions or blanket helper write access.
- [ ] Finish the re-audit with documented tests, frozen evidence and explicit blockers. **Do not mark P1 complete from documentation review alone.**

**Source-level inventory begun:** [P1_SUPPORTED_APIS.md](P1_SUPPORTED_APIS.md) (P1 still open pending exhaustive binary/SDK/command audit).\n\n**Exit gate:** `P1_SUPPORTED_APIS.md` with verified and unsupported operations, audit source/commit per item and safe implementation decisions for P2/P3.

## P2 — Live telemetry (SECOND)

- [ ] Implement single-APK, version-authenticated BYD shell helper and bounded read-only native SDK/Binder bridge based on audited P1 contracts.
- [x] Implement first typed native EventChannel **candidate stream** from reflected known SOC/odometer FIDs with timestamp, monotonic sequence, stale suppression and foreground-only lifecycle.\n- [ ] Complete full stable/verified telemetry: native helper version/authentication, background/ignition/sleep/reboot recovery, speed/gear/etc field validation and OEM checks.
- [ ] Discover and verify SDK/FID mapping for SOC, odometer, speed, gear, vehicle power, charge status, BMS, temperatures, tire pressure, indicators and AC **one field at a time**; unknown fields remain unavailable.
- [ ] Separate `transportConnected`, `helperAlive`, `firmwareCompatible`, `dataFresh`, `valueVerified`, and `writesAuthorized` — do not equate them.
- [ ] Exercise missing permissions, ADB disabled, daemon older than APK, process kill, sleep/restart, sample dropouts and fallback.
- [ ] Compare representative on-car values against OEM display at multiple SOC/mileage levels with timestamped evidence, and demonstrate no interference with BYD native UI.

**Exit gate:** stable live **read-only** telemetry on the owner's OS 2602 unit with understandable connected/stale/offline UI, verified field map, CI + parked device results.

## P3 — Vehicle control (THIRD; safety-gated and explicit consent)

- [ ] From P1 audit, build `VehicleControlCapabilities` by exact firmware, with **deny-by-default** action/parameter/target list and unsupported-action reasons.
- [ ] First implement a **read-only capabilities screen / simulated no-write control path**; do not infer actual authorization from BYDMate Leopard 3 commands or UI buttons.
- [ ] Start with explicitly selected comfort-only features **after P2 telemetry freshness/gear/speed state is proven**. Each action needs owner opt-in, stationary/vehicle-state gate, per-action confirmation, command timeout, audit log and independent readback.
- [ ] No generic write shell, wildcard allowlist, remote unlock, hazardous actuation, ADAS disabling, safety intervention or charging protection changes.
- [ ] Validate individual controls parked, across restart/failure/rollback, with OEM UI and safety supervision. Stop if permissions/model differ or vehicle state cannot be verified.
- [ ] Preserve hard-fail behavior when command unsupported; record success only after readback and on-car evidence.

**Exit gate:** documented, per-firmware approved comfort controls genuinely operate on the owner's parked car **with explicit approval and readback**, or remain labelled unavailable. Do not enable controls until gate passes.

## P4 — Real range (FOURTH)

- [x] Operator-supplied usable battery capacity, candidate SOC/odometer/energy reader and defensive mathematical window unit tests already exist.
- [ ] Identify the **true meaning, scale and reset behavior** of the energy counter or acquire verified BMS consumption data via P2; do not use the unresolved cumulative counter blindly.
- [ ] Persist timestamped trip/charge observations across process/ignition changes; exclude AC/DC charging, invalid odometer deltas and insufficient travel.
- [ ] Add consumption smoothing and measured-window range estimate, usable-capacity provenance, uncertainty/data age and graceful unavailable handling.
- [ ] Validate across owner trip samples against OEM SOC, odometer and independent charge/energy observations. Keep consumption only when proven physically plausible.

**Exit gate:** clear **real range** card based on driving consumption and validated units, or explicitly unavailable with diagnostic reasons.

## P5 — Blind spot (FIFTH)

- [x] Decision/backoff tests and discovered turn-signal symbol exist; **no native AVM feed implemented**.
- [ ] After P2 source validation, confirm turn-signal events, side-camera feed API and Kinex/BYDMate behavior by source or parked observed output; never assume a proprietary Kinex receiver exposes an API.
- [ ] Implement isolated, configurable left/right camera overlay with bounded speed/hazard/stale-signal policies and an immediate operator kill switch.
- [ ] Guarantee factory reverse/360 and critical alerts **preempt** any custom window; clean up surfaces on stop, app background, error and reboot.
- [ ] Run stationary left/right and OEM AVM takeover tests first, then a controlled road acceptance only after independent safety check.

**Exit gate:** real side-camera preview works with the owner's signal indicators and does not suppress the OEM safety views.

## P6 — Telegram (SIXTH)

- [x] Bot token/chat encrypted on device; manual TEST message and persisted single-item manual retry are implemented.
- [ ] First deliver manual **status report from recent P2-verified telemetry** with owner confirmation, no token/vehicle private data in logs.
- [ ] Then implement event-based ON/OFF and optionally charge-state notifications **only from independently verified events** (BMS raw 15 remains unknown; SOC 100% is not proof of finished charging).
- [ ] Durable outbox, controlled retries, deduplication, ignition/sleep recovery, opt-in settings, mute and privacy/retention controls.
- [ ] Validate network-offline/reboot/duplicate/no-coverage cases and track delivered vs pending without false success claims.

**Exit gate:** real user-controlled Telegram status and verified-event alerts work reliably, without duplicate/synthetic reports.

---

## Deferred — Automations (NOT in the six requested active priorities)
- Existing local rules editor, trigger evaluator and draft storage remain in repo; no dispatch is connected.
- **Do not schedule standalone automation implementation before P1–P6.** Revisit only on explicit owner request, or after all six user priorities, leveraging validated P2/P3 capabilities and safe writes.

## Continuous test/release gates (not competing roadmap priorities)
- Per phase: link source commit, CI jobs, APK filename (`v<semver>-build<number>-debug-<sha>.apk`), on-car evidence, permissions, success/failure cases, and rollback.
- Release as a **Technical Preview** until P2 telemetry and relevant feature actually work on OS 2602. Do not market UI mockups or tests as vehicle integrations.
- OEM Home/360/reverse remain accessible; no undocumented proprietary Kinex assets or source reuse; BYDMate PolyForm Noncommercial requirements apply.
- Never commit owner diagnostic raw exports, VIN, IMEI, fingerprints, bot tokens, credentials or precise car location.

## Traceability to original issues
Historical GitHub issue numbers do not define priority: P1 = [#13](https://github.com/engziada/BYD-Smart-Launcher/issues/13); P2 = [#1](https://github.com/engziada/BYD-Smart-Launcher/issues/1); P3 = [#6](https://github.com/engziada/BYD-Smart-Launcher/issues/6); P4 = [#3](https://github.com/engziada/BYD-Smart-Launcher/issues/3); P5 = [#2](https://github.com/engziada/BYD-Smart-Launcher/issues/2); P6 = [#5](https://github.com/engziada/BYD-Smart-Launcher/issues/5); automation [#4](https://github.com/engziada/BYD-Smart-Launcher/issues/4) deferred. Umbrella acceptance: [#12](https://github.com/engziada/BYD-Smart-Launcher/issues/12).
