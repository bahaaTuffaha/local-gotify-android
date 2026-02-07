package com.github.gotify.database

import android.content.Context
import com.github.gotify.client.model.Application
import com.github.gotify.client.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tinylog.kotlin.Logger

internal class LocalDataRepository(context: Context) {
    private val db = GotifyDatabase.get(context)
    private val messageDao = db.messageDao()
    private val applicationDao = db.applicationDao()

    suspend fun getAllMessages(): List<Message> = withContext(Dispatchers.IO) {
        messageDao.getAll().map { it.toClient() }
    }

    suspend fun getMessagesByApp(appId: Long): List<Message> = withContext(Dispatchers.IO) {
        messageDao.getByApp(appId).map { it.toClient() }
    }

    suspend fun insertMessages(messages: List<Message>) = withContext(Dispatchers.IO) {
        try {
            val dbMessages = messages.filter { it.id != null }.map { it.toDb() }
            if (dbMessages.isNotEmpty()) {
                messageDao.insert(dbMessages)
            }
        } catch (e: Exception) {
            Logger.error(e, "LocalDataRepository: failed to insert messages into Room")
        }
    }

    suspend fun deleteMessage(id: Long) = withContext(Dispatchers.IO) {
        messageDao.delete(id)
    }

    suspend fun deleteMessagesByApp(appId: Long) = withContext(Dispatchers.IO) {
        messageDao.deleteByApp(appId)
    }

    suspend fun deleteAllMessages() = withContext(Dispatchers.IO) {
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
}
