package com.ziada.bydsmartlauncher

import java.util.Locale

internal enum class VoiceCommand {
    BATTERY, ODOMETER, TYRES, CABIN_TEMP, OUTSIDE_TEMP, COMFORT, UNKNOWN;

    companion object {
        fun parse(text: String): VoiceCommand {
            val phrase = text.lowercase(Locale.ROOT)
                .replace(Regex("[\\u0640\\u064B-\\u065F]"), "")
                .replace(Regex("[أإآ]"), "ا")
                .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
                .trim()
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
