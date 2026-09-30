package com.arditips.simbridge.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
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

    @SuppressLint("MissingPermission")
    fun start(inputStream: InputStream, outputStream: OutputStream) {
        if (isRunning.getAndSet(true)) return

        AppLog.i(tag, "Starting low-latency voice relay session")
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = true

            // Trigger Bluetooth SCO if available
            try {
                if (audioManager.isBluetoothScoAvailableOffCall) {
                    audioManager.startBluetoothSco()
                    audioManager.isBluetoothScoOn = true
                }
            } catch (_: Exception) {}

            val minRecordBuf = AudioRecord.getMinBufferSize(sampleRate, channelIn, encoding)
            val minPlayBuf = AudioTrack.getMinBufferSize(sampleRate, channelOut, encoding)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                channelIn,
                encoding,
                maxOf(minRecordBuf, 2048)
            )

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
            AppLog.i(tag, "Audio record & play pipelines active")
        } catch (e: Exception) {
            AppLog.e(tag, "Error starting voice pipeline", e)
            stop()
            return
        }

        // Fast streaming thread (Mic -> Output Socket)
        recordThread = Thread {
            val buffer = ByteArray(640) // 20ms chunk at 16kHz
            while (isRunning.get()) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (read > 0) {
                    try {
                        outputStream.write(buffer, 0, read)
                        outputStream.flush()
                    } catch (e: Exception) {
                        AppLog.d(tag, "Record socket send finished")
                        break
                    }
                }
            }
        }.apply {
            priority = Thread.MAX_PRIORITY
            start()
        }

        // Fast playing thread (Input Socket -> Speaker)
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
                    AppLog.d(tag, "Play socket read finished")
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
            try {
                if (audioManager.isBluetoothScoOn) {
                    audioManager.isBluetoothScoOn = false
                    audioManager.stopBluetoothSco()
                }
            } catch (_: Exception) {}
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (e: Exception) {
            AppLog.e(tag, "Error stopping audio relay", e)
        }
    }
}
