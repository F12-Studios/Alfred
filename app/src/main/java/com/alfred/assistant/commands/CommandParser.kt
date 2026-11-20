package com.alfred.assistant.commands

/**
 * Parses raw spoken text into a structured [ParsedCommand].
 * Handles natural language variations with flexible regex and token matching.
 */
object CommandParser {

    /**
     * Parses the given spoken [text] and returns the corresponding [ParsedCommand].
     */
    fun parse(text: String): ParsedCommand {
        var input = text.trim().lowercase()

        // Clean out conversational filler and wake word remnants
        val stripPrefixes = listOf(
            "hey alfred,", "hey alfred", "alfred,", "alfred",
            "please alfred", "please ", "can you ", "could you ", "will you "
        )
        for (p in stripPrefixes) {
            if (input.startsWith(p)) {
                input = input.removePrefix(p).trim().removePrefix(",").trim()
            }
        }

        return when {
            // --- Selfie ---
            matchesSelfie(input) -> ParsedCommand.TakeSelfie

            // --- WhatsApp (must check before generic open/message) ---
            matchesWhatsApp(input) -> parseWhatsApp(input)

            // --- YouTube search ---
            matchesYouTubeSearch(input) -> parseYouTubeSearch(input)

            // --- Send SMS / General Message ---
            matchesSendMessage(input) -> parseSendMessage(input)

            // --- Call contact ---
            matchesCall(input) -> parseCallContact(input)

            // --- Open app ---
            matchesOpenApp(input) -> parseOpenApp(input)

            // --- Flashlight ---
            matchesFlashlight(input) -> parseFlashlight(input)

            // --- Play music ---
            matchesPlayMusic(input) -> ParsedCommand.PlayMusic

            // --- Set alarm ---
            matchesSetAlarm(input) -> parseSetAlarm(input)

            // --- Check time ---
            matchesCheckTime(input) -> ParsedCommand.CheckTime

            // --- Check date ---
            matchesCheckDate(input) -> ParsedCommand.CheckDate

            // --- Battery status ---
            matchesBatteryStatus(input) -> ParsedCommand.BatteryStatus

            // --- Explicit Web search ---
            matchesExplicitWebSearch(input) -> parseWebSearch(input)

            // --- Conversational Assistant (Gemini / Siri style) ---
            else -> ParsedCommand.AskAi(text.trim())
        }
    }

    // ── WhatsApp ────────────────────────────────────────────────────────────

    private fun matchesWhatsApp(input: String): Boolean {
        return input.contains("whatsapp")
    }

    private fun parseWhatsApp(input: String): ParsedCommand {
        // Direct app launch check: "open whatsapp", "launch whatsapp"
        if (input in listOf("open whatsapp", "launch whatsapp", "start whatsapp", "whatsapp")) {
            return ParsedCommand.OpenApp("whatsapp")
        }

        val patterns = listOf(
            // "message Shriraj on whatsapp that he is noob"
            // "send message to Shriraj on whatsapp saying he is noob"
            // "send a message to Shriraj on whatsapp that he is noob"
            Regex("""^(?:send\s+(?:a\s+)?(?:text\s+|message\s+)?to\s+|message\s+|text\s+)?(.+?)\s+on\s+whatsapp\s+(?:that|saying|with\s+message|with\s+the\s+message|message)\s+(.+)$"""),

            // "message Shriraj on whatsapp he is noob"
            Regex("""^(?:send\s+(?:a\s+)?(?:text\s+|message\s+)?to\s+|message\s+|text\s+)?(.+?)\s+on\s+whatsapp\s+(.+)$"""),

            // "send a whatsapp to Shriraj that he is noob"
            // "send whatsapp message to Shriraj saying he is noob"
            // "whatsapp Shriraj that he is noob"
            Regex("""^(?:send\s+(?:a\s+)?whatsapp\s+(?:message\s+)?to\s+|whatsapp\s+)(.+?)\s+(?:that|saying|with\s+message|message)\s+(.+)$"""),

            // "whatsapp Shriraj he is noob"
            Regex("""^whatsapp\s+(.+?)\s+(.+)$""")
        )

        for (pattern in patterns) {
            val match = pattern.find(input)
            if (match != null) {
                val contact = match.groupValues[1].trim()
                val message = match.groupValues[2].trim()
                return ParsedCommand.SendWhatsApp(contact, message)
            }
        }

        // Fallback for WhatsApp query: extract recipient if possible
        val afterWhatsapp = input.substringAfter("whatsapp").trim()
        return if (afterWhatsapp.isNotBlank()) {
            ParsedCommand.SendWhatsApp(afterWhatsapp, "")
        } else {
            ParsedCommand.OpenApp("whatsapp")
        }
    }

    // ── Send SMS / General Message ──────────────────────────────────────────

    private fun matchesSendMessage(input: String): Boolean {
        val prefixes = listOf(
            "send message to", "send a message to", "send text to",
            "send a text to", "text to", "message to", "sms to",
            "send sms to", "send a sms to", "send message", "send text",
            "message ", "text "
        )
        return prefixes.any { input.startsWith(it) } &&
                (input.contains(" that ") || input.contains(" saying ") || input.contains(" to "))
    }

    private fun parseSendMessage(input: String): ParsedCommand {
        val patterns = listOf(
            // "message Shriraj that he is noob"
            // "send message to Shriraj saying hello"
            Regex("""^(?:send\s+(?:a\s+)?(?:text\s+|message\s+)?to\s+|message\s+|text\s+)(.+?)\s+(?:that|saying|with\s+message|with\s+the\s+message)\s+(.+)$"""),

            // "send text to Shriraj hello"
            Regex("""^(?:send\s+(?:a\s+)?(?:text|sms|message)\s+to\s+|text\s+to\s+|message\s+to\s+)(.+?)\s+(.+)$""")
        )

        for (pattern in patterns) {
            val match = pattern.find(input)
            if (match != null) {
                val contact = match.groupValues[1].trim()
                val message = match.groupValues[2].trim()
                return ParsedCommand.SendMessage(contact, message)
            }
        }

        // Fallback
        val words = input.split("\\s+".toRegex()).filter { it.isNotBlank() }
        return if (words.size >= 3) {
            val contact = words[words.size - 2]
            val message = words.last()
            ParsedCommand.SendMessage(contact, message)
        } else {
            ParsedCommand.SendMessage(input, "")
        }
    }

    // ── Open app ────────────────────────────────────────────────────────────

    private fun matchesOpenApp(input: String): Boolean {
        // Exclude music phrases
        if (matchesPlayMusic(input)) return false

        val prefixes = listOf(
            "open ", "launch ", "start ", "run ", "go to ", "play "
        )
        return prefixes.any { input.startsWith(it) }
    }

    private fun parseOpenApp(input: String): ParsedCommand {
        val prefixes = listOf(
            "open ", "launch ", "start ", "run ", "go to ", "play "
        )
        for (prefix in prefixes) {
            if (input.startsWith(prefix)) {
                return ParsedCommand.OpenApp(input.removePrefix(prefix).trim())
            }
        }
        return ParsedCommand.OpenApp(input.substringAfter(" ").trim())
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

    // ── Web search (explicit) ───────────────────────────────────────────────

    private fun matchesExplicitWebSearch(input: String): Boolean {
        val prefixes = listOf(
            "search the web for ", "search the web ", "web search ",
            "open browser and search ", "browse the web for "
        )
        return prefixes.any { input.startsWith(it) }
    }

    private fun parseWebSearch(input: String): ParsedCommand {
        val prefixes = listOf(
            "search the web for ", "search the web ", "web search ",
            "open browser and search ", "browse the web for "
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

        val hourOnly = Regex("""(\d{1,2})\s*(am|pm|a\.m\.|p\.m\.)""")
        val matchHour = hourOnly.find(input)
        if (matchHour != null) {
            var hour = matchHour.groupValues[1].toInt()
            val period = matchHour.groupValues[2].lowercase().replace(".", "")

            if (period == "pm" && hour < 12) hour += 12
            if (period == "am" && hour == 12) hour = 0

            return ParsedCommand.SetAlarm(hour, 0)
        }

        val justNumber = Regex("""(\d{1,2})""")
        val matchNumber = justNumber.find(input)
        if (matchNumber != null) {
            val hour = matchNumber.groupValues[1].toInt()
            return ParsedCommand.SetAlarm(hour, 0)
        }

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
    data class CallContact(val contactName: String) : ParsedCommand()
    data class OpenApp(val appName: String) : ParsedCommand()
    data class SearchYouTube(val query: String) : ParsedCommand()
    data class WebSearch(val query: String) : ParsedCommand()
    data object TakeSelfie : ParsedCommand()
    data class SendWhatsApp(val contact: String, val message: String) : ParsedCommand()
    data class SendMessage(val contact: String, val message: String) : ParsedCommand()
    data object PlayMusic : ParsedCommand()
    data class SetAlarm(val hour: Int, val minute: Int) : ParsedCommand()
    data class Flashlight(val turnOn: Boolean) : ParsedCommand()
    data object CheckTime : ParsedCommand()
    data object CheckDate : ParsedCommand()
    data object BatteryStatus : ParsedCommand()
    data class AskAi(val question: String) : ParsedCommand()
    data class Unknown(val rawText: String) : ParsedCommand()
}
