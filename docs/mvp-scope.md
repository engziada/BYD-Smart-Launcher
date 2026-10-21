# MVP / release scope — revised to match owner's 2026-10-21 priorities

**Vehicle:** BYD Song Plus Flagship SmartDrive 2025 / DiLink 5.0 OS 2602 / Android 12 SDK 32. **One APK**, landscape-first; OEM home, reverse and 360 cameras must remain available.

**The v0.2.0+3 build is a Technical Preview, not a finished functional MVP.** A dashboard showing candidate SOC and odometer is useful, but not sufficient to meet the feature brief. CI success proves the APK builds; owner-verified firmware behavior is a separate acceptance gate.

## Strict owner-ordered roadmap

| Priority | Deliverable | Status as of plan revision | Must be proven on vehicle |
| --- | --- | --- | --- |
| 1 | Recheck **all relevant** references/SDKs/APIs/ADB commands in BYDMate and Kinex | **In progress**: source inventory opened; binary/interface, write semantics and version-diff audit pending | Evidence-backed per-operation capability mapping for OS 2602; unsupported APIs documented, no guessed Kinex internals |
| 2 | **Live telemetry** | **Not implemented**: read-only candidate sampling and manual FID probes only | Stable validated timestamped native stream, stale/reconnect/reboot handling, OEM comparison |
| 3 | **Vehicle control** | **Not implemented**: settings/controls page only | Individually approved comfort writes after verified parked P2 inputs, per-command readback; otherwise explicit unavailable status |
| 4 | **Real range** | **Candidate math only**: no validated energy source/trip history | Validated BMS energy + driving-only consumption and persisted trip window |
| 5 | **Blind spot** | **Decision tests only**: no camera integration | Camera feed follows turn signal, factory AVM always takes precedence |
| 6 | **Telegram** | **TEST send works**; automated reporting not implemented | Verified telemetry status and durable, deduplicated approved event alerts |

**Automations** are **deferred** and not counted among the six active priorities. Their existing editor and tests remain, but no independent automation phase may displace these priorities.

## Previously established evidence (not promotion to full compatibility)
- Owner confirmed BYD dashboard and fallback SOC matched at 35% and 100%; OEM-confirmed odometer ~41,936 km in a parked sample.
- On 2026-10-21 schema-3 export, ADB connected; 75% SOC and 42,069.9 km candidate odometer, firmware reflection symbol discovery, but `helper=not_installed` and `vehicleTelemetry=not_implemented`. The newest odometer figure has not yet been independently confirmed against the OEM cluster in that same sample.
- Charging gun raw 1 and BMS raw 15: charging state **unknown**, not finished; actual charge-completion alerts may not be inferred.
- The cumulative energy counter's units/meaning remain unverified. A raw FID discovered in BYDMate is not proof of a Song Plus vehicle-control write permission.

## Gate definitions
- `documented_in_parent`: API appears in parent code or README.
- `seen_in_binary`: the user-supplied APK manifest/class metadata references a feature, not proof it works.
- `implemented_in_our_source`: code exists.
- `ci_tested`: source compiles and deterministic mock/unit tests pass.
- `on_car_read_verified`: matched against BYD OEM measurements on the target OS.
- `on_car_write_verified`: only after explicit owner approval, verified parked safety and independently observed readback.
- `blocked_unknown`: no safe verified access. **Do not expose it as usable.**

## Release / acceptance
1. **P1 research milestone**: publish exact source- and commit-grounded BYDMate/Kinex SDK & ADB/API inventory; [P1 audit](parent-project-api-adb-audit.md) stays open until confirmed.
2. **P2 telemetry milestone**: no production write path. After stable stream passes CI and owner test, start P3 vehicle controls.
3. **P3 controls milestone**: a preview of control buttons is not a working feature. Accept only safe, explicitly approved, target-firmware-verified actions with readback and rollback.
4. **P4–P6 feature milestones**: range, blind spot and Telegram independently meet their on-car and safety gates.
5. **Functional MVP completed** only when the six prioritized deliverables are addressed with evidence or their blocking limitations have been explicitly accepted by the owner. Don't rename a partial Diagnostic APK as a completed product.

Follow [implementation plan](implementation-plan.md), [parent audit](parent-project-api-adb-audit.md), [phase evidence](phase-evidence.md) and [umbrella tracking issue #12](https://github.com/engziada/BYD-Smart-Launcher/issues/12).
