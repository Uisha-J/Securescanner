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
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageButton
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class ScreenScanService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingButtonView: View
    private lateinit var params: WindowManager.LayoutParams

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createFloatingButton()
        startForegroundService()
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100
        params.y = 100

        val button = floatingButtonView.findViewById<ImageButton>(R.id.floating_button)

        // --- [최종 검증된 표준 로직: performClick() 사용] ---

        // 1. 클릭 시 할 일은 여기에만 정의합니다.
        button.setOnClickListener {
            Log.d("FinalClickTest", "클릭 성공! 화면 스캔을 시작합니다.")
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(this@ScreenScanService, "화면 스캔을 시작합니다...", Toast.LENGTH_SHORT).show()
            }
            scanCurrentScreen()
        }

        // 2. 터치 이벤트는 드래그와 클릭을 구분하여, 클릭일 경우 performClick()을 호출합니다.
        button.setOnTouchListener(object : View.OnTouchListener {
            private var initialX: Int = 0
            private var initialY: Int = 0
            private var initialTouchX: Float = 0f
            private var initialTouchY: Float = 0f
            private var isMoved = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isMoved = false
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true // 이벤트를 계속 처리하기 위해 true를 반환합니다.
                    }
                    MotionEvent.ACTION_MOVE -> {
                        // 손가락이 미세한 움직임(10픽셀) 이상 움직였을 때만 드래그로 간주
                        if (abs(event.rawX - initialTouchX) > 10 || abs(event.rawY - initialTouchY) > 10) {
                            isMoved = true
                            params.x = initialX + (event.rawX - initialTouchX).toInt()
                            params.y = initialY + (event.rawY - initialTouchY).toInt()
                            windowManager.updateViewLayout(floatingButtonView, params)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        // 드래그가 아니었을 경우에만 '클릭'을 실행하라고 시스템에 알림
                        if (!isMoved) {
                            v.performClick()
                        }
                        return true
                    }
                }
                return false
            }
        })

        windowManager.addView(floatingButtonView, params)
    }

    // --- (scanCurrentScreen 및 나머지 코드는 이전과 동일합니다) ---
    private fun scanCurrentScreen() {
        val rootNode: AccessibilityNodeInfo? = rootInActiveWindow
        if (rootNode == null) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(this, "화면 정보를 아직 가져올 수 없습니다.", Toast.LENGTH_SHORT).show()
            }
            return
        }
        val foundKeywords = mutableListOf<String>()
        findTextNodes(rootNode, foundKeywords)
        Handler(Looper.getMainLooper()).post {
            if (foundKeywords.isEmpty()) {
                Toast.makeText(this, "의심스러운 내용을 찾지 못했습니다.", Toast.LENGTH_SHORT).show()
            } else {
                val resultText = "위험 의심 단어 발견: ${foundKeywords.joinToString(", ")}"
                Toast.makeText(this, resultText, Toast.LENGTH_LONG).show()
            }
        }
        rootNode.recycle()
    }

    private fun findTextNodes(node: AccessibilityNodeInfo, foundList: MutableList<String>) {
        if (node.text != null && node.text.isNotEmpty()) {
            val text = node.text.toString().lowercase()
            if (text.contains("bit.ly") || text.contains("flic.kr")) {
                foundList.add("단축 URL ($text)")
            }
            if (text.contains("캄보디아") || text.contains("출국") || text.contains("고수익")) {
                foundList.add("위험 키워드 ($text)")
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            child?.let {
                findTextNodes(it, foundList)
                it.recycle()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingButtonView.isInitialized) {
            windowManager.removeView(floatingButtonView)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
