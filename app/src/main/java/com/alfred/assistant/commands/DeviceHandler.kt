package com.alfred.assistant.commands

import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Handles device-related commands: flashlight, battery, time, and date.
 * All responses are styled as a proper British butler.
 */
object DeviceHandler {

    /**
     * Toggles the device flashlight (torch) on or off.
     *
     * Uses [CameraManager.setTorchMode] on the first available camera
     * that has a flash unit.
     *
     * @param turnOn `true` to turn the flashlight on, `false` to turn it off.
     * @return A butler-style response string.
     */
    fun toggleFlashlight(context: Context, turnOn: Boolean): String {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = getCameraWithFlash(cameraManager)

            if (cameraId != null) {
                cameraManager.setTorchMode(cameraId, turnOn)
                if (turnOn) {
                    "The torch has been illuminated, sir. Mind the shadows."
                } else {
                    "The torch has been extinguished, sir."
                }
            } else {
                "I'm afraid this device doesn't appear to have a flashlight, sir."
            }
        } catch (e: Exception) {
            if (turnOn) {
                "I encountered a difficulty turning on the flashlight, sir: ${e.message}"
            } else {
                "I encountered a difficulty turning off the flashlight, sir: ${e.message}"
            }
        }
    }

    /**
     * Finds the camera ID that has a flash unit available.
     */
    private fun getCameraWithFlash(cameraManager: CameraManager): String? {
        for (cameraId in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val hasFlash = characteristics.get(
                android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE
            ) ?: false
            if (hasFlash) {
                return cameraId
            }
        }
        return null
    }

    /**
     * Returns the current battery level as a butler-style response.
     *
     * Uses [BatteryManager.BATTERY_PROPERTY_CAPACITY] to get the percentage.
     */
    fun getBatteryStatus(context: Context): String {
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

            val status = when {
                batteryLevel >= 80 -> "The battery is at $batteryLevel percent, sir. Quite healthy, I must say."
                batteryLevel >= 50 -> "The battery stands at $batteryLevel percent, sir. Still plenty of charge remaining."
                batteryLevel >= 20 -> "The battery is at $batteryLevel percent, sir. You may wish to consider charging soon."
                batteryLevel >= 10 -> "I must inform you, sir, the battery is rather low at $batteryLevel percent. I'd recommend plugging in at your earliest convenience."
                else -> "Sir, the battery is critically low at $batteryLevel percent. I strongly advise immediate charging."
            }
            status
        } catch (e: Exception) {
            "I'm afraid I was unable to determine the battery level, sir: ${e.message}"
        }
    }

    /**
     * Returns the current time as a butler-style response.
     * Formatted in 12-hour time (e.g., "3:45 PM").
     */
    fun getCurrentTime(): String {
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val currentTime = timeFormat.format(Date())
        return "The time is $currentTime, sir."
    }

    /**
     * Returns the current date as a butler-style response.
     * Formatted as "EEEE, MMMM d, yyyy" (e.g., "Wednesday, November 20, 2026").
     */
    fun getCurrentDate(): String {
        val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
        val currentDate = dateFormat.format(Date())
        return "Today is $currentDate, sir."
    }
}
