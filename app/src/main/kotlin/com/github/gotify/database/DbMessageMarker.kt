package com.github.gotify.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.github.gotify.messages.provider.StoredMessage

@Entity(tableName = "message_marker")
data class DbMessageMarker(
    @PrimaryKey val id: Long,
    val appid: Long,
    val isRead: Boolean = false,
    val isFavorite: Boolean = false
)

data class MessageMarkerSnapshot(
    val id: Long,
    val appId: Long,
    val isRead: Boolean,
    val isFavorite: Boolean
)

internal fun DbMessageMarker.toSnapshot(): MessageMarkerSnapshot = MessageMarkerSnapshot(
    id = id,
    appId = appid,
    isRead = isRead,
    isFavorite = isFavorite
)

internal fun MessageMarkerSnapshot.toDb(): DbMessageMarker = DbMessageMarker(
    id = id,
    appid = appId,
    isRead = isRead,
    isFavorite = isFavorite
)

internal fun DbMessageMarker.toPlaceholderStoredMessage(): StoredMessage = StoredMessage.placeholder(
    id = id,
    appId = appid,
    isRead = isRead,
    isFavorite = isFavorite
)