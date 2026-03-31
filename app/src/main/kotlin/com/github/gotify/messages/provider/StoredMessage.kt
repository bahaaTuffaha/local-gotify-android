package com.github.gotify.messages.provider

import com.github.gotify.Utils
import com.github.gotify.client.model.Message
import com.google.gson.JsonObject

internal data class StoredMessage(
    val message: Message,
    val isRead: Boolean = false,
    val isFavorite: Boolean = false,
    val isPlaceholder: Boolean = false
) {
    val id: Long
        get() = message.id

    val appId: Long
        get() = message.appid

    companion object {
        fun placeholder(id: Long, appId: Long, isRead: Boolean, isFavorite: Boolean): StoredMessage {
            val json = JsonObject().apply {
                addProperty("id", id)
                addProperty("appid", appId)
            }
            return StoredMessage(
                message = Utils.JSON.fromJson(json, Message::class.java),
                isRead = isRead,
                isFavorite = isFavorite,
                isPlaceholder = true
            )
        }
    }
}