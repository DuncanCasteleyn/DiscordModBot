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

package be.duncanc.discordmodbot.commands

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
