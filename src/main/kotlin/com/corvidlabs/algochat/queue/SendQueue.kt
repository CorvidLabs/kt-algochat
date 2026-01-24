package com.corvidlabs.algochat.queue

import com.corvidlabs.algochat.models.PendingMessage
import com.corvidlabs.algochat.models.PendingStatus
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Configuration for the send queue.
 */
data class QueueConfig(
    /** Maximum number of retry attempts. */
    val maxRetries: Int = 3,
    /** Delay between retry attempts. */
    val retryDelay: Duration = Duration.ofSeconds(5),
    /** Maximum queue size. */
    val maxQueueSize: Int = 100
)

/**
 * Exception thrown when the queue is full.
 */
class QueueFullException : Exception("Queue is full")

/**
 * Exception thrown when a message is not found.
 */
class MessageNotFoundException(val messageId: String) :
    Exception("Message not found: $messageId")

/**
 * A queue for managing pending outgoing messages.
 */
class SendQueue(
    private val config: QueueConfig = QueueConfig()
) {
    private val queue = ConcurrentLinkedDeque<PendingMessage>()

    /** Enqueues a new message for sending. */
    suspend fun enqueue(message: PendingMessage) {
        if (queue.size >= config.maxQueueSize) {
            // Remove oldest failed messages that can't be retried
            queue.removeIf { it.status == PendingStatus.FAILED && !it.canRetry(config.maxRetries) }

            if (queue.size >= config.maxQueueSize) {
                throw QueueFullException()
            }
        }

        queue.addLast(message)
    }

    /** Returns the next message ready for sending. */
    suspend fun nextPending(): PendingMessage? =
        queue.find { it.status == PendingStatus.PENDING }

    /** Returns all pending messages. */
    suspend fun allPending(): List<PendingMessage> =
        queue.filter { it.status == PendingStatus.PENDING }

    /** Returns messages ready for retry. */
    suspend fun readyForRetry(): List<PendingMessage> {
        val now = Instant.now()

        return queue.filter { msg ->
            if (!msg.canRetry(config.maxRetries)) {
                return@filter false
            }

            val lastAttempt = msg.lastAttempt ?: return@filter true
            Duration.between(lastAttempt, now) >= config.retryDelay
        }
    }

    /** Marks a message as currently sending. */
    suspend fun markSending(id: String) {
        val msg = findMessage(id) ?: throw MessageNotFoundException(id)
        msg.markSending()
    }

    /** Marks a message as successfully sent. */
    suspend fun markSent(id: String) {
        val msg = findMessage(id) ?: throw MessageNotFoundException(id)
        msg.markSent()
    }

    /** Marks a message as failed with an error. */
    suspend fun markFailed(id: String, error: String) {
        val msg = findMessage(id) ?: throw MessageNotFoundException(id)
        msg.markFailed(error)
    }

    /** Removes a message from the queue. */
    suspend fun remove(id: String): PendingMessage? {
        val msg = findMessage(id) ?: return null
        queue.remove(msg)
        return msg
    }

    /** Removes all sent messages from the queue. */
    suspend fun pruneSent() {
        queue.removeIf { it.status == PendingStatus.SENT }
    }

    /** Removes all messages that have exceeded max retries. */
    suspend fun pruneFailed() {
        queue.removeIf { it.status == PendingStatus.FAILED && !it.canRetry(config.maxRetries) }
    }

    /** Clears all messages from the queue. */
    suspend fun clear() {
        queue.clear()
    }

    /** Returns the number of messages in the queue. */
    val length: Int get() = queue.size

    /** Returns true if the queue is empty. */
    val isEmpty: Boolean get() = queue.isEmpty()

    /** Returns the number of pending messages. */
    suspend fun pendingCount(): Int =
        queue.count { it.status == PendingStatus.PENDING }

    /** Returns the number of failed messages. */
    suspend fun failedCount(): Int =
        queue.count { it.status == PendingStatus.FAILED }

    /** Returns messages for a specific recipient. */
    suspend fun messagesFor(recipient: String): List<PendingMessage> =
        queue.filter { it.recipient == recipient }

    /** Resets a failed message to pending status for retry. */
    suspend fun resetForRetry(id: String) {
        val msg = findMessage(id) ?: throw MessageNotFoundException(id)

        if (!msg.canRetry(config.maxRetries)) {
            throw IllegalStateException("Message has exceeded max retries")
        }

        msg.status = PendingStatus.PENDING
    }

    private fun findMessage(id: String): PendingMessage? =
        queue.find { it.id == id }
}
