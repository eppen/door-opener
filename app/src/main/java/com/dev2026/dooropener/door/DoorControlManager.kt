package com.dev2026.dooropener.door

import android.util.Log
import com.dev2026.dooropener.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.gson.Gson
import java.util.concurrent.TimeUnit

/**
 * Manages sending door control commands (open/close) to the 联掌门户 system.
 *
 * Supports multiple transport mechanisms:
 * - HTTP REST API (primary)
 * - MQTT publish (future)
 *
 * All methods are suspend functions for use with coroutines.
 */
class DoorControlManager {

    private val prefs = App.instance.preferencesManager

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    companion object {
        private const val TAG = "DoorControlManager"
    }

    /**
     * Result of a door control operation.
     */
    sealed class DoorResult {
        data object Success : DoorResult()
        data class Failure(val error: String, val code: Int = -1) : DoorResult()
        data object Timeout : DoorResult()
        data object NotConfigured : DoorResult()
    }

    /**
     * Sends an "open door" command to the 联掌门户 server.
     *
     * @param deviceId The door device ID to open (from the DoorEvent)
     * @return DoorResult indicating success or failure
     */
    suspend fun openDoor(deviceId: String): DoorResult = withContext(Dispatchers.IO) {
        val serverUrl = prefs.lianzhangServerUrl
        val username = prefs.lianzhangUsername

        if (serverUrl.isNullOrBlank()) {
            Log.w(TAG, "联掌门户 server not configured")
            return@withContext DoorResult.NotConfigured
        }

        val requestBody = mapOf(
            "action" to "open",
            "deviceId" to deviceId,
            "username" to (username ?: ""),
            "timestamp" to System.currentTimeMillis()
        )

        val jsonBody = gson.toJson(requestBody)
        val body = jsonBody.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$serverUrl/api/door/open")
            .post(body)
            .header("Content-Type", "application/json")
            .build()

        try {
            withTimeout(5000L) {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    Log.d(TAG, "Open door command succeeded for device: $deviceId")
                    prefs.lastOpenDoorTime = System.currentTimeMillis()
                    DoorResult.Success
                } else {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    Log.e(TAG, "Open door failed: HTTP ${response.code} - $errorBody")
                    DoorResult.Failure(
                        error = errorBody,
                        code = response.code
                    )
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.e(TAG, "Open door request timed out")
            DoorResult.Timeout
        } catch (e: Exception) {
            Log.e(TAG, "Open door request failed: ${e.message}")
            DoorResult.Failure(error = e.message ?: "Network error")
        }
    }

    /**
     * Validates that the 联掌门户 connection settings appear correct
     * by making a lightweight health check request.
     */
    suspend fun validateConnection(): DoorResult = withContext(Dispatchers.IO) {
        val serverUrl = prefs.lianzhangServerUrl
        if (serverUrl.isNullOrBlank()) return@withContext DoorResult.NotConfigured

        val request = Request.Builder()
            .url("$serverUrl/api/health")
            .get()
            .build()

        try {
            withTimeout(5000L) {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) DoorResult.Success
                else DoorResult.Failure("Server returned ${response.code}", response.code)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            DoorResult.Timeout
        } catch (e: Exception) {
            DoorResult.Failure(error = e.message ?: "Connection failed")
        }
    }
}
