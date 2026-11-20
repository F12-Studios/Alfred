package com.alfred.assistant.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * Continuously listens for the wake word **"Hey Alfred"** (or just **"Alfred"**)
 * using Android's [SpeechRecognizer].
 *
 * When the wake word is detected the callback [onWakeWordDetected] fires.
 * If the user appended a command after the wake word in the same utterance
 * (e.g. "Hey Alfred, call Mom"), the trailing text is passed as the parameter;
 * otherwise the parameter is `null` so the caller can start [CommandRecognizer].
 *
 * **Threading:** This class creates its own [SpeechRecognizer] on the main
 * thread via a [Handler], so it is safe to instantiate from any thread.
 */
class WakeWordDetector(
    private val context: Context,
    private val onWakeWordDetected: (commandText: String?) -> Unit
) {

    companion object {
        private const val TAG = "WakeWordDetector"
        private const val RESTART_DELAY_MS = 500L

        // All lowercase variants we match against
        private val WAKE_PHRASES = listOf("hey alfred", "hey, alfred", "alfred")
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var shouldRestart = false

    // ── Public API ──────────────────────────────────────────────────────

    /** Begin continuous wake-word detection. Safe to call from any thread. */
    fun start() {
        mainHandler.post {
            shouldRestart = true
            if (!isListening) {
                createRecognizerIfNeeded()
                beginListening()
            }
        }
    }

    /** Pause detection. The recognizer is stopped but not destroyed. */
    fun stop() {
        mainHandler.post {
            shouldRestart = false
            isListening = false
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping recognizer", e)
            }
        }
    }

    /** Release all resources. After this call the instance is unusable. */
    fun destroy() {
        mainHandler.post {
            shouldRestart = false
            isListening = false
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                Log.w(TAG, "Error destroying recognizer", e)
            }
            speechRecognizer = null
        }
    }

    // ── Internals ───────────────────────────────────────────────────────

    private fun createRecognizerIfNeeded() {
        if (speechRecognizer != null) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "Speech recognition is not available on this device")
            return
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(wakeWordListener)
        }
    }

    private fun buildRecognizerIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            // Keep listening a little longer for trailing commands
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                1500L
            )
        }

    private fun beginListening() {
        try {
            isListening = true
            speechRecognizer?.startListening(buildRecognizerIntent())
            Log.d(TAG, "Listening for wake word...")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening", e)
            isListening = false
            scheduleRestart()
        }
    }

    private fun scheduleRestart() {
        if (!shouldRestart) return
        mainHandler.postDelayed({
            if (shouldRestart) {
                createRecognizerIfNeeded()
                beginListening()
            }
        }, RESTART_DELAY_MS)
    }

    /**
     * Checks whether [text] contains the wake word. If it does, returns the
     * portion of the string *after* the wake word (may be empty / blank).
     * Returns `null` when the wake word is **not** found.
     */
    private fun extractCommandAfterWakeWord(text: String): String? {
        val lower = text.lowercase().trim()
        for (phrase in WAKE_PHRASES) {
            val idx = lower.indexOf(phrase)
            if (idx != -1) {
                val afterWake = text.substring(idx + phrase.length).trim()
                    .removePrefix(",").removePrefix(".").trim()
                return afterWake
            }
        }
        return null
    }

    // ── RecognitionListener ─────────────────────────────────────────────

    private val wakeWordListener = object : RecognitionListener {

        override fun onReadyForSpeech(params: Bundle?) {
            Log.v(TAG, "onReadyForSpeech")
        }

        override fun onBeginningOfSpeech() {
            Log.v(TAG, "onBeginningOfSpeech")
        }

        override fun onRmsChanged(rmsdB: Float) {
            // no-op
        }

        override fun onBufferReceived(buffer: ByteArray?) {
            // no-op
        }

        override fun onEndOfSpeech() {
            Log.v(TAG, "onEndOfSpeech")
            isListening = false
        }

        override fun onError(error: Int) {
            isListening = false
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    // Expected during silence — just restart
                    Log.d(TAG, "No speech / timeout (error=$error), restarting...")
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    Log.w(TAG, "Recognizer busy, will retry")
                }
                SpeechRecognizer.ERROR_CLIENT -> {
                    Log.w(TAG, "Client error, recreating recognizer")
                    speechRecognizer?.destroy()
                    speechRecognizer = null
                }
                else -> {
                    Log.w(TAG, "SpeechRecognizer error: $error")
                }
            }
            scheduleRestart()
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            val matches = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                .orEmpty()

            Log.d(TAG, "onResults: $matches")

            for (match in matches) {
                val trailing = extractCommandAfterWakeWord(match)
                if (trailing != null) {
                    Log.i(TAG, "Wake word detected in: \"$match\"")
                    val command = trailing.ifBlank { null }
                    onWakeWordDetected(command)
                    return // don't auto-restart; the service takes over
                }
            }

            // Wake word not found — keep listening
            scheduleRestart()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            // We could react to partials for faster detection, but for
            // reliability we wait for final results.
        }

        override fun onEvent(eventType: Int, params: Bundle?) {
            // no-op
        }
    }
}
