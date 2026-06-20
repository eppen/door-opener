package com.dev2026.dooropener.bridge

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ServiceLifecycleDispatcher
import com.dev2026.dooropener.App
import com.dev2026.dooropener.door.DoorControlManager
import com.dev2026.dooropener.door.DoorEvent
import com.dev2026.dooropener.door.DoorEventParser
import com.dev2026.dooropener.door.DoorEventListener
import com.dev2026.dooropener.util.NotificationHelper
import com.dev2026.dooropener.wear.WearEngineManager
import com.dev2026.dooropener.wear.WearNotificationManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * Core bridge service that directly listens for 联掌门户 door events
 * and routes them to Huawei Fit 4 notifications via Wear Engine.
 *
 * Architecture (consolidated) — single service handles:
 * 1. Door event listening (HTTP polling / MQTT subscription)
 * 2. Event-to-notification translation (Wear Engine)
 * 3. Watch button callback → door control command
 * 4. Call session lifecycle (timeout, cleanup, fallback)
 *
 * Previously the event listening was in a separate DoorEventService with a
 * broken listener bridge. Now the listening runs inline in this service's scope.
 */
class DoorBridgeService : Service(), LifecycleOwner, DoorEventListener {

    // ---- Lifecycle ----
    private val dispatcher = ServiceLifecycleDispatcher(this)

    // ---- Coroutine infrastructure ----
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Uncaught coroutine exception: ${throwable.message}", throwable)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + exceptionHandler)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)

    // ---- Managers ----
    private val wearEngineManager = WearEngineManager(this)
    private val wearNotificationManager = WearNotificationManager(this)
    private val doorControlManager = DoorControlManager()

    // ---- HTTP client for polling ----
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // ---- State ----
    @Volatile
    private var isListening = false
    @Volatile
    private var activeSession: CallSession? = null
    @Volatile
    private var lastEventId: String? = null
    private var mqttClient: MqttClient? = null

    // ---- Thread-safe listener list ----
    private val _doorEventListeners = CopyOnWriteArrayList<DoorEventListener>()

    companion object {
        private const val TAG = "DoorBridgeService"
        const val ACTION_START = "com.dev2026.dooropener.bridge.START"
        const val ACTION_STOP = "com.dev2026.dooropener.bridge.STOP"

        private const val CALL_TIMEOUT_SECONDS = 30L
        private const val POLL_INTERVAL_MS = 3000L
        private const val WEAR_RETRY_DELAY_MS = 5000L
    }

    // =====================================================================
    //  Service lifecycle
    // =====================================================================

    override fun onCreate() {
        super.onCreate()
        dispatcher.onLifecycleEvent(Lifecycle.Event.ON_CREATE)
        Log.d(TAG, "DoorBridgeService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        dispatcher.onLifecycleEvent(Lifecycle.Event.ON_START)

        startForeground(
            NotificationHelper.SERVICE_NOTIFICATION_ID_VAL,
            NotificationHelper.buildForegroundServiceNotification(this)
        )

        when (intent?.action) {
            ACTION_STOP -> stopBridge()
            else -> startBridge()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopBridge()
        scope.cancel()
        ioScope.cancel()
        dispatcher.onLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    override val lifecycle: Lifecycle get() = dispatcher.lifecycle

    // =====================================================================
    //  Bridge start / stop
    // =====================================================================

    private fun startBridge() {
        if (isListening) {
            Log.d(TAG, "Bridge already running")
            return
        }
        isListening = true
        Log.d(TAG, "Starting door bridge...")

        // Find and connect to Fit 4
        ioScope.launch {
            val device = wearEngineManager.findFit4Device()
            if (device != null) {
                Log.d(TAG, "Fit 4 found: ${device.name}")
            } else {
                Log.w(TAG, "No Fit 4 device found. Retrying in background...")
                retryWearConnection()
            }
        }

        // Start event listening based on configured mode
        val mode = App.instance.preferencesManager.eventListenerMode
        when (mode) {
            "mqtt" -> startMqttListening()
            "http_poll" -> startHttpPolling()
            else -> startHttpPolling() // default / auto
        }
    }

    private fun stopBridge() {
        isListening = false
        activeSession = null
        mqttClient?.disconnect()
    }

    private fun retryWearConnection() {
        ioScope.launch {
            delay(WEAR_RETRY_DELAY_MS)
            wearEngineManager.findFit4Device()
        }
    }

    // =====================================================================
    //  DoorEventListener implementation
    // =====================================================================

    override fun onDoorCall(event: DoorEvent) {
        Log.d(TAG, "onDoorCall: ${event.summary}")
        handleDoorCall(event)
    }

    override fun onCallCancelled(eventId: String) {
        Log.d(TAG, "onCallCancelled: $eventId")
        if (activeSession?.event?.eventId == eventId && activeSession?.isActive == true) {
            activeSession = activeSession?.transition(CallSession.State.TIMEOUT)
        }
    }

    override fun onConnectionStateChanged(connected: Boolean) {
        Log.d(TAG, "Connection state: $connected")
        // Could update UI or notification if needed
    }

    override fun onError(error: String) {
        Log.e(TAG, "Event listener error: $error")
    }

    // =====================================================================
    //  Event listening — HTTP polling
    // =====================================================================

    private var pollingJob: Job? = null

    private fun startHttpPolling() {
        pollingJob?.cancel()
        pollingJob = ioScope.launch {
            while (isActive && isListening) {
                try {
                    pollForEvents()
                } catch (e: Exception) {
                    Log.e(TAG, "Poll error: ${e.message}")
                    onError("Poll error: ${e.message}")
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
                        lastEventId = event.eventId
                        onDoorCall(event)
                    }
                }
                onConnectionStateChanged(true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "HTTP poll failed: ${e.message}")
            onConnectionStateChanged(false)
        }
    }

    // =====================================================================
    //  Event listening — MQTT subscription
    // =====================================================================

    private fun startMqttListening() {
        ioScope.launch {
            try {
                val rawUrl = App.instance.preferencesManager.lianzhangServerUrl
                if (rawUrl.isNullOrBlank()) {
                    Log.w(TAG, "MQTT server URL not configured")
                    return@launch
                }

                // Build MQTT broker URI: use ssl:// → port 8883, tcp:// → port 1883
                val isSecure = rawUrl.startsWith("https://", ignoreCase = true) ||
                        rawUrl.startsWith("ssl://", ignoreCase = true)
                val port = if (isSecure) "8883" else "1883"
                val brokerUri = rawUrl
                    .replace("https://", "ssl://")
                    .replace("http://", "tcp://")
                    .replace("/api", "")
                    .let { if (it.contains(":")) it else "$it:$port" }

                Log.d(TAG, "Connecting to MQTT broker: $brokerUri")

                val client = MqttClient(
                    brokerUri,
                    "door-opener-${System.currentTimeMillis()}",
                    MemoryPersistence()
                )
                mqttClient = client

                val options = MqttConnectOptions().apply {
                    isCleanSession = true
                    connectionTimeout = 10
                    keepAliveInterval = 30
                    App.instance.preferencesManager.lianzhangUsername?.let { userName = it }
                    App.instance.preferencesManager.lianzhangPassword?.let { password = it.toCharArray() }
                }

                client.connect(options)
                onConnectionStateChanged(true)
                Log.d(TAG, "MQTT connected to $brokerUri")

                client.subscribe("door/call/#") { _, message ->
                    val payload = String(message.payload, Charsets.UTF_8)
                    Log.d(TAG, "MQTT received: $payload")
                    val event = DoorEventParser.parse(payload, "mqtt")
                    if (event != null) {
                        lastEventId = event.eventId
                        onDoorCall(event)
                    }
                }

                while (isActive && isListening && client.isConnected) {
                    delay(1000L)
                }

                client.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "MQTT error: ${e.message}")
                onError("MQTT: ${e.message}")
                onConnectionStateChanged(false)
            }
        }
    }

    // =====================================================================
    //  Door call → watch notification
    // =====================================================================

    private fun handleDoorCall(event: DoorEvent) {
        Log.d(TAG, "Handling door call: ${event.summary}")

        if (activeSession?.isActive == true) {
            Log.d(TAG, "Active session exists, ignoring duplicate call")
            return
        }

        val session = CallSession(event)
        activeSession = session

        val device = wearEngineManager.getConnectedDevice()

        if (device != null && device.isConnected) {
            ioScope.launch {
                val success = wearNotificationManager.sendDoorCallNotification(
                    device = device,
                    title = event.shortTitle,
                    body = event.summary,
                    callback = createNotificationCallback(event)
                )
                if (!success) {
                    Log.w(TAG, "Wear notification failed, using phone fallback")
                    showPhoneFallbackNotification(event)
                }
            }
        } else {
            Log.d(TAG, "No watch connected, using phone fallback")
            showPhoneFallbackNotification(event)
        }

        startCallTimeout(session)
    }

    private fun createNotificationCallback(
        event: DoorEvent
    ): WearNotificationManager.OnButtonCallback {
        return object : WearNotificationManager.OnButtonCallback {
            override fun onOpenDoor() {
                Log.d(TAG, "User tapped OPEN on watch")
                activeSession = activeSession?.transition(CallSession.State.OPENING)
                ioScope.launch { executeOpenDoor(event) }
            }

            override fun onReject() {
                Log.d(TAG, "User tapped REJECT on watch")
                activeSession = activeSession?.transition(CallSession.State.REJECTED)
            }

            override fun onIgnore() {
                Log.d(TAG, "User tapped IGNORE on watch")
                activeSession = activeSession?.transition(CallSession.State.IGNORED)
            }

            override fun onError(errorCode: Int, errorMessage: String) {
                Log.e(TAG, "Notification error: $errorCode - $errorMessage")
                activeSession = activeSession?.transition(CallSession.State.ERROR)
                showPhoneFallbackNotification(event)
            }
        }
    }

    private suspend fun executeOpenDoor(event: DoorEvent) {
        val result = doorControlManager.openDoor(event.deviceId)

        when (result) {
            is DoorControlManager.DoorResult.Success -> {
                Log.d(TAG, "Door opened successfully")
                activeSession = activeSession?.transition(CallSession.State.OPENED)
                wearEngineManager.getConnectedDevice()?.let { device ->
                    wearNotificationManager.sendResultNotification(
                        device, "Door Opened", "Door opened successfully"
                    )
                }
            }
            is DoorControlManager.DoorResult.Failure -> {
                Log.e(TAG, "Door open failed: ${result.error}")
                activeSession = activeSession?.transition(CallSession.State.ERROR)
                wearEngineManager.getConnectedDevice()?.let { device ->
                    wearNotificationManager.sendResultNotification(
                        device, "Open Failed", result.error.take(400)
                    )
                }
            }
            is DoorControlManager.DoorResult.Timeout -> {
                Log.e(TAG, "Door open timed out")
                activeSession = activeSession?.transition(CallSession.State.ERROR)
            }
            is DoorControlManager.DoorResult.NotConfigured -> {
                Log.w(TAG, "Door control not configured")
                activeSession = activeSession?.transition(CallSession.State.ERROR)
            }
        }
    }

    private fun showPhoneFallbackNotification(event: DoorEvent) {
        // Show local Android notification so the user can still open the door
        // even when the watch is disconnected
        Log.d(TAG, "Phone fallback notification for: ${event.summary}")
        // TODO: Wire up full phone notification with PendingIntent actions
    }

    private fun startCallTimeout(session: CallSession) {
        scope.launch {
            delay(CALL_TIMEOUT_SECONDS * 1000L)
            val current = activeSession
            if (current?.event?.eventId == session.event.eventId && current.isActive) {
                Log.d(TAG, "Call session timed out: ${session.event.eventId}")
                activeSession = current.transition(CallSession.State.TIMEOUT)
            }
        }
    }
}
