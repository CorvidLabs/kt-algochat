package com.corvidlabs.algochat

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// ============================================================================
// InMemoryMessageCache Tests
// ============================================================================

class InMemoryMessageCacheTest {

    private fun makeMessage(
        id: String,
        sender: String = "ALICE",
        recipient: String = "BOB",
        content: String = "hello",
        round: Long = 100,
        timestamp: Instant = Instant.ofEpochSecond(1700000000)
    ) = Message(
        id = id,
        sender = sender,
        recipient = recipient,
        content = content,
        timestamp = timestamp,
        confirmedRound = round,
        direction = MessageDirection.SENT
    )

    @Test
    fun `store and retrieve messages for participant`() = runTest {
        val cache = InMemoryMessageCache()
        val msg1 = makeMessage("tx1")
        val msg2 = makeMessage("tx2")

        cache.store(listOf(msg1, msg2), "BOB")
        val retrieved = cache.retrieve("BOB")

        assertEquals(2, retrieved.size)
        assertEquals("tx1", retrieved[0].id)
        assertEquals("tx2", retrieved[1].id)
    }

    @Test
    fun `retrieve returns empty list for unknown participant`() = runTest {
        val cache = InMemoryMessageCache()
        val retrieved = cache.retrieve("UNKNOWN")
        assertTrue(retrieved.isEmpty())
    }

    @Test
    fun `store deduplicates messages by id`() = runTest {
        val cache = InMemoryMessageCache()
        val msg = makeMessage("tx1")

        cache.store(listOf(msg), "BOB")
        cache.store(listOf(msg), "BOB")

        val retrieved = cache.retrieve("BOB")
        assertEquals(1, retrieved.size)
    }

    @Test
    fun `store maintains chronological order`() = runTest {
        val cache = InMemoryMessageCache()
        val later = makeMessage("tx1", timestamp = Instant.ofEpochSecond(2000000000))
        val earlier = makeMessage("tx2", timestamp = Instant.ofEpochSecond(1000000000))

        cache.store(listOf(later, earlier), "BOB")
        val retrieved = cache.retrieve("BOB")

        assertEquals("tx2", retrieved[0].id) // earlier first
        assertEquals("tx1", retrieved[1].id) // later second
    }

    @Test
    fun `retrieve with afterRound filters messages`() = runTest {
        val cache = InMemoryMessageCache()
        val msg1 = makeMessage("tx1", round = 50)
        val msg2 = makeMessage("tx2", round = 100)
        val msg3 = makeMessage("tx3", round = 150)

        cache.store(listOf(msg1, msg2, msg3), "BOB")
        val retrieved = cache.retrieve("BOB", afterRound = 100)

        assertEquals(1, retrieved.size)
        assertEquals("tx3", retrieved[0].id)
    }

    @Test
    fun `sync round management`() = runTest {
        val cache = InMemoryMessageCache()

        assertNull(cache.getLastSyncRound("BOB"))

        cache.setLastSyncRound(500, "BOB")
        assertEquals(500L, cache.getLastSyncRound("BOB"))

        cache.setLastSyncRound(1000, "BOB")
        assertEquals(1000L, cache.getLastSyncRound("BOB"))
    }

    @Test
    fun `getCachedConversations returns all participants`() = runTest {
        val cache = InMemoryMessageCache()

        cache.store(listOf(makeMessage("tx1")), "ALICE")
        cache.store(listOf(makeMessage("tx2")), "BOB")
        cache.store(listOf(makeMessage("tx3")), "CAROL")

        val conversations = cache.getCachedConversations()
        assertEquals(3, conversations.size)
        assertTrue(conversations.containsAll(listOf("ALICE", "BOB", "CAROL")))
    }

    @Test
    fun `clear removes all data`() = runTest {
        val cache = InMemoryMessageCache()

        cache.store(listOf(makeMessage("tx1")), "BOB")
        cache.setLastSyncRound(500, "BOB")

        cache.clear()

        assertTrue(cache.retrieve("BOB").isEmpty())
        assertNull(cache.getLastSyncRound("BOB"))
        assertTrue(cache.getCachedConversations().isEmpty())
    }

    @Test
    fun `clearFor removes data for specific participant`() = runTest {
        val cache = InMemoryMessageCache()

        cache.store(listOf(makeMessage("tx1")), "BOB")
        cache.store(listOf(makeMessage("tx2")), "ALICE")
        cache.setLastSyncRound(500, "BOB")
        cache.setLastSyncRound(600, "ALICE")

        cache.clearFor("BOB")

        assertTrue(cache.retrieve("BOB").isEmpty())
        assertNull(cache.getLastSyncRound("BOB"))
        assertEquals(1, cache.retrieve("ALICE").size)
        assertEquals(600L, cache.getLastSyncRound("ALICE"))
    }
}

// ============================================================================
// PublicKeyCache Tests
// ============================================================================

class PublicKeyCacheTest {

    private val testKey = ByteArray(32) { it.toByte() }
    private val testAddress = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAY5HFKQ"

    @Test
    fun `store and retrieve key`() = runTest {
        val cache = PublicKeyCache()

        cache.store(testAddress, testKey)
        val retrieved = cache.retrieve(testAddress)

        assertNotNull(retrieved)
        assertTrue(testKey.contentEquals(retrieved))
    }

    @Test
    fun `retrieve returns null for unknown address`() = runTest {
        val cache = PublicKeyCache()
        assertNull(cache.retrieve("UNKNOWN"))
    }

    @Test
    fun `retrieve returns null for expired key`() = runTest {
        val cache = PublicKeyCache(ttl = Duration.ofMillis(1))

        cache.store(testAddress, testKey)
        Thread.sleep(5)  // Let TTL expire

        assertNull(cache.retrieve(testAddress))
    }

    @Test
    fun `stored key is a copy (not reference)`() = runTest {
        val cache = PublicKeyCache()
        val original = ByteArray(32) { it.toByte() }

        cache.store(testAddress, original)
        original[0] = 0xFF.toByte() // Modify original

        val retrieved = cache.retrieve(testAddress)!!
        assertEquals(0.toByte(), retrieved[0]) // Should be unmodified
    }

    @Test
    fun `retrieved key is a copy (not reference)`() = runTest {
        val cache = PublicKeyCache()

        cache.store(testAddress, testKey)
        val retrieved1 = cache.retrieve(testAddress)!!
        retrieved1[0] = 0xFF.toByte() // Modify retrieved copy

        val retrieved2 = cache.retrieve(testAddress)!!
        assertEquals(0.toByte(), retrieved2[0]) // Should be unmodified
    }

    @Test
    fun `invalidate removes key`() = runTest {
        val cache = PublicKeyCache()

        cache.store(testAddress, testKey)
        cache.invalidate(testAddress)

        assertNull(cache.retrieve(testAddress))
    }

    @Test
    fun `clear removes all keys`() = runTest {
        val cache = PublicKeyCache()

        cache.store("ADDR1", testKey)
        cache.store("ADDR2", testKey)

        cache.clear()

        assertNull(cache.retrieve("ADDR1"))
        assertNull(cache.retrieve("ADDR2"))
    }

    @Test
    fun `pruneExpired removes only expired entries`() = runTest {
        val shortTtl = PublicKeyCache(ttl = Duration.ofMillis(1))

        shortTtl.store("EXPIRE_ME", testKey)
        Thread.sleep(5) // Let it expire

        // Store a fresh one (will use the same short TTL but we check right after)
        val freshKey = ByteArray(32) { (it + 10).toByte() }
        shortTtl.store("KEEP_ME", freshKey)

        shortTtl.pruneExpired()

        assertNull(shortTtl.retrieve("EXPIRE_ME"))
        assertNotNull(shortTtl.retrieve("KEEP_ME"))
    }
}

// ============================================================================
// InMemoryKeyStorage Tests
// ============================================================================

class InMemoryKeyStorageTest {

    private val testKey = ByteArray(32) { it.toByte() }

    @Test
    fun `store and retrieve key`() = runTest {
        val storage = InMemoryKeyStorage()

        storage.store(testKey, "ALICE")
        val retrieved = storage.retrieve("ALICE")

        assertTrue(testKey.contentEquals(retrieved))
    }

    @Test
    fun `retrieve throws for unknown address`() = runTest {
        val storage = InMemoryKeyStorage()

        assertThrows<AlgoChatException.KeyNotFound> {
            storage.retrieve("UNKNOWN")
        }
    }

    @Test
    fun `hasKey returns correct state`() = runTest {
        val storage = InMemoryKeyStorage()

        assertFalse(storage.hasKey("ALICE"))

        storage.store(testKey, "ALICE")
        assertTrue(storage.hasKey("ALICE"))
    }

    @Test
    fun `delete removes key`() = runTest {
        val storage = InMemoryKeyStorage()

        storage.store(testKey, "ALICE")
        storage.delete("ALICE")

        assertFalse(storage.hasKey("ALICE"))
    }

    @Test
    fun `listStoredAddresses returns all addresses`() = runTest {
        val storage = InMemoryKeyStorage()

        storage.store(testKey, "ALICE")
        storage.store(testKey, "BOB")

        val addresses = storage.listStoredAddresses()
        assertEquals(2, addresses.size)
        assertTrue(addresses.containsAll(listOf("ALICE", "BOB")))
    }

    @Test
    fun `stored key is a copy (not reference)`() = runTest {
        val storage = InMemoryKeyStorage()
        val original = ByteArray(32) { it.toByte() }

        storage.store(original, "ALICE")
        original[0] = 0xFF.toByte()

        val retrieved = storage.retrieve("ALICE")
        assertEquals(0.toByte(), retrieved[0])
    }

    @Test
    fun `retrieved key is a copy (not reference)`() = runTest {
        val storage = InMemoryKeyStorage()

        storage.store(testKey, "ALICE")
        val retrieved1 = storage.retrieve("ALICE")
        retrieved1[0] = 0xFF.toByte()

        val retrieved2 = storage.retrieve("ALICE")
        assertEquals(0.toByte(), retrieved2[0])
    }
}
