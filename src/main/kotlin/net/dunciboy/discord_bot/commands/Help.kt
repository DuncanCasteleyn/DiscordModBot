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
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import java.util.*

/**
 * Will create a help command containing information about commands.
 *
 * @since 1.0.0
 */
class Help private constructor() : CommandModule(ALIASES, null, DESCRIPTION) {

    private val helpEmbed: EmbedBuilder = EmbedBuilder().setTitle("Help")


    constructor(vararg commandModules: CommandModule) : this() {
        addCommands(*commandModules)
    }

    fun addCommands(vararg commandModules: CommandModule) {
        Arrays.stream(commandModules).forEach { commandModule -> helpEmbed.addField(Arrays.toString(commandModule.aliases).replace("[", "").replace("]", "").replace(",", ", ") + if (commandModule.argumentationSyntax != null) " " + commandModule.argumentationSyntax else "", commandModule.description, false) }
    }

    /**
     * Do something with the event, command and arguments.

     * @param event     A MessageReceivedEvent that came with the command
     * *
     * @param command   The command alias that was used to trigger this commandExec
     * *
     * @param arguments The arguments that where entered after the command alias
     */
    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        event.author.openPrivateChannel().queue { privateChannel -> privateChannel.sendMessage(helpEmbed.build()).queue() }
    }

    companion object {
        private val ALIASES = arrayOf("Help")
        private val DESCRIPTION = "Show a list of commands"
    }
}
