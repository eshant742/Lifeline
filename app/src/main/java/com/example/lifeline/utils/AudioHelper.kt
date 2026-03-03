package com.example.lifeline.utils

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.io.IOException

/**
 * AudioHelper — Voice message recording and playback.
 * Records audio in AAC format for sending as voice SOS messages.
 * Files are saved to the app's persistent internal storage.
 */
class AudioHelper(private val context: Context) {

    companion object {
        private const val TAG = "AudioHelper"
        private const val AUDIO_DIR = "audio"
    }

    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var currentOutputFile: String? = null

    var isRecording: Boolean = false
        private set

    var isPlaying: Boolean = false
        private set

    /**
     * Get the directory where audio files are stored.
     * Creates it if it doesn't exist.
     */
    private fun getAudioDir(): File {
        val dir = File(context.filesDir, AUDIO_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Start recording audio.
     * @param filename The name of the output file (without extension)
     * @return The absolute path of the output file, or null on failure
     */
    fun startRecording(filename: String): String? {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return currentOutputFile
        }

        val outputFile = File(getAudioDir(), "$filename.m4a")
        currentOutputFile = outputFile.absolutePath

        try {
            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }

            isRecording = true
            Log.d(TAG, "Recording started: ${outputFile.absolutePath}")
            return outputFile.absolutePath
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start recording: ${e.message}", e)
            releaseRecorder()
            return null
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Illegal state starting recording: ${e.message}", e)
            releaseRecorder()
            return null
        }
    }

    /**
     * Stop recording and return the file path.
     * @return The path to the recorded audio file, or null if not recording
     */
    fun stopRecording(): String? {
        if (!isRecording) {
            Log.w(TAG, "Not currently recording")
            return null
        }

        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (e: RuntimeException) {
            Log.e(TAG, "Error stopping recorder: ${e.message}", e)
        }

        recorder = null
        isRecording = false
        val path = currentOutputFile
        Log.d(TAG, "Recording stopped: $path")
        return path
    }

    /**
     * Play audio from a file path.
     */
    fun playAudio(filePath: String, onCompletion: (() -> Unit)? = null) {
        if (isPlaying) {
            stopPlayback()
        }

        try {
            player = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
                setOnCompletionListener {
                    this@AudioHelper.isPlaying = false
                    onCompletion?.invoke()
                }
                start()
            }
            this.isPlaying = true
            Log.d(TAG, "Playing audio: $filePath")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to play audio: ${e.message}", e)
            this.isPlaying = false
        }
    }

    /**
     * Play audio from raw bytes (received via Nearby).
     * Saves to a temp file first, then plays.
     */
    fun playAudioFromBytes(audioBytes: ByteArray, senderId: String, onCompletion: (() -> Unit)? = null) {
        val tempFile = File(getAudioDir(), "received_${senderId}_${System.currentTimeMillis()}.m4a")
        try {
            tempFile.writeBytes(audioBytes)
            playAudio(tempFile.absolutePath, onCompletion)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save/play received audio: ${e.message}", e)
        }
    }

    /**
     * Stop playback.
     */
    fun stopPlayback() {
        try {
            player?.let { mp ->
                if (mp.isPlaying) {
                    mp.stop()
                }
                mp.release()
            }
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Error stopping playback: ${e.message}")
        }
        player = null
        this.isPlaying = false
    }

    /**
     * Release all resources. Call in Activity onDestroy.
     */
    fun release() {
        releaseRecorder()
        stopPlayback()
    }

    private fun releaseRecorder() {
        try {
            recorder?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing recorder: ${e.message}")
        }
        recorder = null
        isRecording = false
    }
}
