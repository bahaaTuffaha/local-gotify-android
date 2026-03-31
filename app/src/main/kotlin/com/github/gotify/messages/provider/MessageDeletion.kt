package com.github.gotify.messages.provider

internal class MessageDeletion(
	val message: StoredMessage,
	val allPosition: Int,
	val appPosition: Int
)
