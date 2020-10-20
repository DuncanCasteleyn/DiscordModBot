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

import be.duncanc.discordmodbot.bot.utils.IOUtils
import be.duncanc.discordmodbot.bot.utils.limitLessBulkDelete
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import org.apache.commons.collections4.map.LinkedMap
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.AsyncResult
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.*


/**
 * Created by Duncan on 14/01/2017.
 *
 *
 * This class duplicates embeds and links to images to keep them alive for logging.
 */
@Component
class AttachmentProxyCreator {
    companion object {
        private const val CACHE_CHANNEL = 310006048595509248L
        private const val CACHE_SIZE = 500
        private val LOG = LoggerFactory.getLogger(AttachmentProxyCreator::class.java)
    }

    private val attachmentCache = LinkedMap<Long, Message>()

    @Synchronized
    fun getAttachmentUrl(id: Long): String? {
        if (attachmentCache.containsKey(id)) {
            val stringBuilder = StringBuilder()
            val message = attachmentCache.remove(id)
            if (message != null) {
                message.attachments.forEach { attachment ->
                    stringBuilder.append("[").append(attachment.fileName).append("](").append(attachment.url)
                            .append(")\n")
                }
                var subMessage = message
                do {
                    if (attachmentCache.containsKey(subMessage?.idLong)) {
                        subMessage = attachmentCache.remove(subMessage?.idLong)
                        if (subMessage != null) {
                            subMessage.attachments.forEach { attachment ->
                                stringBuilder.append("[").append(attachment.fileName).append("](")
                                        .append(attachment.url).append(")\n")
                            }
                        } else {
                            stringBuilder.append("The message either contained an attachment larger then 8MB and could not be uploaded again, or failed to create a proxy.")
                        }
                    } else {
                        subMessage = null
                    }
                } while (subMessage != null)
            } else {
                stringBuilder.append("The message either contained an attachment larger then 8MB and could not be uploaded again, or failed to create a proxy.")
            }
            return stringBuilder.toString()
        } else {
            return null
        }
    }

    @Synchronized
    final tailrec fun informDeleteFromCache(id: Long) {
        if (attachmentCache.containsKey(id)) {
            val message = attachmentCache.remove(id)
            if (message != null) {
                message.delete().queue()
                informDeleteFromCache(message.idLong)
            }
        }
    }

    @Synchronized
    private final tailrec fun addToCache(id: Long, message: Message?) {
        while (attachmentCache.size > CACHE_SIZE) {
            val messageToClean = attachmentCache.remove(attachmentCache.firstKey())
            messageToClean?.delete()?.queue()
            message?.idLong?.let { informDeleteFromCache(it) }
        }
        val messageId = attachmentCache[id]?.idLong
        if (messageId != null) {
            addToCache(messageId, message)
        } else {
            attachmentCache[id] = message
        }
    }

    @Synchronized
    @Async
    fun proxyMessageAttachments(event: GuildMessageReceivedEvent): AsyncResult<Unit> {
        if (event.author.isBot) {
            return AsyncResult(Unit)
        }

        event.message.attachments.forEach { attachment ->
            if (attachment.size < 8 shl 20) {  //8MB
                attachment.retrieveInputStream().thenAccept { inputStream: InputStream ->
                    val outputStream = ByteArrayOutputStream()
                    IOUtils.copy(inputStream, outputStream)

                    event.jda.getTextChannelById(CACHE_CHANNEL)?.sendFile(
                            outputStream.toByteArray(),
                            attachment.fileName
                    )?.complete().let { message -> addToCache(event.message.idLong, message) }
                }.exceptionally { e ->
                    LOG.info("An exception occurred when retrieving one of the attachments", e)
                    addToCache(event.message.idLong, null)
                    null
                }
            } else {
                LOG.warn("The file was larger than 8MB.")
                addToCache(event.message.idLong, null)
            }
        }
        return AsyncResult(Unit)
    }

    @Synchronized
    fun cleanCache() {
        if (attachmentCache.size > 0) {
            attachmentCache[attachmentCache.firstKey()]?.jda?.getTextChannelById(CACHE_CHANNEL)
                    ?.limitLessBulkDelete(ArrayList(attachmentCache.values))
        }
    }
}
