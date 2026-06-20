package com.dev2026.dooropener.door

import com.google.gson.Gson
import com.google.gson.JsonParser
import android.util.Log
import java.util.UUID

/**
 * Parses raw event data from various sources (MQTT, HTTP, notification listener)
 * into a unified [DoorEvent] model.
 *
 * Supported formats:
 * - MQTT JSON payloads
 * - HTTP JSON responses
 * - Android notification content strings (as fallback)
 */
object DoorEventParser {

    private const val TAG = "DoorEventParser"
    private val gson = Gson()

    /**
     * Attempts to parse a JSON string into a DoorEvent.
     * Handles multiple known 联掌门户 payload formats.
     */
    fun parse(raw: String, source: String = "unknown"): DoorEvent? {
        return try {
            val json = JsonParser.parseString(raw).asJsonObject

            DoorEvent(
                eventId = json.get("eventId")?.asString
                    ?: json.get("call_id")?.asString
                    ?: json.get("id")?.asString
                    ?: UUID.randomUUID().toString(),
                deviceId = json.get("deviceId")?.asString
                    ?: json.get("device_id")?.asString
                    ?: json.get("doorId")?.asString
                    ?: "unknown",
                building = json.get("building")?.asString
                    ?: json.get("buildingName")?.asString
                    ?: "",
                unit = json.get("unit")?.asString
                    ?: json.get("roomNo")?.asString
                    ?: "",
                callerInfo = json.get("callerInfo")?.asString
                    ?: json.get("caller")?.asString
                    ?: json.get("visitor")?.asString
                    ?: "Visitor",
                callTime = json.get("callTime")?.asLong
                    ?: json.get("timestamp")?.asLong
                    ?: System.currentTimeMillis(),
                callType = DoorEvent.CallType.fromString(
                    json.get("callType")?.asString
                        ?: json.get("type")?.asString
                        ?: "voice"
                ),
                imageUrl = json.get("imageUrl")?.asString
                    ?: json.get("snapshot")?.asString,
                rawPayload = raw
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse event from $source: ${e.message}")
            // Fallback: create minimal event with raw data preserved
            createFallbackEvent(raw, source)
        }
    }

    /**
     * Parses a notification text string as a fallback when JSON parsing fails.
     * Example input: "Someone is calling from Building A Room 301"
     */
    fun parseFromNotificationText(text: String, title: String = ""): DoorEvent? {
        if (text.isBlank() && title.isBlank()) return null

        val combined = "$title $text".trim()
        if (combined.isBlank()) return null

        return DoorEvent(
            eventId = UUID.randomUUID().toString(),
            deviceId = "notification",
            callerInfo = combined.take(200),
            rawPayload = "notification:$combined"
        )
    }

    /**
     * Creates a minimal DoorEvent when full parsing is impossible.
     */
    private fun createFallbackEvent(raw: String, source: String): DoorEvent? {
        if (raw.isBlank()) return null
        return DoorEvent(
            eventId = UUID.randomUUID().toString(),
            deviceId = "unknown",
            callerInfo = "Door Call",
            rawPayload = raw
        )
    }
}
