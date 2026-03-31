package com.github.gotify.database

import androidx.room.*

@Dao
interface MessageDao {
    @Query("SELECT * FROM message ORDER BY id DESC")
    fun getAll(): List<DbMessage>

    @Query("SELECT * FROM message WHERE appid = :appId ORDER BY id DESC")
    fun getByApp(appId: Long): List<DbMessage>

    @Query("SELECT * FROM message WHERE id IN (:ids)")
    fun getByIds(ids: List<Long>): List<DbMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg messages: DbMessage)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(messages: List<DbMessage>)

    @Query("UPDATE message SET isRead = :isRead WHERE id = :id")
    fun updateReadState(id: Long, isRead: Boolean)

    @Query("UPDATE message SET isFavorite = :isFavorite WHERE id = :id")
    fun updateFavoriteState(id: Long, isFavorite: Boolean)

    @Query("DELETE FROM message WHERE id = :id")
    fun delete(id: Long)

    @Query("DELETE FROM message WHERE appid = :appId")
    fun deleteByApp(appId: Long)

    @Query("DELETE FROM message")
    fun deleteAll()
}

@Dao
interface MessageMarkerDao {
    @Query("SELECT * FROM message_marker")
    fun getAll(): List<DbMessageMarker>

    @Query("SELECT * FROM message_marker WHERE appid = :appId")
    fun getByApp(appId: Long): List<DbMessageMarker>

    @Query("SELECT * FROM message_marker WHERE id IN (:ids)")
    fun getByIds(ids: List<Long>): List<DbMessageMarker>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(markers: List<DbMessageMarker>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg markers: DbMessageMarker)

    @Query("DELETE FROM message_marker WHERE id = :id")
    fun delete(id: Long)

    @Query("DELETE FROM message_marker WHERE appid = :appId")
    fun deleteByApp(appId: Long)

    @Query("DELETE FROM message_marker")
    fun deleteAll()
}

@Dao
interface ApplicationDao {
    @Query("SELECT * FROM application")
    fun getAll(): List<DbApplication>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg applications: DbApplication)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(applications: List<DbApplication>)

    @Query("DELETE FROM application")
    fun deleteAll()
}
