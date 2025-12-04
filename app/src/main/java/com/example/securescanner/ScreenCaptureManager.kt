package com.example.securescanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ScreenCaptureManager(private val context: Context) {

    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var isInitialized = false
    private var mediaProjectionCallback: MediaProjection.Callback? = null

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val displayMetrics = DisplayMetrics()

    // ML Kit Text Recognizer with Korean support
    private val textRecognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())

    init {
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay?.getMetrics(displayMetrics)
        Log.d("ScreenCapture", "ScreenCaptureManager initialized")
    }

    /**
     * Get MediaProjection from singleton holder
     */
    private fun getMediaProjection(): MediaProjection? {
        val projection = MediaProjectionHolder.mediaProjection
        Log.d("ScreenCapture", "Getting MediaProjection from holder: ${projection != null}")
        return projection
    }

    /**
     * Check if media projection is ready
     */
    fun isReady(): Boolean {
        val ready = MediaProjectionHolder.isReady()
        Log.d("ScreenCapture", "isReady check: $ready")
        return ready
    }

    /**
     * Initialize VirtualDisplay and ImageReader (called once)
     */
    private fun initializeCapture(mediaProjection: MediaProjection): Boolean {
        // Check if we can reuse existing resources
        if (isInitialized && virtualDisplay != null && imageReader != null) {
            // Verify the VirtualDisplay is still valid by checking if it was created
            // with the same MediaProjection instance
            try {
                // Test if VirtualDisplay is still usable by checking its display
                val display = virtualDisplay?.display
                if (display != null) {
                    Log.d("ScreenCapture", "Capture already initialized, reusing existing setup")
                    return true
                } else {
                    Log.w("ScreenCapture", "VirtualDisplay is invalid (display is null), reinitializing...")
                    cleanupCapture()
                }
            } catch (e: Exception) {
                Log.e("ScreenCapture", "VirtualDisplay validation failed: ${e.message}, reinitializing...")
                cleanupCapture()
            }
        }

        try {
            val width = displayMetrics.widthPixels
            val height = displayMetrics.heightPixels
            val density = displayMetrics.densityDpi

            Log.d("ScreenCapture", "Initializing capture - ${width}x${height} @${density}dpi")

            // Register MediaProjection callback (required on Android 14+)
            mediaProjectionCallback = object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.d("ScreenCapture", "MediaProjection stopped by system, cleaning up resources")
                    cleanupCapture()
                    // Clear the holder reference (without calling stop() again to avoid recursion)
                    MediaProjectionHolder.clearWithoutStopping()
                    Log.d("ScreenCapture", "MediaProjectionHolder cleared after projection stopped")
                }
            }

            // Register callback BEFORE creating virtual display
            mediaProjection.registerCallback(mediaProjectionCallback!!, Handler(Looper.getMainLooper()))
            Log.d("ScreenCapture", "MediaProjection callback registered")

            // Create ImageReader (reused for all captures)
            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

            // Create VirtualDisplay (kept alive for multiple captures)
            virtualDisplay = mediaProjection.createVirtualDisplay(
                "ScreenCapture",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null, null
            )

            isInitialized = true
            Log.d("ScreenCapture", "VirtualDisplay created and will be kept alive for reuse")
            return true

        } catch (e: Exception) {
            Log.e("ScreenCapture", "Failed to initialize capture: ${e.message}", e)
            cleanupCapture()
            return false
        }
    }

    /**
     * Clean up VirtualDisplay and ImageReader
     */
    private fun cleanupCapture() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        isInitialized = false

        // Unregister callback
        mediaProjectionCallback?.let { callback ->
            getMediaProjection()?.unregisterCallback(callback)
        }
        mediaProjectionCallback = null

        Log.d("ScreenCapture", "Capture resources cleaned up")
    }

    /**
     * Capture screen and extract text using OCR
     * Reuses existing VirtualDisplay to support multiple captures
     */
    suspend fun captureAndExtractText(): List<String> = suspendCancellableCoroutine { continuation ->
        val mediaProjection = getMediaProjection()

        if (mediaProjection == null) {
            Log.e("ScreenCapture", "Media projection not available!")
            continuation.resumeWithException(Exception("Media projection not started. Please request permission first."))
            return@suspendCancellableCoroutine
        }

        // Initialize capture resources if not already done (creates VirtualDisplay once)
        if (!initializeCapture(mediaProjection)) {
            Log.e("ScreenCapture", "Failed to initialize capture")
            continuation.resumeWithException(Exception("Failed to initialize screen capture"))
            return@suspendCancellableCoroutine
        }

        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels

        Log.d("ScreenCapture", "Capturing screen from existing VirtualDisplay - ${width}x${height}")

        try {
            // Wait a moment for the virtual display to render current screen
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    // Acquire image from the persistent ImageReader
                    val image = imageReader?.acquireLatestImage()
                    if (image == null) {
                        Log.e("ScreenCapture", "Failed to acquire image from ImageReader")
                        continuation.resumeWithException(Exception("Failed to capture screen image"))
                        return@postDelayed
                    }

                    Log.d("ScreenCapture", "Image captured successfully")

                    // Convert Image to Bitmap
                    val bitmap = imageToBitmap(image, width, height)
                    image.close()

                    // DON'T release virtualDisplay or imageReader - reuse them for next capture

                    // Process with ML Kit OCR
                    processImageWithOCR(bitmap) { extractedTexts ->
                        continuation.resume(extractedTexts)
                    }

                } catch (e: Exception) {
                    Log.e("ScreenCapture", "Error during screen capture: ${e.message}", e)
                    continuation.resumeWithException(e)
                }
            }, 100) // 100ms delay to ensure display has latest frame

        } catch (e: Exception) {
            Log.e("ScreenCapture", "Failed to capture screen: ${e.message}", e)
            continuation.resumeWithException(e)
        }
    }

    /**
     * Convert ImageReader.Image to Bitmap
     */
    private fun imageToBitmap(image: android.media.Image, width: Int, height: Int): Bitmap {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * width

        val bitmap = Bitmap.createBitmap(
            width + rowPadding / pixelStride,
            height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)

        return Bitmap.createBitmap(bitmap, 0, 0, width, height)
    }

    /**
     * Process bitmap with ML Kit OCR
     */
    private fun processImageWithOCR(bitmap: Bitmap, callback: (List<String>) -> Unit) {
        Log.d("ScreenCapture", "Starting OCR processing...")

        val inputImage = InputImage.fromBitmap(bitmap, 0)

        textRecognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                val extractedTexts = mutableListOf<String>()

                for (block in visionText.textBlocks) {
                    for (line in block.lines) {
                        val text = line.text
                        if (text.isNotBlank()) {
                            extractedTexts.add(text)
                            Log.d("ScreenCapture", "OCR found: $text")
                        }
                    }
                }

                Log.d("ScreenCapture", "OCR complete. Found ${extractedTexts.size} text items")
                callback(extractedTexts)
            }
            .addOnFailureListener { e ->
                Log.e("ScreenCapture", "OCR failed: ${e.message}", e)
                callback(emptyList())
            }
    }

    /**
     * Stop screen capture and clean up all resources
     */
    fun stop() {
        Log.d("ScreenCapture", "Stopping ScreenCaptureManager")
        cleanupCapture()
        // Don't stop the MediaProjection here - it's managed by MediaProjectionHolder
    }
}