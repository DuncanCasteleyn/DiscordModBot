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
import be.duncanc.discordmodbot.bot.utils.rot13
import be.duncanc.discordmodbot.data.services.UserBlock
import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.entities.ChannelType
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.events.message.guild.react.GuildMessageReactionAddEvent
import org.springframework.stereotype.Component

@Component
class Rot13(
        userBlock: UserBlock
) : CommandModule(
        arrayOf("Rot13", "Spoiler", "Sp"),
        "[Spoiler source] | [Content]",
        "Encodes a message to rot 13",
        ignoreWhitelist = true,
        userBlock = userBlock,
        cleanCommandMessage = false
) {
    companion object {
        const val EMBED_TITLE = "Contains spoilers for:"
    }

    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        event.message.delete().queue()
        if (!event.isFromType(ChannelType.TEXT)) {
            throw IllegalArgumentException("This command can only be used in a server/guild")
        }
        val split = arguments?.split('|') ?: throw IllegalArgumentException("Arguments are required for this command")
        if (split.size != 2) {
            throw IllegalArgumentException("Exactly one \'|\' is required. Example: Re:Zero anime | Some Re:Zero anime spoiler")
        }
        val spoilerSource = split[0].trim()
        val spoilerContent = split[1].trim()
        val embedBuilder = EmbedBuilder()
        embedBuilder.setAuthor(event.member.nicknameAndUsername, "https://discordapp.com/users/${event.author.id}", event.author.effectiveAvatarUrl)
        embedBuilder.setTitle("$EMBED_TITLE\n***$spoilerSource***")
        embedBuilder.setDescription("``${spoilerContent.rot13()}``")
        embedBuilder.setFooter("To decrypt the message simply react on it", null)
        event.textChannel.sendMessage(embedBuilder.build()).queue { it.addReaction("\uD83D\uDDB1").queue() }
    }

    override fun onGuildMessageReactionAdd(event: GuildMessageReactionAddEvent) {
        if (event.member.user.isBot || userBlock?.isBlocked(event.user.idLong) == true) {
            return
        }
        event.channel.getMessageById(event.messageIdLong).queue { message ->
            if (message.author == event.jda.selfUser && message.embeds.size == 1 && message.embeds[0].title.split("\n")[0] == EMBED_TITLE) {
                event.member.user.openPrivateChannel().queue {
                    event.jda.retrieveUserById(message.embeds[0].author.url.removePrefix("https://discordapp.com/users/")).queue { user ->
                        it.sendMessage("${user.asMention}'s spoiler message from ${event.channel.asMention}:\n\n" + message.embeds[0].description.rot13()).queue()
                    }
                }
                spamCheck(event.user)
            }
        }
    }
}