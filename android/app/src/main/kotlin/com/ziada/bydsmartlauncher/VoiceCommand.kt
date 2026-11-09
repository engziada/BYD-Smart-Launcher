package com.ziada.bydsmartlauncher

import java.util.Locale

internal enum class VoiceCommand {
    BATTERY, ODOMETER, TYRES, CABIN_TEMP, OUTSIDE_TEMP, COMFORT, UNKNOWN;

    companion object {
        fun parse(text: String): VoiceCommand {
            val phrase = normalizeVoicePhrase(text)
            return when (phrase) {
                "battery", "battery level", "battery percentage", "what is my battery level",
                "what s my battery level", "نسبة البطارية", "البطارية كام",
                "شحن البطارية كام", "نسبة شحن البطارية" -> BATTERY
                "odometer", "mileage", "what is my mileage", "what s my mileage",
                "عداد العربية", "قراءة العداد", "العربية ماشية كام" -> ODOMETER
                "tyre pressure", "tire pressure", "what s my tyre pressure",
                "what s my tire pressure", "what is my tyre pressure",
                "what is my tire pressure", "ضغط الكاوتش", "ضغط الاطارات" -> TYRES
                "cabin temperature", "inside temperature", "درجة الحرارة جوه العربية" -> CABIN_TEMP
                "outside temperature", "درجة الحرارة بره العربية" -> OUTSIDE_TEMP
                "lower the fan", "open the left window", "open the right window",
                "قلل سرعة المروحة", "افتح الشباك الشمال", "افتح الشباك اليمين" -> COMFORT
                else -> UNKNOWN
            }
        }
    }
}

internal fun normalizeVoicePhrase(text: String): String = text.lowercase(Locale.ROOT)
    .replace(Regex("[\\u0640\\u064B-\\u065F]"), "")
    .replace(Regex("[أإآ]"), "ا")
    .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
    .trim()

internal data class ComfortVoiceRequest(
    val mapping: ComfortMapping,
    val target: Int? = null,
    val lowerFan: Boolean = false,
) {
    companion object {
        private val spokenNumbers = mapOf(
            "zero" to 0, "صفر" to 0,
            "one" to 1, "واحد" to 1,
            "two" to 2, "اتنين" to 2, "اثنين" to 2,
            "three" to 3, "تلاتة" to 3, "ثلاثة" to 3,
            "four" to 4, "اربعة" to 4,
            "five" to 5, "خمسة" to 5,
            "six" to 6, "ستة" to 6,
            "seven" to 7, "سبعة" to 7,
        )
        private val signedValue = Regex("[+−–—-]\\s*\\p{Nd}|(?:\\bto|على)\\s*[+−–—-]\\s*\\p{L}", RegexOption.IGNORE_CASE)
        private val setTarget = Regex("(?:set|اضبط|ظبط) (?:the )?(.+) (?:to|على) ([0-9]{1,3}|\\p{L}+)")

        private val aliases = mapOf(
            ComfortMapping.AC_POWER to listOf("ac power", "a c power", "المكيف", "التكييف"),
            ComfortMapping.AC_FAN to listOf("fan", "fan level", "سرعة المروحة"),
            ComfortMapping.AC_TEMPERATURE to listOf("ac temperature", "a c temperature", "درجة حرارة المكيف"),
            ComfortMapping.FRONT_LEFT to listOf("front left window", "left window", "الشباك الشمال"),
            ComfortMapping.FRONT_RIGHT to listOf("front right window", "right window", "الشباك اليمين"),
            ComfortMapping.REAR_LEFT to listOf("rear left window", "الشباك الخلفي الشمال"),
            ComfortMapping.REAR_RIGHT to listOf("rear right window", "الشباك الخلفي اليمين"),
        )

        fun parse(text: String): ComfortVoiceRequest? {
            if (signedValue.containsMatchIn(text)) return null
            val phrase = normalizeVoicePhrase(text)
                .split(' ').joinToString(" ") { if (it == "التكيف") "التكييف" else it }
                .map { it.digitToIntOrNull()?.digitToChar() ?: it }.joinToString("")
            for ((mapping, names) in aliases) {
                for (name in names) {
                    if (phrase == "read $name" || phrase == "اقرا $name") return ComfortVoiceRequest(mapping)
                    if (mapping.isWindow) {
                        if (phrase in setOf("open the $name", "open $name", "افتح $name")) return ComfortVoiceRequest(mapping, 100)
                        if (phrase in setOf("close the $name", "close $name", "اقفل $name")) return ComfortVoiceRequest(mapping, 0)
                    }
                }
            }
            if (phrase in setOf("turn on ac", "turn on a c", "turn on the ac", "turn on the a c", "شغل المكيف", "شغل التكييف")) {
                return ComfortVoiceRequest(ComfortMapping.AC_POWER, 1)
            }
            if (phrase in setOf("turn off ac", "turn off a c", "turn off the ac", "turn off the a c", "اطفي المكيف", "اطفي التكييف", "اطفئ التكييف")) {
                return ComfortVoiceRequest(ComfortMapping.AC_POWER, 0)
            }
            if (phrase in setOf("lower the fan", "قلل سرعة المروحة")) return ComfortVoiceRequest(ComfortMapping.AC_FAN, lowerFan = true)
            val match = setTarget.matchEntire(phrase) ?: return null
            val mapping = aliases.entries.firstOrNull { match.groupValues[1] in it.value }?.key ?: return null
            val target = match.groupValues[2]
            val value = target.toIntOrNull() ?: spokenNumbers[target] ?: return null
            return if (value in mapping.range) ComfortVoiceRequest(mapping, value) else null
        }
    }
}
