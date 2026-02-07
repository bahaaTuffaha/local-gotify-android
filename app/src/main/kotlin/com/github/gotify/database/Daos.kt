package com.github.gotify.database

import androidx.room.*

@Dao
interface MessageDao {
    @Query("SELECT * FROM message ORDER BY id DESC")
    fun getAll(): List<DbMessage>

    @Query("SELECT * FROM message WHERE appid = :appId ORDER BY id DESC")
    fun getByApp(appId: Long): List<DbMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg messages: DbMessage)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(messages: List<DbMessage>)

    @Query("DELETE FROM message WHERE id = :id")
    fun delete(id: Long)

    @Query("DELETE FROM message WHERE appid = :appId")
    fun deleteByApp(appId: Long)

    @Query("DELETE FROM message")
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
