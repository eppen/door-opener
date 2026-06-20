package com.dev2026.dooropener.door

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for DoorEventParser to ensure correct parsing of
 * various 联掌门户 event payload formats.
 */
class DoorEventParserTest {

    @Test
    fun `parse standard JSON door call event`() {
        val json = """
        {
            "eventId": "evt-001",
            "deviceId": "door-main-gate",
            "building": "Building A",
            "unit": "301",
            "callerInfo": "Visitor at Main Gate",
            "callTime": 1700000000000,
            "callType": "video"
        }
        """

        val event = DoorEventParser.parse(json, "test")

        assertNotNull("Event should not be null", event)
        assertEquals("evt-001", event?.eventId)
        assertEquals("door-main-gate", event?.deviceId)
        assertEquals("Building A", event?.building)
        assertEquals("301", event?.unit)
        assertEquals("Visitor at Main Gate", event?.callerInfo)
        assertEquals(1700000000000L, event?.callTime)
        assertEquals(DoorEvent.CallType.VIDEO, event?.callType)
    }

    @Test
    fun `parse JSON with alternative field names`() {
        val json = """
        {
            "call_id": "call-002",
            "doorId": "unit-b-door",
            "buildingName": "Tower 2",
            "roomNo": "1501",
            "visitor": "Delivery Person",
            "timestamp": 1700000001000,
            "type": "voice"
        }
        """

        val event = DoorEventParser.parse(json, "test")

        assertNotNull(event)
        assertEquals("call-002", event?.eventId)
        assertEquals("unit-b-door", event?.deviceId)
        assertEquals("Tower 2", event?.building)
        assertEquals("1501", event?.unit)
        assertEquals("Delivery Person", event?.callerInfo)
        assertEquals(DoorEvent.CallType.VOICE, event?.callType)
    }

    @Test
    fun `parse minimal JSON event`() {
        val json = """{"deviceId": "minimal-door"}"""

        val event = DoorEventParser.parse(json, "test")

        assertNotNull(event)
        assertEquals("minimal-door", event?.deviceId)
        assertEquals("Visitor", event?.callerInfo) // default
    }

    @Test
    fun `parse malformed JSON returns fallback event`() {
        val raw = "not-valid-json{"

        val event = DoorEventParser.parse(raw, "test")

        assertNotNull("Fallback event should not be null", event)
        assertEquals("unknown", event?.deviceId)
        assertEquals("Door Call", event?.callerInfo)
    }

    @Test
    fun `parse empty string returns null`() {
        val event = DoorEventParser.parse("", "test")
        assertNull("Empty string should return null", event)
    }

    @Test
    fun `callType fromString handles various inputs`() {
        assertEquals(DoorEvent.CallType.VIDEO, DoorEvent.CallType.fromString("video"))
        assertEquals(DoorEvent.CallType.VIDEO, DoorEvent.CallType.fromString("VIDEO"))
        assertEquals(DoorEvent.CallType.FACE, DoorEvent.CallType.fromString("face"))
        assertEquals(DoorEvent.CallType.VOICE, DoorEvent.CallType.fromString("voice"))
        assertEquals(DoorEvent.CallType.VOICE, DoorEvent.CallType.fromString("unknown"))
    }

    @Test
    fun `DoorEvent summary formats correctly`() {
        val event = DoorEvent(
            eventId = "test",
            deviceId = "d1",
            building = "Block C",
            unit = "402",
            callerInfo = "Guest"
        )

        assertEquals("Guest @ Block C-402", event.summary)
    }

    @Test
    fun `DoorEvent shortTitle is within Wear Engine limit`() {
        val event = DoorEvent(
            eventId = "test",
            deviceId = "d1",
            building = "Very Long Building Name That Exceeds",
            callerInfo = "Someone"
        )

        assertTrue(event.shortTitle.length <= 28)
    }

    @Test
    fun `parse notification text fallback`() {
        val event = DoorEventParser.parseFromNotificationText(
            text = "Someone is calling from the main entrance",
            title = "Door Call"
        )

        assertNotNull(event)
        assertEquals("notification", event?.deviceId)
        assertTrue(event?.callerInfo?.contains("Someone") == true)
    }

    @Test
    fun `parse notification text with blank input returns null`() {
        val event = DoorEventParser.parseFromNotificationText("", "")
        assertNull(event)
    }
}
