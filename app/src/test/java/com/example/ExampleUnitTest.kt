package com.example

import com.example.engine.TalabatDetectorEngine
import com.example.update.UpdateConfig
import com.example.util.TelemetryFormatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun formatCoordinates_returnsFormattedOrUnavailable() {
        assertEquals("Unavailable", TelemetryFormatters.formatCoordinates(null, null))
        assertEquals("29.37586, 47.97741", TelemetryFormatters.formatCoordinates(29.37586, 47.97741))
    }

    @Test
    fun formatSpeedValue_convertsMetricAndImperial() {
        // 10 m/s == 36.0 km/h == 22.4 mph
        assertEquals("36.0", TelemetryFormatters.formatSpeedValue(10f, useMetric = true))
        assertEquals("22.4", TelemetryFormatters.formatSpeedValue(10f, useMetric = false))
    }

    @Test
    fun formatDistanceMeters_convertsMetricAndImperial() {
        assertEquals("±5.5 m", TelemetryFormatters.formatDistanceMeters(5.5f, useMetric = true))
        assertEquals("±18 ft", TelemetryFormatters.formatDistanceMeters(5.5f, useMetric = false))
    }

    @Test
    fun updateConfig_detectsPlaceholderVsConfiguredUrls() {
        assertFalse(UpdateConfig.isConfigured(UpdateConfig.VERSION_JSON_URL))
        assertTrue(UpdateConfig.isConfigured("https://raw.githubusercontent.com/acme/rider-booster/main/version.json"))
    }

    @Test
    fun talabatLabelMatcher_neverMatchesCustomerAppAndMatchesRiderApps() {
        assertFalse(TalabatDetectorEngine.matchesTalabatRiderLabel("talabat", "com.talabat"))
        assertFalse(TalabatDetectorEngine.matchesTalabatRiderLabel("Talabat Food", "com.talabat"))
        assertTrue(TalabatDetectorEngine.matchesTalabatRiderLabel("Talabat Rider", "com.talabat.rider"))
        assertTrue(TalabatDetectorEngine.matchesTalabatRiderLabel("Roadrunner Courier", "com.deliveryhero.talabat.courier"))
    }
}
