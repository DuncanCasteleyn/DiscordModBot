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


import net.dv8tion.jda.core.entities.Message
import net.dv8tion.jda.core.entities.TextChannel
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.core.events.message.guild.GuildMessageUpdateEvent
import org.apache.commons.collections4.map.LinkedMap
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * This class provides a buffer that will store Message objects so that they can
 * be accessed after being deleted on discord.
 *
 * @author Dunciboy
 * @version 22 October 2016
 */
@Component
class MessageHistory
/**
 * Default constructor
 */
@Autowired
constructor(
        private val attachmentProxyCreator: AttachmentProxyCreator
) {
    companion object {
        private const val HISTORY_SIZE_PER_CHANNEL = 2000

        private val LOG = LoggerFactory.getLogger(MessageHistory::class.java)
    }

    /**
     * Top level Key is the channel id and the value it's Key is the channel Id
     */
    private val channels: HashMap<Long, LinkedMap<Long, Message>> = HashMap()

    fun cacheHistoryOfChannel(textChannel: TextChannel) {
        val channelId = textChannel.idLong
        val messages = channels[channelId] ?: LinkedMap()
        if (channels[channelId] == null) {
            channels[channelId] = messages
        }
        textChannel.iterableHistory.takeAsync(HISTORY_SIZE_PER_CHANNEL).thenAccept { retrieveMessages ->
            synchronized(this) {
                retrieveMessages.reversed().forEach { message ->
                    messages[message.idLong] = message
                }
            }
        }
    }

    /**
     * Used to add a message to the list
     *
     * @param event The event that triggered this method
     */
    @Synchronized
    fun storeMessage(event: GuildMessageReceivedEvent) {
        val channelId = event.channel.idLong
        val messages = channels[channelId] ?: LinkedMap()
        if (channels[channelId] == null) {
            channels[channelId] = messages
        }

        while (messages.size > HISTORY_SIZE_PER_CHANNEL) {
            attachmentProxyCreator.informDeleteFromCache(messages.remove(messages.firstKey())!!.idLong)
        }
        val message = event.message
        if (message.contentDisplay.isNotEmpty() && message.contentDisplay[0] == '!' || message.author.isBot) {
            return
        }
        messages[message.idLong] = message
        if (message.attachments.size > 0) {
            attachmentProxyCreator.proxyMessageAttachments(event)
        }
    }

    /**
     * This method is called to modify an existing object message in the list
     *
     * @param event The event that triggered this method.
     */
    @Synchronized
    fun updateMessage(event: GuildMessageUpdateEvent) {
        val channelId = event.channel.idLong
        val messages = channels[channelId] ?: return
        val message = event.message
        messages.replace(message.idLong, message)
    }

    /**
     * This message will return an object message or null if not found.
     *
     * @param messageId     the message id that we want to receive
     * @param delete Should the message be deleted from the cache?
     * @return object Message, returns null if the id is not in the history
     */
    @Synchronized
    internal fun getMessage(textChannelId: Long, messageId: Long, delete: Boolean = true): Message? {
        val messages = channels[textChannelId] ?: return null
        return if (delete) {
            messages.remove(messageId)
        } else {
            messages[messageId]
        }
    }

    internal fun getAttachmentsString(id: Long): String? {
        return attachmentProxyCreator.getAttachmentUrl(id)
    }
}
