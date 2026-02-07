package com.github.gotify.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.github.gotify.Utils
import com.github.gotify.client.model.Application
import org.threeten.bp.OffsetDateTime

@Entity(tableName = "application")
data class DbApplication(
    @PrimaryKey val id: Long,
    val name: String?,
    val description: String?,
    val internal: Boolean?,
    val image: String?,
    val defaultPriority: Long?,
    val token: String?,
    val lastUsed: OffsetDateTime?
)

fun Application.toDb(): DbApplication = DbApplication(
    id = id,
    name = name,
    description = description,
    internal = isInternal,
    image = image,
    defaultPriority = defaultPriority,
    token = token,
    lastUsed = lastUsed
)

fun DbApplication.toClient(): Application {
    return Utils.JSON.fromJson(Utils.JSON.toJson(this), Application::class.java)
}
