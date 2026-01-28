package com.corvidlabs.algochat

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Manages PSK ratchet counter state for a peer.
 *
 * Tracks send and receive counters with a sliding window to prevent
 * replay attacks while tolerating out-of-order delivery.
 */
class PSKState(
    /** The initial pre-shared key (32 bytes). */
    val initialPSK: ByteArray,
    /** The peer's identifier (e.g., Algorand address). */
    val peerId: String,
    /** Initial send counter value. */
    sendCounter: UInt = 0u,
    /** Initial receive counter value. */
    receiveCounter: UInt = 0u
) {
    private val mutex = Mutex()

    private var _sendCounter: UInt = sendCounter
    private var _receiveCounter: UInt = receiveCounter
    private val _receivedCounters: MutableSet<UInt> = mutableSetOf()

    /** The current send counter. */
    val sendCounter: UInt get() = _sendCounter

    /** The current receive counter (highest accepted). */
    val receiveCounter: UInt get() = _receiveCounter

    /**
     * Gets the next send counter and advances it.
     *
     * @return The counter value to use for the next message
     */
    suspend fun nextSendCounter(): UInt {
        mutex.withLock {
            val current = _sendCounter
            _sendCounter = current + 1u
            return current
        }
    }

    /**
     * Validates and accepts a received counter value.
     *
     * The counter is accepted if:
     * - It has not been seen before (replay protection)
     * - It is within the acceptable window of the current receive counter
     *
     * @param counter The received counter value
     * @return True if the counter is valid and was accepted
     */
    suspend fun acceptReceiveCounter(counter: UInt): Boolean {
        mutex.withLock {
            // Check for replay
            if (counter in _receivedCounters) {
                return false
            }

            // Check window bounds
            val windowStart = if (_receiveCounter >= PSKProtocol.COUNTER_WINDOW.toUInt()) {
                _receiveCounter - PSKProtocol.COUNTER_WINDOW.toUInt()
            } else {
                0u
            }

            // Upper bound: allow up to COUNTER_WINDOW ahead of current
            val windowEnd = _receiveCounter + PSKProtocol.COUNTER_WINDOW.toUInt()

            if (counter < windowStart || counter > windowEnd) {
                return false
            }

            // Accept the counter
            _receivedCounters.add(counter)

            // Advance receive counter if needed
            if (counter >= _receiveCounter) {
                _receiveCounter = counter + 1u
            }

            // Prune old counters outside the window
            pruneReceivedCounters()

            return true
        }
    }

    /**
     * Derives the PSK for the current send counter without advancing it.
     *
     * @return 32-byte derived PSK for the current counter
     */
    fun currentSendPSK(): ByteArray {
        return PSKRatchet.derivePSKAtCounter(initialPSK, _sendCounter)
    }

    /**
     * Derives the PSK for a specific counter value.
     *
     * @param counter The counter value
     * @return 32-byte derived PSK
     */
    fun pskAtCounter(counter: UInt): ByteArray {
        return PSKRatchet.derivePSKAtCounter(initialPSK, counter)
    }

    /**
     * Resets the state to initial values.
     */
    suspend fun reset() {
        mutex.withLock {
            _sendCounter = 0u
            _receiveCounter = 0u
            _receivedCounters.clear()
        }
    }

    /**
     * Removes received counter entries that are outside the current window.
     */
    private fun pruneReceivedCounters() {
        val windowStart = if (_receiveCounter >= PSKProtocol.COUNTER_WINDOW.toUInt()) {
            _receiveCounter - PSKProtocol.COUNTER_WINDOW.toUInt()
        } else {
            0u
        }
        _receivedCounters.removeAll { it < windowStart }
    }
}
