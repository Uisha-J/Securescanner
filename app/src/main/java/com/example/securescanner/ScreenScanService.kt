package com.example.securescanner

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
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
import android.view.accessibility.AccessibilityNodeInfo
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

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createFloatingButton()
        startForegroundService()
        loadKeywordsFromDatabase()
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
                    Log.d("ScreenScanService", "${keywordList.size}개의 키워드를 DB에서 로드했습니다.")
                }
            } catch (e: Exception) {
                Log.e("ScreenScanService", "데이터베이스 로드 실패", e)
            }
        }
    }

    private suspend fun initializeDefaultKeywords(dao: KeywordDao) {
        Log.d("ScreenScanService", "DB가 비어있어 기본 키워드를 추가합니다.")
        dao.insert(Keyword(word = "bit.ly", type = "URL"))
        dao.insert(Keyword(word = "flic.kr", type = "URL"))
        dao.insert(Keyword(word = "캄보디아", type = "RISK"))
        dao.insert(Keyword(word = "출국", type = "RISK"))
        dao.insert(Keyword(word = "고수익", type = "RISK"))
        keywordList = dao.getAllKeywords()
        Log.d("ScreenScanService", "${keywordList.size}개의 키워드를 DB에서 로드했습니다.")
    }

    private fun startForegroundService() {
        val channelId = "floating_button_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "플로팅 버튼 서비스", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("서비스 실행 중")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
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
        handler.post { Toast.makeText(this@ScreenScanService, "화면 스캔을 시작합니다...", Toast.LENGTH_SHORT).show() }
        handler.postDelayed({ scanCurrentScreen() }, 300)
    }

    private fun scanCurrentScreen() {
        val rootNode: AccessibilityNodeInfo? = rootInActiveWindow
        if (rootNode == null) {
            handler.post { Toast.makeText(this, "화면 정보를 아직 가져올 수 없습니다. 잠시 후 다시 시도해주세요.", Toast.LENGTH_SHORT).show() }
            return
        }
        val foundKeywords = mutableListOf<String>()
        findTextNodes(rootNode, foundKeywords)
        handler.post {
            if (foundKeywords.isEmpty()) {
                Toast.makeText(this, "의심스러운 내용을 찾지 못했습니다.", Toast.LENGTH_SHORT).show()
            } else {
                val resultText = "위험 의심 단어 발견:\n${foundKeywords.joinToString(separator = "\n")}"
                Toast.makeText(this, resultText, Toast.LENGTH_LONG).show()
            }
        }
        rootNode.recycle()
    }

    // --- [중요] 스캔 로직 최종 수정 ---
    private fun findTextNodes(node: AccessibilityNodeInfo, foundList: MutableList<String>) {
        // 1. node.text 확인
        if (node.text != null && node.text.isNotEmpty()) {
            checkForKeywords(node.text.toString(), foundList)
        }

        // 2. node.contentDescription 확인 (정확도 향상)
        if (node.contentDescription != null && node.contentDescription.isNotEmpty()) {
            checkForKeywords(node.contentDescription.toString(), foundList)
        }

        // 3. 자식 노드 재귀 순회
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let {
                findTextNodes(it, foundList)
                // 중요: it.recycle() 호출을 제거하여 오류 방지
            }
        }
    }

    private fun checkForKeywords(text: String, foundList: MutableList<String>) {
        val lowerCaseText = text.lowercase(Locale.getDefault())
        for (keyword in keywordList) {
            if (lowerCaseText.contains(keyword.word.lowercase(Locale.getDefault()))) {
                val foundItem = "발견된 키워드 '${keyword.word}' (원본: $text)"
                if (!foundList.contains(foundItem)) {
                    foundList.add(foundItem)
                }
            }
        }
    }
    // ---

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        if (::floatingButtonView.isInitialized) {
            windowManager.removeView(floatingButtonView)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
    