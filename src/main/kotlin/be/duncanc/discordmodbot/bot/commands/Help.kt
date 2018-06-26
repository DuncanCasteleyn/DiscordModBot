/*
 * Copyright 2018.  Duncan Casteleyn
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

package be.duncanc.discordmodbot.bot.commands

import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Will create a help command containing information about commands.
 *
 * @since 1.0.0
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
class Help : CommandModule(
        arrayOf("Help"),
        null,
        "Show a list of commands"
) {

    /**
     * Sends an embed to the users containing help for the commands
     */
    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        val helpEmbed: EmbedBuilder = EmbedBuilder().setTitle("Help")
        event.jda.registeredListeners.filter { it is CommandModule }.forEach {
            it as CommandModule
            helpEmbed.addField(Arrays.toString(it.aliases).replace("[", "").replace("]", "").replace(",", ", ") + if (it.argumentationSyntax != null) " " + it.argumentationSyntax else "", it.description
                    ?: "No description available.", false)
        }

        event.channel.sendMessage(helpEmbed.build()).queue { it.delete().queueAfter(2, TimeUnit.MINUTES) }
    }
}
