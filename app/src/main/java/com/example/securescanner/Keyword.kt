package com.example.securescanner

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "keywords")
data class Keyword(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val word: String,                    // Keyword text (e.g., "bit.ly", "캄보디아")
    val type: String,                    // "URL", "RISK", "PHISHING"
    val category: String = "general",    // "job_scam", "phishing_link", "malware", etc.
    val riskLevel: Int = 3,              // 1-5 (1=low risk, 5=critical)
    val source: String = "default",      // "default", "news_api", "manual_add"
    val addedDate: Long = System.currentTimeMillis(),    // When keyword was added
    val lastUpdated: Long = System.currentTimeMillis(), // Last update timestamp
    val isActive: Boolean = true         // Can be disabled without deletion
)