package com.ziada.bydsmartlauncher

/**
 * ADB reconnect is allowed to terminate a candidate stream, but a repeated
 * "Connect ADB" on an already-open session is NOT a reconnect. This decision
 * does not authorize any vehicle reads/writes.
 */
internal object AdbSessionChangePolicy {
    fun shouldStopStream(command: String, alreadyConnected: Boolean): Boolean =
        command == "adbDisconnect" ||
            (command == "adbConnect" && !alreadyConnected)

    fun effectiveStopReason(
        streamRunning: Boolean,
        currentState: String?,
        currentReason: String?,
        newReason: String
    ): String =
        if (!streamRunning && currentState == "stopped" &&
            !currentReason.isNullOrBlank()) currentReason else newReason
}
