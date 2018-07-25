/*
 * Copyright 2018 Duncan Casteleyn
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

import be.duncanc.discordmodbot.bot.utils.JDALibHelper
import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import org.springframework.stereotype.Component

@Component
class Quote : CommandModule(
        arrayOf("Quote"),
        "[message id to quote] [response text]",
        "Will quote text and put a response under it, response text is optional",
        ignoreWhitelist = true
) {

    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        if (arguments == null) {
            throw IllegalArgumentException("This command requires at least a message id.")
        }
        val channelId = arguments.split(" ")[0]
        val messageToQuote = event.textChannel.getMessageById(channelId).complete()
        if (messageToQuote.contentDisplay.isEmpty()) {
            throw IllegalArgumentException("The message you want to quote has no content to quote.")
        }
        val quoteEmbed = EmbedBuilder()
                .setAuthor(JDALibHelper.getEffectiveNameAndUsername(messageToQuote.member), null, messageToQuote.author.effectiveAvatarUrl)
                .setDescription(messageToQuote.contentDisplay)
                .setFooter(event.author.id, null)
        val response = arguments.substring(channelId.length)
        val responseEmbed = if (response.isEmpty()) {
            null
        } else {
            EmbedBuilder()
                    .setAuthor(JDALibHelper.getEffectiveNameAndUsername(event.member), null, event.author.effectiveAvatarUrl)
                    .setDescription(response)
                    .setFooter(event.author.id, null)
        }
        event.textChannel.sendMessage(quoteEmbed.build()).queue()
        if (responseEmbed != null) {
            event.textChannel.sendMessage(responseEmbed.build()).queue()
        }
    }
}