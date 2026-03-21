package com.github.gotify.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [DbMessage::class, DbApplication::class], version = 1, exportSchema = false)
@TypeConverters(Converter::class)
internal abstract class GotifyDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun applicationDao(): ApplicationDao

    companion object {
        @Volatile
        private var INSTANCE: GotifyDatabase? = null

        fun get(context: Context): GotifyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GotifyDatabase::class.java,
                    "gotify_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
