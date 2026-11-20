package com.alfred.assistant.commands

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Intelligent AI Assistant Handler.
 *
 * Provides conversational AI answers to any question (like Siri / Gemini / Google Assistant).
 *
 * 1. Checks for user-configured Google Gemini API Key in SharedPreferences.
 * 2. If Gemini key is set: queries Gemini 1.5/2.0 Flash for comprehensive, context-aware answers.
 * 3. If no key is set or offline: falls back to Wikipedia Summary API + DuckDuckGo Instant Answer API
 *    and local assistant conversational rules (100% free, zero configuration needed).
 */
object AiAssistantHandler {

    private const val TAG = "AiAssistantHandler"
    private const val PREFS_NAME = "alfred_prefs"
    const val PREF_GEMINI_API_KEY = "gemini_api_key"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val BUTLER_AI_PREFIXES = listOf(
        "Allow me to explain, sir.",
        "According to my understanding, sir.",
        "Indeed, sir.",
        "Here is what I've found for you, sir.",
        "Right away, sir."
    )

    /**
     * Answers any natural-language question or prompt.
     */
    suspend fun answerQuestion(context: Context, query: String): String = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            return@withContext "I am listening, sir. What would you like to know?"
        }

        // 1. Quick local conversational checks
        val localResponse = checkLocalResponses(cleanQuery)
        if (localResponse != null) {
            return@withContext localResponse
        }

        // 2. Check for configured Gemini API key
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val geminiKey = prefs.getString(PREF_GEMINI_API_KEY, null)?.trim()

        if (!geminiKey.isNullOrBlank()) {
            val geminiAnswer = queryGeminiApi(geminiKey, cleanQuery)
            if (geminiAnswer != null) {
                return@withContext geminiAnswer
            }
        }

        // 3. Fallback: Wikipedia Summary API (Free, no key needed)
        val wikiAnswer = queryWikipedia(cleanQuery)
        if (wikiAnswer != null) {
            val prefix = BUTLER_AI_PREFIXES.random()
            return@withContext "$prefix $wikiAnswer"
        }

        // 4. Fallback: DuckDuckGo Instant Answer API (Free, no key needed)
        val ddgAnswer = queryDuckDuckGo(cleanQuery)
        if (ddgAnswer != null) {
            val prefix = BUTLER_AI_PREFIXES.random()
            return@withContext "$prefix $ddgAnswer"
        }

        // 5. Final fallback when no key is configured
        if (geminiKey.isNullOrBlank()) {
            "I have noted your inquiry regarding \"$cleanQuery\", sir. To enable me to answer any question with full conversational intelligence like Gemini or Siri, please add your free Gemini API key in the app settings."
        } else {
            "I'm terribly sorry, sir, but I was unable to retrieve a satisfactory answer for that at this moment. Would you like me to search the web instead?"
        }
    }

    /**
     * Queries Google Gemini Generative Language API.
     */
    private fun queryGeminiApi(apiKey: String, prompt: String): String? {
        val models = listOf("gemini-1.5-flash", "gemini-2.0-flash")

        for (model in models) {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

                val systemInstruction =
                    "You are Alfred, a loyal, highly intelligent, witty, and distinguished British butler voice assistant. " +
                            "Answer questions directly, accurately, and concisely (2 to 3 sentences maximum). " +
                            "Do NOT use markdown, asterisks, hash signs, bullet points, or emojis, as your response will be read aloud via text-to-speech. " +
                            "Speak with gentlemanly butler poise."

                val jsonBody = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", "$systemInstruction\n\nQuestion from user: $prompt")
                                })
                            })
                        })
                    })
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.7)
                        put("maxOutputTokens", 200)
                    })
                }

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val requestBody = jsonBody.toString().toRequestBody(mediaType)

                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

                var parsedResult: String? = null
                val response = httpClient.newCall(request).execute()
                try {
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string()
                        if (!bodyString.isNullOrBlank()) {
                            val json = JSONObject(bodyString)
                            val candidates = json.optJSONArray("candidates")
                            if (candidates != null && candidates.length() > 0) {
                                val firstCandidate = candidates.getJSONObject(0)
                                val content = firstCandidate.optJSONObject("content")
                                val parts = content?.optJSONArray("parts")
                                if (parts != null && parts.length() > 0) {
                                    val text = parts.getJSONObject(0).optString("text")
                                    if (text.isNotBlank()) {
                                        parsedResult = sanitizeForSpeech(text)
                                    }
                                }
                            }
                        }
                    } else {
                        Log.w(TAG, "Gemini call to $model failed with code ${response.code}: ${response.message}")
                    }
                } finally {
                    response.close()
                }

                if (parsedResult != null) {
                    return parsedResult
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error querying Gemini model $model", e)
            }
        }
        return null
    }

    /**
     * Queries Wikipedia REST API for concise page summary.
     */
    private fun queryWikipedia(query: String): String? {
        return try {
            val terms = extractSearchTerms(query)
            val encodedTopic = Uri.encode(terms.replace(" ", "_"))
            val url = "https://en.wikipedia.org/api/rest_v1/page/summary/$encodedTopic"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "AlfredVoiceAssistant/1.0 (Android; contact@alfred.assistant)")
                .get()
                .build()

            var resultText: String? = null
            val response = httpClient.newCall(request).execute()
            try {
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        val json = JSONObject(body)
                        val extract = json.optString("extract")
                        if (extract.isNotBlank() && !extract.contains("may refer to:")) {
                            resultText = limitSentences(extract, 2)
                        }
                    }
                }
            } finally {
                response.close()
            }
            resultText
        } catch (e: Exception) {
            Log.d(TAG, "Wikipedia summary lookup failed", e)
            null
        }
    }

    /**
     * Queries DuckDuckGo Instant Answer API for definition / facts.
     */
    private fun queryDuckDuckGo(query: String): String? {
        return try {
            val encodedQuery = Uri.encode(query)
            val url = "https://api.duckduckgo.com/?q=$encodedQuery&format=json&no_html=1&skip_disambig=1"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "AlfredVoiceAssistant/1.0")
                .get()
                .build()

            var resultText: String? = null
            val response = httpClient.newCall(request).execute()
            try {
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        val json = JSONObject(body)
                        val abstractText = json.optString("AbstractText")
                        if (abstractText.isNotBlank()) {
                            resultText = limitSentences(abstractText, 2)
                        } else {
                            val answer = json.optString("Answer")
                            if (answer.isNotBlank()) {
                                resultText = sanitizeForSpeech(answer)
                            }
                        }
                    }
                }
            } finally {
                response.close()
            }
            resultText
        } catch (e: Exception) {
            Log.d(TAG, "DuckDuckGo instant answer lookup failed", e)
            null
        }
    }

    /**
     * Handles common conversational queries locally without network latency.
     */
    private fun checkLocalResponses(input: String): String? {
        val lower = input.lowercase().trim()

        return when {
            lower in listOf("who are you", "what are you", "what is your name", "who are you alfred") ->
                "I am Alfred, your distinguished personal butler and voice assistant, at your disposal, sir."

            lower in listOf("how are you", "how are you doing", "how do you do") ->
                "Splendid, sir, and in prime condition to serve you."

            lower in listOf("hello", "hi", "hey", "good morning", "good evening", "good afternoon") ->
                "Good day, sir. How may I be of service to you?"

            lower.contains("tell me a joke") || lower.contains("make me laugh") ->
                listOf(
                    "Why do we tell actors to break a leg, sir? Because every play has a cast.",
                    "Parallel lines have so much in common, sir. It is truly a shame they will never meet.",
                    "Why did the scarecrow win an award, sir? Because he was outstanding in his field.",
                    "I asked the librarian if the library had any books on paranoia, sir. She whispered: They're right behind you."
                ).random()

            lower in listOf("thank you", "thanks", "thank you alfred", "thanks alfred") ->
                "It is my absolute pleasure, sir."

            lower in listOf("who made you", "who created you") ->
                "I was created to be your loyal personal butler and voice assistant, sir."

            lower in listOf("what can you do", "help", "what are your features") ->
                "I can place phone calls, capture selfies, open apps, search YouTube, illuminate the torch, set alarms, and answer virtually any question you wish to pose, sir."

            else -> null
        }
    }

    private fun extractSearchTerms(query: String): String {
        var clean = query.lowercase().trim()
        val removePrefixes = listOf(
            "who is ", "who was ", "what is ", "what was ", "what are ",
            "tell me about ", "explain ", "define ", "where is ", "when was "
        )
        for (prefix in removePrefixes) {
            if (clean.startsWith(prefix)) {
                clean = clean.removePrefix(prefix).trim()
                break
            }
        }
        return clean.removeSuffix("?").trim()
    }

    private fun sanitizeForSpeech(text: String): String {
        return text
            .replace(Regex("[*#_`~]"), "") // Remove markdown asterisks, hashes, backticks
            .replace(Regex("\\[.*?\\]"), "") // Remove bracket citations like [1]
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun limitSentences(text: String, maxSentences: Int): String {
        val sentences = text.split(Regex("(?<=[.!?])\\s+"))
        return sentences.take(maxSentences).joinToString(" ").trim()
    }
}
