package com.github.gotify.messages.provider

import com.github.gotify.client.api.MessageApi
import com.github.gotify.client.model.Message
import com.github.gotify.client.model.Paging
import com.github.gotify.database.MessageMarkerSnapshot
import com.github.gotify.database.LocalDataRepository
import com.github.gotify.messages.MessageListMode
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
    operator fun get(appId: Long, mode: MessageListMode): List<MessageWithImage> {
        val sourceAppId = if (mode == MessageListMode.FAVORITES) MessageState.ALL_MESSAGES else appId
        val visibleMessages = state.state(sourceAppId).messages.filter { message ->
            when (mode) {
                MessageListMode.UNREAD -> !message.isRead
                MessageListMode.READ -> message.isRead
                MessageListMode.FAVORITES -> message.isFavorite
            }
        }
        return MessageImageCombiner.combine(visibleMessages, applicationHolder.get())
    }

    @Synchronized
    fun addMessages(messages: List<Message>) {
        messages.filter { it.id != null }.forEach { message ->
            val existing = state.findMessage(message.id)
            state.newMessage(
                StoredMessage(
                    message = message,
                    isRead = existing?.isRead ?: false,
                    isFavorite = existing?.isFavorite ?: false,
                    isPlaceholder = false
                )
            )
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
            this.state.newMessages(
                appId = appId,
                incomingMessages = localMessages,
                hasNext = true,
                nextSince = 0L
            )
        }
    }

    suspend fun loadMore(appId: Long, mode: MessageListMode): List<MessageWithImage> {
        val currentState = state.state(appId)
        if (currentState.hasNext || !currentState.loaded) {
            try {
                val pagedMessages = requester.loadMore(currentState)
                if (pagedMessages != null) {
                    val isFirstPage = currentState.nextSince == 0L
                    val incomingMessages = pagedMessages.messages.filter { it.id != null }.map { message ->
                        val existing = state.findMessage(message.id)
                        StoredMessage(
                            message = message,
                            isRead = existing?.isRead ?: false,
                            isFavorite = existing?.isFavorite ?: false,
                            isPlaceholder = false
                        )
                    }
                    if (isFirstPage) {
                        if (appId == MessageState.ALL_MESSAGES) {
                            repository.replaceAllMessages(pagedMessages.messages)
                        } else {
                            repository.replaceMessagesByApp(appId, pagedMessages.messages)
                        }
                        state.clear()
                    }
                    this.state.newMessages(
                        appId = appId,
                        incomingMessages = incomingMessages,
                        hasNext = pagedMessages.paging?.next != null,
                        nextSince = pagedMessages.paging?.since ?: 0L
                    )
                    if (!isFirstPage) {
                        repository.insertMessages(pagedMessages.messages)
                    }
                } else {
                    if (!currentState.loaded) {
                        this.state.newMessages(appId, emptyList(), false, 0L)
                    }
                }
            } catch (e: Exception) {
                Logger.error(e, "MessageFacade: network load failed")
                if (!currentState.loaded) {
                    this.state.newMessages(appId, emptyList(), false, 0L)
                }
            }
        }
        return get(appId, mode)
    }

    suspend fun loadMoreIfNotPresent(appId: Long) {
        val state = state.state(appId)
        if (!state.loaded) {
            loadMore(appId, MessageListMode.UNREAD)
        }
    }

    @Synchronized
    fun clear() {
        state.clear()
    }

    fun getLastReceivedMessage(): Long = state.lastReceivedMessage

    @Synchronized
    fun markAsRead(message: Message, isRead: Boolean = true) {
        val updated = state.setReadState(message.id, isRead)
        if (updated != null) {
            CoroutineScope(Dispatchers.IO).launch {
                repository.updateMessageReadState(message.id, isRead)
            }
        }
    }

    @Synchronized
    fun toggleFavorite(message: Message): Boolean? {
        val current = state.findMessage(message.id) ?: return null
        val newValue = !current.isFavorite
        state.setFavoriteState(message.id, newValue)
        CoroutineScope(Dispatchers.IO).launch {
            repository.updateMessageFavoriteState(message.id, newValue)
        }
        return newValue
    }

    @Synchronized
    fun deleteLocal(message: Message) {
        // If there is already a deletion pending, that one should be executed before scheduling the
        // next deletion.
        if (state.deletionPending()) commitDelete()
        val storedMessage = state.findMessage(message.id) ?: return
        state.deleteMessage(storedMessage)
        CoroutineScope(Dispatchers.IO).launch {
            repository.deleteMessage(message.id)
        }
    }

    @Synchronized
    fun commitDelete() {
        if (state.deletionPending()) {
            val deletion = state.purgePendingDeletion()
            if (deletion != null && !deletion.message.isPlaceholder) {
                requester.asyncRemoveMessage(deletion.message.message)
            }
        }
    }

    @Synchronized
    fun undoDeleteLocal(): MessageDeletion? {
        val deletion = state.undoPendingDeletion()
        if (deletion != null) {
            CoroutineScope(Dispatchers.IO).launch {
                if (deletion.message.isPlaceholder) {
                    repository.upsertMarkerSnapshot(
                        MessageMarkerSnapshot(
                            id = deletion.message.id,
                            appId = deletion.message.appId,
                            isRead = deletion.message.isRead,
                            isFavorite = deletion.message.isFavorite
                        )
                    )
                } else {
                    repository.insertMessages(listOf(deletion.message.message))
                    repository.updateMessageReadState(deletion.message.id, deletion.message.isRead)
                    repository.updateMessageFavoriteState(
                        deletion.message.id,
                        deletion.message.isFavorite
                    )
                }
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
