package com.alfred.assistant.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.UUID

/**
 * Alfred's voice — a British-accented butler who responds with dignity and wit.
 *
 * Wraps Android [TextToSpeech] with Locale.UK for a proper British accent.
 * Provides butler-style prefixes for a touch of class.
 */
class AlfredSpeaker(context: Context) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "AlfredSpeaker"

        /**
         * Butler-style prefixes that Alfred randomly selects from
         * when speaking with [butlerSpeak].
         */
        private val BUTLER_PREFIXES = listOf(
            "Right away, sir.",
            "As you wish.",
            "Certainly.",
            "Very well.",
            "Consider it done.",
            "At once, sir.",
            "Of course, sir.",
            "Straightaway, sir.",
            "With pleasure, sir.",
            "Indeed, sir.",
            "As you command, sir.",
            "Absolutely, sir."
        )
    }

    private var tts: TextToSpeech? = null
    private var isInitialized: Boolean = false
    private var pendingCallback: (() -> Unit)? = null

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    /**
     * Called when the TTS engine is initialized.
     * Sets the language to British English (Locale.UK).
     */
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.UK)
            isInitialized = when (result) {
                TextToSpeech.LANG_MISSING_DATA,
                TextToSpeech.LANG_NOT_SUPPORTED -> {
                    Log.w(TAG, "British English not supported, falling back to default")
                    // Try US English as fallback
                    tts?.setLanguage(Locale.US)
                    true
                }
                else -> {
                    Log.d(TAG, "Alfred's voice initialized with British English")
                    true
                }
            }

            // Configure voice properties for a distinguished butler
            tts?.setPitch(0.9f)       // Slightly lower pitch for gravitas
            tts?.setSpeechRate(0.95f)  // Slightly slower for clarity and poise

            // Set up the utterance listener
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d(TAG, "Speaking: $utteranceId")
                }

                override fun onDone(utteranceId: String?) {
                    Log.d(TAG, "Finished speaking: $utteranceId")
                    pendingCallback?.invoke()
                    pendingCallback = null
                }

                @Deprecated("Deprecated in API level 21")
                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "TTS error for: $utteranceId")
                    pendingCallback?.invoke()
                    pendingCallback = null
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    Log.e(TAG, "TTS error for: $utteranceId, code: $errorCode")
                    pendingCallback?.invoke()
                    pendingCallback = null
                }
            })
        } else {
            Log.e(TAG, "TTS initialization failed with status: $status")
            isInitialized = false
        }
    }

    /**
     * Speaks the given [text] using TextToSpeech.
     * Optional [onDone] callback invoked when speaking finishes.
     */
    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (!isInitialized) {
            Log.w(TAG, "TTS not initialized, cannot speak: $text")
            onDone?.invoke()
            return
        }

        pendingCallback = onDone
        val utteranceId = UUID.randomUUID().toString()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    /**
     * Speaks the given [text] and invokes [onDone] when finished.
     * Useful for chaining actions after Alfred finishes speaking.
     */
    fun speakWithCallback(text: String, onDone: () -> Unit) {
        speak(text, onDone)
    }

    /**
     * Speaks with a random butler-style prefix followed by the [text].
     *
     * Example output: "Right away, sir. Opening YouTube for you."
     */
    fun butlerSpeak(text: String) {
        val prefix = BUTLER_PREFIXES.random()
        speak("$prefix $text")
    }

    /**
     * Speaks with a random butler-style prefix, then invokes [onDone] when complete.
     */
    fun butlerSpeakWithCallback(text: String, onDone: () -> Unit) {
        val prefix = BUTLER_PREFIXES.random()
        speakWithCallback("$prefix $text", onDone)
    }

    /**
     * Returns a random butler prefix string (without speaking it).
     * Useful when the response already contains the prefix.
     */
    fun getButlerPrefix(): String = BUTLER_PREFIXES.random()

    /**
     * Returns whether the TTS engine is currently speaking.
     */
    fun isSpeaking(): Boolean = tts?.isSpeaking == true

    /**
     * Stops any ongoing speech immediately.
     */
    fun stop() {
        tts?.stop()
        pendingCallback = null
    }

    /**
     * Releases the TTS engine and frees resources.
     * Call this when Alfred is no longer needed (e.g., service destroyed).
     */
    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        Log.d(TAG, "Alfred's voice has been shut down")
    }
}
