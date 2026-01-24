package com.corvidlabs.algochat

import org.bouncycastle.crypto.engines.ChaCha7539Engine
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.macs.Poly1305
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.digests.SHA256Digest
import java.security.SecureRandom

/**
 * Encryption and decryption for AlgoChat messages.
 */
object Crypto {
    private val secureRandom = SecureRandom()

    /**
     * Encrypt a message for a recipient.
     *
     * @param plaintext Message to encrypt
     * @param senderPrivateKey Sender's X25519 private key (unused but kept for API compatibility)
     * @param senderPublicKey Sender's X25519 public key
     * @param recipientPublicKey Recipient's X25519 public key
     * @return ChatEnvelope containing the encrypted message
     */
    fun encryptMessage(
        plaintext: String,
        senderPrivateKey: X25519PrivateKeyParameters,
        senderPublicKey: X25519PublicKeyParameters,
        recipientPublicKey: X25519PublicKeyParameters
    ): ChatEnvelope {
        val messageBytes = plaintext.toByteArray(Charsets.UTF_8)

        if (messageBytes.size > Protocol.MAX_PAYLOAD_SIZE) {
            throw AlgoChatException.EncryptionFailed("Message too large: ${messageBytes.size} bytes (max ${Protocol.MAX_PAYLOAD_SIZE})")
        }

        // Generate ephemeral key pair for this message
        val ephemeralKeyPair = Keys.generateEphemeralKeyPair()

        // Derive symmetric key for message encryption
        val senderPubBytes = Keys.publicKeyToBytes(senderPublicKey)
        val recipientPubBytes = Keys.publicKeyToBytes(recipientPublicKey)
        val ephemeralPubBytes = Keys.publicKeyToBytes(ephemeralKeyPair.publicKey)

        val sharedSecret = Keys.x25519Ecdh(ephemeralKeyPair.privateKey, recipientPublicKey)

        // Build info: prefix + sender pubkey + recipient pubkey
        val info = Protocol.ENCRYPTION_INFO_PREFIX + senderPubBytes + recipientPubBytes

        val symmetricKey = deriveKey(sharedSecret, ephemeralPubBytes, info)

        // Generate random nonce
        val nonce = ByteArray(Protocol.NONCE_SIZE)
        secureRandom.nextBytes(nonce)

        // Encrypt message
        val ciphertext = chaCha20Poly1305Encrypt(symmetricKey, nonce, messageBytes)

        // Encrypt the symmetric key for sender (bidirectional decryption)
        val senderSharedSecret = Keys.x25519Ecdh(ephemeralKeyPair.privateKey, senderPublicKey)
        val senderInfo = Protocol.SENDER_KEY_INFO_PREFIX + senderPubBytes

        val senderEncryptionKey = deriveKey(senderSharedSecret, ephemeralPubBytes, senderInfo)
        val encryptedSenderKey = chaCha20Poly1305Encrypt(senderEncryptionKey, nonce, symmetricKey)

        return ChatEnvelope(
            version = Protocol.VERSION,
            protocolId = Protocol.PROTOCOL_ID,
            senderPublicKey = senderPubBytes,
            ephemeralPublicKey = ephemeralPubBytes,
            nonce = nonce,
            encryptedSenderKey = encryptedSenderKey,
            ciphertext = ciphertext
        )
    }

    /**
     * Decrypt a message from an envelope.
     *
     * @param envelope The encrypted envelope
     * @param myPrivateKey Our X25519 private key
     * @param myPublicKey Our X25519 public key
     * @return DecryptedContent if successful, null if it's a key-publish message
     */
    fun decryptMessage(
        envelope: ChatEnvelope,
        myPrivateKey: X25519PrivateKeyParameters,
        myPublicKey: X25519PublicKeyParameters
    ): DecryptedContent? {
        val myPubBytes = Keys.publicKeyToBytes(myPublicKey)
        val weAreSender = myPubBytes.contentEquals(envelope.senderPublicKey)

        val plaintext = if (weAreSender) {
            decryptAsSender(envelope, myPrivateKey, myPubBytes)
        } else {
            decryptAsRecipient(envelope, myPrivateKey, myPubBytes)
        }

        // Check for key-publish payload
        if (isKeyPublishPayload(plaintext)) {
            return null
        }

        return parseMessagePayload(plaintext)
    }

    private fun decryptAsRecipient(
        envelope: ChatEnvelope,
        recipientPrivateKey: X25519PrivateKeyParameters,
        recipientPubBytes: ByteArray
    ): ByteArray {
        val ephemeralPublic = Keys.publicKeyFromBytes(envelope.ephemeralPublicKey)

        val sharedSecret = Keys.x25519Ecdh(recipientPrivateKey, ephemeralPublic)
        val info = Protocol.ENCRYPTION_INFO_PREFIX + envelope.senderPublicKey + recipientPubBytes

        val symmetricKey = deriveKey(sharedSecret, envelope.ephemeralPublicKey, info)

        return chaCha20Poly1305Decrypt(symmetricKey, envelope.nonce, envelope.ciphertext)
    }

    private fun decryptAsSender(
        envelope: ChatEnvelope,
        senderPrivateKey: X25519PrivateKeyParameters,
        senderPubBytes: ByteArray
    ): ByteArray {
        val ephemeralPublic = Keys.publicKeyFromBytes(envelope.ephemeralPublicKey)

        // First, recover the symmetric key
        val sharedSecret = Keys.x25519Ecdh(senderPrivateKey, ephemeralPublic)
        val senderInfo = Protocol.SENDER_KEY_INFO_PREFIX + senderPubBytes

        val senderDecryptionKey = deriveKey(sharedSecret, envelope.ephemeralPublicKey, senderInfo)
        val symmetricKey = chaCha20Poly1305Decrypt(senderDecryptionKey, envelope.nonce, envelope.encryptedSenderKey)

        // Now decrypt the message
        return chaCha20Poly1305Decrypt(symmetricKey, envelope.nonce, envelope.ciphertext)
    }

    private fun deriveKey(secret: ByteArray, salt: ByteArray, info: ByteArray): ByteArray {
        val hkdf = HKDFBytesGenerator(SHA256Digest())
        hkdf.init(HKDFParameters(secret, salt, info))

        val key = ByteArray(32)
        hkdf.generateBytes(key, 0, 32)

        return key
    }

    private fun chaCha20Poly1305Encrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray): ByteArray {
        val cipher = ChaCha20Poly1305()
        val params = AEADParameters(KeyParameter(key), 128, nonce, null)
        cipher.init(true, params)

        val output = ByteArray(cipher.getOutputSize(plaintext.size))
        val len = cipher.processBytes(plaintext, 0, plaintext.size, output, 0)
        cipher.doFinal(output, len)

        return output
    }

    private fun chaCha20Poly1305Decrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray): ByteArray {
        val cipher = ChaCha20Poly1305()
        val params = AEADParameters(KeyParameter(key), 128, nonce, null)
        cipher.init(false, params)

        val output = ByteArray(cipher.getOutputSize(ciphertext.size))
        var len = cipher.processBytes(ciphertext, 0, ciphertext.size, output, 0)
        len += cipher.doFinal(output, len)

        return output.copyOf(len)
    }

    private fun isKeyPublishPayload(data: ByteArray): Boolean {
        if (data.isEmpty() || data[0] != '{'.code.toByte()) {
            return false
        }
        return try {
            val text = String(data, Charsets.UTF_8)
            text.contains("\"type\"") && text.contains("\"key-publish\"")
        } catch (e: Exception) {
            false
        }
    }

    private fun parseMessagePayload(data: ByteArray): DecryptedContent {
        val text = String(data, Charsets.UTF_8)

        // Try to parse as JSON (for structured messages with reply context)
        if (text.startsWith("{")) {
            try {
                // Simple JSON parsing without external dependency
                if (text.contains("\"text\"")) {
                    val textMatch = Regex("\"text\"\\s*:\\s*\"([^\"\\\\]*(\\\\.[^\"\\\\]*)*)\"").find(text)
                    if (textMatch != null) {
                        val msgText = textMatch.groupValues[1]
                            .replace("\\n", "\n")
                            .replace("\\t", "\t")
                            .replace("\\\"", "\"")
                            .replace("\\\\", "\\")

                        var replyToId: String? = null
                        var replyToPreview: String? = null

                        if (text.contains("\"replyTo\"")) {
                            val txidMatch = Regex("\"txid\"\\s*:\\s*\"([^\"]+)\"").find(text)
                            val previewMatch = Regex("\"preview\"\\s*:\\s*\"([^\"\\\\]*(\\\\.[^\"\\\\]*)*)\"").find(text)

                            replyToId = txidMatch?.groupValues?.get(1)
                            replyToPreview = previewMatch?.groupValues?.get(1)
                        }

                        return DecryptedContent(msgText, replyToId, replyToPreview)
                    }
                }
            } catch (e: Exception) {
                // Fall through to plain text
            }
        }

        return DecryptedContent(text)
    }
}
