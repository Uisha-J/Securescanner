package com.example.securescanner

import com.google.gson.annotations.SerializedName

/**
 * Gemini AI 위험도 평가 응답
 */
data class RiskAssessment(
    @SerializedName("risk_level")
    val riskLevel: String, // "HIGH", "MEDIUM", "LOW"

    @SerializedName("dangerous_keywords")
    val dangerousKeywords: List<String>, // 발견된 위험 키워드 목록

    @SerializedName("reason")
    val reason: String, // 위험한 이유

    @SerializedName("advice")
    val advice: String // 사용자를 위한 조언
)

/**
 * API 응답 파싱용 래퍼
 */
data class GeminiAnalysisResponse(
    val riskAssessment: RiskAssessment?
)
