package com.arditips.simbridge.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import com.arditips.simbridge.bluetooth.BluetoothConstants
import com.arditips.simbridge.util.AppLog
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

class LiveAudioRelay(private val context: Context) {

    private val tag = "LiveAudioRelay"
    private val isRunning = AtomicBoolean(false)
    private var recordThread: Thread? = null
    private var playThread: Thread? = null

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    private val sampleRate = BluetoothConstants.SAMPLE_RATE
    private val channelIn = AudioFormat.CHANNEL_IN_MONO
    private val channelOut = AudioFormat.CHANNEL_OUT_MONO
    private val encoding = AudioFormat.ENCODING_PCM_16BIT

    fun setSpeaker(useSpeaker: Boolean) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        routeAudio(audioManager, useSpeaker)
    }

    private fun routeAudio(audioManager: AudioManager, useSpeaker: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val targetType = if (useSpeaker) AudioDeviceInfo.TYPE_BUILTIN_SPEAKER else AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                val device = audioManager.availableCommunicationDevices.firstOrNull { it.type == targetType }
                    ?: if (!useSpeaker) audioManager.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER } else null
                if (device != null) {
                    audioManager.setCommunicationDevice(device)
                    AppLog.i(tag, "Audio routed to ${if (useSpeaker) "SPEAKER" else "EARPIECE"}")
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = useSpeaker
            }
        } catch (e: Exception) {
            AppLog.e(tag, "Error routing audio", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun start(inputStream: InputStream, outputStream: OutputStream, useSpeaker: Boolean = true) {
        if (isRunning.getAndSet(true)) return

        AppLog.i(tag, "Starting dual-path live voice relay session (speaker=$useSpeaker)")
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        try {
            if (!useSpeaker) {
                // Client side (S26) uses communication mode
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                routeAudio(audioManager, false)
            } else {
                // Host side (S21) is cellular call - DO NOT override mode, only route
                routeAudio(audioManager, true)
            }

            val minRecordBuf = AudioRecord.getMinBufferSize(sampleRate, channelIn, encoding)
            val minPlayBuf = AudioTrack.getMinBufferSize(sampleRate, channelOut, encoding)

            val primarySource = if (useSpeaker) MediaRecorder.AudioSource.MIC else MediaRecorder.AudioSource.VOICE_COMMUNICATION
            val fallbackSource = if (useSpeaker) MediaRecorder.AudioSource.VOICE_COMMUNICATION else MediaRecorder.AudioSource.MIC

            audioRecord = try {
                AudioRecord(
                    primarySource,
                    sampleRate,
                    channelIn,
                    encoding,
                    maxOf(minRecordBuf, 2048)
                )
            } catch (e: Exception) {
                AppLog.w(tag, "Primary source $primarySource failed, falling back to $fallbackSource")
                AudioRecord(
                    fallbackSource,
                    sampleRate,
                    channelIn,
                    encoding,
                    maxOf(minRecordBuf, 2048)
                )
            }

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(encoding)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelOut)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minPlayBuf, 2048))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioRecord?.startRecording()
            audioTrack?.play()
            AppLog.i(tag, "Voice record & play pipelines successfully active")
        } catch (e: Exception) {
            AppLog.e(tag, "Error starting voice pipeline", e)
            stop()
            return
        }

        // Fast streaming thread (Mic -> Remote)
        recordThread = Thread {
            val buffer = ByteArray(640)
            while (isRunning.get()) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (read > 0) {
                    try {
                        outputStream.write(buffer, 0, read)
                        outputStream.flush()
                    } catch (e: Exception) {
                        break
                    }
                }
            }
        }.apply {
            priority = Thread.MAX_PRIORITY
            start()
        }

        // Fast playback thread (Remote -> Speaker)
        playThread = Thread {
            val buffer = ByteArray(640)
            while (isRunning.get()) {
                try {
                    val read = inputStream.read(buffer)
                    if (read > 0) {
                        audioTrack?.write(buffer, 0, read)
                    } else if (read < 0) {
                        break
                    }
                } catch (e: Exception) {
                    break
                }
            }
        }.apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return
        AppLog.i(tag, "Stopping voice relay session")

        try {
            recordThread?.interrupt()
            playThread?.interrupt()

            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null

            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null

            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = false
            }
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (e: Exception) {
            AppLog.e(tag, "Error stopping audio relay", e)
        }
    }
}
