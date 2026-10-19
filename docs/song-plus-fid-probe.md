# Song Plus DiLink 5.0: one-shot firmware FID discovery (M1 diagnostic phase)

**Target:** owner's imported BYD Song Plus Flagship 2025, DiLink 5.0 for BYD AUTO, Android 12 / SDK 32. The owner confirmed one parked reading of **35% SOC** and **41,936.1 km** with the OEM display; fallback addresses remain marked *unverified*.

## Why a one-shot shell probe?

The existing BYDMate v4.0.1 fallback numeric FIDs were developed for Leopard 3. BYDMate's Russian and Chinese source documentation, plus its `FidCatalogManager` and `FidMap`, explain that the firmware's own `BYDAutoFeatureIds` constants can differ by model. In particular, an odometer **catalog** address may report whole km whereas the fallback address reports tenths of km. Applying the fallback 0.1 scale blindly is therefore unsafe.

Reference (Russian / Chinese): [BYDMate](https://github.com/AndyShaman/BYDMate), source at tag `v4.0.1`, `app/src/main/kotlin/com/bydmate/app/data/nativestack/FidMap.kt`. The existing `FidCatalog` parser in this repository retains its PolyForm Noncommercial attribution. The new `FidReflectionProbe` is a purpose-written, smaller diagnostic implementation.

## Exact action

**Settings → Connect ADB → Discover firmware FIDs (one-shot, read-only) → Export sanitized diagnostics JSON**.

- Run the action **while safely parked in P** and after the head unit has authorized loopback ADB. No automatic launch, daemon installation, persistent shell service, Binder registration, settings change, camera control or vehicle write is included.
- The action invokes `CLASSPATH=<installed base.apk> app_process /system/bin com.ziada.bydsmartlauncher.FidReflectionProbe` through the existing loopback ADB transport. The APK path comes from the installed application and is validated against shell metacharacters; it is not accepted from Flutter or user input.
- The probe prints only six allowlisted integer constants: statistic and light device IDs, SOC, total mileage, total electrical consumption, and turn signal. The parser rejects duplicate, extra, malformed, oversized and incomplete output.
- On a valid catalog, **two explicit read-only service calls** retrieve candidate SOC and raw mileage. The raw mileage is **NOT converted to km** without independent scale validation. All reported `firmwareVerified`, `helperPersistent` and `vehicleWriteAuthorized` values remain **false**.
- Export is schema 2 with a sanitized `fidCatalogCandidate` object. It excludes raw ADB output, full build fingerprint, Telegram data, VIN and other private identifiers. The previous `telemetryCandidate` and `firmware` objects remain available.

## Acceptance checks on the head unit

1. Confirm OEM Home remains the default and rear-view/360 camera works normally; the experimental app must not intercept those screens.
2. Connect ADB explicitly and run the *read-only* firmware-identity probe. DiLink 5.0 and SDK 32 should still be reported.
3. Tap **Discover firmware FIDs**, then **Export sanitized diagnostics JSON**. A `catalog_candidate` response is discovery evidence, **not acceptance**; `firmware_classes_unavailable`, `catalog_incomplete` or `probe_markers_invalid` are legitimate unsupported-firmware outcomes.
4. Compare `socCatalogCandidatePercent` to OEM SOC. Compare `odometerCatalogRawCandidate` to OEM kilometer reading **without multiplying by 0.1**; report if it appears scaled differently. Do not interpret the energy counter as confirmed kWh consumption.
5. Confirm settings, demo isolation, light theme and normal ADB disconnect remain functional.
6. Do not enable any blind spot, ignition-based automation, Telegram auto-report, or vehicle control based solely on this probe. The persistent helper and continuous verified stream remain future phases.

Automated tests guard shell command construction, FID parser ambiguity and invalid output, demo isolation, and non-authorizing metadata. Final on-car acceptance is not possible in CI. The feature makes no claims about success on the owner's exact firmware until the user provides a new diagnostic export.
