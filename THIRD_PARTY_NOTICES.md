# Third-party notices

The Android ADB protocol implementation, supporting key/certificate/TLS utilities, blind-spot decision/backoff policy, and verified-power-transition automaton here are adapted from [BYDMate v4.0.1](https://github.com/AndyShaman/BYDMate/tree/v4.0.1).

Required Notice: Copyright AndyShaman (https://github.com/AndyShaman/BYDMate)

**License:** PolyForm Noncommercial License 1.0.0 (see [licenses/BYDMate-LICENSE](licenses/BYDMate-LICENSE)). Uses outside the license's permitted noncommercial scope require the rightsholder's permission.

Changes in this project: decision/backoff copied as portable pure Kotlin without camera capture or write functionality; remove Hilt injection dependency from standalone ADB keystore, adjust diagnostic handshake label, wrap connection in an explicit user-invoked, shell-command-restricted transport; no BYDMate native helper or vehicle commands have been ported yet.

The BYDMate source remains copyrighted by its author. No Kinex APK binaries or assets are redistributed.

The firmware FID catalog parser (`data/nativestack/FidCatalog.kt`) is also adapted from BYDMate v4.0.1 under the same noncommercial license. This parser is not connected to a live BYD helper or vehicle write path in this version.
