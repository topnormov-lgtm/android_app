package com.example.websocketclient

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.view.TextureView

interface CameraStateListener {
    fun onCameraStarted()
    fun onCameraStopped()
    fun onCameraError(errorMessage: String)
}

/**
 * Handles back camera lifecycle and renders real-time preview frames into a TextureView via Camera2 API.
 */
class CameraHelper(
    context: Context,
    private val textureView: TextureView,
    private val listener: CameraStateListener
) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    @Volatile
    var isCameraRunning: Boolean = false
        private set

    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            if (isCameraRunning) {
                openCamera()
            }
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
    }

    init {
        textureView.surfaceTextureListener = surfaceTextureListener
    }

    fun start() {
        if (isCameraRunning) return
        isCameraRunning = true
        startBackgroundThread()

        if (textureView.isAvailable) {
            openCamera()
        }
    }

    @SuppressLint("MissingPermission")
    private fun openCamera() {
        try {
            val cameraId = getBackFacingCameraId() ?: run {
                listener.onCameraError("Тыловая камера не обнаружена на устройстве.")
                stop()
                return
            }

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    startPreviewSession()
                    listener.onCameraStarted()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                    listener.onCameraStopped()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                    listener.onCameraError("Ошибка CameraDevice: $error")
                    stop()
                }
            }, backgroundHandler)
        } catch (e: SecurityException) {
            listener.onCameraError("Нет разрешения на использование камеры.")
            stop()
        } catch (e: Exception) {
            listener.onCameraError("Не удалось открыть камеру: ${e.localizedMessage}")
            stop()
        }
    }

    private fun startPreviewSession() {
        val camera = cameraDevice ?: return
        val surfaceTexture = textureView.surfaceTexture ?: return

        val surface = Surface(surfaceTexture)
        try {
            val previewRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
                set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            }

            @Suppress("DEPRECATION")
            camera.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    if (cameraDevice == null) return
                    captureSession = session
                    try {
                        session.setRepeatingRequest(previewRequestBuilder.build(), null, backgroundHandler)
                    } catch (e: Exception) {
                        listener.onCameraError("Ошибка обновления сессии предпросмотра: ${e.localizedMessage}")
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    listener.onCameraError("Сбой конфигурации сессии предпросмотра.")
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            listener.onCameraError("Ошибка создания сессии предпросмотра: ${e.localizedMessage}")
        }
    }

    private fun getBackFacingCameraId(): String? {
        for (id in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                return id
            }
        }
        return cameraManager.cameraIdList.firstOrNull()
    }

    fun stop() {
        isCameraRunning = false
        try {
            captureSession?.close()
            captureSession = null

            cameraDevice?.close()
            cameraDevice = null
        } catch (e: Exception) {
            Log.e("CameraHelper", "Error closing camera: ${e.localizedMessage}")
        } finally {
            stopBackgroundThread()
            listener.onCameraStopped()
        }
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackgroundWorker").apply {
            start()
            backgroundHandler = Handler(looper)
        }
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
        } catch (_: InterruptedException) {
        }
        backgroundThread = null
        backgroundHandler = null
    }
}
