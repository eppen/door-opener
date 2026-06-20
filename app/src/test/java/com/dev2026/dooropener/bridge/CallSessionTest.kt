package com.dev2026.dooropener.bridge

import com.dev2026.dooropener.door.DoorEvent
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for CallSession state management.
 * Verifies correct state transitions and immutability.
 */
class CallSessionTest {

    private val sampleEvent = DoorEvent(
        eventId = "test-event",
        deviceId = "door-1",
        building = "A",
        callerInfo = "Visitor"
    )

    @Test
    fun `new session starts in RINGING state`() {
        val session = CallSession(sampleEvent)
        assertEquals(CallSession.State.RINGING, session.state)
        assertTrue(session.isActive)
    }

    @Test
    fun `transition from RINGING to OPENING is active`() {
        val session = CallSession(sampleEvent)
        val opened = session.transition(CallSession.State.OPENING)
        assertEquals(CallSession.State.OPENING, opened.state)
        assertTrue(opened.isActive)
    }

    @Test
    fun `transition to OPENED resolves the session`() {
        val session = CallSession(sampleEvent)
        val opened = session.transition(CallSession.State.OPENED)
        assertEquals(CallSession.State.OPENED, opened.state)
        assertFalse(opened.isActive)
        assertTrue(opened.resolvedAt > 0)
    }

    @Test
    fun `transition to REJECTED is not active`() {
        val session = CallSession(sampleEvent)
        val rejected = session.transition(CallSession.State.REJECTED)
        assertEquals(CallSession.State.REJECTED, rejected.state)
        assertFalse(rejected.isActive)
    }

    @Test
    fun `transition to IGNORED is not active`() {
        val session = CallSession(sampleEvent)
        val ignored = session.transition(CallSession.State.IGNORED)
        assertEquals(CallSession.State.IGNORED, ignored.state)
        assertFalse(ignored.isActive)
    }

    @Test
    fun `transition to TIMEOUT is not active`() {
        val session = CallSession(sampleEvent)
        val timeout = session.transition(CallSession.State.TIMEOUT)
        assertEquals(CallSession.State.TIMEOUT, timeout.state)
        assertFalse(timeout.isActive)
    }

    @Test
    fun `transition to ERROR is not active`() {
        val session = CallSession(sampleEvent)
        val error = session.transition(CallSession.State.ERROR)
        assertEquals(CallSession.State.ERROR, error.state)
        assertFalse(error.isActive)
    }

    @Test
    fun `original session is not mutated`() {
        val session = CallSession(sampleEvent)
        session.transition(CallSession.State.OPENED)

        // Original should still be RINGING (immutability)
        assertEquals(CallSession.State.RINGING, session.state)
        assertTrue(session.isActive)
    }

    @Test
    fun `duration is calculated correctly`() {
        val session = CallSession(
            event = sampleEvent,
            state = CallSession.State.OPENED,
            createdAt = 1000L,
            resolvedAt = 5000L
        )

        assertEquals(4000L, session.durationMs)
    }

    @Test
    fun `active session duration uses current time`() {
        val session = CallSession(sampleEvent)
        assertTrue(session.durationMs >= 0)
    }
}
