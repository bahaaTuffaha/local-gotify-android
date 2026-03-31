package com.github.gotify.messages.provider

internal class MessageState {
    var appId = 0L
    var loaded = false
    var hasNext = false
    var nextSince = 0L
    var messages = mutableListOf<StoredMessage>()

    companion object {
        const val ALL_MESSAGES = -1L
    }
}
