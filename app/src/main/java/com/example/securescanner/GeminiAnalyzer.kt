package com.example.securescanner

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Tool
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 피싱/스캠 위험도 평가를 위한 Gemini AI 분석기
 *
 * Firebase AI Logic SDK 사용 (Gemini 2.5 Flash + Google Search Grounding)
 *
 * 설정 방법:
 * 1. Firebase Console에서 Gemini Developer API 활성화
 * 2. google-services.json 파일이 프로젝트에 포함되어 있는지 확인
 * 3. Firebase AI Logic이 자동으로 API 키를 관리합니다 (코드에 API 키 불필요)
 *
 * 참고: 기존 setApiKey() 메서드는 이전 버전과의 호환성을 위해 유지되지만,
 *       Firebase AI Logic SDK는 Firebase 설정을 통해 인증을 처리합니다.
 */
class GeminiAnalyzer {

    companion object {
        private const val TAG = "GeminiAnalyzer"

        // 출력 길이 제한
        private const val MAX_REASON_LENGTH = 300
        private const val MAX_ADVICE_LENGTH = 200
        private const val MAX_KEYWORDS_COUNT = 10

        // 사용자가 설정해야 하는 API 키
        private var apiKey: String? = null

        /**
         * Gemini API 키 설정
         */
        fun setApiKey(key: String) {
            apiKey = key
            Log.d(TAG, "Gemini API key set successfully")
        }

        /**
         * API 키가 설정되었는지 확인
         */
        fun isConfigured(): Boolean = !apiKey.isNullOrBlank()
    }

    private val gson = Gson()

    /**
     * Gemini AI를 사용하여 피싱/스캠 위험도 분석
     *
     * @param extractedText OCR을 통해 화면에서 추출된 텍스트 목록
     * @return 위험도, 설명, 키워드가 포함된 RiskAssessment 객체
     * @throws Exception API 호출 실패 시
     */
    suspend fun analyzeRisk(extractedText: List<String>): RiskAssessment = withContext(Dispatchers.IO) {
        // 모든 추출된 텍스트를 하나의 문자열로 결합
        val fullText = extractedText.joinToString(separator = " ")

        if (fullText.isBlank()) {
            Log.w(TAG, "No text to analyze")
            return@withContext RiskAssessment(
                riskLevel = "LOW",
                dangerousKeywords = emptyList(),
                reason = "분석할 텍스트가 없습니다.",
                advice = "스캔할 화면 내용이 필요합니다."
            )
        }

        Log.d(TAG, "Analyzing text of length: ${fullText.length}")

        try {
            // Firebase AI 초기화 (Google AI backend)
            val firebaseAI = Firebase.ai(backend = GenerativeBackend.googleAI())

            // Gemini 2.5 Flash 모델 + Google Search Grounding
            val generativeModel = firebaseAI.generativeModel(
                modelName = "gemini-2.5-flash",
                tools = listOf(Tool.googleSearch())
            )

            // 위험도 분석을 위한 프롬프트 생성
            val prompt = buildPrompt(fullText)
            Log.d(TAG, "Sending request to Gemini API with Google Search Grounding...")

            // Gemini API 호출
            val response = generativeModel.generateContent(prompt)
            val responseText = response.text.orEmpty()

            if (responseText.isEmpty()) {
                throw IllegalStateException("Empty response from Gemini")
            }

            Log.d(TAG, "Received response from Gemini (${responseText.length} chars)")

            // JSON 응답 파싱
            val rawAssessment = parseResponse(responseText)

            // 출력 길이 제한 적용
            applyLengthLimits(rawAssessment)

        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing risk with Gemini: ${e.message}", e)
            Log.e(TAG, "Returning safe LOW-risk fallback")

            // 안전한 LOW 위험도 폴백 응답 반환
            RiskAssessment(
                riskLevel = "LOW",
                dangerousKeywords = emptyList(),
                reason = "AI 분석을 완료할 수 없습니다.",
                advice = "수동으로 내용을 확인하세요."
            )
        }
    }

    /**
     * 출력 길이 제한 적용
     */
    private fun applyLengthLimits(assessment: RiskAssessment): RiskAssessment {
        val truncatedReason = if (assessment.reason.length > MAX_REASON_LENGTH) {
            assessment.reason.take(MAX_REASON_LENGTH - 3) + "..."
        } else {
            assessment.reason
        }

        val truncatedAdvice = if (assessment.advice.length > MAX_ADVICE_LENGTH) {
            assessment.advice.take(MAX_ADVICE_LENGTH - 3) + "..."
        } else {
            assessment.advice
        }

        val limitedKeywords = if (assessment.dangerousKeywords.size > MAX_KEYWORDS_COUNT) {
            assessment.dangerousKeywords.take(MAX_KEYWORDS_COUNT)
        } else {
            assessment.dangerousKeywords
        }

        return RiskAssessment(
            riskLevel = assessment.riskLevel,
            dangerousKeywords = limitedKeywords,
            reason = truncatedReason,
            advice = truncatedAdvice
        )
    }

    /**
     * Gemini API용 프롬프트 생성 (Google Search Grounding 활성화)
     */
    private fun buildPrompt(text: String): String {
        return """
당신은 한국어 온라인 사기(피싱, 보이스피싱, 구인·구직 사기, 인신매매·강제 노동 유도) 탐지 전문가입니다.
아래 텍스트가 사용자를 위험한 상황으로 유도하는지 평가하세요.

[주요 관심 대상]
- 금융 사기: 대출·투자 권유, 수수료·보증금 선입금 요구, 계좌/OTP/보안코드/인증번호 요구 등
- 보이스피싱·메신저 피싱: 가족·지인·기관(검찰, 경찰, 금융감독원 등) 사칭, 급한 송금 요청, 의심스러운 링크 클릭 유도 등
- 구인·구직/알바 사기: 비정상적으로 높은 급여, 구체적 업무 설명 없이 고수익 보장, 선입금 요구, 계좌·휴대폰·신분증을 대신 만들어 달라는 요청, 불투명한 해외 근무 제안 등
- 인신매매·강제 노동/성착취 위험: 해외 콜센터·가상자산·도박 사이트 운영 인력 모집, 여권/신분증·지문 등 민감 정보 제출 요구, 출국·숙소·교통편을 모두 대신 준비해 준다는 제안, 계약 해지 시 벌금·위협·구금 가능성을 암시하는 표현 등

Google Search Grounding 도구를 사용하여 최신 피싱/스캠 정보를 확인하고, 의심스러운 회사·전화번호·사이트·키워드가 실제로 사기 사례와 연관되어 있는지 교차 검증한 뒤 판단하세요.

[분석할 텍스트]
```
$text
```

다음 JSON 형식으로만 응답하세요:
{
  "risk_level": "HIGH" | "MEDIUM" | "LOW",
  "dangerous_keywords": ["위험한", "키워드", "목록"],
  "reason": "위험한 핵심 이유를 한국어로 간단히 설명 (1-2문장)",
  "advice": "사용자를 위한 조언 (1-2문장)"
}

[판단 기준]
- "HIGH": 위 항목과 명확히 연결되는 사기/착취 가능성이 크고, 돈·개인정보·신분증·계좌·출국/이동 등을 직접 요구하거나 강하게 유도함.
- "MEDIUM": 위 항목과 일부 유사한 표현이나 패턴이 있으나 확신하기 어렵고, 추가 확인이 필요함.
- "LOW": 일반적인 안내·광고·뉴스·검색 결과 등으로 보이며, 위 항목과 뚜렷한 관련성이 없음.

dangerous_keywords에는 실제 텍스트에 등장한 표현만 넣고, JSON 이외의 다른 텍스트는 출력하지 마세요.
""".trimIndent()
    }

    /**
     * Gemini API 응답 파싱
     */
    private fun parseResponse(responseText: String): RiskAssessment {
        return try {
            // 응답에서 JSON 추출 (Gemini가 마크다운 코드 블록으로 감쌀 수 있음)
            val jsonText = extractJsonFromResponse(responseText)

            Log.d(TAG, "Parsing JSON: $jsonText")
            gson.fromJson(jsonText, RiskAssessment::class.java)

        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "Failed to parse JSON response: $responseText", e)

            // 폴백: 수동으로 정보 추출 시도
            extractRiskManually(responseText)
        }
    }

    /**
     * 응답에서 JSON 추출 (마크다운 코드 블록 제거)
     */
    private fun extractJsonFromResponse(text: String): String {
        // 마크다운 코드 블록 제거
        val jsonPattern = Regex("""```json\s*(.*?)\s*```""", RegexOption.DOT_MATCHES_ALL)
        val match = jsonPattern.find(text)

        if (match != null) {
            return match.groupValues[1].trim()
        }

        // 'json' 언어 지정자 없이 시도
        val codeBlockPattern = Regex("""```\s*(.*?)\s*```""", RegexOption.DOT_MATCHES_ALL)
        val codeMatch = codeBlockPattern.find(text)

        if (codeMatch != null) {
            return codeMatch.groupValues[1].trim()
        }

        // 코드 블록이 없으면 JSON 객체 직접 찾기
        val jsonObjectPattern = Regex("""\{.*}""", RegexOption.DOT_MATCHES_ALL)
        val objectMatch = jsonObjectPattern.find(text)

        if (objectMatch != null) {
            return objectMatch.value.trim()
        }

        // 패턴이 일치하지 않으면 원본 텍스트 반환
        return text.trim()
    }

    /**
     * 폴백: JSON 파싱 실패 시 수동으로 위험도 정보 추출
     */
    private fun extractRiskManually(text: String): RiskAssessment {
        val lowerText = text.lowercase()

        val riskLevel = when {
            lowerText.contains("high") || lowerText.contains("위험") -> "HIGH"
            lowerText.contains("medium") || lowerText.contains("주의") -> "MEDIUM"
            else -> "LOW"
        }

        return RiskAssessment(
            riskLevel = riskLevel,
            dangerousKeywords = emptyList(),
            reason = "AI 응답을 파싱할 수 없어 기본 분석을 수행했습니다.",
            advice = "내용을 직접 확인하세요."
        )
    }
}
