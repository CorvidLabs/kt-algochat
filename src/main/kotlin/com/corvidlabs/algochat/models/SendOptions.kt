package com.corvidlabs.algochat.models

/**
 * Result of discovering a user's encryption key.
 */
data class DiscoveredKey(
    /** The X25519 public key (32 bytes). */
    val publicKey: ByteArray,
    /** Whether the key was cryptographically verified via Ed25519 signature. */
    val isVerified: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DiscoveredKey
        if (!publicKey.contentEquals(other.publicKey)) return false
        if (isVerified != other.isVerified) return false
        return true
    }

    override fun hashCode(): Int {
        var result = publicKey.contentHashCode()
        result = 31 * result + isVerified.hashCode()
        return result
    }
}

/**
 * Options for sending a message.
 */
data class SendOptions(
    /** Wait for algod confirmation. */
    val waitForConfirmation: Boolean = false,
    /** Maximum rounds to wait for confirmation. */
    val timeoutRounds: Long = 10,
    /** Wait for indexer visibility. */
    val waitForIndexer: Boolean = false,
    /** Maximum seconds to wait for indexer. */
    val indexerTimeoutSecs: Long = 30,
    /** Reply context if replying to a message. */
    val replyContext: ReplyContext? = null
) {
    /** Set the reply context. */
    fun withReply(context: ReplyContext): SendOptions =
        copy(replyContext = context)

    companion object {
        /** Fire-and-forget (no waiting). */
        fun fireAndForget(): SendOptions = SendOptions()

        /** Wait for algod confirmation only. */
        fun confirmed(): SendOptions = SendOptions(waitForConfirmation = true)

        /** Wait for both algod and indexer. */
        fun indexed(): SendOptions = SendOptions(
            waitForConfirmation = true,
            waitForIndexer = true
        )

        /** Create options for replying to a message. */
        fun replyingTo(message: Message): SendOptions = SendOptions(
            replyContext = ReplyContext.fromMessage(message)
        )
    }
}

/**
 * Result of a successful send operation.
 */
data class SendResult(
    /** Transaction ID. */
    val txid: String,
    /** The sent message (for optimistic UI updates). */
    val message: Message
)
