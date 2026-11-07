package com.ziada.bydsmartlauncher

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceCommandTest {
    @Test fun recognizesShortEnglishAndEgyptianArabicQueries() {
        assertEquals(VoiceCommand.BATTERY, VoiceCommand.parse("What's my battery level?"))
        assertEquals(VoiceCommand.BATTERY, VoiceCommand.parse("نِسبة شحن البطارية؟"))
        assertEquals(VoiceCommand.ODOMETER, VoiceCommand.parse("العربية ماشية كام؟"))
        assertEquals(VoiceCommand.TYRES, VoiceCommand.parse("ضغط الإطارات؟"))
    }

    @Test fun ambiguousOrCombinedPhrasesDoNotResolve() {
        assertEquals(VoiceCommand.COMFORT, VoiceCommand.parse("افتح الشباك اليمين"))
        assertEquals(VoiceCommand.UNKNOWN, VoiceCommand.parse("don't open the right window"))
        assertEquals(VoiceCommand.UNKNOWN, VoiceCommand.parse("battery and open the left window"))
    }
}
