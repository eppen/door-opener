package com.dev2026.dooropener.wear

/**
 * Immutable data class representing a paired wearable device.
 */
data class WearDeviceInfo(
    val deviceId: String,
    val deviceName: String,
    val deviceType: Int,
    val isConnected: Boolean,
    val batteryLevel: Int = -1
) {
    val isFit4: Boolean
        get() = deviceName.contains("Fit 4", ignoreCase = true) ||
                deviceName.contains("FIT-4", ignoreCase = true)

    val isLiteWearable: Boolean
        get() = deviceType == DEVICE_TYPE_LITE_WATCH

    companion object {
        /** Lite wearable device type identifier from Wear Engine */
        const val DEVICE_TYPE_LITE_WATCH = 12
    }
}
