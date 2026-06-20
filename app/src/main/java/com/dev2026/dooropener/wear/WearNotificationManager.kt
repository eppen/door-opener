package com.dev2026.dooropener.wear

import android.content.Context
import android.util.Log
import com.huawei.wearengine.HiWear
import com.huawei.wearengine.notify.Action
import com.huawei.wearengine.notify.Notification
import com.huawei.wearengine.notify.NotificationConstants
import com.huawei.wearengine.notify.NotificationTemplate
import com.huawei.wearengine.notify.NotifyClient
import com.huawei.wearengine.device.Device
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Manages sending template-based notifications to the Huawei Fit 4 watch
 * via Wear Engine. Supports 3-button notifications for door call scenarios.
 *
 * Button mapping:
 *   - Button 1: "Open" — triggers door opening
 *   - Button 2: "Reject" — denies the call
 *   - Button 3: "Ignore" — dismisses the notification
 */
class WearNotificationManager(context: Context) {

    private val notifyClient: NotifyClient = HiWear.getNotifyClient(context)

    companion object {
        private const val TAG = "WearNotificationMgr"
        private const val PACKAGE_NAME = "com.dev2026.dooropener"
        private const val MAX_TITLE_LENGTH = 28
        private const val MAX_BUTTON_LENGTH = 12
    }

    /**
     * Callback interface for wear notification button taps.
     */
    interface OnButtonCallback {
        /** Called when user taps "Open" on the watch */
        fun onOpenDoor()

        /** Called when user taps "Reject" on the watch */
        fun onReject()

        /** Called when user taps "Ignore" or dismisses the notification */
        fun onIgnore()

        /** Called when notification delivery fails */
        fun onError(errorCode: Int, errorMessage: String)
    }

    /**
     * Enum mapping Wear Engine button feedback codes.
     */
    enum class ButtonFeedback(val code: Int) {
        EXIT(0),
        DELETE(1),
        BUTTON_1(2),   // "Open"
        BUTTON_2(3),   // "Reject"
        BUTTON_3(4);   // "Ignore"

        companion object {
            fun fromCode(code: Int): ButtonFeedback =
                entries.firstOrNull { it.code == code } ?: EXIT
        }
    }

    /**
     * Sends a door call notification to the connected wearable.
     *
     * @param device The target wearable device (Fit 4)
     * @param title Notification title (max 28 chars)
     * @param body Notification body text (max 400 chars)
     * @param callback Called when user interacts with the notification buttons
     */
    suspend fun sendDoorCallNotification(
        device: Device,
        title: String,
        body: String,
        callback: OnButtonCallback
    ): Boolean = suspendCancellableCoroutine { continuation ->
        val builder = Notification.Builder()

        // Use 3-button template for Open / Reject / Ignore
        builder.setTemplateId(NotificationTemplate.NOTIFICATION_TEMPLATE_THREE_BUTTONS)
        builder.setPackageName(PACKAGE_NAME)

        // Title and body
        builder.setTitle(title.take(MAX_TITLE_LENGTH))
        builder.setText(body.take(400))

        // Configure buttons
        val buttonContents = HashMap<Int, String>().apply {
            put(NotificationConstants.BUTTON_ONE_CONTENT_KEY, "Open")
            put(NotificationConstants.BUTTON_TWO_CONTENT_KEY, "Reject")
            put(NotificationConstants.BUTTON_THREE_CONTENT_KEY, "Ignore")
        }
        builder.setButtonContents(buttonContents)

        // Handle button callback from the watch
        builder.setAction(object : Action {
            override fun onResult(notification: Notification?, feedback: Int) {
                Log.d(TAG, "Notification feedback: $feedback")
                when (ButtonFeedback.fromCode(feedback)) {
                    ButtonFeedback.BUTTON_1 -> callback.onOpenDoor()
                    ButtonFeedback.BUTTON_2 -> callback.onReject()
                    ButtonFeedback.BUTTON_3 -> callback.onIgnore()
                    ButtonFeedback.DELETE -> callback.onIgnore()
                    else -> { /* EXIT — do nothing */ }
                }
            }

            override fun onError(
                notification: Notification?,
                errorCode: Int,
                errorMsg: String?
            ) {
                Log.e(TAG, "Notification error: $errorCode - $errorMsg")
                callback.onError(errorCode, errorMsg ?: "Unknown error")
            }
        })

        val wearNotification = builder.build()

        if (!device.isConnected) {
            Log.w(TAG, "Device not connected, cannot send notification")
            continuation.resume(false)
            return@suspendCancellableCoroutine
        }

        notifyClient.notify(device, wearNotification)
            .addOnSuccessListener {
                if (continuation.isActive) {
                    Log.d(TAG, "Notification sent successfully to ${device.name}")
                    continuation.resume(true)
                }
            }
            .addOnFailureListener { error ->
                if (continuation.isActive) {
                    Log.e(TAG, "Failed to send notification: ${error.message}")
                    continuation.resume(false)
                }
            }

        continuation.invokeOnCancellation {
            Log.d(TAG, "sendDoorCallNotification coroutine cancelled")
        }
    }

    /**
     * Sends a simple result notification (success/failure) to the watch.
     */
    suspend fun sendResultNotification(
        device: Device,
        title: String,
        body: String
    ): Boolean = suspendCancellableCoroutine { continuation ->
        val builder = Notification.Builder()
        builder.setTemplateId(NotificationTemplate.NOTIFICATION_TEMPLATE_NO_BUTTON)
        builder.setPackageName(PACKAGE_NAME)
        builder.setTitle(title.take(MAX_TITLE_LENGTH))
        builder.setText(body.take(400))

        if (!device.isConnected) {
            continuation.resume(false)
            return@suspendCancellableCoroutine
        }

        notifyClient.notify(device, builder.build())
            .addOnSuccessListener {
                if (continuation.isActive) continuation.resume(true)
            }
            .addOnFailureListener {
                if (continuation.isActive) continuation.resume(false)
            }

        continuation.invokeOnCancellation {
            Log.d(TAG, "sendResultNotification coroutine cancelled")
        }
    }
}
