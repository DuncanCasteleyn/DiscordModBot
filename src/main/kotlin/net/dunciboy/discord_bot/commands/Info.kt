/*
 * Copyright 2017 Duncan C.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
