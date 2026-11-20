package com.alfred.assistant.commands

/**
 * Parses raw spoken text into a structured [ParsedCommand].
 * Uses keyword matching with flexibility for natural language variations.
 */
object CommandParser {

    /**
     * Parses the given spoken [text] and returns the corresponding [ParsedCommand].
     * The input is normalized to lowercase and trimmed before matching.
     */
    fun parse(text: String): ParsedCommand {
        val input = text.trim().lowercase()

        return when {
            // --- Selfie (check before generic "open camera") ---
            matchesSelfie(input) -> ParsedCommand.TakeSelfie

            // --- YouTube search (must be checked before generic "open" and "search") ---
            matchesYouTubeSearch(input) -> parseYouTubeSearch(input)

            // --- Send message ---
            matchesSendMessage(input) -> parseSendMessage(input)

            // --- Call contact ---
            matchesCall(input) -> parseCallContact(input)

            // --- Open app ---
            matchesOpenApp(input) -> parseOpenApp(input)

            // --- Web search ---
            matchesWebSearch(input) -> parseWebSearch(input)

            // --- Play music ---
            matchesPlayMusic(input) -> ParsedCommand.PlayMusic

            // --- Set alarm ---
            matchesSetAlarm(input) -> parseSetAlarm(input)

            // --- Flashlight ---
            matchesFlashlight(input) -> parseFlashlight(input)

            // --- Check time ---
            matchesCheckTime(input) -> ParsedCommand.CheckTime

            // --- Check date ---
            matchesCheckDate(input) -> ParsedCommand.CheckDate

            // --- Battery status ---
            matchesBatteryStatus(input) -> ParsedCommand.BatteryStatus

            // --- Unknown ---
            else -> ParsedCommand.Unknown(text)
        }
    }

    // ── Selfie ──────────────────────────────────────────────────────────────

    private fun matchesSelfie(input: String): Boolean {
        val patterns = listOf(
            "take a selfie", "take selfie", "click a selfie", "click selfie",
            "take a photo", "take photo", "take a picture", "take picture",
            "click a photo", "click photo", "click a picture", "click picture",
            "selfie", "capture selfie", "capture a selfie",
            "take my photo", "take my picture", "click my photo", "click my picture"
        )
        return patterns.any { input.contains(it) }
    }

    // ── YouTube search ──────────────────────────────────────────────────────

    private fun matchesYouTubeSearch(input: String): Boolean {
        return input.contains("youtube") && input.contains("search")
    }

    private fun parseYouTubeSearch(input: String): ParsedCommand {
        // Patterns: "open youtube and search for X", "youtube search X", "search youtube for X"
        val patterns = listOf(
            Regex("""(?:open\s+)?youtube\s+(?:and\s+)?search\s+(?:for\s+)?(.+)"""),
            Regex("""search\s+(?:on\s+)?youtube\s+(?:for\s+)?(.+)"""),
            Regex("""search\s+youtube\s+(.+)""")
        )
        for (pattern in patterns) {
            val match = pattern.find(input)
            if (match != null) {
                return ParsedCommand.SearchYouTube(match.groupValues[1].trim())
            }
        }
        return ParsedCommand.SearchYouTube(input.substringAfter("search").trim())
    }

    // ── Send message ────────────────────────────────────────────────────────

    private fun matchesSendMessage(input: String): Boolean {
        val prefixes = listOf(
            "send message to", "send a message to", "send text to",
            "send a text to", "text to", "message to", "sms to",
            "send sms to", "send a sms to"
        )
        return prefixes.any { input.startsWith(it) }
    }

    private fun parseSendMessage(input: String): ParsedCommand {
        // Pattern: "send message to [contact] [message]"
        // We need to split contact from message. Heuristic: look for common separators
        val prefixes = listOf(
            "send a message to", "send message to", "send a text to",
            "send text to", "send a sms to", "send sms to",
            "text to", "message to", "sms to"
        )
        var remaining = input
        for (prefix in prefixes) {
            if (input.startsWith(prefix)) {
                remaining = input.removePrefix(prefix).trim()
                break
            }
        }

        // Try to split on common delimiters: "saying", "that", "with message"
        val delimiters = listOf(" saying ", " that ", " with message ", " message ")
        for (delimiter in delimiters) {
            val idx = remaining.indexOf(delimiter)
            if (idx > 0) {
                val contact = remaining.substring(0, idx).trim()
                val message = remaining.substring(idx + delimiter.length).trim()
                return ParsedCommand.SendMessage(contact, message)
            }
        }

        // Fallback: first word(s) up to a natural break
        // If no delimiter found, assume everything after the first couple of words is the message
        val words = remaining.split(" ")
        return if (words.size >= 2) {
            // Try to guess: contact is usually 1–2 words, rest is message
            val contact = words.take(1).joinToString(" ")
            val message = words.drop(1).joinToString(" ")
            ParsedCommand.SendMessage(contact, message)
        } else {
            ParsedCommand.SendMessage(remaining, "")
        }
    }

    // ── Call contact ────────────────────────────────────────────────────────

    private fun matchesCall(input: String): Boolean {
        val prefixes = listOf(
            "call ", "phone ", "dial ", "ring ",
            "make a call to ", "make call to ", "call up "
        )
        return prefixes.any { input.startsWith(it) }
    }

    private fun parseCallContact(input: String): ParsedCommand {
        val prefixes = listOf(
            "make a call to ", "make call to ", "call up ",
            "call ", "phone ", "dial ", "ring "
        )
        for (prefix in prefixes) {
            if (input.startsWith(prefix)) {
                return ParsedCommand.CallContact(input.removePrefix(prefix).trim())
            }
        }
        return ParsedCommand.CallContact(input.substringAfter(" ").trim())
    }

    // ── Open app ────────────────────────────────────────────────────────────

    private fun matchesOpenApp(input: String): Boolean {
        val prefixes = listOf(
            "open ", "launch ", "start ", "run ", "go to "
        )
        return prefixes.any { input.startsWith(it) }
    }

    private fun parseOpenApp(input: String): ParsedCommand {
        val prefixes = listOf(
            "open ", "launch ", "start ", "run ", "go to "
        )
        for (prefix in prefixes) {
            if (input.startsWith(prefix)) {
                return ParsedCommand.OpenApp(input.removePrefix(prefix).trim())
            }
        }
        return ParsedCommand.OpenApp(input.substringAfter(" ").trim())
    }

    // ── Web search ──────────────────────────────────────────────────────────

    private fun matchesWebSearch(input: String): Boolean {
        val prefixes = listOf(
            "search for ", "search ", "google ", "look up ", "look for ",
            "find ", "web search ", "search the web for "
        )
        return prefixes.any { input.startsWith(it) }
    }

    private fun parseWebSearch(input: String): ParsedCommand {
        val prefixes = listOf(
            "search the web for ", "web search ",
            "search for ", "search ", "google ",
            "look up ", "look for ", "find "
        )
        for (prefix in prefixes) {
            if (input.startsWith(prefix)) {
                return ParsedCommand.WebSearch(input.removePrefix(prefix).trim())
            }
        }
        return ParsedCommand.WebSearch(input.substringAfter(" ").trim())
    }

    // ── Play music ──────────────────────────────────────────────────────────

    private fun matchesPlayMusic(input: String): Boolean {
        val patterns = listOf(
            "play music", "play some music", "play songs", "play some songs",
            "start music", "music please", "play tunes", "start playing music"
        )
        return patterns.any { input.contains(it) }
    }

    // ── Set alarm ───────────────────────────────────────────────────────────

    private fun matchesSetAlarm(input: String): Boolean {
        return input.contains("set alarm") || input.contains("set an alarm") ||
                input.contains("alarm for") || input.contains("wake me up")
    }

    private fun parseSetAlarm(input: String): ParsedCommand {
        // Try to extract time from various formats

        // Pattern: "7:30 am", "7:30am", "7 30 am"
        val timeWithColon = Regex("""(\d{1,2})[:\s](\d{2})\s*(am|pm|a\.m\.|p\.m\.)?""")
        val matchColon = timeWithColon.find(input)
        if (matchColon != null) {
            var hour = matchColon.groupValues[1].toInt()
            val minute = matchColon.groupValues[2].toInt()
            val period = matchColon.groupValues[3].lowercase().replace(".", "")

            if (period == "pm" && hour < 12) hour += 12
            if (period == "am" && hour == 12) hour = 0

            return ParsedCommand.SetAlarm(hour, minute)
        }

        // Pattern: "7 am", "7am", "7 pm"
        val hourOnly = Regex("""(\d{1,2})\s*(am|pm|a\.m\.|p\.m\.)""")
        val matchHour = hourOnly.find(input)
        if (matchHour != null) {
            var hour = matchHour.groupValues[1].toInt()
            val period = matchHour.groupValues[2].lowercase().replace(".", "")

            if (period == "pm" && hour < 12) hour += 12
            if (period == "am" && hour == 12) hour = 0

            return ParsedCommand.SetAlarm(hour, 0)
        }

        // Pattern: just a number like "set alarm 7"
        val justNumber = Regex("""(\d{1,2})""")
        val matchNumber = justNumber.find(input)
        if (matchNumber != null) {
            val hour = matchNumber.groupValues[1].toInt()
            return ParsedCommand.SetAlarm(hour, 0)
        }

        // Default: 7:00 AM
        return ParsedCommand.SetAlarm(7, 0)
    }

    // ── Flashlight ──────────────────────────────────────────────────────────

    private fun matchesFlashlight(input: String): Boolean {
        val keywords = listOf("flashlight", "torch", "flash light")
        return keywords.any { input.contains(it) }
    }

    private fun parseFlashlight(input: String): ParsedCommand {
        val turnOff = listOf("off", "disable", "stop", "close", "kill")
        val shouldTurnOff = turnOff.any { input.contains(it) }
        return ParsedCommand.Flashlight(turnOn = !shouldTurnOff)
    }

    // ── Check time ──────────────────────────────────────────────────────────

    private fun matchesCheckTime(input: String): Boolean {
        val patterns = listOf(
            "what time is it", "what's the time", "whats the time",
            "current time", "tell me the time", "what is the time",
            "time please", "time right now"
        )
        return patterns.any { input.contains(it) }
    }

    // ── Check date ──────────────────────────────────────────────────────────

    private fun matchesCheckDate(input: String): Boolean {
        val patterns = listOf(
            "what's the date", "whats the date", "what date is it",
            "current date", "tell me the date", "what is the date",
            "today's date", "todays date", "what day is it", "what day is today"
        )
        return patterns.any { input.contains(it) }
    }

    // ── Battery status ──────────────────────────────────────────────────────

    private fun matchesBatteryStatus(input: String): Boolean {
        val patterns = listOf(
            "battery status", "battery level", "battery percentage",
            "how much battery", "battery left", "check battery",
            "what's the battery", "whats the battery", "battery charge"
        )
        return patterns.any { input.contains(it) }
    }
}

/**
 * Sealed class hierarchy representing all commands Alfred can understand.
 */
sealed class ParsedCommand {
    /** Call a contact by name. */
    data class CallContact(val contactName: String) : ParsedCommand()

    /** Open an installed app by name. */
    data class OpenApp(val appName: String) : ParsedCommand()

    /** Open YouTube and search for a query. */
    data class SearchYouTube(val query: String) : ParsedCommand()

    /** Perform a web search. */
    data class WebSearch(val query: String) : ParsedCommand()

    /** Take a selfie with the front camera. */
    data object TakeSelfie : ParsedCommand()

    /** Send a message to a contact. */
    data class SendMessage(val contact: String, val message: String) : ParsedCommand()

    /** Play music on the default music player. */
    data object PlayMusic : ParsedCommand()

    /** Set an alarm for the given time. */
    data class SetAlarm(val hour: Int, val minute: Int) : ParsedCommand()

    /** Toggle the flashlight on or off. */
    data class Flashlight(val turnOn: Boolean) : ParsedCommand()

    /** Check the current time. */
    data object CheckTime : ParsedCommand()

    /** Check the current date. */
    data object CheckDate : ParsedCommand()

    /** Check the battery level. */
    data object BatteryStatus : ParsedCommand()

    /** Unrecognized command. */
    data class Unknown(val rawText: String) : ParsedCommand()
}
