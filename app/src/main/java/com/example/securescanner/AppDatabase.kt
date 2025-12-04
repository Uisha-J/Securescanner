package com.example.securescanner

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Keyword::class], version = 2, exportSchema = false)  // Version changed to 2
abstract class AppDatabase : RoomDatabase() {

    abstract fun keywordDao(): KeywordDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "keyword_database"
                )
                    .fallbackToDestructiveMigration()  // This will delete old data and recreate
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}