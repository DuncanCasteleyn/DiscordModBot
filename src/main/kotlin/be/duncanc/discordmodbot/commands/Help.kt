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
import net.dv8tion.jda.core.JDA
import net.dv8tion.jda.core.events.ReadyEvent
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Will create a help command containing information about commands.
 *
 * @since 1.0.0
 */
class Help constructor() : CommandModule(ALIASES, null, DESCRIPTION) {

    private val helpEmbed: EmbedBuilder = EmbedBuilder().setTitle("Help")

    @Suppress("UNUSED_PARAMETER")
    @Deprecated("No longer supported, use default constructor instead.", ReplaceWith("Help()"))
    constructor(vararg commandModules: CommandModule) : this()

    @Suppress("UNUSED_PARAMETER")
    @Deprecated("No longer supported.", ReplaceWith("loadCommands(jda)"))
    fun addCommands(vararg commandModules: CommandModule) {
        throw UnsupportedOperationException("No longer supported.")
        //Arrays.stream(commandModules).forEach { commandModule -> helpEmbed.addField(Arrays.toString(commandModule.aliases).replace("[", "").replace("]", "").replace(",", ", ") + if (commandModule.argumentationSyntax != null) " " + commandModule.argumentationSyntax else "", commandModule.description, false) }
    }

    fun loadCommands(jda: JDA) {
        helpEmbed.clearFields()
        jda.registeredListeners.filter { it is CommandModule }.forEach {
            it as CommandModule
            helpEmbed.addField(Arrays.toString(it.aliases).replace("[", "").replace("]", "").replace(",", ", ") + if (it.argumentationSyntax != null) " " + it.argumentationSyntax else "", it.description ?: "No description available.", false)
        }
    }

    override fun onReady(event: ReadyEvent) {
        loadCommands(event.jda)
    }

    /**
     * Sends an embed to the users containing help for the commands
     */
    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        event.channel.sendMessage(helpEmbed.build()).queue { it.delete().queueAfter(2, TimeUnit.MINUTES) }
    }

    companion object {
        private val ALIASES = arrayOf("Help")
        private val DESCRIPTION = "Show a list of commands"
    }
}
