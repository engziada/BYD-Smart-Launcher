# Evidence register

| Milestone | Status | Evidence | Pending |
|---|---|---|---|
| M1 Flutter launcher shell | Validated prototype | Flutter analyze/test and Android debug compile succeeded on commit `207ca2a` ([CI](https://github.com/engziada/BYD-Smart-Launcher/actions/runs/39171979998)) | No vehicle integration at that stage |
| M1 ADB handshake | Ported, **awaiting latest CI and car testing** | BYDMate v4.0.1 ADB protocol/keypair/TLS classes under third-party license; user-initiated 127.0.0.1:5555 connect/disconnect and read-only version/SOC probes; test source | Confirm Android build and actual ADB pairing on DiLink OS 2602 |
| M1 helper and live telemetry | Not implemented | Native code explicitly returns `source=unavailable`; helper remains uninstalled | Port helper, firmware catalog, FID discovery, BYD-only data bridge |
| M2 blind spot | Decision logic ported; camera NOT implemented | BYDMate v4.0.1 pure decision and backoff sources, Android JVM tests; no camera surface access | Port actual AVM capture/displays after M1 helper and accept OEM override on-car |
| M3 real range | Candidate read-only prototype | On-demand FID reads for SOC, cumulative energy and odometer; bounded 25-km consumption trend; operator-entered capacity and native tests | FID catalog resolution and on-car verification, persistent history, calibrated range, automated tracking |
| M4 automations | Not implemented | Page placeholder | Port rules database/engine, local triggers, constraints |
| M5 Telegram | Manual test foundation validated | Bot settings encrypted on device; manual HTTPS test and removal; CI passed on `83278f2` | Durable queue, verified power-on/off events and actual notification automation |
| M6 vehicle control | Not implemented | Writes unavailable | Strict per-firmware allowlist, stationary confirmation, readback |

## Notes
- `adb.connected` confirms transport only; it **never** marks vehicle telematics as live.
- SOC probe uses BYDMate's Leopard 3 *fallback* address and is marked UNVERIFIED for Song Plus OS 2602 until device-catalog validation.
- A prior Android CI build on `7ce2850` failed during BouncyCastle duplicate META-INF packaging; new exclusion config applied.
- No production APK or on-car acceptance completed. No keys/tokens/vehicle ID uploaded.
