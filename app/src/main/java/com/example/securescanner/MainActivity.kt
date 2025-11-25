package com.example.securescanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // 권한 요청 후 다시 확인 절차 시작
        checkAllPermissions()
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // 설정 화면에서 돌아온 후 다시 확인 절차 시작
        checkAllPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btn_go_to_settings).setOnClickListener {
            checkAllPermissions()
        }

        // 앱 시작 시 즉시 권한 확인 시작
        checkAllPermissions()
    }

    override fun onResume() {
        super.onResume()
        // 다른 설정 화면에서 돌아왔을 때 권한 상태 다시 확인
        checkAllPermissions()
    }

    private fun checkAllPermissions() {
        // 1단계: 알림 권한 (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestNotificationPermission()
            return
        }

        // 2단계: 오버레이 권한
        if (!checkOverlayPermission()) {
            requestOverlayPermission()
            return
        }

        // 3단계: 접근성 권한
        if (!isAccessibilityServiceEnabled()) {
            guideToAccessibilitySettings()
            return
        }

        // 모든 권한이 충족된 경우
        allPermissionsGranted()
    }

    private fun requestNotificationPermission() {
        AlertDialog.Builder(this)
            .setTitle("알림 권한 안내")
            .setMessage("스캔 시작 및 결과 등, 앱의 상태를 토스트 메시지로 알려주기 위해 알림 권한이 필요합니다.")
            .setPositiveButton("확인") { _, _ ->
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            .setNegativeButton("취소") { _, _ ->
                Toast.makeText(this, "알림 권한이 거부되어 앱의 상태 안내가 표시되지 않을 수 있습니다.", Toast.LENGTH_LONG).show()
                checkAllPermissions() // 거부해도 다음 단계 진행
            }
            .setCancelable(false)
            .show()
    }

    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this)
        else true
    }

    private fun requestOverlayPermission() {
        AlertDialog.Builder(this)
            .setTitle("오버레이 권한 안내")
            .setMessage("'다른 앱 위에 표시' 권한을 허용해야 플로팅 버튼을 사용할 수 있습니다.")
            .setPositiveButton("설정으로 이동") { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    overlayPermissionLauncher.launch(intent)
                }
            }
            .setNegativeButton("종료") { _, _ ->
                Toast.makeText(this, "필수 권한이 없어 앱을 종료합니다.", Toast.LENGTH_SHORT).show()
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun guideToAccessibilitySettings() {
        AlertDialog.Builder(this)
            .setTitle("접근성 권한 안내")
            .setMessage("화면의 텍스트를 분석하려면 접근성 설정에서 'SecureScanner'를 찾아 활성화해야 합니다.")
            .setPositiveButton("설정으로 이동") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("종료") { _, _ ->
                Toast.makeText(this, "필수 권한이 없어 앱을 종료합니다.", Toast.LENGTH_SHORT).show()
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val serviceId = "$packageName/${ScreenScanService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabledServices?.contains(serviceId, ignoreCase = true) == true
    }

    private fun allPermissionsGranted() {
        Toast.makeText(this, "모든 권한이 확인되었습니다. 서비스를 시작합니다.", Toast.LENGTH_LONG).show()
        Handler(Looper.getMainLooper()).postDelayed({ finish() }, 1500)
    }
}
