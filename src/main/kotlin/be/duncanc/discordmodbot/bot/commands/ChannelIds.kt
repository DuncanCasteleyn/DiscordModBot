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

import be.duncanc.discordmodbot.data.services.UserBlock
import net.dv8tion.jda.core.MessageBuilder
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.ChannelType
import net.dv8tion.jda.core.entities.TextChannel
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * Retrieves all the channel ids of the current guild.
 *
 *
 * Created by Duncan on 19/02/2017.
 */
@Component
class ChannelIds(
        userBlock: UserBlock
) : CommandModule(
        ALIASES,
        null,
        DESCRIPTION,
        userBlock = userBlock
) {
    companion object {
        private val ALIASES = arrayOf("ChannelIds", "GetChannelIds")
        private const val DESCRIPTION = "Returns all channel ids of the guild where executed."
    }

    public override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        if (!event.isFromType(ChannelType.TEXT)) {
            event.channel.sendMessage("This command only works in a guild.").queue()
        } else if (!event.member.hasPermission(Permission.MANAGE_CHANNEL)) {
            event.channel.sendMessage("You need manage channels permission to use this command.").queue { message -> message.delete().queueAfter(1, TimeUnit.MINUTES) }
        } else {
            val result = StringBuilder()
            if (event.guild != null) {
                event.guild.textChannels.forEach { channel: TextChannel -> result.append(channel.toString()).append("\n") }
            }
            event.author.openPrivateChannel().queue { privateChannel ->
                val messages = MessageBuilder().appendCodeBlock(result.toString(), "text").buildAll(MessageBuilder.SplitPolicy.NEWLINE)
                messages.forEach { message -> privateChannel.sendMessage(message).queue() }
            }
        }
    }
}
