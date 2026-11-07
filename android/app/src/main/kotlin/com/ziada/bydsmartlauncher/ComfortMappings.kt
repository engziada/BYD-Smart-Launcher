package com.ziada.bydsmartlauncher

/** Assistant NG symbolic pairs. The launcher only reads state during commissioning. */
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
}
