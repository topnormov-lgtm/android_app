package com.example.websocketclient

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.TextureView
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity(), WsCallback, AudioDataListener, CameraStateListener {

    private lateinit var etServerIp: EditText
    private lateinit var etServerPort: EditText
    private lateinit var btnConnect: Button
    private lateinit var btnDisconnect: Button
    private lateinit var btnMicToggle: Button
    private lateinit var btnCamToggle: Button
    private lateinit var tvStatus: TextView
    private lateinit var textureViewCamera: TextureView
    private lateinit var tvCameraPlaceholder: View

    private lateinit var wsClient: WsClient
    private lateinit var audioRecorder: AudioRecorder
    private lateinit var cameraHelper: CameraHelper

    // Permission launcher for RECORD_AUDIO
    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startAudioRecording()
        } else {
            Toast.makeText(
                this,
                "Для захвата звука необходимо разрешение на микрофон.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Permission launcher for CAMERA
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCameraPreview()
        } else {
            Toast.makeText(
                this,
                "Для работы предпросмотра необходимо разрешение на камеру.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Permission launcher for POST_NOTIFICATIONS (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Start foreground service regardless or inform user
        startMyForegroundService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()

        wsClient = WsClient(this)
        audioRecorder = AudioRecorder(listener = this)
        cameraHelper = CameraHelper(this, textureViewCamera, this)

        setupListeners()
    }

    private fun initViews() {
        etServerIp = findViewById(R.id.etServerIp)
        etServerPort = findViewById(R.id.etServerPort)
        btnConnect = findViewById(R.id.btnConnect)
        btnDisconnect = findViewById(R.id.btnDisconnect)
        btnMicToggle = findViewById(R.id.btnMicToggle)
        btnCamToggle = findViewById(R.id.btnCamToggle)
        tvStatus = findViewById(R.id.tvStatus)
        textureViewCamera = findViewById(R.id.textureViewCamera)
        tvCameraPlaceholder = findViewById(R.id.tvCameraPlaceholder)
    }

    private fun setupListeners() {
        btnConnect.setOnClickListener {
            handleConnect()
        }

        btnDisconnect.setOnClickListener {
            handleDisconnect()
        }

        btnMicToggle.setOnClickListener {
            handleMicToggle()
        }

        btnCamToggle.setOnClickListener {
            handleCamToggle()
        }
    }

    // --- Foreground Service Management ---

    private fun checkNotificationPermissionAndStartService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        startMyForegroundService()
    }

    private fun startMyForegroundService() {
        val serviceIntent = Intent(this, MyForegroundService::class.java).apply {
            action = MyForegroundService.ACTION_START
        }
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun stopMyForegroundService() {
        val serviceIntent = Intent(this, MyForegroundService::class.java).apply {
            action = MyForegroundService.ACTION_STOP
        }
        startService(serviceIntent)
    }

    // --- Microphone Logic ---

    private fun handleMicToggle() {
        if (audioRecorder.isRecording) {
            stopAudioRecording()
        } else {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                startAudioRecording()
            } else {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun startAudioRecording() {
        val success = audioRecorder.start()
        if (success) {
            btnMicToggle.text = "Микрофон: Вкл"
            btnMicToggle.setTextColor(Color.parseColor("#2ECC71")) // Green
            Toast.makeText(this, "Микрофон включен", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopAudioRecording() {
        audioRecorder.stop()
        btnMicToggle.text = "Микрофон: Выкл"
        btnMicToggle.setTextColor(Color.parseColor("#888888"))
        Toast.makeText(this, "Микрофон выключен", Toast.LENGTH_SHORT).show()
    }

    override fun onAudioChunk(data: ByteArray) {
        Log.d("AudioRecorder", "Captured audio chunk: ${data.size} bytes")
    }

    override fun onError(errorMessage: String) {
        runOnUiThread {
            Log.e("AudioRecorder", "Audio error: $errorMessage")
            Toast.makeText(this, "Ошибка микрофона: $errorMessage", Toast.LENGTH_SHORT).show()
            stopAudioRecording()
        }
    }

    // --- Camera Logic ---

    private fun handleCamToggle() {
        if (cameraHelper.isCameraRunning) {
            stopCameraPreview()
        } else {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                startCameraPreview()
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun startCameraPreview() {
        cameraHelper.start()
    }

    private fun stopCameraPreview() {
        cameraHelper.stop()
    }

    override fun onCameraStarted() {
        runOnUiThread {
            btnCamToggle.text = "Камера: Вкл"
            btnCamToggle.setTextColor(Color.parseColor("#2ECC71")) // Green
            tvCameraPlaceholder.visibility = View.GONE
            Toast.makeText(this, "Камера включена", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCameraStopped() {
        runOnUiThread {
            btnCamToggle.text = "Камера: Выкл"
            btnCamToggle.setTextColor(Color.parseColor("#888888"))
            tvCameraPlaceholder.visibility = View.VISIBLE
        }
    }

    override fun onCameraError(errorMessage: String) {
        runOnUiThread {
            Log.e("CameraHelper", "Camera error: $errorMessage")
            Toast.makeText(this, "Ошибка камеры: $errorMessage", Toast.LENGTH_SHORT).show()
            stopCameraPreview()
        }
    }

    // --- WebSocket Logic ---

    private fun handleConnect() {
        val ip = etServerIp.text.toString().trim()
        val portStr = etServerPort.text.toString().trim()

        if (ip.isEmpty()) {
            etServerIp.error = "Введите IP-адрес сервера"
            etServerIp.requestFocus()
            return
        }

        val port = portStr.toIntOrNull()
        if (port == null || port !in 1..65535) {
            etServerPort.error = "Некорректный порт (1 - 65535)"
            etServerPort.requestFocus()
            return
        }

        tvStatus.text = "Подключение..."
        tvStatus.setTextColor(Color.parseColor("#FFA500")) // Orange
        btnConnect.isEnabled = false

        // Launch Foreground Service as requested
        checkNotificationPermissionAndStartService()

        // Connect WebSocket client
        wsClient.connect(ip, port)
    }

    private fun handleDisconnect() {
        wsClient.disconnect()
        stopMyForegroundService()
        updateUiDisconnected("Отключено пользователем")
    }

    override fun onConnected() {
        runOnUiThread {
            tvStatus.text = getString(R.string.proxy_active)
            tvStatus.setTextColor(Color.parseColor("#7F8C8D")) // Gray

            btnConnect.isEnabled = false
            btnDisconnect.isEnabled = true
            etServerIp.isEnabled = false
            etServerPort.isEnabled = false

            Toast.makeText(this, "Соединение установлено", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDisconnected(reason: String) {
        runOnUiThread {
            updateUiDisconnected(reason)
        }
    }

    override fun onError(errorMessage: String) {
        runOnUiThread {
            tvStatus.text = "Ошибка: $errorMessage"
            tvStatus.setTextColor(Color.parseColor("#E74C3C")) // Red

            btnConnect.isEnabled = true
            btnDisconnect.isEnabled = false
            etServerIp.isEnabled = true
            etServerPort.isEnabled = true
        }
    }

    override fun onMessageReceived(text: String) {
        Log.d("WebSocket", "Received message: $text")
    }

    private fun updateUiDisconnected(statusText: String) {
        tvStatus.text = statusText
        tvStatus.setTextColor(Color.parseColor("#888888"))

        btnConnect.isEnabled = true
        btnDisconnect.isEnabled = false
        etServerIp.isEnabled = true
        etServerPort.isEnabled = true
    }

    override fun onPause() {
        super.onPause()
        if (cameraHelper.isCameraRunning) {
            stopCameraPreview()
        }
    }

    override fun onDestroy() {
        stopMyForegroundService()
        if (audioRecorder.isRecording) {
            audioRecorder.stop()
        }
        if (cameraHelper.isCameraRunning) {
            cameraHelper.stop()
        }
        wsClient.disconnect()
        super.onDestroy()
    }
}
