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

package be.duncanc.discordmodbot.bot.services


import be.duncanc.discordmodbot.data.redis.hash.DiscordMessage
import be.duncanc.discordmodbot.data.repositories.key.value.DiscordMessageRepository
import net.dv8tion.jda.api.entities.Emote
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageUpdateEvent
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * This class provides a buffer that will store Message objects so that they can
 * be accessed after being deleted on discord.
 *
 * @author Dunciboy
 * @version 22 October 2016
 */
@Component
@Transactional
class MessageHistory
/**
 * Default constructor
 */
@Autowired
constructor(
    private val discordMessageRepository: DiscordMessageRepository,
    private val attachmentProxyCreator: AttachmentProxyCreator
) {
    /**
     * Used to add a message to the list
     *
     * @param event The event that triggered this method
     */
    fun storeMessage(event: GuildMessageReceivedEvent) {
        val message = event.message
        if (message.contentDisplay.isNotEmpty() && message.contentDisplay[0] == '!' || message.author.isBot) {
            return
        }
        val discordMessage = DiscordMessage(
            message.idLong,
            message.guild.idLong,
            message.channel.idLong,
            message.author.idLong,
            message.contentDisplay,
            linkEmotes(message.emotes)
        )
        discordMessageRepository.save(discordMessage)
        if (message.attachments.size > 0) {
            attachmentProxyCreator.proxyMessageAttachments(event)
        }
    }

    /**
     * This method is called to modify an existing object message in the list
     *
     * @param event The event that triggered this method.
     */
    fun updateMessage(event: GuildMessageUpdateEvent) {
        if (discordMessageRepository.existsById(event.messageIdLong)) {
            val message = event.message
            val discordMessage = DiscordMessage(
                message.idLong,
                message.guild.idLong,
                message.channel.idLong,
                message.author.idLong,
                message.contentDisplay
            )
            discordMessageRepository.save(discordMessage)
        }
    }

    /**
     * This message will return an object message or null if not found.
     *
     * @param messageId     the message id that we want to receive
     * @param delete Should the message be deleted from the cache?
     * @return object Message, returns null if the id is not in the history
     */
    internal fun getMessage(textChannelId: Long, messageId: Long, delete: Boolean = true): DiscordMessage? {
        val discordMessage: DiscordMessage? = discordMessageRepository.findById(messageId).orElse(null)
        if (discordMessage != null && delete) {
            discordMessageRepository.deleteById(messageId)
        }
        return discordMessage
    }

    internal fun getAttachmentsString(id: Long): String? {
        return attachmentProxyCreator.getAttachmentUrl(id)
    }

    private fun linkEmotes(emotes: MutableList<Emote>): String? {
        if (emotes.isEmpty()) {
            return null
        }
        val stringBuilder = StringBuilder()
        emotes.forEach {
            stringBuilder.append("[" + it.name + "](" + it.imageUrl + ")\n")
        }
        return stringBuilder.toString()
    }
}
