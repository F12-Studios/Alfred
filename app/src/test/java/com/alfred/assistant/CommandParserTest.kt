package com.alfred.assistant

import com.alfred.assistant.commands.CommandParser
import com.alfred.assistant.commands.ParsedCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandParserTest {

    @Test
    fun testUserReportedPhrases() {
        // "message Shriraj on whatsapp that he is noob"
        val cmd1 = CommandParser.parse("message Shriraj on whatsapp that he is noob")
        assertTrue("Expected SendWhatsApp but was $cmd1", cmd1 is ParsedCommand.SendWhatsApp)
        val whatsappCmd1 = cmd1 as ParsedCommand.SendWhatsApp
        assertEquals("shriraj", whatsappCmd1.contact.lowercase())
        assertEquals("he is noob", whatsappCmd1.message)

        // "hey alfred open Duolingo"
        val cmd2 = CommandParser.parse("hey alfred open Duolingo")
        assertTrue("Expected OpenApp but was $cmd2", cmd2 is ParsedCommand.OpenApp)
        assertEquals("duolingo", (cmd2 as ParsedCommand.OpenApp).appName.lowercase())

        // "open chess"
        val cmd3 = CommandParser.parse("open chess")
        assertTrue("Expected OpenApp but was $cmd3", cmd3 is ParsedCommand.OpenApp)
        assertEquals("chess", (cmd3 as ParsedCommand.OpenApp).appName.lowercase())

        // "play chess"
        val cmd4 = CommandParser.parse("play chess")
        assertTrue("Expected OpenApp but was $cmd4", cmd4 is ParsedCommand.OpenApp)
        assertEquals("chess", (cmd4 as ParsedCommand.OpenApp).appName.lowercase())
    }

    @Test
    fun testWhatsAppVariations() {
        val variations = listOf(
            "send message to Shriraj on whatsapp that he is noob",
            "send a message to Shriraj on whatsapp saying he is noob",
            "message Shriraj on whatsapp saying he is noob",
            "whatsapp Shriraj that he is noob",
            "send whatsapp to Shriraj that he is noob"
        )

        for (phrase in variations) {
            val cmd = CommandParser.parse(phrase)
            assertTrue("Failed for phrase: '$phrase', got: $cmd", cmd is ParsedCommand.SendWhatsApp)
            val wa = cmd as ParsedCommand.SendWhatsApp
            assertEquals("Failed contact for '$phrase'", "shriraj", wa.contact.lowercase())
            assertEquals("Failed message for '$phrase'", "he is noob", wa.message)
        }
    }

    @Test
    fun testGeneralKnowledgeAi() {
        val q1 = CommandParser.parse("who is Albert Einstein")
        assertTrue(q1 is ParsedCommand.AskAi)

        val q2 = CommandParser.parse("what is the speed of light")
        assertTrue(q2 is ParsedCommand.AskAi)
    }
}
