package com.github.gotify.messages.provider

import kotlin.math.max

internal class MessageStateHolder {
    @get:Synchronized
    var lastReceivedMessage = -1L
        private set
    private var states = mutableMapOf<Long, MessageState>()
    private var pendingDeletion: MessageDeletion? = null

    @Synchronized
    fun clear() {
        states = mutableMapOf()
    }

    @Synchronized
    fun newMessages(
        appId: Long,
        incomingMessages: List<StoredMessage>,
        hasNext: Boolean,
        nextSince: Long
    ) {
        val state = state(appId)

        if (!state.loaded && incomingMessages.isNotEmpty()) {
            lastReceivedMessage = max(incomingMessages[0].id, lastReceivedMessage)
        }

        state.apply {
            loaded = true
            incomingMessages.forEach { incoming ->
                val existingIndex = this.messages.indexOfFirst { it.id == incoming.id }
                if (existingIndex == -1) {
                    this.messages.add(incoming)
                } else {
                    this.messages[existingIndex] = incoming.copy(
                        isRead = incoming.isRead || this.messages[existingIndex].isRead,
                        isFavorite = incoming.isFavorite || this.messages[existingIndex].isFavorite
                    )
                }
            }
            this.messages.sortByDescending { it.id }
            this.hasNext = hasNext
            this.nextSince = nextSince
            this.appId = appId
        }
        states[appId] = state

        // If there is a message with pending deletion, it should not reappear in the list in case
        // it is added again.
        if (deletionPending()) {
            deleteMessage(pendingDeletion!!.message)
        }
    }

    @Synchronized
    fun newMessage(message: StoredMessage) {
        // If there is a message with pending deletion, its indices are going to change. To keep
        // them consistent the deletion is undone first and redone again after adding the new
        // message.
        val deletion = undoPendingDeletion()
        removeMessage(message.id)
        addMessage(message, 0, 0)
        lastReceivedMessage = message.id
        if (deletion != null) deleteMessage(deletion.message)
    }

    @Synchronized
    fun state(appId: Long): MessageState = states[appId] ?: emptyState(appId)

    @Synchronized
    fun deleteAll(appId: Long) {
        clear()
        val state = state(appId)
        state.loaded = true
        states[appId] = state
    }

    private fun emptyState(appId: Long): MessageState {
        return MessageState().apply {
            loaded = false
            hasNext = false
            nextSince = 0L
            this.appId = appId
        }
    }

    @Synchronized
    fun deleteMessage(message: StoredMessage) {
        val allMessages = state(MessageState.ALL_MESSAGES)
        val appMessages = state(message.appId)
        var pendingDeletedAllPosition = -1
        var pendingDeletedAppPosition = -1

        if (allMessages.loaded) {
            val allPosition = allMessages.messages.indexOfFirst { it.id == message.id }
            if (allPosition != -1) allMessages.messages.removeAt(allPosition)
            pendingDeletedAllPosition = allPosition
        }
        if (appMessages.loaded) {
            val appPosition = appMessages.messages.indexOfFirst { it.id == message.id }
            if (appPosition != -1) appMessages.messages.removeAt(appPosition)
            pendingDeletedAppPosition = appPosition
        }
        pendingDeletion = MessageDeletion(
            message,
            pendingDeletedAllPosition,
            pendingDeletedAppPosition
        )
    }

    @Synchronized
    fun undoPendingDeletion(): MessageDeletion? {
        if (pendingDeletion != null) {
            addMessage(
                pendingDeletion!!.message,
                pendingDeletion!!.allPosition,
                pendingDeletion!!.appPosition
            )
        }
        return purgePendingDeletion()
    }

    @Synchronized
    fun purgePendingDeletion(): MessageDeletion? {
        val result = pendingDeletion
        pendingDeletion = null
        return result
    }

    @Synchronized
    fun deletionPending(): Boolean = pendingDeletion != null

    @Synchronized
    fun findMessage(messageId: Long): StoredMessage? {
        states.values.forEach { state ->
            state.messages.firstOrNull { it.id == messageId }?.let { return it }
        }
        return null
    }

    @Synchronized
    fun setReadState(messageId: Long, isRead: Boolean): StoredMessage? {
        return updateMessage(messageId) { it.copy(isRead = isRead) }
    }

    @Synchronized
    fun setFavoriteState(messageId: Long, isFavorite: Boolean): StoredMessage? {
        return updateMessage(messageId) { it.copy(isFavorite = isFavorite) }
    }

    private fun addMessage(message: StoredMessage, allPosition: Int, appPosition: Int) {
        val allMessages = state(MessageState.ALL_MESSAGES)
        val appMessages = state(message.appId)

        if (allMessages.loaded && allPosition != -1) {
            allMessages.messages.add(allPosition, message)
        }
        if (appMessages.loaded && appPosition != -1) {
            appMessages.messages.add(appPosition, message)
        }
    }

    private fun removeMessage(messageId: Long) {
        states.values.forEach { state ->
            val position = state.messages.indexOfFirst { it.id == messageId }
            if (position != -1) {
                state.messages.removeAt(position)
            }
        }
    }

    private fun updateMessage(
        messageId: Long,
        update: (StoredMessage) -> StoredMessage
    ): StoredMessage? {
        var updated: StoredMessage? = null
        states.values.forEach { state ->
            val position = state.messages.indexOfFirst { it.id == messageId }
            if (position != -1) {
                val newValue = update(state.messages[position])
                state.messages[position] = newValue
                updated = newValue
            }
        }
        return updated
    }
}
