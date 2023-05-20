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
import be.duncanc.discordmodbot.data.redis.hash.AttachmentProxy
import be.duncanc.discordmodbot.data.repositories.key.value.AttachmentProxyRepository
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.utils.FileUpload
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit


/**
 * Created by Duncan on 14/01/2017.
 *
 *
 * This class duplicates embeds and links to images to keep them alive for logging.
 */
@Component
class AttachmentProxyCreator(
    private val attachmentProxyRepository: AttachmentProxyRepository
) {
    companion object {
        private const val CACHE_CHANNEL = 310006048595509248L
        private val LOG = LoggerFactory.getLogger(AttachmentProxyCreator::class.java)
    }

    fun getAttachmentUrl(id: Long): String? {
        return attachmentProxyRepository.findById(id)
            .map {
                val attachmentUrlsBuilder = StringBuilder(it.attachmentUrls.joinToString("\n"))
                if (it.hadFailedCaches) {
                    attachmentUrlsBuilder.append("The message either contained (an) attachment(s) larger then 8MB and could not be uploaded again, or failed to create a proxy.")
                }
                attachmentUrlsBuilder.toString()
            }
            .orElse(null)
    }

    @Async
    fun proxyMessageAttachments(event: MessageReceivedEvent): CompletableFuture<Unit> {
        if (!event.isFromGuild || event.author.isBot) {
            return CompletableFuture.completedFuture(Unit)
        }

        val attachments = ArrayList<String>()
        var hadFailures = false
        event.message.attachments.forEach { attachment ->
            try {
                if (attachment.size < 8 shl 20) {  //8MB
                    attachment.proxy.download().get(30, TimeUnit.SECONDS).let { inputStream: InputStream ->
                        val outputStream = ByteArrayOutputStream()
                        IOUtils.copy(inputStream, outputStream)

                        event.jda.getTextChannelById(CACHE_CHANNEL)?.sendFiles(
                            FileUpload.fromData(outputStream.toByteArray(), attachment.fileName)
                        )?.map { message ->
                            message.attachments.map { messageAttachment ->
                                "[${messageAttachment.fileName}](${messageAttachment.url})"
                            }
                        }?.submit()?.get(30, TimeUnit.SECONDS)?.let {
                            attachments.addAll(it)
                        }
                    }
                } else {
                    LOG.warn("The file was larger than 8MB.")
                    hadFailures = true
                }
            } catch (e: Exception) {
                LOG.info("An exception occurred when retrieving one of the attachments", e)
                hadFailures = true
            }
        }
        val attachmentProxy = when {
            attachments.isNotEmpty() -> {
                AttachmentProxy(event.messageIdLong, attachments, hadFailures)
            }

            hadFailures -> {
                AttachmentProxy(event.messageIdLong, emptyList(), hadFailures)
            }

            else -> {
                null
            }
        }
        attachmentProxy?.let { attachmentProxyRepository.save(it) }
        return CompletableFuture.completedFuture(Unit)
    }
}
