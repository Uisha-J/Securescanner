package com.example.securescanner

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete

@Dao
interface KeywordDao {
    // Get all keywords
    @Query("SELECT * FROM keywords WHERE isActive = 1")
    suspend fun getAllKeywords(): List<Keyword>

    // Get keywords by type
    @Query("SELECT * FROM keywords WHERE type = :type AND isActive = 1")
    suspend fun getKeywordsByType(type: String): List<Keyword>

    // Get keywords by category
    @Query("SELECT * FROM keywords WHERE category = :category AND isActive = 1")
    suspend fun getKeywordsByCategory(category: String): List<Keyword>

    // Get high risk keywords (riskLevel >= 4)
    @Query("SELECT * FROM keywords WHERE riskLevel >= 4 AND isActive = 1")
    suspend fun getHighRiskKeywords(): List<Keyword>

    // Search keyword by word
    @Query("SELECT * FROM keywords WHERE word LIKE '%' || :searchTerm || '%' AND isActive = 1")
    suspend fun searchKeywords(searchTerm: String): List<Keyword>

    // Insert keyword
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(keyword: Keyword)

    // Insert multiple keywords
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(keywords: List<Keyword>)

    // Update keyword
    @Update
    suspend fun update(keyword: Keyword)

    // Delete keyword
    @Delete
    suspend fun delete(keyword: Keyword)

    // Deactivate keyword (soft delete)
    @Query("UPDATE keywords SET isActive = 0 WHERE id = :keywordId")
    suspend fun deactivateKeyword(keywordId: Int)

    // Delete all keywords (for testing)
    @Query("DELETE FROM keywords")
    suspend fun deleteAll()

    // Count keywords
    @Query("SELECT COUNT(*) FROM keywords WHERE isActive = 1")
    suspend fun getKeywordCount(): Int
}