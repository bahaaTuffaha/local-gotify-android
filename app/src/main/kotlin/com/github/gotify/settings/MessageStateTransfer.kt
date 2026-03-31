package com.github.gotify.settings

import com.github.gotify.database.MessageMarkerSnapshot

internal data class MessageStateTransfer(
    val version: Int = 1,
    val markers: List<MessageMarkerSnapshot> = emptyList()
)