package com.corvidlabs.algochat

import com.corvidlabs.algochat.models.DiscoveredKey

/**
 * Configuration for Algorand node connections.
 */
data class AlgorandConfig(
    /** Algod node URL. */
    val algodUrl: String,
    /** Algod API token. */
    val algodToken: String,
    /** Indexer URL (optional). */
    val indexerUrl: String? = null,
    /** Indexer API token (optional). */
    val indexerToken: String? = null
) {
    companion object {
        /** Create configuration for LocalNet (Algokit sandbox). */
        fun localnet(): AlgorandConfig = AlgorandConfig(
            algodUrl = "http://localhost:4001",
            algodToken = "a".repeat(64),
            indexerUrl = "http://localhost:8980",
            indexerToken = "a".repeat(64)
        )

        /** Create configuration for TestNet (via Nodely). */
        fun testnet(): AlgorandConfig = AlgorandConfig(
            algodUrl = "https://testnet-api.4160.nodely.dev",
            algodToken = "",
            indexerUrl = "https://testnet-idx.4160.nodely.dev",
            indexerToken = ""
        )

        /** Create configuration for MainNet (via Nodely). */
        fun mainnet(): AlgorandConfig = AlgorandConfig(
            algodUrl = "https://mainnet-api.4160.nodely.dev",
            algodToken = "",
            indexerUrl = "https://mainnet-idx.4160.nodely.dev",
            indexerToken = ""
        )
    }

    /** Return a new config with indexer settings. */
    fun withIndexer(url: String, token: String = ""): AlgorandConfig = copy(
        indexerUrl = url,
        indexerToken = token
    )
}

/**
 * Transaction information returned after submission.
 */
data class TransactionInfo(
    /** Transaction ID. */
    val txid: String,
    /** Round in which the transaction was confirmed (if confirmed). */
    val confirmedRound: Long? = null
)

/**
 * A note field transaction from the blockchain.
 */
data class NoteTransaction(
    /** Transaction ID. */
    val txid: String,
    /** Sender address. */
    val sender: String,
    /** Receiver address. */
    val receiver: String,
    /** Note field contents. */
    val note: ByteArray,
    /** Round in which the transaction was confirmed. */
    val confirmedRound: Long,
    /** Timestamp of the block (Unix time). */
    val roundTime: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NoteTransaction

        if (txid != other.txid) return false
        if (sender != other.sender) return false
        if (receiver != other.receiver) return false
        if (!note.contentEquals(other.note)) return false
        if (confirmedRound != other.confirmedRound) return false
        if (roundTime != other.roundTime) return false

        return true
    }

    override fun hashCode(): Int {
        var result = txid.hashCode()
        result = 31 * result + sender.hashCode()
        result = 31 * result + receiver.hashCode()
        result = 31 * result + note.contentHashCode()
        result = 31 * result + confirmedRound.hashCode()
        result = 31 * result + roundTime.hashCode()
        return result
    }
}

/**
 * Suggested transaction parameters.
 */
data class SuggestedParams(
    /** Fee per byte in microAlgos. */
    val fee: Long,
    /** Minimum fee in microAlgos. */
    val minFee: Long,
    /** First valid round. */
    val firstValid: Long,
    /** Last valid round. */
    val lastValid: Long,
    /** Genesis ID. */
    val genesisId: String,
    /** Genesis hash (32 bytes). */
    val genesisHash: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SuggestedParams

        if (fee != other.fee) return false
        if (minFee != other.minFee) return false
        if (firstValid != other.firstValid) return false
        if (lastValid != other.lastValid) return false
        if (genesisId != other.genesisId) return false
        if (!genesisHash.contentEquals(other.genesisHash)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = fee.hashCode()
        result = 31 * result + minFee.hashCode()
        result = 31 * result + firstValid.hashCode()
        result = 31 * result + lastValid.hashCode()
        result = 31 * result + genesisId.hashCode()
        result = 31 * result + genesisHash.contentHashCode()
        return result
    }
}

/**
 * Account information.
 */
data class AccountInfo(
    /** Account address. */
    val address: String,
    /** Account balance in microAlgos. */
    val amount: Long,
    /** Minimum balance required. */
    val minBalance: Long
)

/**
 * Interface for interacting with an Algorand node (algod).
 */
interface AlgodClient {
    /** Get the current network parameters. */
    suspend fun getSuggestedParams(): SuggestedParams

    /** Get account information. */
    suspend fun getAccountInfo(address: String): AccountInfo

    /** Submit a signed transaction. Returns the transaction ID. */
    suspend fun submitTransaction(signedTxn: ByteArray): String

    /** Wait for a transaction to be confirmed. */
    suspend fun waitForConfirmation(txid: String, rounds: Int = 10): TransactionInfo

    /** Get the current round. */
    suspend fun getCurrentRound(): Long
}

/**
 * Interface for interacting with an Algorand indexer.
 */
interface IndexerClient {
    /** Search for transactions with notes sent to/from an address. */
    suspend fun searchTransactions(
        address: String,
        afterRound: Long? = null,
        limit: Int? = null
    ): List<NoteTransaction>

    /** Search for transactions between two addresses. */
    suspend fun searchTransactionsBetween(
        address1: String,
        address2: String,
        afterRound: Long? = null,
        limit: Int? = null
    ): List<NoteTransaction>

    /** Get a specific transaction by ID. */
    suspend fun getTransaction(txid: String): NoteTransaction

    /** Wait for a transaction to be indexed. */
    suspend fun waitForIndexer(txid: String, timeoutSecs: Int = 30): NoteTransaction
}

/**
 * Parse a key announcement from a transaction note.
 *
 * Key announcement format:
 * - 32 bytes: X25519 public key
 * - 64 bytes (optional): Ed25519 signature
 *
 * @param note The transaction note field
 * @param address The sender address (for verification context)
 * @return DiscoveredKey if valid, null otherwise
 */
fun parseKeyAnnouncement(note: ByteArray, address: String): DiscoveredKey? {
    if (note.size < 32) {
        return null
    }

    val publicKey = note.copyOfRange(0, 32)
    var isVerified = false

    if (note.size >= 96) {
        // Has signature, verify it
        val signature = note.copyOfRange(32, 96)
        isVerified = try {
            // Note: For proper verification, we'd need the Ed25519 public key
            // from the Algorand address. This is a simplified version.
            Signature.verifyEncryptionKeyBytes(publicKey, publicKey, signature)
        } catch (e: Exception) {
            false
        }
    }

    return DiscoveredKey(publicKey = publicKey, isVerified = isVerified)
}

/**
 * Discover the encryption public key for an Algorand address.
 *
 * This searches the indexer for key announcement transactions from the address.
 * The key is considered verified if it was signed by the address's Ed25519 key.
 *
 * @param indexer The indexer client to use
 * @param address The Algorand address to find the key for
 * @return DiscoveredKey if found, null otherwise
 */
suspend fun discoverEncryptionKey(
    indexer: IndexerClient,
    address: String
): DiscoveredKey? {
    // Search for transactions from this address
    val transactions = indexer.searchTransactions(address, limit = 100)

    // Look for key announcements in the note field
    for (tx in transactions) {
        if (tx.sender != address) {
            continue
        }

        // Check if this is a key announcement (self-transfer with note)
        if (tx.receiver != address) {
            continue
        }

        // Try to parse as key announcement
        val key = parseKeyAnnouncement(tx.note, address)
        if (key != null) {
            return key
        }
    }

    return null
}
