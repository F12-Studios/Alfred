package com.alfred.assistant.commands

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock

/**
 * Handles media-related commands: playing music and setting alarms.
 */
object MediaHandler {

    /**
     * Launches the default music player or Spotify to play music.
     *
     * Attempts the following in order:
     * 1. Launch Spotify if installed.
     * 2. Use [Intent.CATEGORY_APP_MUSIC] to open the default music app.
     * 3. Fall back to [Intent.ACTION_VIEW] with an audio MIME type.
     *
     * @return A butler-style response string.
     */
    fun playMusic(context: Context): String {
        // 1. Try Spotify first
        try {
            val spotifyIntent = context.packageManager.getLaunchIntentForPackage("com.spotify.music")
            if (spotifyIntent != null) {
                spotifyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(spotifyIntent)
                return "Opening Spotify for you, sir. Enjoy the music."
            }
        } catch (_: Exception) {
            // Spotify not available, continue
        }

        // 2. Try the default music app category
        try {
            val musicIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_MUSIC)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (musicIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(musicIntent)
                return "Opening your music player, sir. Shall I request anything in particular?"
            }
        } catch (_: Exception) {
            // Category not resolved, continue
        }

        // 3. Try YouTube Music
        try {
            val ytMusicIntent =
                context.packageManager.getLaunchIntentForPackage("com.google.android.apps.youtube.music")
            if (ytMusicIntent != null) {
                ytMusicIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(ytMusicIntent)
                return "Opening YouTube Music for you, sir."
            }
        } catch (_: Exception) {
            // Not available, continue
        }

        // 4. Fallback: try any audio intent
        try {
            val audioIntent = Intent(Intent.ACTION_VIEW).apply {
                type = "audio/*"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(audioIntent, "Play music with").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            return "Opening a music player for you, sir."
        } catch (_: Exception) {
            // Nothing worked
        }

        return "I'm afraid I couldn't find a music player on this device, sir. Perhaps you'd like to install one?"
    }

    /**
     * Sets an alarm for the specified [hour] and [minute] using [AlarmClock.ACTION_SET_ALARM].
     *
     * This uses the system alarm clock and doesn't require additional permissions.
     *
     * @param hour The hour of the alarm (0–23).
     * @param minute The minute of the alarm (0–59).
     * @return A butler-style response string.
     */
    fun setAlarm(context: Context, hour: Int, minute: Int): String {
        return try {
            val formattedTime = formatTime(hour, minute)

            val alarmIntent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false) // Show the alarm UI for confirmation
                putExtra(AlarmClock.EXTRA_MESSAGE, "Alfred Alarm")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (alarmIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(alarmIntent)
                "Setting an alarm for $formattedTime, sir. I shall ensure you're right on schedule."
            } else {
                "I'm afraid there's no alarm clock app available on this device, sir."
            }
        } catch (e: Exception) {
            "I encountered a difficulty setting the alarm, sir: ${e.message}"
        }
    }

    /**
     * Formats the given [hour] and [minute] into a human-readable 12-hour time string.
     */
    private fun formatTime(hour: Int, minute: Int): String {
        val period = if (hour < 12) "AM" else "PM"
        val displayHour = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return if (minute == 0) {
            "$displayHour $period"
        } else {
            "$displayHour:${minute.toString().padStart(2, '0')} $period"
        }
    }
}
