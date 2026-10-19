package com.ziada.bydsmartlauncher

import java.security.MessageDigest

/**
 * Read-only, opt-in firmware discovery helpers. A fingerprint hash is enough to compare
 * repeat visits to the SAME firmware without disclosing the complete build fingerprint.
 * A model string or autoservice presence NEVER grants BYD vehicle write privileges.
 */
internal object FirmwareDiagnostics {
    fun property(raw: String?): String? {
        val value = raw?.trim()?.lineSequence()?.firstOrNull()?.trim() ?: return null
        if (value.isEmpty() || value.length > 80) return null
        return value.takeIf { s ->
            s.all { c -> c.isLetterOrDigit() || c in " ._/-+()" }
        }
    }

    fun sdkInt(raw: String?): Int? =
        raw?.trim()?.toIntOrNull()?.takeIf { it in 19..50 }

    fun servicePresent(raw: String?): Boolean? {
        val s = raw?.trim()?.lowercase() ?: return null
        if (!s.startsWith("service autoservice:")) return null
        return when {
            "not found" in s -> false
            "found" in s -> true
            else -> null
        }
    }

    fun fingerprintDigest(raw: String?): String? {
        val value = raw?.trim() ?: return null
        if (value.length !in 1..250 || value.any { c ->
            !(c.isLetterOrDigit() || c in "._/:@+-")
        }) return null
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return digest.take(10).joinToString("") { "%02x".format(it) }
    }
}
