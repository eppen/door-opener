package com.dev2026.dooropener.door

/**
 * Interface for listening to door call events from 联掌门户.
 * Implementations bridge the raw events into the app's business logic.
 */
interface DoorEventListener {

    /**
     * Called when a door call event is received (someone is at the door).
     *
     * @param event The parsed door event with caller details
     */
    fun onDoorCall(event: DoorEvent)

    /**
     * Called when a door call is cancelled or times out.
     *
     * @param eventId The ID of the cancelled call event
     */
    fun onCallCancelled(eventId: String)

    /**
     * Called when the connection to the event source changes.
     *
     * @param connected true if connected to the event source, false otherwise
     */
    fun onConnectionStateChanged(connected: Boolean)

    /**
     * Called when an error occurs in the event source.
     *
     * @param error Description of the error
     */
    fun onError(error: String)
}
