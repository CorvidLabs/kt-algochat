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
class AlgoChatException(message: String, cause: Throwable? = null) : Exception(message, cause)
