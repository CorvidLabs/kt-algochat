package com.corvidlabs.algochat.storage

import com.corvidlabs.algochat.models.Message
import java.util.concurrent.ConcurrentHashMap

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
    private val messages = ConcurrentHashMap<String, MutableList<Message>>()
    private val syncRounds = ConcurrentHashMap<String, Long>()

    override suspend fun store(messages: List<Message>, participant: String) {
        val existing = this.messages.getOrPut(participant) { mutableListOf() }
        val existingIds = existing.map { it.id }.toSet()

        synchronized(existing) {
            for (message in messages) {
                if (message.id !in existingIds) {
                    existing.add(message)
                }
            }
            existing.sortBy { it.timestamp }
        }
    }

    override suspend fun retrieve(participant: String, afterRound: Long?): List<Message> {
        val msgs = messages[participant] ?: return emptyList()

        return synchronized(msgs) {
            if (afterRound != null) {
                msgs.filter { it.confirmedRound > afterRound }
            } else {
                msgs.toList()
            }
        }
    }

    override suspend fun getLastSyncRound(participant: String): Long? =
        syncRounds[participant]

    override suspend fun setLastSyncRound(round: Long, participant: String) {
        syncRounds[participant] = round
    }

    override suspend fun getCachedConversations(): List<String> =
        messages.keys().toList()

    override suspend fun clear() {
        messages.clear()
        syncRounds.clear()
    }

    override suspend fun clearFor(participant: String) {
        messages.remove(participant)
        syncRounds.remove(participant)
    }
}
