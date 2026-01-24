package com.corvidlabs.algochat.models

import java.time.Instant

/**
 * Direction of a message relative to the current user.
 */
enum class MessageDirection {
    /** Message was sent by the current user. */
    SENT,
    /** Message was received by the current user. */
    RECEIVED
}

/**
 * Context for a reply message, linking it to the original.
 */
data class ReplyContext(
    /** Transaction ID of the original message. */
    val messageId: String,
    /** Preview of the original message (truncated). */
    val preview: String
) {
    companion object {
        /**
         * Creates a reply context from a message, truncating the preview.
         */
        fun fromMessage(message: Message, maxLength: Int = 80): ReplyContext {
            val preview = if (message.content.length > maxLength) {
                message.content.take(maxLength - 3) + "..."
            } else {
                message.content
            }
            return ReplyContext(messageId = message.id, preview = preview)
        }
    }
}

/**
 * A chat message between Algorand addresses.
 */
data class Message(
    /** Unique identifier (transaction ID). */
    val id: String,
    /** Sender's Algorand address. */
    val sender: String,
    /** Recipient's Algorand address. */
    val recipient: String,
    /** Decrypted message content. */
    val content: String,
    /** Timestamp when the message was confirmed on-chain. */
    val timestamp: Instant,
    /** The round in which the transaction was confirmed. */
    val confirmedRound: Long,
    /** Message direction relative to the current user. */
    val direction: MessageDirection,
    /** Reply context if this message is a reply. */
    val replyContext: ReplyContext? = null
) {
    /** Whether this message is a reply to another message. */
    fun isReply(): Boolean = replyContext != null

    /** Returns the Unix timestamp in seconds. */
    fun unixTimestamp(): Long = timestamp.epochSecond
}
