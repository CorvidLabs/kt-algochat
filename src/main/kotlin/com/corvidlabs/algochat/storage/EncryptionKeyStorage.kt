package com.corvidlabs.algochat.storage

import java.util.concurrent.ConcurrentHashMap

/**
 * Exception thrown when a key is not found.
 */
class KeyNotFoundException(val address: String) :
    Exception("Key not found for address: $address")

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
    private val keys = ConcurrentHashMap<String, ByteArray>()

    override suspend fun store(privateKey: ByteArray, address: String, requireBiometric: Boolean) {
        keys[address] = privateKey.copyOf()
    }

    override suspend fun retrieve(address: String): ByteArray {
        val key = keys[address] ?: throw KeyNotFoundException(address)
        return key.copyOf()
    }

    override suspend fun hasKey(address: String): Boolean = address in keys

    override suspend fun delete(address: String) {
        keys.remove(address)
    }

    override suspend fun listStoredAddresses(): List<String> = keys.keys().toList()
}
