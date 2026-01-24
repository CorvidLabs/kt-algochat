package com.corvidlabs.algochat.models

import com.corvidlabs.algochat.AlgoChatException
import com.corvidlabs.algochat.Keys
import com.corvidlabs.algochat.storage.EncryptionKeyStorage
import com.corvidlabs.algochat.storage.KeyNotFoundError
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters

/**
 * A chat-enabled Algorand account with encryption keys.
 *
 * The ChatAccount wraps an Algorand address with derived X25519 encryption keys.
 * The private key should be stored securely using an EncryptionKeyStorage implementation.
 *
 * @property address The Algorand address (58 characters).
 * @property encryptionPrivateKey The X25519 private key for decryption.
 * @property encryptionPublicKey The X25519 public key for encryption.
 * @property ed25519PublicKey The Ed25519 public key from the Algorand account (optional).
 */
data class ChatAccount(
    val address: String,
    val encryptionPrivateKey: X25519PrivateKeyParameters,
    val encryptionPublicKey: X25519PublicKeyParameters,
    val ed25519PublicKey: ByteArray? = null
) {
    /**
     * The encryption public key as raw bytes (32 bytes).
     */
    val publicKeyBytes: ByteArray
        get() = Keys.publicKeyToBytes(encryptionPublicKey)

    /**
     * The encryption private key as raw bytes (32 bytes).
     *
     * Warning: Handle with care. This should only be used for secure storage.
     */
    fun privateKeyBytes(): ByteArray = encryptionPrivateKey.encoded

    /**
     * Save the encryption key to storage.
     *
     * This allows the account to be loaded later without the full mnemonic.
     *
     * @param storage The key storage to save to.
     * @param requireBiometric If true, require biometric authentication to retrieve.
     */
    suspend fun saveEncryptionKey(
        storage: EncryptionKeyStorage,
        requireBiometric: Boolean = true
    ) {
        storage.store(
            privateKey = privateKeyBytes(),
            address = address,
            requireBiometric = requireBiometric
        )
    }

    /**
     * Check if an encryption key is stored for this account.
     *
     * @param storage The key storage to check.
     * @return True if a key exists in storage for this address.
     */
    suspend fun hasStoredEncryptionKey(storage: EncryptionKeyStorage): Boolean {
        return storage.hasKey(address)
    }

    /**
     * Delete the stored encryption key for this account.
     *
     * @param storage The key storage to delete from.
     */
    suspend fun deleteStoredEncryptionKey(storage: EncryptionKeyStorage) {
        storage.delete(address)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ChatAccount

        if (address != other.address) return false
        if (!encryptionPrivateKey.encoded.contentEquals(other.encryptionPrivateKey.encoded)) return false
        if (!encryptionPublicKey.encoded.contentEquals(other.encryptionPublicKey.encoded)) return false
        if (ed25519PublicKey != null) {
            if (other.ed25519PublicKey == null) return false
            if (!ed25519PublicKey.contentEquals(other.ed25519PublicKey)) return false
        } else if (other.ed25519PublicKey != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = address.hashCode()
        result = 31 * result + encryptionPrivateKey.encoded.contentHashCode()
        result = 31 * result + encryptionPublicKey.encoded.contentHashCode()
        result = 31 * result + (ed25519PublicKey?.contentHashCode() ?: 0)
        return result
    }

    override fun toString(): String = "ChatAccount($address)"

    companion object {
        /**
         * Create a ChatAccount from a 32-byte seed.
         *
         * This derives the X25519 encryption keys from the seed using HKDF-SHA256.
         *
         * @param address The Algorand address.
         * @param seed 32-byte seed (typically first 32 bytes of Algorand secret key).
         * @return A new ChatAccount instance.
         * @throws AlgoChatException if seed is not 32 bytes.
         */
        fun fromSeed(address: String, seed: ByteArray): ChatAccount {
            val keyPair = Keys.deriveKeysFromSeed(seed)
            return ChatAccount(
                address = address,
                encryptionPrivateKey = keyPair.privateKey,
                encryptionPublicKey = keyPair.publicKey
            )
        }

        /**
         * Create a ChatAccount from an Algorand secret key.
         *
         * The Algorand secret key is 64 bytes: the first 32 are the seed,
         * and the last 32 are the Ed25519 public key.
         *
         * @param address The Algorand address.
         * @param secretKey 64-byte Algorand secret key.
         * @return A new ChatAccount instance.
         * @throws IllegalArgumentException if secretKey is not 64 bytes.
         */
        fun fromAlgorandAccount(address: String, secretKey: ByteArray): ChatAccount {
            require(secretKey.size == 64) { "Secret key must be 64 bytes, got ${secretKey.size}" }

            val seed = secretKey.copyOfRange(0, 32)
            val ed25519PublicKey = secretKey.copyOfRange(32, 64)

            val keyPair = Keys.deriveKeysFromSeed(seed)
            return ChatAccount(
                address = address,
                encryptionPrivateKey = keyPair.privateKey,
                encryptionPublicKey = keyPair.publicKey,
                ed25519PublicKey = ed25519PublicKey
            )
        }

        /**
         * Create a ChatAccount by retrieving the encryption key from storage.
         *
         * This allows loading an account without the full mnemonic, useful when
         * the encryption key was previously saved with biometric protection.
         *
         * @param address The Algorand address.
         * @param storage The key storage to retrieve from.
         * @param ed25519PublicKey Optional Ed25519 public key for the account.
         * @return A new ChatAccount instance.
         * @throws KeyNotFoundError if no key is stored for this address.
         */
        suspend fun fromStorage(
            address: String,
            storage: EncryptionKeyStorage,
            ed25519PublicKey: ByteArray? = null
        ): ChatAccount {
            val privateKeyBytes = storage.retrieve(address)
            val privateKey = X25519PrivateKeyParameters(privateKeyBytes, 0)
            val publicKey = privateKey.generatePublicKey()

            return ChatAccount(
                address = address,
                encryptionPrivateKey = privateKey,
                encryptionPublicKey = publicKey,
                ed25519PublicKey = ed25519PublicKey
            )
        }
    }
}
