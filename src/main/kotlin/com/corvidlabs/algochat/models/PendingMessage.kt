package com.corvidlabs.algochat.models

import java.time.Instant
import java.util.UUID

/**
 * Status of a pending message in the send queue.
 */
enum class PendingStatus {
    /** Waiting to be sent. */
    PENDING,
    /** Currently being sent. */
    SENDING,
    /** Send attempt failed. */
    FAILED,
    /** Successfully sent. */
    SENT
}

/**
 * A message queued for sending (for offline support).
 */
data class PendingMessage(
    /** Unique identifier. */
    val id: String,
    /** Recipient's Algorand address. */
    val recipient: String,
    /** Message content. */
    val content: String,
    /** Reply context if replying. */
    val replyContext: ReplyContext?,
    /** When the message was created. */
    val createdAt: Instant,
    /** Number of retry attempts. */
    var retryCount: Int = 0,
    /** Last attempt time. */
    var lastAttempt: Instant? = null,
    /** Current status. */
    var status: PendingStatus = PendingStatus.PENDING,
    /** Last error message. */
    var lastError: String? = null
) {
    /** Mark as currently sending. */
    fun markSending() {
        status = PendingStatus.SENDING
        lastAttempt = Instant.now()
    }

    /** Mark as failed with an error. */
    fun markFailed(error: String) {
        status = PendingStatus.FAILED
        retryCount += 1
        lastError = error
    }

    /** Mark as successfully sent. */
    fun markSent() {
        status = PendingStatus.SENT
    }

    /** Whether the message can be retried. */
    fun canRetry(maxRetries: Int): Boolean =
        retryCount < maxRetries && status == PendingStatus.FAILED

    companion object {
        /** Creates a new pending message. */
        fun create(
            recipient: String,
            content: String,
            replyContext: ReplyContext? = null
        ): PendingMessage {
            return PendingMessage(
                id = UUID.randomUUID().toString(),
                recipient = recipient,
                content = content,
                replyContext = replyContext,
                createdAt = Instant.now()
            )
        }
    }
}
