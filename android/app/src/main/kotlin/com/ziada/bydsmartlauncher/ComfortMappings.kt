package com.ziada.bydsmartlauncher

/** Assistant NG symbolic pairs. Reads can be discovered; writes require a reviewed profile. */
internal enum class ComfortMapping(
    val featureGroup: String,
    val command: String,
    val state: String,
    val range: IntRange
) {
    AC_POWER("Ac", "AC_POWER_STATE_SET", "AC_POWER_STATE", 0..1),
    AC_FAN("Ac", "AC_WIND_LEVEL_SET", "AC_WIND_LEVEL", 0..7),
    AC_TEMPERATURE("Ac", "AC_TEMP_MAIN_SET", "AC_TEMP_MAIN", 17..33),
    FRONT_LEFT("Bodywork", "BODYWORK_LF_WINDOW_TARGET_POSITION_SET", "BODYWORK_WINDOW_LEFT_FRONT_PERCENT", 0..100),
    FRONT_RIGHT("Bodywork", "BODYWORK_RF_WINDOW_TARGET_POSITION_SET", "BODYWORK_WINDOW_RIGHT_FRONT_PERCENT", 0..100),
    REAR_LEFT("Bodywork", "BODYWORK_LR_WINDOW_TARGET_POSITION_SET", "BODYWORK_WINDOW_LEFT_REAR_PERCENT", 0..100),
    REAR_RIGHT("Bodywork", "BODYWORK_RR_WINDOW_TARGET_POSITION_SET", "BODYWORK_WINDOW_RIGHT_REAR_PERCENT", 0..100);

    val commandSymbol get() = "$featureGroup.$command"
    val stateSymbol get() = "$featureGroup.$state"
    val deviceGroup get() = if (featureGroup == "Ac") "AC" else "BODYWORK"
    val deviceType get() = if (featureGroup == "Ac") 1000 else 1001
    val isWindow get() = featureGroup == "Bodywork"
    val id get() = name.lowercase(java.util.Locale.ROOT)
    val label get() = when (this) {
        AC_POWER -> "A/C power"
        AC_FAN -> "A/C fan level"
        AC_TEMPERATURE -> "A/C set temperature"
        FRONT_LEFT -> "Front left window"
        FRONT_RIGHT -> "Front right window"
        REAR_LEFT -> "Rear left window"
        REAR_RIGHT -> "Rear right window"
    }
    val arabicLabel get() = when (this) {
        AC_POWER -> "حالة التكييف"
        AC_FAN -> "سرعة المروحة"
        AC_TEMPERATURE -> "درجة حرارة المكيف"
        FRONT_LEFT -> "الشباك الأمامي الشمال"
        FRONT_RIGHT -> "الشباك الأمامي اليمين"
        REAR_LEFT -> "الشباك الخلفي الشمال"
        REAR_RIGHT -> "الشباك الخلفي اليمين"
    }
}
