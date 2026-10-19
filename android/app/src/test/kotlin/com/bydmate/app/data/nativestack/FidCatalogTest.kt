package com.bydmate.app.data.nativestack

import org.junit.Assert.*
import org.junit.Test

class FidCatalogTest {
    @Test fun readsDynamicDeviceAndSymbolAddresses() {
        val dump = """
            BYDAutoConstants.BYDAUTO_DEVICE_STATISTIC=1014
            Statistic.STATISTIC_ELEC_PERCENTAGE=1246777400
            Statistic.STATISTIC_TOTAL_MILEAGE=98765
            BYDAutoFeatureIds.STATISTIC_TOTAL_MILEAGE=1234
        """.trimIndent()
        val catalog = FidCatalog.parse(dump)
        assertEquals(1014, catalog.deviceOf("STATISTIC"))
        assertEquals(1246777400, catalog.fidOf("Statistic.STATISTIC_ELEC_PERCENTAGE"))
        assertEquals(98765, catalog.fidOf("Statistic.STATISTIC_TOTAL_MILEAGE"))
        assertNull(catalog.fidOf("BYDAutoFeatureIds.STATISTIC_TOTAL_MILEAGE"))
        assertEquals(2, catalog.totalSymbols)
    }

    @Test fun ambiguousSymbolsCannotBeTrusted() {
        val dump = """
            Statistic.STATISTIC_ELEC_PERCENTAGE=100
            Statistic.STATISTIC_ELEC_PERCENTAGE=200
            Speed.SPEED_AUTO_SPEED=300
        """.trimIndent()
        val catalog = FidCatalog.parse(dump)
        assertNull(catalog.fidOf("Statistic.STATISTIC_ELEC_PERCENTAGE"))
        assertEquals(300, catalog.fidOf("Speed.SPEED_AUTO_SPEED"))
    }

    @Test fun rejectsOverflowAndInvalidLines() {
        val dump = """
            Statistic.BAD=999999999999999999999999
            Statistic.ALSO_BAD=2147483648
            Statistic.NO_VALUE=
            Header line
            BYDAutoConstants.BYDAUTO_DEVICE_STATISTIC=1009
            Statistic.GOOD=-123
        """.trimIndent()
        val catalog = FidCatalog.parse(dump)
        assertEquals(-123, catalog.fidOf("Statistic.GOOD"))
        assertNull(catalog.fidOf("Statistic.ALSO_BAD"))
        assertEquals(1009, catalog.deviceOf("STATISTIC"))
        assertEquals(1, catalog.totalSymbols)
    }
}
