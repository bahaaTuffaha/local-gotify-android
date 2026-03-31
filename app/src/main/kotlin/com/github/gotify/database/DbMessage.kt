package com.github.gotify.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.github.gotify.Utils
import com.github.gotify.client.model.Message
import com.github.gotify.messages.provider.StoredMessage
import org.threeten.bp.OffsetDateTime

@Entity(tableName = "message")
data class DbMessage(
    @PrimaryKey val id: Long,
    val appid: Long,
    val message: String?,
    val title: String?,
    val priority: Long?,
    val date: OffsetDateTime?,
    val extras: Map<String, Any>?,
    val isRead: Boolean = false,
    val isFavorite: Boolean = false
)

fun Message.toDb(isRead: Boolean = false, isFavorite: Boolean = false): DbMessage = DbMessage(
    id = id,
    appid = appid,
    message = message,
    title = title,
    priority = priority,
    date = date,
    extras = extras,
    isRead = isRead,
    isFavorite = isFavorite
)

fun DbMessage.toClient(): Message {
    val json = Utils.JSON.toJson(this)
    return Utils.JSON.fromJson(json, Message::class.java)
}

internal fun DbMessage.toStored(
    isRead: Boolean = this.isRead,
    isFavorite: Boolean = this.isFavorite
): StoredMessage = StoredMessage(
    message = toClient(),
    isRead = isRead,
    isFavorite = isFavorite
)
