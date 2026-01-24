package com.corvidlabs.algochat

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.MessageDigest

/**
 * Signature verification for AlgoChat encryption keys.
 *
 * This object provides functions to sign encryption public keys with an
 * Algorand account's Ed25519 key, and verify those signatures. This prevents
 * key substitution attacks by proving key ownership.
 */
object Signature {
    /** Size of an Ed25519 signature in bytes. */
    const val ED25519_SIGNATURE_SIZE = 64

    /** Size of an Ed25519 public key in bytes. */
    const val ED25519_PUBLIC_KEY_SIZE = 32

    /** Size of an X25519 public key in bytes. */
    const val X25519_PUBLIC_KEY_SIZE = 32

    /**
     * Signs an encryption public key with an Ed25519 signing key.
     *
     * This creates a cryptographic proof that the encryption key belongs to
     * the holder of the Ed25519 private key (Algorand account).
     *
     * @param encryptionPublicKey The X25519 public key to sign (32 bytes)
     * @param signingKey The Ed25519 signing key
     * @return The Ed25519 signature (64 bytes)
     * @throws AlgoChatException If the key length is invalid
     */
    fun signEncryptionKey(
        encryptionPublicKey: ByteArray,
        signingKey: Ed25519PrivateKeyParameters
    ): ByteArray {
        if (encryptionPublicKey.size != X25519_PUBLIC_KEY_SIZE) {
            throw AlgoChatException(
                "Encryption public key must be $X25519_PUBLIC_KEY_SIZE bytes, got ${encryptionPublicKey.size}"
            )
        }

        val signer = Ed25519Signer()
        signer.init(true, signingKey)
        signer.update(encryptionPublicKey, 0, encryptionPublicKey.size)

        return signer.generateSignature()
    }

    /**
     * Signs an encryption public key using raw Ed25519 private key bytes.
     *
     * @param encryptionPublicKey The X25519 public key to sign (32 bytes)
     * @param signingKeyBytes The Ed25519 private key bytes (32 bytes)
     * @return The Ed25519 signature (64 bytes)
     * @throws AlgoChatException If the key lengths are invalid
     */
    fun signEncryptionKeyBytes(
        encryptionPublicKey: ByteArray,
        signingKeyBytes: ByteArray
    ): ByteArray {
        if (signingKeyBytes.size != ED25519_PUBLIC_KEY_SIZE) {
            throw AlgoChatException(
                "Signing key must be $ED25519_PUBLIC_KEY_SIZE bytes, got ${signingKeyBytes.size}"
            )
        }

        val signingKey = Ed25519PrivateKeyParameters(signingKeyBytes, 0)
        return signEncryptionKey(encryptionPublicKey, signingKey)
    }

    /**
     * Verifies that an encryption public key was signed by an Ed25519 key.
     *
     * This checks that the signature over the X25519 encryption key was
     * created by the Ed25519 private key corresponding to the given public key.
     *
     * @param encryptionPublicKey The X25519 public key (32 bytes)
     * @param verifyingKey The Ed25519 public key
     * @param signature The Ed25519 signature to verify (64 bytes)
     * @return `true` if the signature is valid, `false` otherwise
     * @throws AlgoChatException If the key or signature lengths are invalid
     */
    fun verifyEncryptionKey(
        encryptionPublicKey: ByteArray,
        verifyingKey: Ed25519PublicKeyParameters,
        signature: ByteArray
    ): Boolean {
        if (encryptionPublicKey.size != X25519_PUBLIC_KEY_SIZE) {
            throw AlgoChatException(
                "Encryption public key must be $X25519_PUBLIC_KEY_SIZE bytes, got ${encryptionPublicKey.size}"
            )
        }

        if (signature.size != ED25519_SIGNATURE_SIZE) {
            throw AlgoChatException(
                "Signature must be $ED25519_SIGNATURE_SIZE bytes, got ${signature.size}"
            )
        }

        return try {
            val verifier = Ed25519Signer()
            verifier.init(false, verifyingKey)
            verifier.update(encryptionPublicKey, 0, encryptionPublicKey.size)
            verifier.verifySignature(signature)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Verifies an encryption key using raw Ed25519 public key bytes.
     *
     * @param encryptionPublicKey The X25519 public key (32 bytes)
     * @param ed25519PublicKey The Ed25519 public key bytes (32 bytes, e.g., Algorand address bytes)
     * @param signature The Ed25519 signature (64 bytes)
     * @return `true` if the signature is valid, `false` otherwise
     * @throws AlgoChatException If any key or signature lengths are invalid
     */
    fun verifyEncryptionKeyBytes(
        encryptionPublicKey: ByteArray,
        ed25519PublicKey: ByteArray,
        signature: ByteArray
    ): Boolean {
        if (ed25519PublicKey.size != ED25519_PUBLIC_KEY_SIZE) {
            throw AlgoChatException(
                "Ed25519 public key must be $ED25519_PUBLIC_KEY_SIZE bytes, got ${ed25519PublicKey.size}"
            )
        }

        val verifyingKey = try {
            Ed25519PublicKeyParameters(ed25519PublicKey, 0)
        } catch (e: Exception) {
            throw AlgoChatException("Invalid Ed25519 public key: ${e.message}")
        }

        return verifyEncryptionKey(encryptionPublicKey, verifyingKey, signature)
    }

    /**
     * Gets the Ed25519 public key from a private key.
     *
     * @param privateKey The Ed25519 private key
     * @return The Ed25519 public key bytes (32 bytes)
     */
    fun getPublicKey(privateKey: Ed25519PrivateKeyParameters): ByteArray {
        return privateKey.generatePublicKey().encoded
    }

    /**
     * Gets the Ed25519 public key from raw private key bytes.
     *
     * @param privateKeyBytes The Ed25519 private key bytes (32 bytes)
     * @return The Ed25519 public key bytes (32 bytes)
     */
    fun getPublicKeyBytes(privateKeyBytes: ByteArray): ByteArray {
        val privateKey = Ed25519PrivateKeyParameters(privateKeyBytes, 0)
        return getPublicKey(privateKey)
    }

    /**
     * Generates a human-readable fingerprint for an encryption public key.
     *
     * The fingerprint is a truncated SHA-256 hash formatted for easy comparison.
     *
     * @param publicKey The encryption public key (32 bytes)
     * @return A fingerprint string like "A7B3C9D1 E5F28A4B"
     */
    fun fingerprint(publicKey: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(publicKey)

        // Take first 8 bytes and format as hex groups
        return hash.take(8)
            .chunked(2)
            .joinToString(" ") { chunk ->
                chunk.joinToString("") { byte ->
                    "%02X".format(byte)
                }
            }
    }
}
