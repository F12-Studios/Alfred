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
 * Captures a **single utterance** after the wake word has been detected.
 *
 * The recognized text is delivered via [onCommandRecognized].
 * If no speech is detected within ~5 seconds of silence the callback is
 * invoked with an empty string so the caller can resume wake-word listening.
 *
 * **Threading:** Must be created and operated on the main (UI) thread.
 * All public methods post to the main [Handler] for safety.
 */
class CommandRecognizer(
    private val context: Context,
    private val onCommandRecognized: (text: String) -> Unit
) {

    companion object {
        private const val TAG = "CommandRecognizer"
        private const val SILENCE_TIMEOUT_MS = 5_000L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    /** Timeout runnable — fires if no result arrives in time. */
    private val timeoutRunnable = Runnable {
        Log.d(TAG, "Command capture timed out")
        cancelAndDeliver("")
    }

    // ── Public API ──────────────────────────────────────────────────────

    /** Start listening for a single command utterance. */
    fun startListening() {
        mainHandler.post {
            if (isListening) {
                Log.w(TAG, "Already listening, ignoring duplicate start")
                return@post
            }
            createRecognizerIfNeeded()
            beginListening()
        }
    }

    /** Stop listening without delivering a result. */
    fun stop() {
        mainHandler.post {
            isListening = false
            mainHandler.removeCallbacks(timeoutRunnable)
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping recognizer", e)
            }
        }
    }

    /** Release all resources permanently. */
    fun destroy() {
        mainHandler.post {
            isListening = false
            mainHandler.removeCallbacks(timeoutRunnable)
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
            it.setRecognitionListener(commandListener)
        }
    }

    private fun buildRecognizerIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Allow up to 5 s of silence before auto-stopping
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                SILENCE_TIMEOUT_MS
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                3_000L
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
                1_000L
            )
        }

    private fun beginListening() {
        try {
            isListening = true
            speechRecognizer?.startListening(buildRecognizerIntent())
            // Safety-net timeout in case the recognizer hangs
            mainHandler.postDelayed(timeoutRunnable, SILENCE_TIMEOUT_MS + 2_000L)
            Log.d(TAG, "Listening for command...")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start command listening", e)
            isListening = false
            onCommandRecognized("")
        }
    }

    private fun cancelAndDeliver(text: String) {
        mainHandler.removeCallbacks(timeoutRunnable)
        isListening = false
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
        } catch (_: Exception) {
        }
        onCommandRecognized(text)
    }

    // ── RecognitionListener ─────────────────────────────────────────────

    private val commandListener = object : RecognitionListener {

        override fun onReadyForSpeech(params: Bundle?) {
            Log.v(TAG, "onReadyForSpeech — awaiting command")
        }

        override fun onBeginningOfSpeech() {
            Log.v(TAG, "onBeginningOfSpeech")
            // User started speaking — reset the timeout
            mainHandler.removeCallbacks(timeoutRunnable)
        }

        override fun onRmsChanged(rmsdB: Float) {
            // no-op
        }

        override fun onBufferReceived(buffer: ByteArray?) {
            // no-op
        }

        override fun onEndOfSpeech() {
            Log.v(TAG, "onEndOfSpeech")
        }

        override fun onError(error: Int) {
            isListening = false
            mainHandler.removeCallbacks(timeoutRunnable)

            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    Log.d(TAG, "No command heard (error=$error)")
                    onCommandRecognized("")
                }
                else -> {
                    Log.w(TAG, "SpeechRecognizer error during command capture: $error")
                    onCommandRecognized("")
                }
            }
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            mainHandler.removeCallbacks(timeoutRunnable)

            val matches = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                .orEmpty()

            val bestMatch = matches.firstOrNull().orEmpty().trim()
            Log.i(TAG, "Command captured: \"$bestMatch\"")
            onCommandRecognized(bestMatch)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            // We wait for final results for accuracy
        }

        override fun onEvent(eventType: Int, params: Bundle?) {
            // no-op
        }
    }
}
