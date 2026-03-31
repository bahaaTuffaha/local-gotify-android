package com.github.gotify.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DbMessage::class, DbApplication::class, DbMessageMarker::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converter::class)
internal abstract class GotifyDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun messageMarkerDao(): MessageMarkerDao
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
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE message ADD COLUMN isRead INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE message ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `message_marker` (`id` INTEGER NOT NULL, `appid` INTEGER NOT NULL, `isRead` INTEGER NOT NULL, `isFavorite` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "INSERT OR REPLACE INTO message_marker(id, appid, isRead, isFavorite) SELECT id, appid, isRead, isFavorite FROM message WHERE isRead = 1 OR isFavorite = 1"
                )
            }
        }
    }
}
