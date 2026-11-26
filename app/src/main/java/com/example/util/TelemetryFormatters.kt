package com.example.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

object TelemetryFormatters {
    fun formatCoordinates(lat: Double?, lon: Double?): String {
        if (lat == null || lon == null) return "Unavailable"
        return String.format(Locale.US, "%.5f, %.5f", lat, lon)
    }

    fun formatDistanceMeters(meters: Float?, useMetric: Boolean): String {
        if (meters == null) return "Unavailable"
        return if (useMetric) {
            if (meters < 10f) String.format(Locale.US, "±%.1f m", meters)
            else "±${meters.roundToInt()} m"
        } else {
            val feet = meters * 3.28084f
            "±${feet.roundToInt()} ft"
        }
    }

    fun formatAltitude(meters: Double?, useMetric: Boolean): String {
        if (meters == null) return "Unavailable"
        return if (useMetric) {
            String.format(Locale.US, "%.1f m", meters)
        } else {
            String.format(Locale.US, "%.0f ft", meters * 3.28084)
        }
    }

    fun formatSpeedValue(speedMps: Float?, useMetric: Boolean): String {
        if (speedMps == null) return "--"
        val converted = if (useMetric) speedMps * 3.6f else speedMps * 2.23694f
        return String.format(Locale.US, "%.1f", converted)
    }

    fun speedUnitLabel(useMetric: Boolean): String = if (useMetric) "km/h" else "mph"

    fun formatAgeSeconds(ageSeconds: Long?): String {
        if (ageSeconds == null) return "No fix yet"
        return when {
            ageSeconds < 2L -> "Just now (${ageSeconds}s)"
            ageSeconds < 60L -> "${ageSeconds}s ago"
            else -> "${ageSeconds / 60L}m ${ageSeconds % 60L}s ago"
        }
    }

    fun formatTime(timestampMs: Long?): String {
        if (timestampMs == null) return "--:--:--"
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestampMs))
    }

    fun formatDateTime(timestampMs: Long): String {
        val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
        return sdf.format(Date(timestampMs))
    }
}
