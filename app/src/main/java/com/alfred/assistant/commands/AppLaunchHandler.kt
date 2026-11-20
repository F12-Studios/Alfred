package com.alfred.assistant.commands

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.util.Log

/**
 * Handles launching installed applications dynamically by name.
 * Uses queryIntentActivities (Android launcher category) and getInstalledApplications
 * with intelligent token and fuzzy matching.
 */
object AppLaunchHandler {

    private const val TAG = "AppLaunchHandler"

    private data class AppCandidate(
        val label: String,
        val packageName: String,
        val cleanLabel: String,
        val words: List<String>
    )

    // Common abbreviations and aliases
    private val ALIASES = mapOf(
        "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
        "duolingo" to listOf("com.duolingo"),
        "chess" to listOf("com.chess", "jwtc.android.chess"),
        "yt" to listOf("com.google.android.youtube"),
        "youtube" to listOf("com.google.android.youtube"),
        "insta" to listOf("com.instagram.android"),
        "instagram" to listOf("com.instagram.android"),
        "fb" to listOf("com.facebook.katana"),
        "facebook" to listOf("com.facebook.katana"),
        "spotify" to listOf("com.spotify.music"),
        "chrome" to listOf("com.android.chrome"),
        "gmail" to listOf("com.google.android.gm"),
        "maps" to listOf("com.google.android.apps.maps"),
        "camera" to listOf("com.android.camera2", "com.google.android.GoogleCamera"),
        "settings" to listOf("com.android.settings"),
        "calculator" to listOf("com.google.android.calculator"),
        "clock" to listOf("com.google.android.deskclock"),
        "calendar" to listOf("com.google.android.calendar"),
        "messages" to listOf("com.google.android.apps.messaging"),
        "phone" to listOf("com.google.android.dialer"),
        "photos" to listOf("com.google.android.apps.photos"),
        "twitter" to listOf("com.twitter.android"),
        "x" to listOf("com.twitter.android"),
        "telegram" to listOf("org.telegram.messenger"),
        "snapchat" to listOf("com.snapchat.android"),
        "netflix" to listOf("com.netflix.mediaclient"),
        "amazon" to listOf("com.amazon.mShop.android.shopping"),
        "play store" to listOf("com.android.vending")
    )

    /**
     * Attempts to find and launch an installed app matching [appName].
     */
    fun launch(context: Context, appName: String): String {
        val query = cleanSearchQuery(appName)
        if (query.isBlank()) {
            return "Which app would you like me to open, sir?"
        }

        val pm = context.packageManager

        // 1. Check known aliases first for lightning fast response
        val aliasPackages = ALIASES[query]
        if (aliasPackages != null) {
            for (pkg in aliasPackages) {
                val intent = pm.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    val label = try {
                        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                    } catch (_: Exception) {
                        appName
                    }
                    return "Opening $label for you, sir."
                }
            }
        }

        // 2. Dynamically collect all installed apps from the device
        val candidates = collectInstalledApps(pm)
        if (candidates.isEmpty()) {
            Log.w(TAG, "No launchable apps discovered by PackageManager")
        }

        // 3. Score all candidates against user query
        var bestApp: AppCandidate? = null
        var highestScore = -1

        val cleanQuery = query.replace(Regex("[^a-z0-9]"), "")
        val queryWords = query.split(Regex("\\s+")).filter { it.isNotBlank() }

        for (candidate in candidates) {
            val score = calculateMatchScore(candidate, query, cleanQuery, queryWords)
            if (score > highestScore) {
                highestScore = score
                bestApp = candidate
            }
        }

        if (bestApp != null && highestScore >= 50) {
            return try {
                val launchIntent = pm.getLaunchIntentForPackage(bestApp.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    Log.d(TAG, "Launched '${bestApp.label}' (${bestApp.packageName}) with score $highestScore")
                    "Opening ${bestApp.label} for you, sir."
                } else {
                    "I located ${bestApp.label}, sir, but it cannot be opened directly."
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error launching package ${bestApp.packageName}", e)
                "I encountered a difficulty opening ${bestApp.label}, sir: ${e.message}"
            }
        }

        return "I'm afraid I couldn't find an app called \"$appName\" on this device, sir."
    }

    /**
     * Cleans up common prefixes and suffixes in user queries.
     */
    private fun cleanSearchQuery(raw: String): String {
        var q = raw.lowercase().trim()
        val removePrefixes = listOf("the ", "my ")
        for (p in removePrefixes) {
            if (q.startsWith(p)) {
                q = q.removePrefix(p).trim()
            }
        }
        val removeSuffixes = listOf(" app", " application", " game")
        for (s in removeSuffixes) {
            if (q.endsWith(s)) {
                q = q.removeSuffix(s).trim()
            }
        }
        return q.trim()
    }

    private fun calculateMatchScore(
        app: AppCandidate,
        query: String,
        cleanQuery: String,
        queryWords: List<String>
    ): Int {
        val lowerLabel = app.label.lowercase()
        val cleanLabel = app.cleanLabel
        val lowerPkg = app.packageName.lowercase()

        // Exact match
        if (lowerLabel == query || cleanLabel == cleanQuery) {
            return 100
        }

        // App starts with query (e.g. "chess" matches "Chess - Play & Learn")
        if (lowerLabel.startsWith(query) || cleanLabel.startsWith(cleanQuery)) {
            return 95
        }

        // Exact word match (e.g. query "duolingo" in ["duolingo", "language", "lessons"])
        if (app.words.any { it == query } || queryWords.any { qWord -> app.words.contains(qWord) }) {
            return 85
        }

        // Any word in label starts with query
        if (app.words.any { it.startsWith(query) }) {
            return 80
        }

        // Label contains query substring
        if (lowerLabel.contains(query) || cleanLabel.contains(cleanQuery)) {
            return 70
        }

        // Package name contains query (e.g. "com.chess" contains "chess", "com.duolingo" contains "duolingo")
        if (lowerPkg.contains(query) || lowerPkg.contains(cleanQuery)) {
            return 60
        }

        return 0
    }

    /**
     * Collects all launchable apps dynamically on the device.
     */
    private fun collectInstalledApps(pm: PackageManager): List<AppCandidate> {
        val appMap = mutableMapOf<String, AppCandidate>()

        // 1. Query launcher intent activities (this gives standard user-facing apps)
        val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveList: List<ResolveInfo> = pm.queryIntentActivities(launcherIntent, 0)
        for (resolve in resolveList) {
            val pkg = resolve.activityInfo.packageName
            val label = resolve.loadLabel(pm).toString().trim()
            if (label.isNotBlank() && !appMap.containsKey(pkg)) {
                val clean = label.lowercase().replace(Regex("[^a-z0-9]"), "")
                val words = label.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }
                appMap[pkg] = AppCandidate(label, pkg, clean, words)
            }
        }

        // 2. Query all installed applications to catch any missed
        try {
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (appInfo in installedApps) {
                val pkg = appInfo.packageName
                if (!appMap.containsKey(pkg) && pm.getLaunchIntentForPackage(pkg) != null) {
                    val label = pm.getApplicationLabel(appInfo).toString().trim()
                    if (label.isNotBlank()) {
                        val clean = label.lowercase().replace(Regex("[^a-z0-9]"), "")
                        val words = label.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }
                        appMap[pkg] = AppCandidate(label, pkg, clean, words)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error querying installed applications", e)
        }

        return appMap.values.toList()
    }
}
