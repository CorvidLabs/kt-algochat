package com.corvidlabs.algochat.models

/**
 * A conversation between two Algorand addresses.
 */
class Conversation(
    /** The other party's Algorand address. */
    val participant: String,
    /** Cached encryption public key for the participant (32 bytes). */
    var participantEncryptionKey: ByteArray? = null,
    /** The round of the last fetched message (for pagination). */
    var lastFetchedRound: Long? = null
) {
    private val _messages: MutableList<Message> = mutableListOf()

    /** Returns the unique identifier (the participant's address). */
    val id: String get() = participant

    /** Returns all messages in the conversation. */
    val messages: List<Message> get() = _messages.toList()

    /** Returns the most recent message. */
    fun lastMessage(): Message? = _messages.lastOrNull()

    /** Returns the most recent received message. */
    fun lastReceived(): Message? =
        _messages.lastOrNull { it.direction == MessageDirection.RECEIVED }

    /** Returns the most recent sent message. */
    fun lastSent(): Message? =
        _messages.lastOrNull { it.direction == MessageDirection.SENT }

    /** Returns all received messages. */
    fun receivedMessages(): List<Message> =
        _messages.filter { it.direction == MessageDirection.RECEIVED }

    /** Returns all sent messages. */
    fun sentMessages(): List<Message> =
        _messages.filter { it.direction == MessageDirection.SENT }

    /** Returns the number of messages. */
    fun messageCount(): Int = _messages.size

    /** Whether the conversation has any messages. */
    fun isEmpty(): Boolean = _messages.isEmpty()

    /** Adds a message to the conversation (maintains chronological order). */
    fun append(message: Message) {
        if (_messages.any { it.id == message.id }) {
            return
        }
        _messages.add(message)
        _messages.sortBy { it.timestamp }
    }

    /** Merges new messages into the conversation. */
    fun merge(newMessages: Iterable<Message>) {
        for (message in newMessages) {
            append(message)
        }
    }

    companion object {
        /** Creates a conversation with a known encryption key. */
        fun withKey(participant: String, encryptionKey: ByteArray): Conversation {
            return Conversation(
                participant = participant,
                participantEncryptionKey = encryptionKey
            )
        }
    }
}
