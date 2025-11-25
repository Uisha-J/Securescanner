package com.example.securescanner

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "keywords")
data class Keyword(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val word: String,
    val type: String // "URL", "RISK" 등 키워드 종류
)
