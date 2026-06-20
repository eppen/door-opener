package com.dev2026.dooropener.door

/**
 * Immutable data class representing a door call event from the 联掌门户 system.
 *
 * @property eventId Unique identifier for this call event
 * @property deviceId The door device ID (which door/intercom unit)
 * @property building The building name or number
 * @property unit The unit/room number
 * @property callerInfo Description of the caller (e.g., "Visitor at Main Gate")
 * @property callTime Timestamp of when the call was initiated (epoch millis)
 * @property callType Type of call: "voice", "video", "face"
 * @property imageUrl Optional URL to a snapshot/image of the caller
 * @property rawPayload The raw event data from the source (for debugging)
 */
data class DoorEvent(
    val eventId: String,
    val deviceId: String,
    val building: String = "",
    val unit: String = "",
    val callerInfo: String = "Visitor",
    val callTime: Long = System.currentTimeMillis(),
    val callType: CallType = CallType.VOICE,
    val imageUrl: String? = null,
    val rawPayload: String? = null
) {
    enum class CallType {
        VOICE,
        VIDEO,
        FACE;

        companion object {
            fun fromString(value: String): CallType = when (value.lowercase()) {
                "video" -> VIDEO
                "face" -> FACE
                else -> VOICE
            }
        }
    }

    /** Human-readable summary for notification display */
    val summary: String
        get() = buildString {
            append(callerInfo)
            if (building.isNotBlank()) {
                append(" @ ")
                append(building)
                if (unit.isNotBlank()) {
                    append("-")
                    append(unit)
                }
            }
        }

    /** Short title for watch notification (max 28 chars for Wear Engine) */
    val shortTitle: String
        get() = if (building.isNotBlank()) "Door: $building" else "Door Call"
}
