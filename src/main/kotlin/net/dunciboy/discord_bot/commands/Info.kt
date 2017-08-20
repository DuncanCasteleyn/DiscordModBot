/*
 * MIT License
 *
 * Copyright (c) 2017 Duncan Casteleyn
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package net.dunciboy.discord_bot.commands

import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.entities.MessageEmbed
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import java.awt.Color

/**
 * Information commands for the bot.
 *
 * @since 1.0.0
 */
class Info : CommandModule(ALIASES, null, DESCRIPTION) {
    companion object {

        private val ALIASES = arrayOf("Info")
        private const val DESCRIPTION = "Returns information about the bot."
        private val INFO_MESSAGE: MessageEmbed = EmbedBuilder()
                .setTitle("Discord bot", null)
                .setDescription("**Author:** Dunciboy\n**Language:** Java & Kotlin\n**Discord-lib:** JDA\n**Bot hosting:** freegamehosting.eu")
                .setColor(Color.RED)
                .build()

    }

    /**
     * Sends information about the bot to the user.

     * @param event     A MessageReceivedEvent that came with the command
     * *
     * @param command   The command alias that was used to trigger this commandExec
     * *
     * @param arguments The arguments that where entered after the command alias
     */
    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        event.author.openPrivateChannel().queue { it.sendMessage(INFO_MESSAGE).queue() }
    }
}
