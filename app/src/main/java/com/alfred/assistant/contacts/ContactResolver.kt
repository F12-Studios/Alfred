package com.alfred.assistant.contacts

import android.content.Context
import android.provider.ContactsContract
import android.telephony.TelephonyManager
import android.util.Log
import java.util.Locale

/**
 * Resolves contact names to phone numbers and structured contact details.
 * Performs intelligent token and fuzzy matching against the device's contacts.
 */
object ContactResolver {

    private const val TAG = "AlfredContacts"

    data class ContactMatch(
        val name: String,
        val phoneNumber: String,
        val formattedWhatsAppNumber: String
    )

    /**
     * Resolves a contact name to a phone number. Backward compatible.
     */
    fun resolve(context: Context, name: String): String? {
        return resolveContact(context, name)?.phoneNumber
    }

    /**
     * Finds the best matching contact from the device's contacts list.
     */
    fun resolveContact(context: Context, name: String): ContactMatch? {
        val query = name.trim().lowercase()
        if (query.isBlank()) {
            Log.w(TAG, "Contact query is blank")
            return null
        }

        return try {
            val contacts = fetchAllContacts(context)
            if (contacts.isEmpty()) {
                Log.w(TAG, "No contacts found on device or permission not granted")
                return null
            }

            // Clean search terms (remove punctuation, extraneous symbols)
            val cleanQuery = query.replace(Regex("[^a-z0-9 ]"), "").trim()
            val queryTokens = cleanQuery.split("\\s+".toRegex()).filter { it.isNotBlank() }

            // Score and rank all contacts
            var bestMatch: Pair<String, String>? = null // (name, phoneNumber)
            var highestScore = -1

            for ((contactName, phoneNumber) in contacts) {
                val lowerContact = contactName.lowercase().trim()
                val cleanContact = lowerContact.replace(Regex("[^a-z0-9 ]"), "").trim()
                val contactTokens = cleanContact.split("\\s+".toRegex()).filter { it.isNotBlank() }

                var score = 0

                // 1. Exact match: 100
                if (lowerContact == query || cleanContact == cleanQuery) {
                    score = 100
                }
                // 2. Starts with: 90
                else if (lowerContact.startsWith(query) || cleanContact.startsWith(cleanQuery)) {
                    score = 90
                }
                // 3. Any token matches exactly: 85 (e.g. query "Shriraj" matches "Shriraj Patil")
                else if (contactTokens.any { it == cleanQuery } || queryTokens.any { token -> contactTokens.contains(token) }) {
                    score = 85
                }
                // 4. Any contact token starts with query token: 75
                else if (queryTokens.any { qTok -> contactTokens.any { cTok -> cTok.startsWith(qTok) } }) {
                    score = 75
                }
                // 5. Contains substring: 60
                else if (cleanContact.contains(cleanQuery) || cleanQuery.contains(cleanContact)) {
                    score = 60
                }

                if (score > highestScore) {
                    highestScore = score
                    bestMatch = Pair(contactName, phoneNumber)
                }
            }

            if (bestMatch != null && highestScore >= 60) {
                val (matchedName, rawNumber) = bestMatch
                val formattedNumber = formatForWhatsApp(context, rawNumber)
                Log.d(TAG, "Matched '$name' -> '$matchedName' ($formattedNumber), score=$highestScore")
                ContactMatch(
                    name = matchedName,
                    phoneNumber = rawNumber,
                    formattedWhatsAppNumber = formattedNumber
                )
            } else {
                Log.d(TAG, "No suitable contact match for: '$name' (highest score: $highestScore)")
                null
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing READ_CONTACTS permission", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving contact: $name", e)
            null
        }
    }

    /**
     * Reads all contacts with phone numbers.
     */
    private fun fetchAllContacts(context: Context): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        val cursor = context.contentResolver.query(
            uri,
            projection,
            null,
            null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        )

        cursor?.use {
            val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (it.moveToNext()) {
                val name = if (nameIdx >= 0) it.getString(nameIdx) ?: "" else ""
                val num = if (numIdx >= 0) it.getString(numIdx) ?: "" else ""
                if (name.isNotBlank() && num.isNotBlank()) {
                    list.add(Pair(name, num))
                }
            }
        }
        return list
    }

    /**
     * Cleans and formats phone number for WhatsApp deep link (api.whatsapp.com/send?phone=NUMBER).
     * WhatsApp requires international format digits with NO leading '+' or dashes or spaces.
     */
    fun formatForWhatsApp(context: Context, rawNumber: String): String {
        // Keep only digits and '+'
        val cleaned = rawNumber.trim().replace(Regex("[^0-9+]"), "")

        // If it starts with '+', remove '+'
        if (cleaned.startsWith("+")) {
            return cleaned.removePrefix("+")
        }

        // If starts with 00 (international call prefix), remove 00
        if (cleaned.startsWith("00")) {
            return cleaned.removePrefix("00")
        }

        // Detect default country calling code
        val countryCode = detectDefaultCountryCode(context)

        // If starts with single 0 and length is 11 (common local dial pattern e.g. 09876543210)
        if (cleaned.startsWith("0") && cleaned.length == 11) {
            return countryCode + cleaned.substring(1)
        }

        // Standard 10 digit mobile number without country code
        if (cleaned.length == 10) {
            return countryCode + cleaned
        }

        return cleaned
    }

    private fun detectDefaultCountryCode(context: Context): String {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val simCountry = tm?.simCountryIso?.uppercase(Locale.ROOT)
            val netCountry = tm?.networkCountryIso?.uppercase(Locale.ROOT)
            val deviceCountry = simCountry?.ifBlank { null }
                ?: netCountry?.ifBlank { null }
                ?: Locale.getDefault().country.uppercase(Locale.ROOT)

            when (deviceCountry) {
                "IN" -> "91"
                "US", "CA" -> "1"
                "GB", "UK" -> "44"
                "AU" -> "61"
                "DE" -> "49"
                "FR" -> "33"
                else -> "91"
            }
        } catch (_: Exception) {
            "91"
        }
    }
}
