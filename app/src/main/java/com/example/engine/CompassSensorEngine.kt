package com.example.engine

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.domain.model.CompassState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.roundToInt

class CompassSensorEngine(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val _state = MutableStateFlow(
        CompassState(hasCompassSensors = rotationSensor != null || (accelerometer != null && magnetometer != null))
    )
    val state: StateFlow<CompassState> = _state.asStateFlow()

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private val lastAccel = FloatArray(3)
    private val lastMag = FloatArray(3)
    private var hasAccel = false
    private var hasMag = false
    private var smoothedAzimuth: Float? = null
    private var isListening = false

    fun start() {
        val hasSensors = rotationSensor != null || (accelerometer != null && magnetometer != null)
        if (!hasSensors) {
            _state.update { it.copy(hasCompassSensors = false, isActive = false) }
            return
        }
        if (isListening) return

        if (rotationSensor != null) {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
            magnetometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        }
        isListening = true
        _state.update { it.copy(hasCompassSensors = true, isActive = true) }
    }

    fun stop() {
        if (isListening) {
            sensorManager.unregisterListener(this)
            isListening = false
        }
        _state.update { it.copy(isActive = false) }
    }

    override fun onSensorChanged(event: SensorEvent) {
        var azimuthDeg: Float? = null
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                azimuthDeg = ((Math.toDegrees(orientationAngles[0].toDouble()) + 360.0) % 360.0).toFloat()
            }
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, lastAccel, 0, 3)
                hasAccel = true
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, lastMag, 0, 3)
                hasMag = true
            }
        }

        if (azimuthDeg == null && hasAccel && hasMag) {
            if (SensorManager.getRotationMatrix(rotationMatrix, null, lastAccel, lastMag)) {
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                azimuthDeg = ((Math.toDegrees(orientationAngles[0].toDouble()) + 360.0) % 360.0).toFloat()
            }
        }

        azimuthDeg?.let { raw ->
            val prev = smoothedAzimuth
            val smoothed = if (prev == null) {
                raw
            } else {
                var delta = raw - prev
                if (delta > 180f) delta -= 360f
                if (delta < -180f) delta += 360f
                ((prev + 0.18f * delta) + 360f) % 360f
            }
            smoothedAzimuth = smoothed
            _state.update {
                it.copy(
                    azimuthDegrees = smoothed,
                    cardinalDirection = toCardinal(smoothed)
                )
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        val needsCal = accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE ||
            accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW
        _state.update {
            it.copy(sensorAccuracy = accuracy, needsCalibration = needsCal)
        }
    }

    private fun toCardinal(deg: Float): String {
        val dirs = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val index = ((deg / 45f).roundToInt()) % 8
        return dirs[index]
    }
}
