package com.arditips.simbridge.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import com.arditips.simbridge.bluetooth.BluetoothConstants
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

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = true

        val minRecordBuf = AudioRecord.getMinBufferSize(sampleRate, channelIn, encoding)
        val minPlayBuf = AudioTrack.getMinBufferSize(sampleRate, channelOut, encoding)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                channelIn,
                encoding,
                maxOf(minRecordBuf, 4096)
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
                .setBufferSizeInBytes(maxOf(minPlayBuf, 4096))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioRecord?.startRecording()
            audioTrack?.play()
        } catch (e: Exception) {
            Log.e(tag, "Error initializing audio record/track", e)
            stop()
            return
        }

        // Recording & Sending Thread
        recordThread = Thread {
            val buffer = ByteArray(2048)
            while (isRunning.get()) {
                val readBytes = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (readBytes > 0) {
                    try {
                        outputStream.write(buffer, 0, readBytes)
                        outputStream.flush()
                    } catch (e: Exception) {
                        Log.e(tag, "Audio record send failed", e)
                        break
                    }
                }
            }
        }.apply { start() }

        // Receiving & Playing Thread
        playThread = Thread {
            val buffer = ByteArray(2048)
            while (isRunning.get()) {
                try {
                    val readBytes = inputStream.read(buffer)
                    if (readBytes > 0) {
                        audioTrack?.write(buffer, 0, readBytes)
                    } else if (readBytes < 0) {
                        break
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Audio play receive failed", e)
                    break
                }
            }
        }.apply { start() }
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return

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
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (e: Exception) {
            Log.e(tag, "Error stopping audio relay", e)
        }
    }
}
