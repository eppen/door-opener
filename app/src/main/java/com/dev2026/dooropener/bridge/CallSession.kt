package com.dev2026.dooropener.bridge

import com.dev2026.dooropener.door.DoorEvent

/**
 * Represents an active door call session.
 * Tracks the state of a door call from initiation to resolution.
 *
 * Immutable data — state transitions create new instances.
 */
data class CallSession(
    val event: DoorEvent,
    val state: State = State.RINGING,
    val createdAt: Long = System.currentTimeMillis(),
    val resolvedAt: Long = 0L
) {
    enum class State {
        /** Notification sent to watch, waiting for user response */
        RINGING,
        /** User tapped "Open" — door is being opened */
        OPENING,
        /** Door opened successfully */
        OPENED,
        /** User tapped "Reject" — call rejected */
        REJECTED,
        /** User tapped "Ignore" or notification dismissed */
        IGNORED,
        /** Call timed out without user action */
        TIMEOUT,
        /** An error occurred during processing */
        ERROR
    }

    val isActive: Boolean
        get() = state == State.RINGING || state == State.OPENING

    val durationMs: Long
        get() = if (resolvedAt > 0) resolvedAt - createdAt
        else System.currentTimeMillis() - createdAt

    fun transition(newState: State): CallSession {
        return copy(
            state = newState,
            resolvedAt = if (!newState.let { it == State.RINGING || it == State.OPENING }) {
                System.currentTimeMillis()
            } else {
                0L
            }
        )
    }
}
