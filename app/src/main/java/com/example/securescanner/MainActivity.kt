package com.example.securescanner

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
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
        checkAllPermissions()
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkAllPermissions()
    }

    private val screenCapturePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d("MainActivity", "=== Screen Capture Permission Result ===")
        Log.d("MainActivity", "Result code: ${result.resultCode}")
        Log.d("MainActivity", "RESULT_OK constant: ${Activity.RESULT_OK}")
        Log.d("MainActivity", "Result data: ${result.data}")
        Log.d("MainActivity", "Data is null: ${result.data == null}")

        if (result.resultCode == Activity.RESULT_OK) {
            Log.d("MainActivity", "Permission GRANTED")

            // Check if data is null
            if (result.data == null) {
                Log.e("MainActivity", "ERROR: result.data is NULL!")
                Toast.makeText(this, "화면 캡처 권한 데이터가 없습니다", Toast.LENGTH_SHORT).show()
                finish()
                return@registerForActivityResult
            }

            try {
                // STEP 1: Start Service FIRST with MEDIA_PROJECTION type
                Log.d("MainActivity", "Step 1: Starting ScreenScanService with MEDIA_PROJECTION type...")
                val serviceIntent = Intent(this, ScreenScanService::class.java).apply {
                    action = ScreenScanService.ACTION_SCREEN_CAPTURE_READY
                    putExtra(ScreenScanService.EXTRA_RESULT_CODE, result.resultCode)
                    // Store the intent data for service to use
                }
                startService(serviceIntent)

                // STEP 2: Wait a moment for service to start, then create MediaProjection
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        Log.d("MainActivity", "Step 2: Creating MediaProjection...")

                        // Get MediaProjectionManager
                        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager

                        if (projectionManager == null) {
                            Log.e("MainActivity", "ERROR: MediaProjectionManager is NULL!")
                            Toast.makeText(this, "MediaProjectionManager를 가져올 수 없습니다", Toast.LENGTH_SHORT).show()
                            finish()
                            return@postDelayed
                        }

                        Log.d("MainActivity", "MediaProjectionManager obtained: $projectionManager")

                        // Create MediaProjection
                        val mediaProjection = projectionManager.getMediaProjection(result.resultCode, result.data!!)
                        Log.d("MainActivity", "MediaProjection created: $mediaProjection")

                        if (mediaProjection == null) {
                            Log.e("MainActivity", "MediaProjection is null!")
                            Toast.makeText(this, "화면 캡처 초기화 실패", Toast.LENGTH_SHORT).show()
                            finish()
                            return@postDelayed
                        }

                        // STEP 3: Store in singleton
                        MediaProjectionHolder.setProjection(mediaProjection)
                        Log.d("MainActivity", "MediaProjection stored in holder")
                        Log.d("MainActivity", "MediaProjectionHolder.isReady() = ${MediaProjectionHolder.isReady()}")

                        Toast.makeText(this, "화면 캡처 권한이 허용되었습니다.", Toast.LENGTH_SHORT).show()

                        // STEP 4: Close MainActivity
                        Handler(Looper.getMainLooper()).postDelayed({
                            Log.d("MainActivity", "Finishing MainActivity")
                            finish()
                        }, 1000)

                    } catch (e: Exception) {
                        Log.e("MainActivity", "Exception in delayed creation: ${e::class.java.simpleName}")
                        Log.e("MainActivity", "Message: ${e.message}")
                        Log.e("MainActivity", "Stack trace:", e)
                        Toast.makeText(
                            this,
                            "화면 캡처 초기화 중 오류:\n${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                    }
                }, 500) // Wait 500ms for service to start

            } catch (e: Exception) {
                Log.e("MainActivity", "Exception while starting service: ${e::class.java.simpleName}")
                Log.e("MainActivity", "Message: ${e.message}")
                Log.e("MainActivity", "Stack trace:", e)
                Toast.makeText(
                    this,
                    "서비스 시작 중 오류:\n${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }

        } else {
            Log.e("MainActivity", "Permission DENIED (resultCode = ${result.resultCode})")
            Toast.makeText(this, "화면 캡처 권한이 거부되었습니다.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.d("MainActivity", "onCreate called")

        findViewById<Button>(R.id.btn_go_to_settings).setOnClickListener {
            checkAllPermissions()
        }

        // Check if launched for screen capture permission request
        val requestScreenCapture = intent.getBooleanExtra("request_screen_capture", false)
        Log.d("MainActivity", "request_screen_capture = $requestScreenCapture")

        if (requestScreenCapture) {
            // Only request screen capture
            requestScreenCapturePermission()
        } else {
            // Normal startup - check all permissions
            checkAllPermissions()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("MainActivity", "onResume called")
        // Don't auto-check on resume to avoid loops
    }

    private fun checkAllPermissions() {
        Log.d("MainActivity", "Checking all permissions...")

        // Step 1: Notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestNotificationPermission()
            return
        }

        // Step 2: Overlay permission
        if (!checkOverlayPermission()) {
            requestOverlayPermission()
            return
        }

        // Step 3: Accessibility permission
        if (!isAccessibilityServiceEnabled()) {
            guideToAccessibilitySettings()
            return
        }

        // All basic permissions granted
        // Screen capture will be requested on first button click
        allPermissionsGranted()
    }

    private fun requestNotificationPermission() {
        AlertDialog.Builder(this)
            .setTitle("알림 권한 안내")
            .setMessage("스캔 결과를 알려주기 위해 알림 권한이 필요합니다.")
            .setPositiveButton("확인") { _, _ ->
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            .setNegativeButton("취소") { _, _ ->
                Toast.makeText(this, "알림 권한이 거부되어 상태 안내가 표시되지 않을 수 있습니다.", Toast.LENGTH_LONG).show()
                checkAllPermissions()
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

    private fun requestScreenCapturePermission() {
        Log.d("MainActivity", "Requesting screen capture permission")
        AlertDialog.Builder(this)
            .setTitle("화면 캡처 권한 필요")
            .setMessage("화면의 텍스트를 읽기 위해 화면 캡처 권한이 필요합니다.\n\n다음 화면에서 '지금 시작'을 눌러주세요.")
            .setPositiveButton("권한 요청") { _, _ ->
                val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                Log.d("MainActivity", "Launching screen capture intent")
                screenCapturePermissionLauncher.launch(projectionManager.createScreenCaptureIntent())
            }
            .setNegativeButton("취소") { _, _ ->
                Log.d("MainActivity", "User cancelled screen capture permission")
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun guideToAccessibilitySettings() {
        AlertDialog.Builder(this)
            .setTitle("접근성 권한 안내")
            .setMessage("플로팅 버튼을 표시하기 위해 접근성 설정에서 'SecureScanner'를 찾아 활성화해야 합니다.")
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
        Log.d("MainActivity", "All permissions granted")
        Toast.makeText(this, "모든 권한이 확인되었습니다.\n앱이 백그라운드에서 실행됩니다.", Toast.LENGTH_LONG).show()

        Handler(Looper.getMainLooper()).postDelayed({
            moveTaskToBack(true)
        }, 1500)
    }
}