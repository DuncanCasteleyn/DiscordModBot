package be.duncanc.discordmodbot.logging

import be.duncanc.discordmodbot.logging.persistence.AttachmentProxy
import be.duncanc.discordmodbot.logging.persistence.AttachmentProxyRepository
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.utils.FileUpload
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture

/**
 * Created by Duncan on 14/01/2017.
 *
 *
 * This class duplicates embeds and links to images to keep them alive for logging.
 */
@Component
class AttachmentProxyCreator(
    private val attachmentProxyRepository: AttachmentProxyRepository,
    private val attachmentCacheProperties: AttachmentCacheProperties
) {
    companion object {
        private val LOG = LoggerFactory.getLogger(AttachmentProxyCreator::class.java)
    }

    fun getAttachmentUrl(id: Long): String? {
        return attachmentProxyRepository.findById(id)
            .map {
                val attachmentUrlsBuilder = StringBuilder(it.attachmentUrls.joinToString("\n"))
                if (it.hadFailedCaches) {
                    attachmentUrlsBuilder.append("The message either contained (an) attachment(s) larger than 20MB and could not be uploaded again, or failed to create a proxy.")
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
        val originalMessage = event.message

        val result = CompletableFuture<Unit>()

        val channel = event.jda.getTextChannelById(attachmentCacheProperties.channelId)
        if (channel == null) {
            LOG.error(
                "The configured attachment cache channel could not be found: {}",
                attachmentCacheProperties.channelId
            )
            hadFailures = true
            finalizeProxy(event.messageIdLong, attachments, hadFailures, result)
            return result
        }

        val eligibleAttachments = originalMessage.attachments.filterNot { attachment ->
            if (attachment.size >= 20 shl 20) {  //20MB
                LOG.warn("The file was larger than 20MB.")
                hadFailures = true
                true
            } else {
                false
            }
        }

        val iterator = eligibleAttachments.chunked(Message.MAX_FILE_AMOUNT).iterator()

        fun processNextChunk() {
            val chunk = if (iterator.hasNext()) iterator.next() else {
                finalizeProxy(event.messageIdLong, attachments, hadFailures, result)
                return
            }
            channel.sendFiles(*chunk.map { attachment ->
                attachment.proxy.downloadAsFileUpload(attachment.fileName)
            }.toTypedArray())
                .addContent(originalMessage.jumpUrl)
                .queue({ message ->
                    message.attachments.mapTo(attachments) { messageAttachment ->
                        "[${messageAttachment.fileName}](${messageAttachment.url})"
                    }
                    processNextChunk()
                }, { throwable ->
                    LOG.info("An exception occurred when retrieving one of the attachments", throwable)
                    hadFailures = true
                    processNextChunk()
                })
        }
        processNextChunk()
        return result
    }

    private fun finalizeProxy(messageId: Long, attachments: List<String>, hadFailures: Boolean, result: CompletableFuture<Unit>) {
        val attachmentProxy = when {
            attachments.isNotEmpty() -> {
                AttachmentProxy(messageId, attachments, hadFailures)
            }

            hadFailures -> {
                AttachmentProxy(messageId, emptyList(), true)
            }

            else -> {
                null
            }
        }
        try {
            attachmentProxy?.let { attachmentProxyRepository.save(it) }
            result.complete(Unit)
        } catch (e: Exception) {
            result.completeExceptionally(e)
        }
    }
}
