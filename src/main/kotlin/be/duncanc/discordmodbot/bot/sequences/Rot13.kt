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

package be.duncanc.discordmodbot.bot.sequences

import be.duncanc.discordmodbot.bot.commands.CommandModule
import be.duncanc.discordmodbot.bot.utils.JDALibHelper
import be.duncanc.discordmodbot.data.services.UserBlock
import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.entities.ChannelType
import net.dv8tion.jda.core.entities.MessageChannel
import net.dv8tion.jda.core.entities.TextChannel
import net.dv8tion.jda.core.entities.User
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.events.message.guild.react.GuildMessageReactionAddEvent
import org.springframework.stereotype.Component

@Component
class Rot13(
        userBlock: UserBlock
) : CommandModule(
        arrayOf("Rot13"),
        null,
        "Encodes a message to rot 13",
        ignoreWhitelist = true,
        userBlock = userBlock
) {
    companion object {
        const val EMBED_TITLE = "Contains spoilers for:"
    }

    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        if (!event.isFromType(ChannelType.TEXT)) {
            throw IllegalArgumentException("This command can only be used in a server/guild")
        }
        event.author.openPrivateChannel().queue {
            event.jda.addEventListener(Rot13Sequence(event.textChannel, event.author, it))
        }
    }

    override fun onGuildMessageReactionAdd(event: GuildMessageReactionAddEvent) {
        if (event.member.user.isBot || userBlock?.isBlocked(event.user.idLong) == true) {
            return
        }
        event.channel.getMessageById(event.messageIdLong).queue { message ->
            if (message.author == event.jda.selfUser && message.embeds.size == 1 && message.embeds[0].title.split("\n")[0] == EMBED_TITLE) {
                event.member.user.openPrivateChannel().queue {
                    it.sendMessage("Decoded message: " + JDALibHelper.rot13(message.embeds[0].description)).queue()
                }
            }
        }
        spamCheck(event.user)
    }

    inner class Rot13Sequence(
            private val targetChannel: TextChannel,
            user: User,
            channel: MessageChannel
    ) : Sequence(
            user,
            channel
    ) {
        private var rot13Message: String? = null


        init {
            channel.sendMessage("Please enter the text you want to encode into rot 13").queue()
        }

        override fun onMessageReceivedDuringSequence(event: MessageReceivedEvent) {
            if (rot13Message == null) {
                rot13Message = JDALibHelper.rot13(event.message.contentRaw)
                channel.sendMessage("Please enter the source of your spoilers.\nExample: Re:Zero Novel, Fairy Tail anime, None\nDon'ts: Re:Zero, I dunno").queue()
            } else {
                val embedBuilder = EmbedBuilder()
                embedBuilder.setAuthor(JDALibHelper.getEffectiveNameAndUsername(targetChannel.guild.getMember(user)), "https://discordapp.com/users/${event.author.id}" ,user.effectiveAvatarUrl)
                embedBuilder.setTitle("$EMBED_TITLE\n${event.message.contentStripped}")
                embedBuilder.setDescription(rot13Message)
                embedBuilder.setFooter("To decrypt the message simply react on it", null)
                targetChannel.sendMessage(embedBuilder.build()).queue { it.addReaction("\uD83D\uDDB1").queue() }
            }
        }
    }
}