package com.alfred.assistant.commands

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * Handles taking selfies with the front camera using CameraX.
 * Saves photos to the gallery via MediaStore.
 */
object CameraHandler {

    private const val TAG = "AlfredCamera"

    /**
     * Takes a selfie using the front-facing camera and saves it to the gallery.
     *
     * Uses CameraX [ProcessCameraProvider] to bind an [ImageCapture] use case
     * to the front camera, captures a single frame, and writes it to MediaStore.
     *
     * @return A butler-style response string indicating success or failure.
     */
    suspend fun takeSelfie(context: Context): String {
        return try {
            val result = captureImage(context)
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to take selfie", e)
            "I'm terribly sorry, sir. I was unable to capture your selfie. ${e.message}"
        }
    }

    private suspend fun captureImage(context: Context): String =
        suspendCancellableCoroutine { continuation ->
            val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
            val mainExecutor = ContextCompat.getMainExecutor(context)

            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()

                    // Create a custom LifecycleOwner for headless capture
                    val lifecycleOwner = HeadlessLifecycleOwner()
                    lifecycleOwner.start()

                    // Configure ImageCapture
                    val imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()

                    // Select front camera
                    val cameraSelector = CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                        .build()

                    // Unbind any existing use cases
                    cameraProvider.unbindAll()

                    // Bind to lifecycle
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        imageCapture
                    )

                    // Generate filename with timestamp
                    val timestamp = SimpleDateFormat(
                        "yyyyMMdd_HHmmss", Locale.getDefault()
                    ).format(Date())
                    val fileName = "Alfred_Selfie_$timestamp.jpg"

                    // Create MediaStore output options
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(
                                MediaStore.MediaColumns.RELATIVE_PATH,
                                Environment.DIRECTORY_PICTURES + "/Alfred"
                            )
                        }
                    }

                    val outputOptions = ImageCapture.OutputFileOptions.Builder(
                        context.contentResolver,
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        contentValues
                    ).build()

                    // Add a small delay so the camera can initialize and auto-expose
                    mainExecutor.execute {
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            // Take the picture
                            imageCapture.takePicture(
                                outputOptions,
                                cameraExecutor,
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(
                                        output: ImageCapture.OutputFileResults
                                    ) {
                                        Log.d(TAG, "Selfie saved: ${output.savedUri}")

                                        // Clean up
                                        cameraProvider.unbindAll()
                                        lifecycleOwner.stop()
                                        cameraExecutor.shutdown()

                                        if (continuation.isActive) {
                                            continuation.resume(
                                                "Your selfie has been captured and saved to the gallery as $fileName, sir. Looking splendid, if I may say so."
                                            )
                                        }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        Log.e(TAG, "Selfie capture failed", exception)

                                        // Clean up
                                        cameraProvider.unbindAll()
                                        lifecycleOwner.stop()
                                        cameraExecutor.shutdown()

                                        if (continuation.isActive) {
                                            continuation.resume(
                                                "I'm afraid the camera encountered an error, sir: ${exception.message}"
                                            )
                                        }
                                    }
                                }
                            )
                        }, 1000L) // 1-second delay for camera warm-up
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Camera initialization failed", e)
                    cameraExecutor.shutdown()
                    if (continuation.isActive) {
                        continuation.resume(
                            "I was unable to access the camera, sir. ${e.message}"
                        )
                    }
                }
            }, mainExecutor)

            // Handle cancellation
            continuation.invokeOnCancellation {
                cameraExecutor.shutdown()
            }
        }

    /**
     * A minimal [LifecycleOwner] for headless CameraX capture without an Activity/Fragment.
     */
    private class HeadlessLifecycleOwner : LifecycleOwner {

        private val lifecycleRegistry = LifecycleRegistry(this)

        override val lifecycle: Lifecycle
            get() = lifecycleRegistry

        fun start() {
            lifecycleRegistry.currentState = Lifecycle.State.STARTED
        }

        fun stop() {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        }
    }
}
