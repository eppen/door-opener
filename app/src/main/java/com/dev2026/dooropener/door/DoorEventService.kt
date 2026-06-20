package com.dev2026.dooropener.door

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ServiceLifecycleDispatcher
import com.dev2026.dooropener.App
import com.dev2026.dooropener.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Foreground service that listens for door call events from 联掌门户.
 *
 * Supports multiple listening modes:
 * - HTTP polling: periodically checks an API endpoint for new call events
 * - MQTT subscription: subscribes to MQTT topics (implemented via Paho)
 * - Notification listener: intercepts notifications from 联掌门户 app (future)
 *
 * This service runs in the foreground to prevent Android from killing it.
 */
class DoorEventService : Service(), LifecycleOwner {

    private val dispatcher = ServiceLifecycleDispatcher(this)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isListening = false
    private var listeningMode: String = "auto"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private var lastEventId: String? = null

    val doorEventListeners = mutableListOf<DoorEventListener>()

    companion object {
        private const val TAG = "DoorEventService"
        private const val POLL_INTERVAL_MS = 3000L  // Poll every 3 seconds
        const val ACTION_START = "com.dev2026.dooropener.START_LISTENING"
        const val ACTION_STOP = "com.dev2026.dooropener.STOP_LISTENING"
    }

    override fun onCreate() {
        super.onCreate()
        dispatcher.onLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        dispatcher.onLifecycleEvent(Lifecycle.Event.ON_START)
        startForeground(
            NotificationHelper.SERVICE_NOTIFICATION_ID_VAL,
            NotificationHelper.buildForegroundServiceNotification(this)
        )

        when (intent?.action) {
            ACTION_STOP -> stopListening()
            else -> startListening()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopListening()
        scope.cancel()
        dispatcher.onLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    val lifecycle: Lifecycle get() = dispatcher.lifecycle

    // ---- Listener management ----

    fun addListener(listener: DoorEventListener) {
        if (!doorEventListeners.contains(listener)) {
            doorEventListeners.add(listener)
        }
    }

    fun removeListener(listener: DoorEventListener) {
        doorEventListeners.remove(listener)
    }

    private fun notifyCall(event: DoorEvent) {
        lastEventId = event.eventId
        doorEventListeners.forEach { listener ->
            try {
                listener.onDoorCall(event)
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying listener: ${e.message}")
            }
        }
    }

    private fun notifyConnectionState(connected: Boolean) {
        doorEventListeners.forEach { listener ->
            try {
                listener.onConnectionStateChanged(connected)
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying connection state: ${e.message}")
            }
        }
    }

    private fun notifyError(error: String) {
        doorEventListeners.forEach { listener ->
            try {
                listener.onError(error)
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying error: ${e.message}")
            }
        }
    }

    // ---- Listening modes ----

    private fun startListening() {
        if (isListening) return
        isListening = true
        listeningMode = App.instance.preferencesManager.eventListenerMode

        Log.d(TAG, "Starting door event listener in mode: $listeningMode")

        when (listeningMode) {
            "mqtt" -> startMqttListening()
            "http_poll" -> startHttpPolling()
            "auto" -> startHttpPolling() // Default to HTTP polling
            else -> startHttpPolling()
        }
    }

    private fun stopListening() {
        isListening = false
        Log.d(TAG, "Stopping door event listener")
    }

    /**
     * HTTP polling mode: periodically checks the 联掌门户 API for new call events.
     */
    private fun startHttpPolling() {
        scope.launch {
            while (isActive && isListening) {
                try {
                    pollForEvents()
                } catch (e: Exception) {
                    Log.e(TAG, "Poll error: ${e.message}")
                    notifyError("Poll error: ${e.message}")
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun pollForEvents() {
        val serverUrl = App.instance.preferencesManager.lianzhangServerUrl
        if (serverUrl.isNullOrBlank()) return

        val request = Request.Builder()
            .url("$serverUrl/api/call/events?lastId=${lastEventId ?: ""}")
            .get()
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return
                if (body.isNotBlank() && body != "null" && body != "[]") {
                    val event = DoorEventParser.parse(body, "http_poll")
                    if (event != null && event.eventId != lastEventId) {
                        notifyCall(event)
                    }
                }
                notifyConnectionState(true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "HTTP poll failed: ${e.message}")
            notifyConnectionState(false)
        }
    }

    /**
     * MQTT listening mode: subscribes to 联掌门户 MQTT topics.
     * Uses Eclipse Paho MQTT client.
     */
    private fun startMqttListening() {
        scope.launch {
            try {
                val serverUrl = App.instance.preferencesManager.lianzhangServerUrl
                if (serverUrl.isNullOrBlank()) {
                    Log.w(TAG, "MQTT server URL not configured")
                    return@launch
                }

                // MQTT broker URI — typically tcp://<host>:1883 or ssl://<host>:8883
                val brokerUri = serverUrl
                    .replace("https://", "ssl://")
                    .replace("http://", "tcp://")
                    .replace("/api", "") + ":1883"

                Log.d(TAG, "Connecting to MQTT broker: $brokerUri")

                val client = org.eclipse.paho.client.mqttv3.MqttClient(
                    brokerUri,
                    "door-opener-${System.currentTimeMillis()}",
                    org.eclipse.paho.client.mqttv3.persist.MemoryPersistence()
                )

                val options = org.eclipse.paho.client.mqttv3.MqttConnectOptions().apply {
                    isCleanSession = true
                    connectionTimeout = 10
                    keepAliveInterval = 30
                    userName = App.instance.preferencesManager.lianzhangUsername
                    password = App.instance.preferencesManager.lianzhangPassword?.toCharArray()
                }

                client.connect(options)
                notifyConnectionState(true)
                Log.d(TAG, "MQTT connected")

                client.subscribe("door/call/#") { _, message ->
                    val payload = String(message.payload, Charsets.UTF_8)
                    Log.d(TAG, "MQTT received: $payload")
                    val event = DoorEventParser.parse(payload, "mqtt")
                    if (event != null) {
                        notifyCall(event)
                    }
                }

                // Keep connection alive
                while (isActive && isListening && client.isConnected) {
                    delay(1000)
                }

                client.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "MQTT error: ${e.message}")
                notifyError("MQTT: ${e.message}")
                notifyConnectionState(false)
            }
        }
    }
}
