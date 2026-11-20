package com.alfred.assistant.commands

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Handles launching installed applications by name.
 * Uses a hardcoded map for common apps and falls back to fuzzy matching
 * against installed app labels.
 */
object AppLaunchHandler {

    /**
     * Map of common app names (lowercase) to their package names.
     */
    private val KNOWN_APPS = mapOf(
        "youtube" to "com.google.android.youtube",
        "whatsapp" to "com.whatsapp",
        "instagram" to "com.instagram.android",
        "spotify" to "com.spotify.music",
        "chrome" to "com.android.chrome",
        "gmail" to "com.google.android.gm",
        "maps" to "com.google.android.apps.maps",
        "google maps" to "com.google.android.apps.maps",
        "camera" to "com.android.camera2",
        "settings" to "com.android.settings",
        "calculator" to "com.google.android.calculator",
        "clock" to "com.google.android.deskclock",
        "calendar" to "com.google.android.calendar",
        "messages" to "com.google.android.apps.messaging",
        "phone" to "com.google.android.dialer",
        "dialer" to "com.google.android.dialer",
        "photos" to "com.google.android.apps.photos",
        "google photos" to "com.google.android.apps.photos",
        "twitter" to "com.twitter.android",
        "x" to "com.twitter.android",
        "facebook" to "com.facebook.katana",
        "telegram" to "org.telegram.messenger",
        "snapchat" to "com.snapchat.android",
        "netflix" to "com.netflix.mediaclient",
        "amazon" to "com.amazon.mShop.android.shopping",
        "amazon shopping" to "com.amazon.mShop.android.shopping",
        "google" to "com.google.android.googlequicksearchbox",
        "play store" to "com.android.vending",
        "google play" to "com.android.vending",
        "files" to "com.google.android.apps.nbu.files",
        "drive" to "com.google.android.apps.docs",
        "google drive" to "com.google.android.apps.docs",
        "keep" to "com.google.android.keep",
        "notes" to "com.google.android.keep",
        "contacts" to "com.google.android.contacts",
        "meet" to "com.google.android.apps.meetings",
        "google meet" to "com.google.android.apps.meetings",
        "zoom" to "us.zoom.videomeetings",
        "teams" to "com.microsoft.teams",
        "discord" to "com.discord",
        "reddit" to "com.reddit.frontpage",
        "linkedin" to "com.linkedin.android",
        "pinterest" to "com.pinterest",
        "tiktok" to "com.zhiliaoapp.musically",
        "uber" to "com.ubercab",
        "lyft" to "me.lyft.android"
    )

    /**
     * Attempts to launch the app matching the given [appName].
     *
     * 1. Checks the [KNOWN_APPS] map first.
     * 2. Falls back to fuzzy matching against installed app labels.
     *
     * @return A butler-style response string.
     */
    fun launch(context: Context, appName: String): String {
        val normalizedName = appName.lowercase().trim()

        // 1. Check known apps map
        val knownPackage = KNOWN_APPS[normalizedName]
        if (knownPackage != null) {
            return launchPackage(context, knownPackage, appName)
        }

        // 2. Try exact substring match in known apps keys
        val partialMatch = KNOWN_APPS.entries.find { (key, _) ->
            key.contains(normalizedName) || normalizedName.contains(key)
        }
        if (partialMatch != null) {
            return launchPackage(context, partialMatch.value, appName)
        }

        // 3. Fuzzy match against installed app labels
        val matchedPackage = fuzzyMatchInstalledApp(context, normalizedName)
        if (matchedPackage != null) {
            return launchPackage(context, matchedPackage, appName)
        }

        return "I'm afraid I couldn't find an app called \"$appName\" on this device, sir."
    }

    /**
     * Launches the app identified by [packageName].
     */
    private fun launchPackage(context: Context, packageName: String, appName: String): String {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                "Opening $appName for you, sir."
            } else {
                "I found $appName, sir, but it doesn't appear to have a launchable activity."
            }
        } catch (e: Exception) {
            "I encountered a difficulty while trying to open $appName, sir: ${e.message}"
        }
    }

    /**
     * Searches installed apps for one whose label matches the given [name].
     * Uses case-insensitive contains matching, with preference for exact matches.
     *
     * @return The package name of the best match, or null if none found.
     */
    private fun fuzzyMatchInstalledApp(context: Context, name: String): String? {
        val pm = context.packageManager
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        // First pass: exact label match (case-insensitive)
        for (appInfo in installedApps) {
            val label = pm.getApplicationLabel(appInfo).toString().lowercase()
            if (label == name) {
                return appInfo.packageName
            }
        }

        // Second pass: label contains the search name
        for (appInfo in installedApps) {
            val label = pm.getApplicationLabel(appInfo).toString().lowercase()
            if (label.contains(name)) {
                // Verify it has a launch intent
                if (pm.getLaunchIntentForPackage(appInfo.packageName) != null) {
                    return appInfo.packageName
                }
            }
        }

        // Third pass: search name contains the label (for short labels)
        for (appInfo in installedApps) {
            val label = pm.getApplicationLabel(appInfo).toString().lowercase()
            if (label.length >= 3 && name.contains(label)) {
                if (pm.getLaunchIntentForPackage(appInfo.packageName) != null) {
                    return appInfo.packageName
                }
            }
        }

        return null
    }
}
