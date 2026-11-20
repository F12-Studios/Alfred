package com.alfred.assistant.commands

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Handles YouTube search and web search commands.
 */
object SearchHandler {

    /**
     * Opens YouTube with a search for the given [query].
     *
     * Attempts to use the YouTube app's deep link first.
     * Falls back to the YouTube website URL if the app isn't installed.
     *
     * @return A butler-style response string.
     */
    fun searchYouTube(context: Context, query: String): String {
        return try {
            val encodedQuery = Uri.encode(query)

            // Try YouTube app intent first
            val youtubeAppIntent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage("com.google.android.youtube")
                putExtra(SearchManager.QUERY, query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (youtubeAppIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(youtubeAppIntent)
            } else {
                // Fallback to browser with YouTube URL
                val webIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://www.youtube.com/results?search_query=$encodedQuery")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
            }

            "Searching YouTube for \"$query\", sir."
        } catch (e: Exception) {
            // Ultimate fallback: try the vnd.youtube scheme
            try {
                val encodedQuery = Uri.encode(query)
                val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://www.youtube.com/results?search_query=$encodedQuery")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
                "Searching YouTube for \"$query\", sir."
            } catch (e2: Exception) {
                "I'm afraid I was unable to search YouTube, sir: ${e2.message}"
            }
        }
    }

    /**
     * Performs a web search for the given [query].
     *
     * Uses [Intent.ACTION_WEB_SEARCH] with [SearchManager.QUERY] extra,
     * which opens the default browser or search app.
     *
     * @return A butler-style response string.
     */
    fun webSearch(context: Context, query: String): String {
        return try {
            val searchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra(SearchManager.QUERY, query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(searchIntent)
            "Searching the web for \"$query\", sir."
        } catch (e: Exception) {
            // Fallback: open browser directly with Google search
            try {
                val encodedQuery = Uri.encode(query)
                val browserIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://www.google.com/search?q=$encodedQuery")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
                "Searching the web for \"$query\", sir."
            } catch (e2: Exception) {
                "I'm terribly sorry, sir. I was unable to perform the web search: ${e2.message}"
            }
        }
    }
}
