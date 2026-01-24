package com.corvidlabs.algochat

import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.security.SecureRandom
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SignatureTest {
    private val secureRandom = SecureRandom()

    private fun generateEd25519KeyPair(): Pair<Ed25519PrivateKeyParameters, Ed25519PublicKeyParameters> {
        val generator = Ed25519KeyPairGenerator()
        generator.init(Ed25519KeyGenerationParameters(secureRandom))
        val keyPair = generator.generateKeyPair()
        return Pair(
            keyPair.private as Ed25519PrivateKeyParameters,
            keyPair.public as Ed25519PublicKeyParameters
        )
    }

    @Test
    fun `sign and verify roundtrip`() {
        val (signingKey, verifyingKey) = generateEd25519KeyPair()

        // Fake X25519 public key (32 bytes)
        val encryptionKey = ByteArray(32) { 42 }

        val signature = Signature.signEncryptionKey(encryptionKey, signingKey)
        assertEquals(Signature.ED25519_SIGNATURE_SIZE, signature.size)

        val valid = Signature.verifyEncryptionKey(encryptionKey, verifyingKey, signature)
        assertTrue(valid)
    }

    @Test
    fun `sign and verify with bytes`() {
        val signingKeyBytes = ByteArray(32).also { secureRandom.nextBytes(it) }
        val signingKey = Ed25519PrivateKeyParameters(signingKeyBytes, 0)
        val verifyingKeyBytes = signingKey.generatePublicKey().encoded

        val encryptionKey = ByteArray(32) { 42 }

        val signature = Signature.signEncryptionKeyBytes(encryptionKey, signingKeyBytes)
        assertEquals(Signature.ED25519_SIGNATURE_SIZE, signature.size)

        val valid = Signature.verifyEncryptionKeyBytes(encryptionKey, verifyingKeyBytes, signature)
        assertTrue(valid)
    }

    @Test
    fun `verify with wrong key fails`() {
        val (signingKey, _) = generateEd25519KeyPair()
        val (_, wrongKey) = generateEd25519KeyPair()

        val encryptionKey = ByteArray(32) { 42 }
        val signature = Signature.signEncryptionKey(encryptionKey, signingKey)

        val valid = Signature.verifyEncryptionKey(encryptionKey, wrongKey, signature)
        assertFalse(valid)
    }

    @Test
    fun `verify with wrong message fails`() {
        val (signingKey, verifyingKey) = generateEd25519KeyPair()

        val encryptionKey = ByteArray(32) { 42 }
        val wrongKey = ByteArray(32) { 99 }

        val signature = Signature.signEncryptionKey(encryptionKey, signingKey)

        val valid = Signature.verifyEncryptionKey(wrongKey, verifyingKey, signature)
        assertFalse(valid)
    }

    @Test
    fun `fingerprint format`() {
        val key = ByteArray(32) { 0 }
        val fp = Signature.fingerprint(key)

        // Should be 4 groups of 4 hex chars separated by spaces: "XXXX XXXX XXXX XXXX"
        assertEquals(19, fp.length)
        assertTrue(fp.matches(Regex("^[0-9A-F]{4} [0-9A-F]{4} [0-9A-F]{4} [0-9A-F]{4}$")))
    }

    @Test
    fun `fingerprint is deterministic`() {
        val key = ByteArray(32) { 123 }
        val fp1 = Signature.fingerprint(key)
        val fp2 = Signature.fingerprint(key)

        assertEquals(fp1, fp2)
    }

    @Test
    fun `different keys have different fingerprints`() {
        val key1 = ByteArray(32) { 1 }
        val key2 = ByteArray(32) { 2 }

        assertNotEquals(Signature.fingerprint(key1), Signature.fingerprint(key2))
    }

    @Test
    fun `getPublicKey returns correct bytes`() {
        val (privateKey, publicKey) = generateEd25519KeyPair()
        val expected = publicKey.encoded

        val result = Signature.getPublicKey(privateKey)
        assertTrue(expected.contentEquals(result))
    }

    @Test
    fun `getPublicKeyBytes from raw bytes`() {
        val privateKeyBytes = ByteArray(32).also { secureRandom.nextBytes(it) }
        val privateKey = Ed25519PrivateKeyParameters(privateKeyBytes, 0)
        val expected = privateKey.generatePublicKey().encoded

        val result = Signature.getPublicKeyBytes(privateKeyBytes)
        assertTrue(expected.contentEquals(result))
    }

    @Test
    fun `invalid encryption key length throws`() {
        val (signingKey, _) = generateEd25519KeyPair()

        assertThrows<AlgoChatException> {
            Signature.signEncryptionKey(ByteArray(16), signingKey)
        }
    }

    @Test
    fun `invalid signing key bytes length throws`() {
        assertThrows<AlgoChatException> {
            Signature.signEncryptionKeyBytes(ByteArray(32), ByteArray(16))
        }
    }

    @Test
    fun `invalid signature length throws`() {
        val (_, verifyingKey) = generateEd25519KeyPair()

        assertThrows<AlgoChatException> {
            Signature.verifyEncryptionKey(ByteArray(32), verifyingKey, ByteArray(32))
        }
    }

    @Test
    fun `invalid verifying key bytes length throws`() {
        assertThrows<AlgoChatException> {
            Signature.verifyEncryptionKeyBytes(ByteArray(32), ByteArray(16), ByteArray(64))
        }
    }
}
