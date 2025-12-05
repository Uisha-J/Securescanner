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

        // Check if using AI mode
        val useAI = intent.getBooleanExtra(EXTRA_USE_AI, false)

        // Get UI elements
        val messageTextView = findViewById<TextView>(R.id.warning_message)
        val keywordsTextView = findViewById<TextView>(R.id.warning_keywords)
        val aiRiskLevelTextView = findViewById<TextView>(R.id.ai_risk_level)
        val riskAccentBar = findViewById<android.view.View>(R.id.risk_accent_bar)
        val reasonLabel = findViewById<TextView>(R.id.reason_label)
        val reasonText = findViewById<TextView>(R.id.reason_text)
        val adviceLabel = findViewById<TextView>(R.id.advice_label)
        val adviceText = findViewById<TextView>(R.id.advice_text)
        val generalWarning = findViewById<TextView>(R.id.general_warning)
        val closeButton = findViewById<Button>(R.id.btn_close_warning)

        if (useAI) {
            // AI mode: Display risk assessment
            val riskLevel = intent.getStringExtra(EXTRA_RISK_LEVEL) ?: "주의"
            val explanation = intent.getStringExtra(EXTRA_EXPLANATION) ?: ""
            val aiKeywords = intent.getStringArrayListExtra(EXTRA_AI_KEYWORDS) ?: emptyList()

            Log.d("WarningActivity", "AI Mode - Risk: $riskLevel, Keywords: ${aiKeywords.size}")

            // Show AI risk level
            aiRiskLevelTextView.text = riskLevel
            aiRiskLevelTextView.visibility = android.view.View.VISIBLE

            // Set color based on risk level
            val riskColor = when (riskLevel) {
                "위험" -> android.graphics.Color.parseColor("#C62828") // Red
                "주의" -> android.graphics.Color.parseColor("#F57C00") // Orange
                "안전" -> android.graphics.Color.parseColor("#388E3C") // Green
                else -> android.graphics.Color.parseColor("#616161") // Gray
            }
            aiRiskLevelTextView.setTextColor(riskColor)
            riskAccentBar.setBackgroundColor(riskColor)

            // Split explanation into reason and advice (if separated by double newline)
            val explanationParts = explanation.split("\n\n")
            if (explanationParts.isNotEmpty() && explanationParts[0].isNotBlank()) {
                reasonLabel.visibility = android.view.View.VISIBLE
                reasonText.visibility = android.view.View.VISIBLE
                reasonText.text = explanationParts[0]
            }

            if (explanationParts.size > 1 && explanationParts[1].isNotBlank()) {
                adviceLabel.visibility = android.view.View.VISIBLE
                adviceText.visibility = android.view.View.VISIBLE
                adviceText.text = explanationParts[1]
            }

            // Update message label
            messageTextView.text = "감지된 위험 키워드"

            // Show AI-detected keywords
            if (aiKeywords.isNotEmpty()) {
                keywordsTextView.text = aiKeywords.joinToString(separator = "\n") { "• $it" }
                Log.d("WarningActivity", "AI Keywords: $aiKeywords")
            } else {
                keywordsTextView.text = "(특정 키워드 없음)"
            }

        } else {
            // Legacy mode: Display keyword-based detection
            val keywords = intent.getStringArrayListExtra(EXTRA_KEYWORDS) ?: emptyList()
            val keywordCount = intent.getIntExtra(EXTRA_KEYWORD_COUNT, 0)

            Log.d("WarningActivity", "Legacy Mode - Keyword count: $keywordCount")

            // Set default risk level for legacy mode (always 위험)
            aiRiskLevelTextView.text = "위험"
            val riskColor = android.graphics.Color.parseColor("#C62828")
            aiRiskLevelTextView.setTextColor(riskColor)
            riskAccentBar.setBackgroundColor(riskColor)

            // Hide AI-specific sections
            reasonLabel.visibility = android.view.View.GONE
            reasonText.visibility = android.view.View.GONE
            adviceLabel.visibility = android.view.View.GONE
            adviceText.visibility = android.view.View.GONE

            // Show general warning
            generalWarning.visibility = android.view.View.VISIBLE

            // Update message label
            messageTextView.text = "감지된 위험 키워드"

            // Show keywords with Korean category labels
            if (keywords.isNotEmpty()) {
                val formattedKeywords = keywords.map { keywordData ->
                    formatKeywordWithKoreanLabel(keywordData)
                }
                keywordsTextView.text = formattedKeywords.joinToString(separator = "\n")
                Log.d("WarningActivity", "Displaying ${keywords.size} keywords: $formattedKeywords")
            } else {
                keywordsTextView.text = "의심스러운 키워드가 감지되었습니다."
            }
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

        // AI mode extras
        const val EXTRA_USE_AI = "extra_use_ai"
        const val EXTRA_RISK_LEVEL = "extra_risk_level"
        const val EXTRA_EXPLANATION = "extra_explanation"
        const val EXTRA_AI_KEYWORDS = "extra_ai_keywords"
    }
}