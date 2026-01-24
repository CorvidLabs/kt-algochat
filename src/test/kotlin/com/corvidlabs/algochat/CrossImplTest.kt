package com.corvidlabs.algochat

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Cross-implementation tests for AlgoChat.
 *
 * These tests verify that Kotlin can decrypt messages encrypted by other
 * implementations, ensuring full protocol compatibility.
 */
class CrossImplTest {
    companion object {
        const val BOB_SEED_HEX = "0000000000000000000000000000000000000000000000000000000000000002"

        val TEST_MESSAGES = mapOf(
            "empty" to "",
            "single_char" to "X",
            "whitespace" to "   \t\n   ",
            "numbers" to "1234567890",
            "punctuation" to "!@#\$%^&*()_+-=[]{}\\|;':\",./<>?",
            "newlines" to "Line 1\nLine 2\nLine 3",
            "emoji_simple" to "Hello 👋 World 🌍",
            "emoji_zwj" to "Family: 👨‍👩‍👧‍👦",
            "chinese" to "你好世界 - Hello World",
            "arabic" to "مرحبا بالعالم",
            "japanese" to "こんにちは世界 カタカナ 漢字",
            "korean" to "안녕하세요 세계",
            "accents" to "Café résumé naïve",
            "cyrillic" to "Привет мир",
            "json" to """{"key": "value", "num": 42}""",
            "html" to """<div class="test">Content</div>""",
            "url" to "https://example.com/path?q=test&lang=en",
            "code" to """func hello() { print("Hi") }""",
            "long_text" to "The quick brown fox jumps over the lazy dog. ".repeat(11),
            "max_payload" to "A".repeat(Protocol.MAX_PAYLOAD_SIZE)
        )

        fun hexToBytes(hex: String): ByteArray {
            return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }

        fun bobKeys(): KeyPair {
            return Keys.deriveKeysFromSeed(hexToBytes(BOB_SEED_HEX))
        }
    }

    private fun decryptEnvelopeFile(file: File, bob: KeyPair): String? {
        if (!file.exists()) return null

        return try {
            val hexContent = file.readText().trim()
            val envelopeBytes = hexToBytes(hexContent)

            if (!isChatMessage(envelopeBytes)) return null

            val envelope = ChatEnvelope.decode(envelopeBytes)
            val result = Crypto.decryptMessage(envelope, bob.privateKey, bob.publicKey)
            result?.text
        } catch (e: Exception) {
            null
        }
    }

    @Test
    fun `decrypt Swift envelopes`() {
        val swiftDir = File("../test-algochat/test-envelopes-swift")
        if (!swiftDir.exists()) {
            println("Skipping Swift envelope tests - directory not found")
            return
        }

        val bob = bobKeys()
        var passed = 0
        var failed = 0

        for ((key, expected) in TEST_MESSAGES) {
            val file = File(swiftDir, "$key.hex")
            val decrypted = decryptEnvelopeFile(file, bob)

            if (decrypted == expected) {
                passed++
                println("✓ $key")
            } else if (decrypted != null) {
                failed++
                println("✗ $key - mismatch")
            } else if (file.exists()) {
                failed++
                println("✗ $key - failed to decrypt")
            }
        }

        println("Swift cross-impl: $passed/${passed + failed} passed")
        assertEquals(0, failed, "Some Swift envelopes failed to decrypt")
    }

    @Test
    fun `decrypt TypeScript envelopes`() {
        val tsDir = File("../test-algochat/test-envelopes-ts")
        if (!tsDir.exists()) {
            println("Skipping TypeScript envelope tests - directory not found")
            return
        }

        val bob = bobKeys()
        var passed = 0
        var failed = 0

        for ((key, expected) in TEST_MESSAGES) {
            val file = File(tsDir, "$key.hex")
            val decrypted = decryptEnvelopeFile(file, bob)

            if (decrypted == expected) {
                passed++
            } else if (file.exists()) {
                failed++
            }
        }

        println("TypeScript cross-impl: $passed/${passed + failed} passed")
        assertEquals(0, failed, "Some TypeScript envelopes failed to decrypt")
    }

    @Test
    fun `decrypt Python envelopes`() {
        val pyDir = File("../test-algochat/test-envelopes-python")
        if (!pyDir.exists()) {
            println("Skipping Python envelope tests - directory not found")
            return
        }

        val bob = bobKeys()
        var passed = 0
        var failed = 0

        for ((key, expected) in TEST_MESSAGES) {
            val file = File(pyDir, "$key.hex")
            val decrypted = decryptEnvelopeFile(file, bob)

            if (decrypted == expected) {
                passed++
            } else if (file.exists()) {
                failed++
            }
        }

        println("Python cross-impl: $passed/${passed + failed} passed")
        assertEquals(0, failed, "Some Python envelopes failed to decrypt")
    }
}
