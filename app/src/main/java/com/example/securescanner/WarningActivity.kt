package com.example.securescanner

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.TextView
import android.view.Gravity

class WarningActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show activity on lock screen and turn screen on
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        setContentView(R.layout.activity_warning)

        // Get detected keywords from intent
        val keywords = intent.getStringArrayListExtra(EXTRA_KEYWORDS) ?: emptyList()
        val keywordCount = intent.getIntExtra(EXTRA_KEYWORD_COUNT, 0)

        // Display keywords
        val messageTextView = findViewById<TextView>(R.id.warning_message)
        val keywordsTextView = findViewById<TextView>(R.id.warning_keywords)
        val closeButton = findViewById<Button>(R.id.btn_close_warning)

        // Show ALL detected keywords with Korean category labels
        if (keywords.isNotEmpty()) {
            val formattedKeywords = keywords.map { keywordData ->
                formatKeywordWithKoreanLabel(keywordData)
            }
            keywordsTextView.text = formattedKeywords.joinToString(separator = "\n")
            Log.d("WarningActivity", "Displaying ${keywords.size} keywords: $formattedKeywords")
        } else {
            keywordsTextView.text = "의심스러운 키워드가 감지되었습니다."
        }

        // Close button handler
        closeButton.setOnClickListener {
            finish()
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

    /**
     * Format keyword with Korean category label
     * Input format: "category|word" (e.g., "job_scam|출국")
     * Output format: "[Korean Label] word" (e.g., "[직업 사기] 출국")
     */
    private fun formatKeywordWithKoreanLabel(keywordData: String): String {
        val parts = keywordData.split("|")
        if (parts.size != 2) {
            // Fallback: if format is unexpected, return as-is
            Log.w("WarningActivity", "Unexpected keyword format: $keywordData")
            return keywordData
        }

        val category = parts[0]
        val word = parts[1]

        // Map category to Korean label
        val koreanLabel = when (category) {
            "job_scam" -> "[직업 사기]"
            "voice_phishing" -> "[보이스피싱]"
            "url_shortener" -> "[단축 URL]"
            else -> {
                // Fallback for unmapped categories
                Log.d("WarningActivity", "Unmapped category: $category, using default label")
                "[의심 키워드]"
            }
        }

        return "$koreanLabel $word"
    }

    companion object {
        const val EXTRA_KEYWORDS = "extra_keywords"
        const val EXTRA_KEYWORD_COUNT = "extra_keyword_count"
    }
}