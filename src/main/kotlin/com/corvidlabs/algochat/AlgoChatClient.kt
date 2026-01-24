package com.corvidlabs.algochat

import com.corvidlabs.algochat.models.*
import com.corvidlabs.algochat.storage.MessageCache
import com.corvidlabs.algochat.storage.PublicKeyCache
import java.time.Instant

/**
 * Base exception for AlgoChat errors.
 */
open class AlgoChatError(message: String) : Exception(message)

/**
 * Raised when a user's public key cannot be found.
 */
class PublicKeyNotFoundError(val address: String) :
    AlgoChatError("Public key not found for address: $address")

/**
 * Raised when account has insufficient balance.
 */
class InsufficientBalanceError(val required: Long, val available: Long) :
    AlgoChatError("Insufficient balance: required $required microAlgos, available $available")

/**
 * Raised when message exceeds maximum size.
 */
class MessageTooLargeError(val maxSize: Int) :
    AlgoChatError("Message exceeds maximum size of $maxSize bytes")

/**
 * High-level client for AlgoChat encrypted messaging.
 *
 * The AlgoChatClient provides methods for:
 * - Sending encrypted messages
 * - Fetching and decrypting messages
 * - Managing conversations
 * - Discovering encryption keys
 * - Publishing your encryption key
 *
 * Example usage:
 * ```kotlin
 * // Create client
 * val client = AlgoChatClient(
 *     account = chatAccount,
 *     algod = myAlgodClient,
 *     indexer = myIndexerClient,
 * )
 *
 * // Send a message
 * val result = client.sendMessage(
 *     "Hello, Algorand!",
 *     to = "RECIPIENT_ADDRESS..."
 * )
 *
 * // Fetch messages
 * val conv = client.refreshConversation("RECIPIENT_ADDRESS...")
 * conv.messages.forEach { msg ->
 *     println("${msg.sender}: ${msg.content}")
 * }
 * ```
 */
class AlgoChatClient(
    val account: ChatAccount,
    private val algod: AlgodClient,
    private val indexer: IndexerClient,
    private val messageCache: MessageCache? = null,
    private val publicKeyCache: PublicKeyCache = PublicKeyCache()
) {
    companion object {
        /** Minimum transaction fee in microAlgos. */
        const val MIN_TRANSACTION_FEE = 1000L

        /** Minimum account balance in microAlgos. */
        const val MIN_ACCOUNT_BALANCE = 100_000L

        /** Maximum message payload size. */
        const val MAX_PAYLOAD_SIZE = 882
    }

    /** The account's Algorand address. */
    val address: String get() = account.address

    /** The account's encryption public key (32 bytes). */
    val publicKey: ByteArray get() = account.publicKeyBytes

    // MARK: - Conversations

    /**
     * Get or create a conversation with a participant.
     *
     * @param participant The other party's Algorand address.
     * @return A Conversation object (may be empty if no history exists).
     */
    suspend fun conversation(participant: String): Conversation {
        val conv = Conversation(participant)

        // Try to get the participant's encryption key
        if (participant == account.address) {
            conv.participantEncryptionKey = account.publicKeyBytes
        } else {
            try {
                val discovered = discoverKey(participant)
                conv.participantEncryptionKey = discovered.publicKey
            } catch (_: PublicKeyNotFoundError) {
                // Key not found, leave it null
            }
        }

        return conv
    }

    /**
     * Fetch all conversations for the current account.
     *
     * @param limit Maximum number of transactions to scan.
     * @return List of conversations, sorted by most recent message.
     */
    suspend fun conversations(limit: Int = 100): List<Conversation> {
        val transactions = indexer.searchTransactions(account.address, limit = limit)

        // Group by participant
        val participants = mutableMapOf<String, MutableList<NoteTransaction>>()
        for (tx in transactions) {
            if (tx.note.isEmpty() || !Envelope.isChatMessage(tx.note)) {
                continue
            }

            // Determine participant
            val participant = if (tx.sender == account.address) tx.receiver else tx.sender

            // Skip self-payments (key publishes)
            if (participant == account.address) {
                continue
            }

            participants.getOrPut(participant) { mutableListOf() }.add(tx)
        }

        // Build conversations
        val convs = mutableListOf<Conversation>()
        for ((participant, txs) in participants) {
            val conv = Conversation(participant)
            val messages = parseMessages(txs)
            conv.merge(messages)
            if (!conv.isEmpty()) {
                convs.add(conv)
            }
        }

        // Sort by most recent message
        convs.sortByDescending { it.lastMessage()?.timestamp ?: Instant.MIN }

        return convs
    }

    /**
     * Fetch messages for a conversation.
     *
     * @param participant The conversation participant's address.
     * @param afterRound Only fetch messages after this round (for incremental sync).
     * @param limit Maximum number of messages to fetch.
     * @return Updated Conversation with messages.
     */
    suspend fun refreshConversation(
        participant: String,
        afterRound: Long? = null,
        limit: Int = 50
    ): Conversation {
        val conv = conversation(participant)

        // Check cache for last sync round
        var effectiveAfterRound = afterRound
        if (effectiveAfterRound == null && messageCache != null) {
            effectiveAfterRound = messageCache.getLastSyncRound(participant)
        }

        // Fetch transactions between accounts
        val transactions = indexer.searchTransactionsBetween(
            account.address,
            participant,
            afterRound = effectiveAfterRound,
            limit = limit
        )

        // Parse and decrypt messages
        val messages = parseMessages(transactions)
        conv.merge(messages)

        // Update last fetched round
        if (messages.isNotEmpty()) {
            val lastRound = messages.maxOf { it.confirmedRound }
            conv.lastFetchedRound = lastRound

            // Update cache
            messageCache?.let { cache ->
                cache.store(messages, participant)
                cache.setLastSyncRound(lastRound, participant)
            }
        }

        // Try to discover participant's public key from received messages
        if (conv.participantEncryptionKey == null) {
            if (messages.any { it.direction == MessageDirection.RECEIVED }) {
                try {
                    val discovered = discoverKey(participant)
                    conv.participantEncryptionKey = discovered.publicKey
                } catch (_: PublicKeyNotFoundError) {
                    // Key not found
                }
            }
        }

        return conv
    }

    /**
     * Load cached messages for a conversation (offline access).
     *
     * @param participant The conversation participant.
     * @return Cached messages, or empty list if no cache.
     */
    suspend fun loadCached(participant: String): List<Message> {
        return try {
            messageCache?.retrieve(participant) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    // MARK: - Sending Messages

    /**
     * Send an encrypted message.
     *
     * @param content The message text.
     * @param to Recipient's Algorand address.
     * @param options Send options (default: fire-and-forget).
     * @return SendResult with transaction ID and message.
     * @throws MessageTooLargeError if message exceeds max size.
     * @throws InsufficientBalanceError if account balance is too low.
     * @throws PublicKeyNotFoundError if recipient's key cannot be found.
     */
    suspend fun sendMessage(
        content: String,
        to: String,
        options: SendOptions = SendOptions.fireAndForget()
    ): SendResult {
        // Validate message size
        val messageBytes = content.toByteArray(Charsets.UTF_8)
        if (messageBytes.size > MAX_PAYLOAD_SIZE) {
            throw MessageTooLargeError(MAX_PAYLOAD_SIZE)
        }

        // Check balance
        val accountInfo = algod.getAccountInfo(account.address)
        val required = MIN_TRANSACTION_FEE + MIN_ACCOUNT_BALANCE
        if (accountInfo.amount < required) {
            throw InsufficientBalanceError(required, accountInfo.amount)
        }

        // Get recipient's public key
        val recipientKey = getRecipientKey(to)
        val recipientPublicKey = Keys.publicKeyFromBytes(recipientKey)

        // Encrypt message
        val envelope = Crypto.encryptMessage(
            content,
            account.encryptionPrivateKey,
            account.encryptionPublicKey,
            recipientPublicKey,
            options.replyContext
        )

        // Encode envelope
        val note = Envelope.encode(envelope)

        // Get transaction parameters
        val params = algod.getSuggestedParams()

        // Build and sign transaction
        // Note: This requires the caller to have implemented transaction building
        val signedTxn = buildPaymentTransaction(
            sender = account.address,
            receiver = to,
            amount = MIN_TRANSACTION_FEE,
            note = note,
            params = params
        )

        // Submit transaction
        val txid = algod.submitTransaction(signedTxn)

        // Wait for confirmation if requested
        var confirmedRound = 0L
        if (options.waitForConfirmation) {
            val txInfo = algod.waitForConfirmation(txid, rounds = options.timeoutRounds)
            confirmedRound = txInfo.confirmedRound ?: 0
        }

        // Wait for indexer if requested
        if (options.waitForIndexer) {
            indexer.waitForIndexer(txid, timeoutSecs = options.indexerTimeoutSecs)
        }

        // Build sent message
        val message = Message(
            id = txid,
            sender = account.address,
            recipient = to,
            content = content,
            timestamp = Instant.now(),
            confirmedRound = confirmedRound,
            direction = MessageDirection.SENT,
            replyContext = options.replyContext
        )

        return SendResult(txid = txid, message = message)
    }

    /**
     * Build a signed payment transaction.
     *
     * Note: This is a placeholder. Real implementations should use algosdk
     * or another SDK to build and sign transactions.
     */
    private fun buildPaymentTransaction(
        sender: String,
        receiver: String,
        amount: Long,
        note: ByteArray,
        params: SuggestedParams
    ): ByteArray {
        throw NotImplementedError(
            "Transaction building must be implemented by the SDK user. " +
            "Use algosdk-kotlin or java-algorand-sdk to build and sign transactions."
        )
    }

    // MARK: - Key Management

    /**
     * Discover a user's encryption public key from their transaction history.
     *
     * @param address The user's Algorand address.
     * @return DiscoveredKey with public key and verification status.
     * @throws PublicKeyNotFoundError if no chat history exists.
     */
    suspend fun discoverKey(address: String): DiscoveredKey {
        // Check cache first
        val cached = publicKeyCache.retrieve(address)
        if (cached != null) {
            return DiscoveredKey(publicKey = cached, isVerified = false)
        }

        // Search for transactions from this address
        val transactions = indexer.searchTransactions(address, limit = 200)

        for (tx in transactions) {
            // Only look at transactions sent by this address
            if (tx.sender != address) {
                continue
            }

            if (tx.note.isEmpty() || !Envelope.isChatMessage(tx.note)) {
                continue
            }

            try {
                val envelope = Envelope.decode(tx.note)
                val publicKey = envelope.senderPublicKey

                // Cache for future lookups
                publicKeyCache.store(address, publicKey)

                return DiscoveredKey(publicKey = publicKey, isVerified = false)
            } catch (_: Exception) {
                continue
            }
        }

        throw PublicKeyNotFoundError(address)
    }

    /**
     * Fetch a user's encryption public key.
     *
     * This is a convenience wrapper around discoverKey that returns just the key bytes.
     *
     * @param address The user's Algorand address.
     * @return The X25519 public key (32 bytes).
     * @throws PublicKeyNotFoundError if no chat history exists.
     */
    suspend fun fetchPublicKey(address: String): ByteArray {
        return discoverKey(address).publicKey
    }

    /**
     * Publish this account's encryption key to the blockchain.
     *
     * This creates a zero-value self-payment transaction containing the
     * encryption public key, allowing others to discover it.
     *
     * @return The transaction ID.
     */
    suspend fun publishKey(): String {
        // Create a self-encrypted message containing the key
        val envelope = Crypto.encryptMessage(
            "{\"type\": \"key-publish\"}",
            account.encryptionPrivateKey,
            account.encryptionPublicKey,
            account.encryptionPublicKey  // Self-encrypt
        )

        val note = Envelope.encode(envelope)
        val params = algod.getSuggestedParams()

        val signedTxn = buildPaymentTransaction(
            sender = account.address,
            receiver = account.address,  // Self-payment
            amount = 0,
            note = note,
            params = params
        )

        return algod.submitTransaction(signedTxn)
    }

    // MARK: - Account Info

    /**
     * Get the account balance in microAlgos.
     *
     * @return Balance in microAlgos.
     */
    suspend fun balance(): Long {
        val info = algod.getAccountInfo(account.address)
        return info.amount
    }

    // MARK: - Cache Management

    /**
     * Clear all cached data (messages and public keys).
     */
    suspend fun clearCache() {
        messageCache?.clear()
        publicKeyCache.clear()
    }

    /**
     * Clear cached data for a specific conversation.
     */
    suspend fun clearCacheFor(participant: String) {
        messageCache?.clearFor(participant)
        publicKeyCache.invalidate(participant)
    }

    /**
     * Invalidate a cached public key.
     */
    fun invalidateCachedPublicKey(address: String) {
        publicKeyCache.invalidate(address)
    }

    // MARK: - Private Helpers

    private suspend fun getRecipientKey(address: String): ByteArray {
        if (address == account.address) {
            return account.publicKeyBytes
        }

        val cached = publicKeyCache.retrieve(address)
        if (cached != null) {
            return cached
        }

        return discoverKey(address).publicKey
    }

    private fun parseMessages(transactions: List<NoteTransaction>): List<Message> {
        val messages = mutableListOf<Message>()

        for (tx in transactions) {
            if (tx.note.isEmpty() || !Envelope.isChatMessage(tx.note)) {
                continue
            }

            try {
                val envelope = Envelope.decode(tx.note)

                // Determine direction
                val direction = if (tx.sender == account.address) {
                    MessageDirection.SENT
                } else {
                    MessageDirection.RECEIVED
                }

                // Decrypt
                val decrypted = Crypto.decryptMessage(
                    envelope,
                    account.encryptionPrivateKey,
                    account.encryptionPublicKey
                ) ?: continue  // Key-publish or unrelated message

                // Build reply context if present
                val replyContext = if (decrypted.replyToId != null) {
                    ReplyContext(
                        messageId = decrypted.replyToId,
                        preview = decrypted.replyToPreview ?: ""
                    )
                } else {
                    null
                }

                messages.add(
                    Message(
                        id = tx.txid,
                        sender = tx.sender,
                        recipient = tx.receiver,
                        content = decrypted.text,
                        timestamp = Instant.ofEpochSecond(tx.roundTime),
                        confirmedRound = tx.confirmedRound,
                        direction = direction,
                        replyContext = replyContext
                    )
                )
            } catch (_: Exception) {
                // Skip messages that can't be decrypted
                continue
            }
        }

        return messages
    }
}
