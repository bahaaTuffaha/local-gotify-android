package com.github.gotify.messages.provider

import com.github.gotify.client.model.Message

internal data class MessageWithImage(
	val message: Message,
	val image: String?,
	val isRead: Boolean,
	val isFavorite: Boolean,
	val isPlaceholder: Boolean
)
