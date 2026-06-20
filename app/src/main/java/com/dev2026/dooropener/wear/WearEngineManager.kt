package com.dev2026.dooropener.wear

import android.content.Context
import android.util.Log
import com.huawei.wearengine.HiWear
import com.huawei.wearengine.device.Device
import com.huawei.wearengine.device.DeviceClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Manages Huawei Wear Engine device discovery and connection.
 * Encapsulates all Wear Engine API interactions for the Fit 4 wearable.
 *
 * Uses Huawei Wear Engine SDK to communicate with the paired smartwatch
 * through the Huawei Health app bridge.
 */
class WearEngineManager(context: Context) {

    private val deviceClient: DeviceClient = HiWear.getDeviceClient(context)
    private var connectedDevice: Device? = null

    companion object {
        private const val TAG = "WearEngineManager"
    }

    /**
     * Checks whether Wear Engine is available on this device.
     * Requires Huawei Health app to be installed and HMS Core.
     */
    fun isWearEngineAvailable(context: Context): Boolean {
        return try {
            HiWear.getApiLevel() > 0
        } catch (e: Exception) {
            Log.w(TAG, "Wear Engine not available: ${e.message}")
            false
        }
    }

    /**
     * Retrieves the list of paired wearable devices.
     * Suspends until devices are fetched or an error occurs.
     *
     * @return List of paired devices, or empty list if none found.
     */
    suspend fun getPairedDevices(): List<Device> = suspendCancellableCoroutine { continuation ->
        val bondedDevices = deviceClient.bondedDevices
        bondedDevices
            .addOnSuccessListener { devices ->
                if (continuation.isActive) {
                    Log.d(TAG, "Found ${devices.size} paired device(s)")
                    continuation.resume(devices ?: emptyList())
                }
            }
            .addOnFailureListener { error ->
                if (continuation.isActive) {
                    Log.e(TAG, "Failed to get paired devices: ${error.message}")
                    continuation.resume(emptyList())
                }
            }
        continuation.invokeOnCancellation {
            // Guard: isActive checks above prevent resume on cancelled continuation.
            // Wear Engine Task listeners are fire-and-forget — no removeListener API.
            Log.d(TAG, "getPairedDevices coroutine cancelled")
        }
    }

    /**
     * Searches for a Fit 4 device among the paired devices.
     *
     * @return The Fit 4 Device if found and connected, null otherwise.
     */
    suspend fun findFit4Device(): Device? {
        val devices = getPairedDevices()
        val fit4 = devices.firstOrNull { device ->
            val name = device.name ?: ""
            name.contains("Fit 4", ignoreCase = true) ||
                name.contains("FIT-4", ignoreCase = true)
        }
        if (fit4 != null) {
            connectedDevice = fit4
            Log.d(TAG, "Found Fit 4: ${fit4.name} (${fit4.id})")
        } else {
            Log.w(TAG, "No Fit 4 found among ${devices.size} paired devices")
        }
        return fit4
    }

    /**
     * Returns the currently connected wearable device, or null.
     */
    fun getConnectedDevice(): Device? = connectedDevice

    /**
     * Checks if a wearable device is currently connected and reachable.
     */
    fun isDeviceConnected(): Boolean {
        return connectedDevice?.isConnected == true
    }

    /**
     * Resolves device name for display purposes.
     */
    fun getDeviceDisplayName(): String {
        return connectedDevice?.name ?: "No device"
    }

    /**
     * Maps a Wear Engine Device to our internal WearDeviceInfo model.
     */
    fun mapToDeviceInfo(device: Device): WearDeviceInfo {
        return WearDeviceInfo(
            deviceId = device.id ?: "unknown",
            deviceName = device.name ?: "Unknown",
            deviceType = device.type,
            isConnected = device.isConnected
        )
    }
}
