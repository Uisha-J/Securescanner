package com.example.securescanner

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageButton
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

class ScreenScanService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingButtonView: View
    private lateinit var params: WindowManager.LayoutParams
    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var keywordList: List<Keyword> = emptyList()

    // OCR 매니저
    private var screenCaptureManager: ScreenCaptureManager? = null

    // Gemini AI 분석기
    private val geminiAnalyzer = GeminiAnalyzer()

    // 내부 UI 메시지 필터 (OCR에서 추출된 자체 앱 메시지 제외)
    private val internalMessages = setOf(
        "화면 스캔을 시작합니다",
        "화면 스캔을 시작합니다...",
        "AI 분석 중",
        "AI 분석 중...",
        "화면 캡처 권한을 설정해주세요",
        "화면 캡처 권한을 설정해주세요.",
        "화면 캡처 준비 완료",
        "화면 캡처 준비 완료.",
        "의심스러운 내용을 찾지 못했습니다",
        "의심스러운 내용을 찾지 못했습니다.",
        "스캔 중 오류가 발생했습니다",
        "스캔 중 오류가 발생했습니다.",
        "SecureScanner 실행 중",
        "화면 스캔 준비됨",
        "SecureScanner 화면 캡처 활성화",
        "화면 스캔 준비 완료. 플로팅 버튼을 눌러 검사하세요."
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createFloatingButton()
        startForegroundService()
        loadKeywordsFromDatabase()

        // ScreenCaptureManager 초기화
        screenCaptureManager = ScreenCaptureManager(applicationContext)

        // Firebase AI Logic SDK 사용 - API 키는 Firebase Console에서 관리됩니다
        // 설정 방법:
        // 1. Firebase Console에서 Gemini Developer API 활성화
        // 2. google-services.json이 프로젝트에 포함되어 있는지 확인
        Log.d("ScreenScanService", "Service connected. OCR mode enabled with Firebase AI Logic.")
        Log.d("ScreenScanService", "Gemini AI analysis available via Firebase configuration.")
    }

    private fun loadKeywordsFromDatabase() {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val dao = AppDatabase.getDatabase(applicationContext).keywordDao()
                val loadedKeywords = dao.getAllKeywords()
                if (loadedKeywords.isEmpty()) {
                    initializeDefaultKeywords(dao)
                } else {
                    keywordList = loadedKeywords
                    Log.d("ScreenScanService", "Loaded ${keywordList.size} keywords from DB.")
                }
            } catch (e: Exception) {
                Log.e("ScreenScanService", "Database load failed", e)
            }
        }
    }

    private suspend fun initializeDefaultKeywords(dao: KeywordDao) {
        Log.d("ScreenScanService", "DB is empty, initializing keyword database...")

        val urlKeywords = listOf(
            Keyword(word = "bit.ly", type = "URL", category = "url_shortener", riskLevel = 4, source = "default"),
            Keyword(word = "tinyurl.com", type = "URL", category = "url_shortener", riskLevel = 4, source = "default")
        )

        val jobScamKeywords = listOf(
            Keyword(word = "캄보디아", type = "RISK", category = "job_scam", riskLevel = 5, source = "default"),
            Keyword(word = "고수익", type = "RISK", category = "job_scam", riskLevel = 5, source = "default"),
            Keyword(word = "월 2000만원", type = "RISK", category = "job_scam", riskLevel = 5, source = "default"),
            Keyword(word = "출국", type = "RISK", category = "job_scam", riskLevel = 4, source = "default")
        )

        val voicePhishingKeywords = listOf(
            Keyword(word = "금융감독원", type = "RISK", category = "voice_phishing", riskLevel = 5, source = "default"),
            Keyword(word = "검찰청", type = "RISK", category = "voice_phishing", riskLevel = 5, source = "default"),
            Keyword(word = "당첨", type = "RISK", category = "voice_phishing", riskLevel = 4, source = "default")
        )

        val allKeywords = urlKeywords + jobScamKeywords + voicePhishingKeywords
        dao.insertAll(allKeywords)

        keywordList = dao.getAllKeywords()
        Log.d("ScreenScanService", "Loaded ${keywordList.size} keywords from DB.")
    }

    private fun startForegroundService() {
        val channelId = "floating_button_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "플로팅 버튼 서비스", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("SecureScanner 실행 중")
            .setContentText("화면 스캔 준비됨")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()

        // Start with specialUse only - mediaProjection type requires screen capture permission first
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                // Android 14+: Start with specialUse only (for floating button functionality)
                // mediaProjection type will be added later when permission is granted
                startForeground(
                    1,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
                Log.d("ScreenScanService", "Started foreground with SPECIAL_USE type")
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                // Android 10-13: Start without specific type initially
                // Will add mediaProjection type after permission is granted
                startForeground(1, notification)
                Log.d("ScreenScanService", "Started foreground without type (will add later)")
            }
            else -> {
                // Android 9 and below: No type specification needed
                startForeground(1, notification)
                Log.d("ScreenScanService", "Started foreground (pre-Q)")
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    private fun createFloatingButton() {
        floatingButtonView = LayoutInflater.from(this).inflate(R.layout.floating_button, null)
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100
        params.y = 100

        val button = floatingButtonView.findViewById<ImageButton>(R.id.floating_button)
        button.setOnTouchListener(object : View.OnTouchListener {
            private var initialX: Int = 0
            private var initialY: Int = 0
            private var initialTouchX: Float = 0f
            private var initialTouchY: Float = 0f
            private var isMoved = false
            private var downTime: Long = 0
            private val touchSlop = ViewConfiguration.get(this@ScreenScanService).scaledTouchSlop

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isMoved = false
                        downTime = System.currentTimeMillis()
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!isMoved && (abs(event.rawX - initialTouchX) > touchSlop || abs(event.rawY - initialTouchY) > touchSlop)) {
                            isMoved = true
                        }
                        if (isMoved) {
                            params.x = initialX + (event.rawX - initialTouchX).toInt()
                            params.y = initialY + (event.rawY - initialTouchY).toInt()
                            windowManager.updateViewLayout(floatingButtonView, params)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val isClick = !isMoved && (System.currentTimeMillis() - downTime < ViewConfiguration.getLongPressTimeout())
                        if (isClick) {
                            performClickAction()
                        }
                        return true
                    }
                }
                return false
            }
        })
        windowManager.addView(floatingButtonView, params)
    }

    private fun performClickAction() {
        val isReady = MediaProjectionHolder.isReady()
        val projectionExists = MediaProjectionHolder.mediaProjection != null

        Log.d("ScreenScanService", "=== Button Click Debug ===")
        Log.d("ScreenScanService", "MediaProjectionHolder.isReady() = $isReady")
        Log.d("ScreenScanService", "MediaProjection exists = $projectionExists")
        Log.d("ScreenScanService", "ScreenCaptureManager = ${screenCaptureManager != null}")

        if (!isReady) {
            Log.w("ScreenScanService", "Screen capture permission not granted yet")
            handler.post {
                Toast.makeText(this, "화면 캡처 권한을 설정해주세요.", Toast.LENGTH_SHORT).show()
            }
            handler.postDelayed({
                val intent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("request_screen_capture", true)
                }
                startActivity(intent)
            }, 500)
            return
        }

        Log.d("ScreenScanService", "Starting screen scan...")
        handler.post {
            Toast.makeText(this@ScreenScanService, "화면 스캔을 시작합니다...", Toast.LENGTH_SHORT).show()
        }
        handler.postDelayed({ scanCurrentScreenWithOCR() }, 300)
    }

    private fun scanCurrentScreenWithOCR() {
        Log.d("ScreenScanService", "Starting OCR scan...")

        serviceScope.launch(Dispatchers.IO) {
            try {
                val extractedTexts = screenCaptureManager?.captureAndExtractText() ?: emptyList()

                Log.d("ScreenScanService", "OCR extracted ${extractedTexts.size} text items")

                // 로컬 데이터베이스를 사용하여 키워드 검사 (폴백)
                val foundKeywords = mutableListOf<String>()
                for (text in extractedTexts) {
                    checkForKeywords(text, foundKeywords)
                }

                // 내부 메시지를 제거한 텍스트 목록 (AI 분석용)
                val filteredTexts = filterInternalMessages(extractedTexts)
                Log.d("ScreenScanService", "Filtered ${extractedTexts.size - filteredTexts.size} internal messages, ${filteredTexts.size} texts remain for AI")

                // Gemini AI 분석 수행 (Firebase AI Logic SDK)
                val riskAssessment = if (filteredTexts.isNotEmpty()) {
                    try {
                        Log.d("ScreenScanService", "Analyzing with Gemini AI (Firebase AI Logic)...")
                        handler.post {
                            Toast.makeText(this@ScreenScanService, "AI 분석 중...", Toast.LENGTH_SHORT).show()
                        }
                        geminiAnalyzer.analyzeRisk(filteredTexts)
                    } catch (e: Exception) {
                        Log.e("ScreenScanService", "Gemini analysis failed: ${e.message}", e)
                        Log.e("ScreenScanService", "Ensure Gemini Developer API is enabled in Firebase Console")
                        null
                    }
                } else {
                    null
                }

                handler.post {
                    if (riskAssessment != null) {
                        // AI 기반 위험도 평가 결과 표시
                        Log.d("ScreenScanService", "AI Risk Assessment: ${riskAssessment.riskLevel}")
                        launchWarningActivityWithAI(riskAssessment)
                    } else if (foundKeywords.isEmpty()) {
                        Toast.makeText(this@ScreenScanService, "의심스러운 내용을 찾지 못했습니다.", Toast.LENGTH_SHORT).show()
                    } else {
                        // 키워드 기반 경고로 폴백
                        val uniqueKeywords = foundKeywords.distinct()
                        Log.d("ScreenScanService", "Found suspicious keywords: $uniqueKeywords")
                        launchWarningActivity(ArrayList(uniqueKeywords))
                    }
                }

            } catch (e: Exception) {
                Log.e("ScreenScanService", "OCR scan failed: ${e.message}", e)
                handler.post {
                    Toast.makeText(this@ScreenScanService, "스캔 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * 내부 UI 메시지 필터링 - 앱 자체의 Toast/알림 메시지를 AI 분석 대상에서 제외
     *
     * OCR이 화면의 모든 텍스트를 추출하기 때문에, 앱 자체의 상태 메시지도 캡처됩니다.
     * 이 함수는 알려진 내부 메시지를 필터링하여 AI가 자체 메시지를 피싱으로 오인하지 않도록 합니다.
     */
    private fun filterInternalMessages(texts: List<String>): List<String> {
        return texts.filter { text ->
            val trimmedText = text.trim()

            // 정확히 일치하는 내부 메시지 제외
            if (internalMessages.contains(trimmedText)) {
                Log.d("ScreenScanService", "Filtered exact internal message: $trimmedText")
                return@filter false
            }

            // 부분 일치 검사 (점, 마침표 등의 변형 처리)
            val normalizedText = trimmedText.replace(Regex("[.…]+$"), "") // 끝의 점 제거
            if (internalMessages.any { it.replace(Regex("[.…]+$"), "") == normalizedText }) {
                Log.d("ScreenScanService", "Filtered normalized internal message: $trimmedText")
                return@filter false
            }

            true
        }
    }

    private fun checkForKeywords(text: String, foundList: MutableList<String>) {
        val lowerCaseText = text.lowercase(Locale.getDefault())
        for (keyword in keywordList) {
            if (lowerCaseText.contains(keyword.word.lowercase(Locale.getDefault()))) {
                // Format as "category|word" so WarningActivity can parse and localize
                val foundItem = "${keyword.category}|${keyword.word}"
                if (!foundList.contains(foundItem)) {
                    foundList.add(foundItem)
                    Log.d("ScreenScanService", "Keyword matched: ${keyword.word} (${keyword.category})")
                }
            }
        }
    }

    /**
     * 감지된 키워드를 표시하기 위해 WarningActivity 실행
     * MediaProjection 또는 오버레이가 활성화된 상태에서도 안정적으로 작동
     */
    private fun launchWarningActivity(keywords: ArrayList<String>) {
        try {
            val intent = Intent(this, WarningActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putStringArrayListExtra(WarningActivity.EXTRA_KEYWORDS, keywords)
                putExtra(WarningActivity.EXTRA_KEYWORD_COUNT, keywords.size)
            }
            startActivity(intent)
            Log.d("ScreenScanService", "Launched WarningActivity with ${keywords.size} keywords")
        } catch (e: Exception) {
            Log.e("ScreenScanService", "Failed to launch WarningActivity: ${e.message}", e)
            // Fallback to Toast if activity launch fails
            Toast.makeText(this, "위험 키워드 ${keywords.size}개 발견!", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * AI 기반 위험도 평가 결과와 함께 WarningActivity 실행
     */
    private fun launchWarningActivityWithAI(riskAssessment: RiskAssessment) {
        try {
            // 영문 위험도를 한글로 매핑
            val koreanRiskLevel = when (riskAssessment.riskLevel.uppercase()) {
                "HIGH" -> "위험"
                "MEDIUM" -> "주의"
                "LOW" -> "안전"
                else -> "주의" // 기본 폴백
            }

            // reason과 advice를 explanation으로 결합
            val explanation = buildString {
                if (riskAssessment.reason.isNotBlank()) {
                    append(riskAssessment.reason)
                }
                if (riskAssessment.advice.isNotBlank()) {
                    if (isNotEmpty()) append("\n\n")
                    append(riskAssessment.advice)
                }
            }

            val intent = Intent(this, WarningActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(WarningActivity.EXTRA_USE_AI, true)
                putExtra(WarningActivity.EXTRA_RISK_LEVEL, koreanRiskLevel)
                putExtra(WarningActivity.EXTRA_EXPLANATION, explanation)
                putStringArrayListExtra(
                    WarningActivity.EXTRA_AI_KEYWORDS,
                    ArrayList(riskAssessment.dangerousKeywords)
                )
            }
            startActivity(intent)
            Log.d("ScreenScanService", "Launched WarningActivity with AI assessment: $koreanRiskLevel (${riskAssessment.riskLevel})")
        } catch (e: Exception) {
            Log.e("ScreenScanService", "Failed to launch WarningActivity: ${e.message}", e)
            val koreanRisk = when (riskAssessment.riskLevel.uppercase()) {
                "HIGH" -> "위험"
                "MEDIUM" -> "주의"
                "LOW" -> "안전"
                else -> "주의"
            }
            Toast.makeText(this, "위험도: $koreanRisk", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        if (::floatingButtonView.isInitialized) {
            windowManager.removeView(floatingButtonView)
        }
        screenCaptureManager?.stop()
        Log.d("ScreenScanService", "Service destroyed")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("ScreenScanService", "onStartCommand called")

        when (intent?.action) {
            ACTION_SCREEN_CAPTURE_READY -> {
                Log.d("ScreenScanService", "=== SCREEN_CAPTURE_READY Received ===")

                // Update notification to show screen capture is ready
                val notification = createNotification()

                try {
                    when {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                            // Android 14+: Use both types (must match initial declaration)
                            startForeground(
                                NOTIFICATION_ID,
                                notification,
                                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                            )
                            Log.d("ScreenScanService", "Notification updated with both service types")
                        }
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                            // Android 10-13: Use only mediaProjection type (matches initial declaration)
                            startForeground(
                                NOTIFICATION_ID,
                                notification,
                                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                            )
                            Log.d("ScreenScanService", "Notification updated with mediaProjection type")
                        }
                        else -> {
                            // Android 9 and below: No type specification
                            startForeground(NOTIFICATION_ID, notification)
                            Log.d("ScreenScanService", "Notification updated (pre-Q)")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ScreenScanService", "Failed to update notification", e)
                }

                // Wait a moment for MediaProjection to be stored
                Handler(Looper.getMainLooper()).postDelayed({
                    if (MediaProjectionHolder.isReady()) {
                        Log.d("ScreenScanService", "Screen capture ready")
                        handler.post {
                            Toast.makeText(this, "화면 캡처 준비 완료.", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Log.w("ScreenScanService", "MediaProjection not ready yet")
                    }
                }, 1000)
            }
            else -> {
                Log.d("ScreenScanService", "Service started normally")
            }
        }

        return START_STICKY
    }

    // Create notification for MEDIA_PROJECTION foreground service
    private fun createNotification(): Notification {
        val channelId = "screen_capture_service_channel"
        val channelName = "Screen Capture Service"

        // Android 8.0+ requires notification channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("SecureScanner 화면 캡처 활성화")
            .setContentText("화면 스캔 준비 완료. 플로팅 버튼을 눌러 검사하세요.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_SCREEN_CAPTURE_READY = "com.example.securescanner.SCREEN_CAPTURE_READY"
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        private const val NOTIFICATION_ID = 1001
    }
}