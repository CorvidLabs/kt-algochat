package com.corvidlabs.algochat

/**
 * Protocol constants for AlgoChat.
 */
object Protocol {
    /** Protocol version byte. */
    const val VERSION: Byte = 0x01

    /** Protocol ID byte. */
    const val PROTOCOL_ID: Byte = 0x01

    /** Size of the envelope header in bytes. */
    const val HEADER_SIZE = 126

    /** Size of the authentication tag in bytes. */
    const val TAG_SIZE = 16

    /** Size of the encrypted sender key (32-byte key + 16-byte tag). */
    const val ENCRYPTED_SENDER_KEY_SIZE = 48

    /** Maximum payload size in bytes. */
    const val MAX_PAYLOAD_SIZE = 882

    /** Size of the nonce in bytes. */
    const val NONCE_SIZE = 12

    /** Size of a public key in bytes. */
    const val PUBLIC_KEY_SIZE = 32

    /** Key derivation salt. */
    val KEY_DERIVATION_SALT = "AlgoChat-v1-encryption".toByteArray()

    /** Key derivation info. */
    val KEY_DERIVATION_INFO = "x25519-key".toByteArray()

    /** Encryption info prefix for message encryption. */
    val ENCRYPTION_INFO_PREFIX = "AlgoChatV1".toByteArray()

    /** Sender key info prefix for bidirectional decryption. */
    val SENDER_KEY_INFO_PREFIX = "AlgoChatV1-SenderKey".toByteArray()

    /** Size of an Ed25519 signature in bytes. */
    const val SIGNATURE_SIZE = 64

    /** Minimum payment amount in microAlgos. */
    const val MINIMUM_PAYMENT = 1000L
}

/**
 * Decrypted message content.
 */
data class DecryptedContent(
    /** The message text. */
    val text: String,
    /** Transaction ID this message replies to, if any. */
    val replyToId: String? = null,
    /** Preview of the replied message, if any. */
    val replyToPreview: String? = null
)

/**
 * Exception thrown for AlgoChat errors.
 */
sealed class AlgoChatException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** Invalid public key format or length. */
    class InvalidPublicKey(details: String) : AlgoChatException("Invalid public key: $details")

    /** Key derivation failed. */
    class KeyDerivationFailed(details: String) : AlgoChatException("Key derivation failed: $details")

    /** Invalid signature format or verification failed. */
    class InvalidSignature(details: String) : AlgoChatException("Invalid signature: $details")

    /** Encryption failed. */
    class EncryptionFailed(details: String) : AlgoChatException("Encryption failed: $details")

    /** Decryption failed. */
    class DecryptionFailed(details: String) : AlgoChatException("Decryption failed: $details")

    /** Invalid envelope format. */
    class InvalidEnvelope(details: String) : AlgoChatException("Invalid envelope: $details")

    /** Indexer not configured. */
    class IndexerNotConfigured : AlgoChatException("Indexer not configured")

    /** Public key not found for address. */
    class PublicKeyNotFound(address: String) : AlgoChatException("Public key not found for address: $address")

    /** Invalid recipient address. */
    class InvalidRecipient(details: String) : AlgoChatException("Invalid recipient: $details")

    /** Transaction failed. */
    class TransactionFailed(details: String) : AlgoChatException("Transaction failed: $details")

    /** Insufficient balance. */
    class InsufficientBalance(required: Long, available: Long) :
        AlgoChatException("Insufficient balance: required $required, available $available")

    /** Key not found in storage. */
    class KeyNotFound(address: String) : AlgoChatException("Key not found for address: $address")

    /** Storage operation failed. */
    class StorageFailed(details: String) : AlgoChatException("Storage failed: $details")

    /** Message not found. */
    class MessageNotFound(id: String) : AlgoChatException("Message not found: $id")
}
