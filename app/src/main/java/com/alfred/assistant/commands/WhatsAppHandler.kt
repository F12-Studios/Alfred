package com.alfred.assistant.commands

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import com.alfred.assistant.contacts.ContactResolver

/**
 * Handles sending messages directly via WhatsApp or WhatsApp Business.
 */
object WhatsAppHandler {

    private const val TAG = "AlfredWhatsApp"
    private val WHATSAPP_PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b")

    /**
     * Finds the installed WhatsApp package name (standard WhatsApp or WhatsApp Business).
     */
    fun getInstalledWhatsAppPackage(context: Context): String? {
        val pm = context.packageManager
        for (pkg in WHATSAPP_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0)
                return pkg
            } catch (_: PackageManager.NameNotFoundException) {
            }
        }
        return null
    }

    /**
     * Sends a message to [contactName] via WhatsApp.
     *
     * 1. Resolves contact from device address book to get international phone number.
     * 2. If number found: opens WhatsApp direct 1-on-1 chat with pre-filled message text.
     * 3. If contact not found: opens WhatsApp contact selector with pre-filled message text.
     */
    fun sendMessage(context: Context, contactName: String, message: String): String {
        val whatsappPackage = getInstalledWhatsAppPackage(context)
        if (whatsappPackage == null) {
            return "I'm terribly sorry, sir, but WhatsApp is not installed on this device."
        }

        val cleanContact = contactName.trim().removeSuffix("'s").removeSuffix("'")
        val cleanMessage = message.trim()

        val contactMatch = ContactResolver.resolveContact(context, cleanContact)

        return if (contactMatch != null) {
            try {
                val formattedPhone = contactMatch.formattedWhatsAppNumber
                val encodedText = Uri.encode(cleanMessage)
                val url = "https://api.whatsapp.com/send?phone=$formattedPhone&text=$encodedText"

                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    setPackage(whatsappPackage)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "Right away, sir. Opening WhatsApp to message ${contactMatch.name}."
            } catch (e: Exception) {
                Log.e(TAG, "Error opening WhatsApp chat", e)
                fallbackShare(context, whatsappPackage, cleanContact, cleanMessage)
            }
        } else {
            // Contact not in address book, open WhatsApp with message ready to pick recipient
            fallbackShare(context, whatsappPackage, cleanContact, cleanMessage)
        }
    }

    private fun fallbackShare(
        context: Context,
        whatsappPackage: String,
        contactName: String,
        message: String
    ): String {
        return try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                setPackage(whatsappPackage)
                putExtra(Intent.EXTRA_TEXT, message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            if (contactName.isNotBlank()) {
                "I couldn't locate $contactName in your contacts, sir, but I've opened WhatsApp with your message ready to send."
            } else {
                "Opening WhatsApp with your message ready, sir."
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing to WhatsApp", e)
            "I encountered a difficulty opening WhatsApp, sir: ${e.message}"
        }
    }
}
