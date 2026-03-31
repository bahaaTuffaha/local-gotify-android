package com.github.gotify.messages.provider

import com.github.gotify.client.model.Application

internal object MessageImageCombiner {
    fun combine(
        messages: List<StoredMessage>,
        applications: List<Application>
    ): List<MessageWithImage> {
        val appIdToImage = appIdToImage(applications)
        return messages.map {
            MessageWithImage(
                message = it.message,
                image = appIdToImage[it.appId],
                isRead = it.isRead,
                isFavorite = it.isFavorite,
                isPlaceholder = it.isPlaceholder
            )
        }
    }

    private fun appIdToImage(applications: List<Application>): Map<Long, String> {
        val map = mutableMapOf<Long, String>()
        applications.forEach {
            map[it.id] = it.image
        }
        return map
    }
}
