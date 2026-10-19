# DiLink 5.0 charging-state diagnostic: safe one-shot acceptance

**Scope:** BYD Song Plus Flagship 2025 head unit, Android 12 / SDK 32. Experimental debug-only version `0.1.1+2`. This is a **read-only diagnostic**; it does not detect charging in the background, operate the charger, or send charge-complete Telegram events.

## Evidence and limitations

- A prior on-car, owner-confirmed OEM **100% SOC** matched a candidate SOC reading while the car was connected to a **DC fast charger**. We do **not** know whether that charging session had ended. This fact cannot validate any charge-finished logic.
- Owner-confirmed 35% SOC and ~41,936 km in a separate parked observation, and a successful firmware FID lookup. Those are separate from charge-gun and BMS-state tests.
- The historical `Statistic.STATISTIC_TOTAL_ELEC_CONSUMPTION` candidate moved ~0.1 while the odometer stayed constant. The meaning and units are still uncertain; no charging energy or real range is calculated from it.

## Exact implementation

- The existing user-authorized loopback ADB session invokes the APK's **one-shot** `FidReflectionProbe` via `app_process`. No persistent helper is installed.
- A firmware-derived `BYDAUTO_DEVICE_CHARGING` plus `Charging.CHARGING_GUN_CONNECT_STATE` and `Charging.CHARGING_BATTERRY_DEVICE_STATE` are **required** before any diagnostic reads. `Charging.CHARGING_TYPE` is optional and always reported as a **raw unclassified integer**.
- Each **Read charging state once** action uses at most three fixed autoservice transaction-5 GET reads; there are **zero transaction-6/8 writes**, no polling service and no arbitrary shell input.
- Raw fields `gunStateRaw` / `bmsStateRaw` and textual **hypotheses** are included in `chargingCandidate` in schema-3 JSON exports. Enum interpretations are based on BYDMate v4.0.1's Leopard 3 observations, not certified for this Song Plus:
  - gun: 1=no plug, 2=AC, 3=DC, 4=AC/DC, 5=VTOL
  - BMS: 1=charging, 2=finished, 13=paused
  - `finished_candidate` means **possible charge finish, NOT verified completed charging**.
- Unknown, contradictory or missing codes produce `unknown` or `conflicting_signals` and cannot generate a completion event.
- **Hard-coded gates:** `firmwareVerified=false`, `completionConfirmed=false`, `automaticEventEligible=false`, `vehicleWriteAuthorized=false`. These are not user-toggleable. No Telegram event is emitted or rule dispatched.
- No VIN, Telegram secrets, ADB keys, arbitrary stdout, full build fingerprint or location is exported.

## Operator acceptance steps (safely parked)

1. Do **not** select this APK as the car's default Home; verify the factory rear/360 camera remains available.
2. Open Settings, explicitly **Connect ADB** and **Probe firmware identity**. After the read-only firmware FID discovery, select **Read charging state once (read-only candidate)**.
3. Read `gunKindCandidate`, `bmsPhaseCandidate` and `chargeStateCandidate` in Settings; compare against the **OEM charging screen**, not just SOC. Export sanitized diagnostics JSON **after** the action.
4. A first sample **unplugged** is useful, and later a voluntary sample when connected to AC or DC charger can be compared to OEM state. Do not perform repeated charging sessions just for this test; no deadline.
5. Any `charging_fids_unavailable`, `unknown`, `conflicting_signals` or missing catalog symbols means **keep features disabled**; no automatic feature fallback to Leopard 3 addresses.
6. Further gating before Telegram: confirm charger attached / active / completed separately on the OEM screen, repeated on-car consistency, unplug and reboot/reconnect behavior, and a durable independently proven event source. This APK includes **none** of that automation.

Reference: [BYDMate v4.0.1 source](https://github.com/AndyShaman/BYDMate/tree/v4.0.1), specifically `FidMap.kt`, `GunStateEdgeDetector.kt` and `LoopState.kt` (PolyForm Noncommercial license; attribution retained). Firmware symbols were confirmed available in BYDMate test fixture `fid-catalog-songplus.txt` from another Song Plus build, **not** assumed identical to the owner's 2025 unit.
