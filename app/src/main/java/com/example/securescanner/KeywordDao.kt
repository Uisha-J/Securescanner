package com.example.securescanner

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface KeywordDao {
    @Query("SELECT * FROM keywords")
    suspend fun getAllKeywords(): List<Keyword>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(keyword: Keyword)
}
