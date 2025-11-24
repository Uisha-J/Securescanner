package com.example.securescanner

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    // '다른 앱 위에 그리기' 권한 요청 결과를 처리하는 런처
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // 사용자가 권한 설정 화면에서 돌아왔을 때 onResume()이 어차피 호출되므로,
        // 여기서는 별도의 로직 없이 간단한 피드백만 제공합니다.
        if (checkOverlayPermission()) {
            Toast.makeText(this, "오버레이 권한이 허용되었습니다.", Toast.LENGTH_SHORT).show()
            // 권한이 허용되었으니, 다시 한 번 체크 로직을 실행해 다음 단계(접근성)로 안내합니다.
            checkPermissionsAndStart()
        } else {
            Toast.makeText(this, "오버레이 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btn_go_to_settings).setOnClickListener {
            // 버튼이 클릭될 때마다 권한 상태를 처음부터 확인합니다.
            checkPermissionsAndStart()
        }
    }

    // --- [최종 수정된 부분] ---
    override fun onResume() {
        super.onResume()
        // 사용자가 설정에서 돌아왔을 때를 대비해 권한 상태를 다시 확인합니다.
        if (checkOverlayPermission() && isAccessibilityServiceEnabled()) {
            // 서비스가 안정적으로 시작되고 화면 제어권이 넘어갈 시간을 벌기 위해
            // 1.5초 후에 MainActivity를 종료합니다.
            Toast.makeText(this, "모든 권한이 활성화되었습니다. 잠시 후 앱이 종료됩니다.", Toast.LENGTH_LONG).show()

            // 1.5초 지연 후 finish() 호출
            Handler(Looper.getMainLooper()).postDelayed({
                finish()
            }, 1500)
        }
    }

    private fun checkPermissionsAndStart() {
        // 1단계: '다른 앱 위에 그리기' 권한부터 확인
        if (!checkOverlayPermission()) {
            requestOverlayPermission() // 권한이 없으면 요청 화면으로 보냅니다.
            return // 다음 단계로 넘어가지 않고 여기서 함수를 종료합니다.
        }

        // 2단계: '접근성 서비스' 권한 확인
        if (!isAccessibilityServiceEnabled()) {
            guideToAccessibilitySettings() // 1단계 권한이 있으면 2단계 권한을 요청합니다.
            return
        }
    }

    // '다른 앱 위에 그리기' 권한이 있는지 확인
    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true // M(마시멜로) 미만 버전은 항상 true
        }
    }

    // '다른 앱 위에 그리기' 권한 설정 화면으로 이동 요청
    private fun requestOverlayPermission() {
        Toast.makeText(this, "'다른 앱 위에 표시' 권한을 허용해주세요.", Toast.LENGTH_LONG).show()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        }
    }

    // '접근성 설정' 화면으로 사용자를 안내
    private fun guideToAccessibilitySettings() {
        Toast.makeText(this, "접근성 설정에서 '화면 분석 서비스'를 찾아 활성화해주세요.", Toast.LENGTH_LONG).show()
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    // 우리 서비스가 활성화되어 있는지 확인하는 도우미 함수 (개선된 버전)
    private fun isAccessibilityServiceEnabled(): Boolean {
        val serviceId = "$packageName/${ScreenScanService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return enabledServices?.contains(serviceId, ignoreCase = true) == true
    }
}
