package com.corvidlabs.algochat.storage

import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Entry in the public key cache with expiration.
 */
private data class CacheEntry(
    val key: ByteArray,
    val expiresAt: Instant
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as CacheEntry
        if (!key.contentEquals(other.key)) return false
        if (expiresAt != other.expiresAt) return false
        return true
    }

    override fun hashCode(): Int {
        var result = key.contentHashCode()
        result = 31 * result + expiresAt.hashCode()
        return result
    }
}

/** Default TTL: 24 hours */
private val DEFAULT_TTL: Duration = Duration.ofHours(24)

/**
 * In-memory cache for public keys with TTL expiration.
 */
class PublicKeyCache(
    private val ttl: Duration = DEFAULT_TTL
) {
    private val cache = ConcurrentHashMap<String, CacheEntry>()

    /** Store a public key for an address. */
    fun store(address: String, key: ByteArray) {
        cache[address] = CacheEntry(
            key = key.copyOf(),
            expiresAt = Instant.now().plus(ttl)
        )
    }

    /** Retrieve a public key for an address (returns null if expired). */
    fun retrieve(address: String): ByteArray? {
        val entry = cache[address] ?: return null

        if (entry.expiresAt <= Instant.now()) {
            cache.remove(address)
            return null
        }

        return entry.key.copyOf()
    }

    /** Invalidate the cached key for an address. */
    fun invalidate(address: String) {
        cache.remove(address)
    }

    /** Clear all cached keys. */
    fun clear() {
        cache.clear()
    }

    /** Remove all expired entries. */
    fun pruneExpired() {
        val now = Instant.now()
        cache.entries.removeIf { it.value.expiresAt <= now }
    }
}
