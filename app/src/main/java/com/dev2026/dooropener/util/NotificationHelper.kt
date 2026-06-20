package com.dev2026.dooropener.util

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.dev2026.dooropener.App
import com.dev2026.dooropener.R
import com.dev2026.dooropener.ui.MainActivity

/**
 * Utility for building Android system notifications as a fallback
 * when the wearable is not connected.
 */
object NotificationHelper {

    private const val CALL_NOTIFICATION_ID = 2001
    private const val SERVICE_NOTIFICATION_ID = 1001

    fun buildForegroundServiceNotification(context: Context): Notification {
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, App.CHANNEL_SERVICE)
            .setContentTitle("Door Opener")
            .setContentText("Waiting for door calls...")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    fun buildFallbackCallNotification(
        context: Context,
        callerInfo: String,
        onOpenAction: PendingIntent,
        onIgnoreAction: PendingIntent
    ): Notification {
        return NotificationCompat.Builder(context, App.CHANNEL_DOOR_CALL)
            .setContentTitle("Door Call")
            .setContentText(callerInfo)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setTimeoutAfter(30_000)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Ignore", onIgnoreAction)
            .addAction(android.R.drawable.ic_lock_idle_lock, "Open", onOpenAction)
            .setFullScreenIntent(onOpenAction, true)
            .build()
    }

    fun cancelCallNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(CALL_NOTIFICATION_ID)
    }

    fun showFallbackCallNotification(context: Context, notification: Notification) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(CALL_NOTIFICATION_ID, notification)
    }

    const val CALL_NOTIFICATION_ID_VAL = CALL_NOTIFICATION_ID
    const val SERVICE_NOTIFICATION_ID_VAL = SERVICE_NOTIFICATION_ID
}
