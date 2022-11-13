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

import be.duncanc.discordmodbot.bot.utils.nicknameAndUsername
import be.duncanc.discordmodbot.data.services.UserBlockService
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.springframework.stereotype.Component

@Component
class Quote(
    userBlockService: UserBlockService
) : CommandModule(
    arrayOf("Quote"),
    "[message id / message link to quote] [response text]",
    "Will quote text and put a response under it, response text is optional",
    ignoreWhitelist = true,
    userBlockService = userBlockService
) {
    companion object {
        private const val JUMP_URL_PREFIX = "https://discord.com/channels/"
    }

    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        if (arguments == null) {
            throw IllegalArgumentException("This command requires at least a message id or link.")
        }

        val toQuoteSource = arguments.split(" ")[0]
        // This assumes a standard format of
        // https://discord.com/channels/<SERVER ID>/<CHANNEL ID>/<MESSAGE ID>
        if (toQuoteSource.startsWith(JUMP_URL_PREFIX)) {
            // idArray, as shown above, follows the pattern Server ID, TextChannel ID, Message ID.
            val idArray = toQuoteSource
                .removePrefix(JUMP_URL_PREFIX)
                .split("/")
            // make sure we are pulling from a valid place
            val guild = event.jda.getGuildById(idArray[0])
            guild?.getTextChannelById(idArray[1])?.retrieveMessageById(idArray[2])?.queue { messageToQuote ->
                if (messageToQuote != null) {
                    if (messageToQuote.contentDisplay.isBlank()) {
                        throw IllegalArgumentException("The message you want to quote has no content to quote.")
                    }
                    quoteMessage(event, messageToQuote, arguments.substring(toQuoteSource.length))
                } else {
                    throw IllegalArgumentException("This command requires a valid Message Link.")
                }
            }

        }
        // In case its a standard MessageID
        else {
            val messageId = arguments.split(" ")[0]
            event.textChannel.retrieveMessageById(messageId).queue { messageToQuote ->
                if (messageToQuote.contentDisplay.isBlank()) {
                    throw IllegalArgumentException("The message you want to quote has no content to quote.")
                }
                quoteMessage(event, messageToQuote, arguments.substring(messageId.length))
            }
        }
    }

    private fun quoteMessage(
        event: MessageReceivedEvent,
        quotedMessage: Message,
        responseString: String
    ) {
        val quoteEmbed = EmbedBuilder()
            .setAuthor(
                quotedMessage.member?.nicknameAndUsername,
                quotedMessage.jumpUrl,
                quotedMessage.author.effectiveAvatarUrl
            )
            .setDescription(quotedMessage.contentDisplay)
            .setFooter("Posted by ${event.author}", event.author.effectiveAvatarUrl)
        val responseEmbed = if (responseString.isBlank()) {
            null
        } else {
            EmbedBuilder()
                .setAuthor(event.member?.nicknameAndUsername, null, event.author.effectiveAvatarUrl)
                .setDescription(responseString)
                .setFooter("Posted by ${event.member}", event.author.effectiveAvatarUrl)
        }
        event.textChannel.sendMessageEmbeds(quoteEmbed.build()).queue()

        if (responseEmbed != null) {
            event.textChannel.sendMessageEmbeds(responseEmbed.build()).queue()
        }
    }
}
