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
 */

package be.duncanc.discordmodbot.services

import be.duncanc.discordmodbot.utils.JDALibHelper
import net.dv8tion.jda.core.MessageBuilder
import net.dv8tion.jda.core.entities.Message
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.apache.commons.collections4.map.LinkedMap
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.*

/**
 * Created by Duncan on 14/01/2017.
 *
 *
 * This class duplicates embeds and links to images to keep them alive for logging.
 */
internal class AttachmentProxyCreator {
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
                message.attachments.forEach { attachment -> stringBuilder.append("[").append(attachment.fileName).append("](").append(attachment.url).append(")\n") }
                var subMessage = message
                do {
                    if (attachmentCache.containsKey(subMessage!!.idLong)) {
                        subMessage = attachmentCache.remove(subMessage.idLong)
                        if (subMessage != null) {
                            subMessage.attachments.forEach { attachment -> stringBuilder.append("[").append(attachment.fileName).append("](").append(attachment.url).append(")\n") }
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
    fun informDeleteFromCache(id: Long) {
        if (attachmentCache.containsKey(id)) {
            val message = attachmentCache.remove(id)
            if (message != null) {
                message.delete().queue()
                informDeleteFromCache(message.idLong)
            }
        }
    }

    @Synchronized
    private fun addToCache(id: Long, message: Message?) {
        while (attachmentCache.size > CACHE_SIZE) {
            val messageToClean = attachmentCache.remove(attachmentCache.firstKey())
            messageToClean?.delete()?.queue()
            informDeleteFromCache(message!!.idLong)
        }
        if (attachmentCache.containsKey(id)) {
            addToCache(attachmentCache[id]!!.idLong, message)
        } else {
            attachmentCache[id] = message
        }
    }


    @Deprecated("Replaced with proxyMessageAttachment", ReplaceWith("proxyMessageAttachments(event)"))
    fun storeMessageAttachments(event: GuildMessageReceivedEvent) {
        proxyMessageAttachments(event)
    }

    fun proxyMessageAttachments(event: GuildMessageReceivedEvent) {
        if (event.author.isBot) {
            return
        }

        event.message.attachments.forEach { attachment ->
            if (attachment.size < 8 shl 20) {  //8MB
                var inputStream: InputStream? = null
                try {
                    /*Request request = new Request.Builder().addHeader("user-agent", Requester.USER_AGENT).url(attachment.getUrl()).build();
                    Response response = ((JDAImpl) event.getJDA()).getRequester().getHttpClient().newCall(request).execute();
                    //noinspection ConstantConditions
                    inputStream = response.body().byteStream();*/
                    inputStream = attachment.inputStream
                    val buffer = ByteArrayOutputStream()


                    val data = ByteArray(8192)

                    var nRead = inputStream.read(data, 0, data.size)
                    while (nRead != -1) {
                        buffer.write(data, 0, nRead)
                        nRead = inputStream.read(data, 0, data.size)
                    }

                    buffer.flush()

                    event.jda.getTextChannelById(CACHE_CHANNEL).sendFile(buffer.toByteArray(), attachment.fileName, MessageBuilder().append(event.message.id).build()).queue { message -> addToCache(event.message.idLong, message) }
                } catch (e: Exception) {
                    LOG.info("An exception occurred when retrieving one of the attachments", e)
                    addToCache(event.message.idLong, null)
                } finally {
                    if (inputStream != null) {
                        try {
                            inputStream.close()
                        } catch (ignored: Exception) {
                        }

                    }
                }
            } else {
                LOG.warn("The file was larger than 8MB.")
                addToCache(event.message.idLong, null)
            }
        }
    }

    @Synchronized
    fun cleanCache() {
        if (attachmentCache.size > 0) {
            JDALibHelper.limitLessBulkDelete(attachmentCache[attachmentCache.firstKey()]!!.jda.getTextChannelById(CACHE_CHANNEL), ArrayList(attachmentCache.values))
        }
    }
}
