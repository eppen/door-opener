package com.dev2026.dooropener.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.dev2026.dooropener.bridge.DoorBridgeService

/**
 * Receives BOOT_COMPLETED broadcast to auto-start the consolidated bridge service
 * after device reboot, ensuring door call monitoring resumes automatically.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, DoorBridgeService::class.java).apply {
                action = DoorBridgeService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
