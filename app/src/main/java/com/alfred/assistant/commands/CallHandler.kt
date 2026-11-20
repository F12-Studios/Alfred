package com.alfred.assistant.commands

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.alfred.assistant.contacts.ContactResolver

/**
 * Handles phone call commands.
 * Resolves the contact name to a phone number and initiates the call.
 */
object CallHandler {

    /**
     * Attempts to call the given [contactName].
     *
     * 1. Resolves the contact name to a phone number via [ContactResolver].
     * 2. If found, launches [Intent.ACTION_CALL] with the tel: URI.
     * 3. If not found, falls back to [Intent.ACTION_DIAL] with just the name
     *    so the user can manually select the contact.
     *
     * @return A butler-style response string.
     */
    fun call(context: Context, contactName: String): String {
        val phoneNumber = ContactResolver.resolve(context, contactName)

        return if (phoneNumber != null) {
            try {
                val callIntent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:$phoneNumber")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(callIntent)
                "Placing a call to $contactName right away, sir."
            } catch (e: SecurityException) {
                // Permission not granted — fall back to dialer
                openDialer(context, phoneNumber)
                "Opening the dialer for $contactName, sir. It appears I lack the permission to call directly."
            } catch (e: Exception) {
                "I'm terribly sorry, sir. I encountered an issue while trying to call $contactName: ${e.message}"
            }
        } else {
            // Contact not found — try to open dialer with the raw input
            // It could be a phone number spoken directly (e.g., "dial 911")
            val sanitized = contactName.replace(Regex("[^\\d+]"), "")
            if (sanitized.isNotEmpty()) {
                openDialer(context, sanitized)
                "I couldn't find a contact named $contactName, sir, but I've opened the dialer with $sanitized."
            } else {
                openDialer(context, null)
                "I wasn't able to find $contactName in your contacts, sir. I've opened the dialer for you."
            }
        }
    }

    /**
     * Opens the dialer with an optional pre-filled [number].
     */
    private fun openDialer(context: Context, number: String?) {
        try {
            val dialIntent = if (number != null) {
                Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$number")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                Intent(Intent.ACTION_DIAL).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(dialIntent)
        } catch (e: Exception) {
            // Silently fail — the response string already indicates the issue
        }
    }
}
