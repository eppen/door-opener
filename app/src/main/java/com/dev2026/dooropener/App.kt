package com.dev2026.dooropener

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.dev2026.dooropener.util.PreferencesManager

/**
 * Application entry point. Initializes global state, notification channels,
 * and starts the foreground service for door event listening.
 */
class App : Application() {

    lateinit var preferencesManager: PreferencesManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        preferencesManager = PreferencesManager(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        // Foreground service channel — low importance to avoid intrusive status bar icon
        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            "Door Opener Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when the door opener bridge service is running"
            setShowBadge(false)
        }

        // Door call alert channel — high importance for door calls
        val callChannel = NotificationChannel(
            CHANNEL_DOOR_CALL,
            "Door Call Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts when someone is at the door"
            enableVibration(true)
            setShowBadge(true)
        }

        manager.createNotificationChannel(serviceChannel)
        manager.createNotificationChannel(callChannel)
    }

    companion object {
        const val CHANNEL_SERVICE = "door_opener_service"
        const val CHANNEL_DOOR_CALL = "door_opener_call"

        lateinit var instance: App
            private set
    }
}
