package com.alfred.assistant.commands

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.alfred.assistant.contacts.ContactResolver

/**
 * Routes a [ParsedCommand] to the appropriate handler and returns
 * a butler-style response string for Alfred to speak.
 */
object CommandExecutor {

    /**
     * Executes the given [command] using the provided [context].
     * Returns a response string suitable for TTS output.
     */
    suspend fun execute(context: Context, command: ParsedCommand): String {
        return when (command) {
            is ParsedCommand.CallContact -> {
                CallHandler.call(context, command.contactName)
            }

            is ParsedCommand.OpenApp -> {
                AppLaunchHandler.launch(context, command.appName)
            }

            is ParsedCommand.SearchYouTube -> {
                SearchHandler.searchYouTube(context, command.query)
            }

            is ParsedCommand.WebSearch -> {
                SearchHandler.webSearch(context, command.query)
            }

            is ParsedCommand.TakeSelfie -> {
                CameraHandler.takeSelfie(context)
            }

            is ParsedCommand.SendWhatsApp -> {
                WhatsAppHandler.sendMessage(context, command.contact, command.message)
            }

            is ParsedCommand.SendMessage -> {
                MessageHandler.sendMessage(context, command.contact, command.message)
            }

            is ParsedCommand.PlayMusic -> {
                MediaHandler.playMusic(context)
            }

            is ParsedCommand.SetAlarm -> {
                MediaHandler.setAlarm(context, command.hour, command.minute)
            }

            is ParsedCommand.Flashlight -> {
                DeviceHandler.toggleFlashlight(context, command.turnOn)
            }

            is ParsedCommand.CheckTime -> {
                DeviceHandler.getCurrentTime()
            }

            is ParsedCommand.CheckDate -> {
                DeviceHandler.getCurrentDate()
            }

            is ParsedCommand.BatteryStatus -> {
                DeviceHandler.getBatteryStatus(context)
            }

            is ParsedCommand.AskAi -> {
                AiAssistantHandler.answerQuestion(context, command.question)
            }

            is ParsedCommand.Unknown -> {
                AiAssistantHandler.answerQuestion(context, command.rawText)
            }
        }
    }
}

/**
 * Handles sending SMS/text messages to contacts.
 */
object MessageHandler {

    fun sendMessage(context: Context, contact: String, message: String): String {
        val contactMatch = ContactResolver.resolveContact(context, contact)

        return if (contactMatch != null) {
            try {
                val smsUri = Uri.parse("smsto:${contactMatch.phoneNumber}")
                val intent = Intent(Intent.ACTION_SENDTO, smsUri).apply {
                    putExtra("sms_body", message)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "Opening messages for ${contactMatch.name}, sir. Your message is ready to send."
            } catch (e: Exception) {
                "I'm afraid I encountered an issue opening the messaging app, sir: ${e.message}"
            }
        } else {
            // Fallback: open messaging with just the draft message
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("sms:")
                    putExtra("sms_body", message)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "I couldn't locate $contact in your contacts, sir, but I've opened messaging with your message."
            } catch (e: Exception) {
                "I'm terribly sorry, sir. I was unable to open messaging: ${e.message}"
            }
        }
    }
}
