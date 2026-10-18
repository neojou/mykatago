package com.neojou.mykatago.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class AppSettingsTest {
    @Test
    fun defaultsRoundTrip() {
        val decoded = decodeSettings(encodeSettings(AppSettings()))
        assertEquals(AppSettings(), decoded)
    }

    @Test
    fun missingFieldsUseDefaultsAndUnknownKeysAreIgnored() {
        val decoded = decodeSettings("""{"maxVisits":800,"extra":true}""")
        assertEquals(800, decoded.maxVisits)
        assertEquals(25, decoded.fastVisits)
        assertEquals(8.0, decoded.maxTimeSeconds)
        assertEquals(BoardToggles(), decoded.toggles)
    }
}
