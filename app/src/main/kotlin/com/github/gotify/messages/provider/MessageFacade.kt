package com.github.gotify.messages.provider

import com.github.gotify.client.api.MessageApi
import com.github.gotify.client.model.Message
import com.github.gotify.client.model.PagedMessages
import com.github.gotify.client.model.Paging
import com.github.gotify.database.LocalDataRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.tinylog.kotlin.Logger

internal class MessageFacade(
    private val api: MessageApi,
    private val applicationHolder: ApplicationHolder,
    private val repository: LocalDataRepository
) {
    private val requester = MessageRequester(api)
    private val state = MessageStateHolder()

    @Synchronized
    operator fun get(appId: Long): List<MessageWithImage> {
        return MessageImageCombiner.combine(state.state(appId).messages, applicationHolder.get())
    }

    @Synchronized
    fun addMessages(messages: List<Message>) {
        messages.forEach {
            state.newMessage(it)
        }
        CoroutineScope(Dispatchers.IO).launch {
            repository.insertMessages(messages)
        }
    }

    fun isLoaded(appId: Long): Boolean = state.state(appId).loaded

    suspend fun loadFromDb(appId: Long) {
        val currentState = state.state(appId)
        if (!currentState.loaded) {
            val localMessages = if (appId == MessageState.ALL_MESSAGES) {
                repository.getAllMessages()
            } else {
                repository.getMessagesByApp(appId)
            }
            val localPaged = PagedMessages().paging(Paging())
            localPaged.messages.addAll(localMessages)
            this.state.newMessages(appId, localPaged)
        }
    }

    suspend fun loadMore(appId: Long): List<MessageWithImage> {
        val currentState = state.state(appId)
        if (currentState.hasNext || !currentState.loaded) {
            try {
                val pagedMessages = requester.loadMore(currentState)
                if (pagedMessages != null) {
                    this.state.newMessages(appId, pagedMessages)
                    repository.insertMessages(pagedMessages.messages)
                } else {
                    if (!currentState.loaded) {
                        this.state.newMessages(appId, PagedMessages())
                    }
                }
            } catch (e: Exception) {
                Logger.error(e, "MessageFacade: network load failed")
                if (!currentState.loaded) {
                    this.state.newMessages(appId, PagedMessages())
                }
            }
        }
        return get(appId)
    }

    suspend fun loadMoreIfNotPresent(appId: Long) {
        val state = state.state(appId)
        if (!state.loaded) {
            loadMore(appId)
        }
    }

    @Synchronized
    fun clear() {
        state.clear()
    }

    fun getLastReceivedMessage(): Long = state.lastReceivedMessage

    @Synchronized
    fun deleteLocal(message: Message) {
        // If there is already a deletion pending, that one should be executed before scheduling the
        // next deletion.
        if (state.deletionPending()) commitDelete()
        state.deleteMessage(message)
        CoroutineScope(Dispatchers.IO).launch {
            repository.deleteMessage(message.id)
        }
    }

    @Synchronized
    fun commitDelete() {
        if (state.deletionPending()) {
            val deletion = state.purgePendingDeletion()
            requester.asyncRemoveMessage(deletion!!.message)
        }
    }

    @Synchronized
    fun undoDeleteLocal(): MessageDeletion? {
        val deletion = state.undoPendingDeletion()
        if (deletion != null) {
            CoroutineScope(Dispatchers.IO).launch {
                repository.insertMessages(listOf(deletion.message))
            }
        }
        return deletion
    }

    suspend fun deleteAll(appId: Long): Boolean {
        val success = requester.deleteAll(appId)
        if (success) {
            state.deleteAll(appId)
            if (appId == MessageState.ALL_MESSAGES) {
                repository.deleteAllMessages()
            } else {
                repository.deleteMessagesByApp(appId)
            }
        }
        return success
    }

    @Synchronized
    fun canLoadMore(appId: Long): Boolean = state.state(appId).hasNext
}
