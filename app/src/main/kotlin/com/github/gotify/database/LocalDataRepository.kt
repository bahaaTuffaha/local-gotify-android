package com.github.gotify.database

import android.content.Context
import com.github.gotify.client.model.Application
import com.github.gotify.client.model.Message
import com.github.gotify.messages.provider.StoredMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tinylog.kotlin.Logger

internal class LocalDataRepository(context: Context) {
    private val db = GotifyDatabase.get(context)
    private val messageDao = db.messageDao()
    private val messageMarkerDao = db.messageMarkerDao()
    private val applicationDao = db.applicationDao()

    suspend fun getAllMessages(): List<StoredMessage> = withContext(Dispatchers.IO) {
        mergeMessagesWithMarkers(messageDao.getAll(), messageMarkerDao.getAll())
    }

    suspend fun getMessagesByApp(appId: Long): List<StoredMessage> = withContext(Dispatchers.IO) {
        mergeMessagesWithMarkers(messageDao.getByApp(appId), messageMarkerDao.getByApp(appId))
    }

    suspend fun insertMessages(messages: List<Message>) = withContext(Dispatchers.IO) {
        try {
            val messageIds = messages.mapNotNull { it.id }.distinct()
            val existingMessagesById = if (messageIds.isEmpty()) {
                emptyMap()
            } else {
                messageDao.getByIds(messageIds).associateBy { it.id }
            }
            val existingMarkersById = if (messageIds.isEmpty()) {
                emptyMap()
            } else {
                messageMarkerDao.getByIds(messageIds).associateBy { it.id }
            }
            val dbMessages = messages.filter { it.id != null }.map { message ->
                val existing = existingMessagesById[message.id]
                val marker = existingMarkersById[message.id]
                message.toDb(
                    isRead = marker?.isRead ?: existing?.isRead ?: false,
                    isFavorite = marker?.isFavorite ?: existing?.isFavorite ?: false
                )
            }
            if (dbMessages.isNotEmpty()) {
                messageDao.insert(dbMessages)
            }
        } catch (e: Exception) {
            Logger.error(e, "LocalDataRepository: failed to insert messages into Room")
        }
    }

    suspend fun replaceAllMessages(messages: List<Message>) = withContext(Dispatchers.IO) {
        messageDao.deleteAll()
        insertMessages(messages)
    }

    suspend fun replaceMessagesByApp(appId: Long, messages: List<Message>) = withContext(Dispatchers.IO) {
        messageDao.deleteByApp(appId)
        insertMessages(messages)
    }

    suspend fun updateMessageReadState(id: Long, isRead: Boolean) = withContext(Dispatchers.IO) {
        val appId = messageDao.getByIds(listOf(id)).firstOrNull()?.appid
            ?: messageMarkerDao.getByIds(listOf(id)).firstOrNull()?.appid
            ?: 0L
        upsertMarker(id, appId, isRead = isRead, isFavorite = null)
        messageDao.updateReadState(id, isRead)
    }

    suspend fun updateMessageFavoriteState(id: Long, isFavorite: Boolean) =
        withContext(Dispatchers.IO) {
            val appId = messageDao.getByIds(listOf(id)).firstOrNull()?.appid
                ?: messageMarkerDao.getByIds(listOf(id)).firstOrNull()?.appid
                ?: 0L
            upsertMarker(id, appId, isRead = null, isFavorite = isFavorite)
            messageDao.updateFavoriteState(id, isFavorite)
        }

    suspend fun exportMarkerSnapshots(): List<MessageMarkerSnapshot> = withContext(Dispatchers.IO) {
        messageMarkerDao.getAll().map { it.toSnapshot() }.sortedByDescending { it.id }
    }

    suspend fun importMarkerSnapshots(snapshots: List<MessageMarkerSnapshot>) = withContext(Dispatchers.IO) {
        val filtered = snapshots.filter { it.isRead || it.isFavorite }
        if (filtered.isEmpty()) return@withContext

        messageMarkerDao.insert(filtered.map { it.toDb() })
        val existingMessages = messageDao.getByIds(filtered.map { it.id }).associateBy { it.id }
        filtered.forEach { snapshot ->
            if (existingMessages.containsKey(snapshot.id)) {
                messageDao.updateReadState(snapshot.id, snapshot.isRead)
                messageDao.updateFavoriteState(snapshot.id, snapshot.isFavorite)
            }
        }
    }

    suspend fun upsertMarkerSnapshot(snapshot: MessageMarkerSnapshot) = withContext(Dispatchers.IO) {
        if (!snapshot.isRead && !snapshot.isFavorite) {
            messageMarkerDao.delete(snapshot.id)
        } else {
            messageMarkerDao.insert(snapshot.toDb())
        }
    }

    suspend fun deleteMessage(id: Long) = withContext(Dispatchers.IO) {
        messageMarkerDao.delete(id)
        messageDao.delete(id)
    }

    suspend fun deleteMessagesByApp(appId: Long) = withContext(Dispatchers.IO) {
        messageMarkerDao.deleteByApp(appId)
        messageDao.deleteByApp(appId)
    }

    suspend fun deleteAllMessages() = withContext(Dispatchers.IO) {
        messageMarkerDao.deleteAll()
        messageDao.deleteAll()
    }

    suspend fun getAllApplications(): List<Application> = withContext(Dispatchers.IO) {
        applicationDao.getAll().map { it.toClient() }
    }

    suspend fun insertApplications(applications: List<Application>) = withContext(Dispatchers.IO) {
        applicationDao.insert(applications.map { it.toDb() })
    }

    suspend fun deleteAllApplications() = withContext(Dispatchers.IO) {
        applicationDao.deleteAll()
    }

    suspend fun hasData(): Boolean = withContext(Dispatchers.IO) {
        applicationDao.getAll().isNotEmpty()
    }

    private fun mergeMessagesWithMarkers(
        dbMessages: List<DbMessage>,
        markers: List<DbMessageMarker>
    ): List<StoredMessage> {
        val markerById = markers.associateBy { it.id }
        val merged = dbMessages.map { dbMessage ->
            val marker = markerById[dbMessage.id]
            dbMessage.toStored(
                isRead = marker?.isRead ?: dbMessage.isRead,
                isFavorite = marker?.isFavorite ?: dbMessage.isFavorite
            )
        }.toMutableList()
        val existingIds = dbMessages.map { it.id }.toHashSet()
        markers.filter { it.id !in existingIds }.forEach { marker ->
            merged.add(marker.toPlaceholderStoredMessage())
        }
        return merged.sortedByDescending { it.id }
    }

    private fun upsertMarker(
        id: Long,
        appId: Long,
        isRead: Boolean?,
        isFavorite: Boolean?
    ) {
        val existing = messageMarkerDao.getByIds(listOf(id)).firstOrNull()
        val updated = DbMessageMarker(
            id = id,
            appid = existing?.appid?.takeIf { it != 0L } ?: appId,
            isRead = isRead ?: existing?.isRead ?: false,
            isFavorite = isFavorite ?: existing?.isFavorite ?: false
        )
        if (!updated.isRead && !updated.isFavorite) {
            messageMarkerDao.delete(id)
        } else {
            messageMarkerDao.insert(updated)
        }
    }
}
