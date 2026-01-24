package com.corvidlabs.algochat

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant

// ============================================================================
// Message Cache
// ============================================================================

/**
 * Interface for storing and retrieving messages.
 */
interface MessageCache {
    /** Store messages for a conversation. */
    suspend fun store(messages: List<Message>, participant: String)

    /** Retrieve cached messages for a conversation. */
    suspend fun retrieve(participant: String, afterRound: Long? = null): List<Message>

    /** Get the last synced round for a conversation. */
    suspend fun getLastSyncRound(participant: String): Long?

    /** Set the last synced round for a conversation. */
    suspend fun setLastSyncRound(round: Long, participant: String)

    /** Get all cached conversation participants. */
    suspend fun getCachedConversations(): List<String>

    /** Clear all cached data. */
    suspend fun clear()

    /** Clear cached data for a specific conversation. */
    suspend fun clearFor(participant: String)
}

/**
 * In-memory implementation of MessageCache.
 */
class InMemoryMessageCache : MessageCache {
    private val messages = mutableMapOf<String, MutableList<Message>>()
    private val syncRounds = mutableMapOf<String, Long>()
    private val mutex = Mutex()

    override suspend fun store(messages: List<Message>, participant: String) {
        mutex.withLock {
            val existing = this.messages.getOrPut(participant) { mutableListOf() }
            val existingIds = existing.map { it.id }.toSet()

            for (message in messages) {
                if (message.id !in existingIds) {
                    existing.add(message)
                }
            }

            existing.sortBy { it.timestamp }
        }
    }

    override suspend fun retrieve(participant: String, afterRound: Long?): List<Message> {
        mutex.withLock {
            val cached = messages[participant]?.toList() ?: emptyList()

            return if (afterRound != null) {
                cached.filter { it.confirmedRound > afterRound }
            } else {
                cached
            }
        }
    }

    override suspend fun getLastSyncRound(participant: String): Long? {
        mutex.withLock {
            return syncRounds[participant]
        }
    }

    override suspend fun setLastSyncRound(round: Long, participant: String) {
        mutex.withLock {
            syncRounds[participant] = round
        }
    }

    override suspend fun getCachedConversations(): List<String> {
        mutex.withLock {
            return messages.keys.toList()
        }
    }

    override suspend fun clear() {
        mutex.withLock {
            messages.clear()
            syncRounds.clear()
        }
    }

    override suspend fun clearFor(participant: String) {
        mutex.withLock {
            messages.remove(participant)
            syncRounds.remove(participant)
        }
    }
}

// ============================================================================
// Public Key Cache
// ============================================================================

/**
 * Entry in the public key cache with expiration.
 */
private data class CacheEntry(
    val key: ByteArray,
    val expiresAt: Instant
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CacheEntry) return false
        return key.contentEquals(other.key) && expiresAt == other.expiresAt
    }

    override fun hashCode(): Int {
        var result = key.contentHashCode()
        result = 31 * result + expiresAt.hashCode()
        return result
    }
}

/**
 * In-memory cache for public keys with TTL expiration.
 */
class PublicKeyCache(
    private val ttl: Duration = Duration.ofHours(24)
) {
    private val cache = mutableMapOf<String, CacheEntry>()
    private val mutex = Mutex()

    /** Store a public key for an address. */
    suspend fun store(address: String, key: ByteArray) {
        mutex.withLock {
            cache[address] = CacheEntry(
                key = key.copyOf(),
                expiresAt = Instant.now().plus(ttl)
            )
        }
    }

    /** Retrieve a public key for an address (returns null if expired). */
    suspend fun retrieve(address: String): ByteArray? {
        mutex.withLock {
            val entry = cache[address] ?: return null
            return if (entry.expiresAt.isAfter(Instant.now())) {
                entry.key.copyOf()
            } else {
                null
            }
        }
    }

    /** Invalidate the cached key for an address. */
    suspend fun invalidate(address: String) {
        mutex.withLock {
            cache.remove(address)
        }
    }

    /** Clear all cached keys. */
    suspend fun clear() {
        mutex.withLock {
            cache.clear()
        }
    }

    /** Remove all expired entries. */
    suspend fun pruneExpired() {
        mutex.withLock {
            val now = Instant.now()
            cache.entries.removeIf { it.value.expiresAt.isBefore(now) || it.value.expiresAt == now }
        }
    }
}

// ============================================================================
// Encryption Key Storage
// ============================================================================

/**
 * Interface for storing encryption private keys.
 */
interface EncryptionKeyStorage {
    /** Store a private key for an address. */
    suspend fun store(privateKey: ByteArray, address: String, requireBiometric: Boolean = false)

    /** Retrieve a private key for an address. */
    suspend fun retrieve(address: String): ByteArray

    /** Check if a key exists for an address. */
    suspend fun hasKey(address: String): Boolean

    /** Delete a key for an address. */
    suspend fun delete(address: String)

    /** List all stored addresses. */
    suspend fun listStoredAddresses(): List<String>
}

/**
 * In-memory implementation of EncryptionKeyStorage (for testing).
 *
 * WARNING: This is NOT secure for production use. Keys are stored in memory
 * without encryption and are lost when the process exits.
 */
class InMemoryKeyStorage : EncryptionKeyStorage {
    private val keys = mutableMapOf<String, ByteArray>()
    private val mutex = Mutex()

    override suspend fun store(privateKey: ByteArray, address: String, requireBiometric: Boolean) {
        mutex.withLock {
            keys[address] = privateKey.copyOf()
        }
    }

    override suspend fun retrieve(address: String): ByteArray {
        mutex.withLock {
            return keys[address]?.copyOf()
                ?: throw AlgoChatException.KeyNotFound(address)
        }
    }

    override suspend fun hasKey(address: String): Boolean {
        mutex.withLock {
            return address in keys
        }
    }

    override suspend fun delete(address: String) {
        mutex.withLock {
            keys.remove(address)
        }
    }

    override suspend fun listStoredAddresses(): List<String> {
        mutex.withLock {
            return keys.keys.toList()
        }
    }
}
