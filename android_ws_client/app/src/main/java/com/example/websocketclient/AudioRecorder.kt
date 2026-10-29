package com.example.websocketclient

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException

interface AudioDataListener {
    fun onAudioChunk(data: ByteArray)
    fun onError(errorMessage: String)
}

/**
 * Handles capturing raw audio samples from the microphone using AudioRecord (16 kHz, Mono, PCM 16-bit).
 */
class AudioRecorder(
    private val sampleRate: Int = 16000,
    private val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
    private val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT,
    private val listener: AudioDataListener
) {
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val recordingScope = CoroutineScope(Dispatchers.IO)

    @Volatile
    var isRecording: Boolean = false
        private set

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (isRecording) return true

        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            listener.onError("Недопустимые параметры аудиозаписи для данного устройства.")
            return false
        }

        val bufferSize = maxOf(minBufferSize, 2048)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
        } catch (e: Exception) {
            listener.onError("Ошибка создания AudioRecord: ${e.localizedMessage}")
            return false
        }

        val recordInstance = audioRecord
        if (recordInstance == null || recordInstance.state != AudioRecord.STATE_INITIALIZED) {
            recordInstance?.release()
            audioRecord = null
            listener.onError("AudioRecord не инициализирован. Микрофон может быть занят другим приложением.")
            return false
        }

        try {
            recordInstance.startRecording()
        } catch (e: Exception) {
            listener.onError("Не удалось начать запись: ${e.localizedMessage}")
            stop()
            return false
        }

        isRecording = true

        recordingJob = recordingScope.launch {
            val buffer = ByteArray(bufferSize)
            try {
                while (isActive && isRecording) {
                    val bytesRead = recordInstance.read(buffer, 0, buffer.size)
                    if (bytesRead > 0) {
                        val chunk = buffer.copyOf(bytesRead)
                        listener.onAudioChunk(chunk)
                    } else if (bytesRead == AudioRecord.ERROR_INVALID_OPERATION ||
                        bytesRead == AudioRecord.ERROR_BAD_VALUE
                    ) {
                        throw IOException("Сбой чтения потока из AudioRecord.")
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    listener.onError("Сбой в процессе захвата звука: ${e.localizedMessage}")
                }
            } finally {
                stopInternal()
            }
        }

        return true
    }

    fun stop() {
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null
        stopInternal()
    }

    private fun stopInternal() {
        try {
            audioRecord?.let {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    it.stop()
                }
                it.release()
            }
        } catch (_: Exception) {
        } finally {
            audioRecord = null
        }
    }
}
