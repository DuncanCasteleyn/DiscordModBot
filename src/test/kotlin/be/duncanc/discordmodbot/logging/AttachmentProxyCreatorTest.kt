package be.duncanc.discordmodbot.logging

import be.duncanc.discordmodbot.logging.persistence.AttachmentProxy
import be.duncanc.discordmodbot.logging.persistence.AttachmentProxyRepository
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction
import net.dv8tion.jda.api.utils.FileUpload
import net.dv8tion.jda.api.utils.NamedAttachmentProxy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer

@ExtendWith(MockitoExtension::class)
class AttachmentProxyCreatorTest {
    @Mock
    private lateinit var attachmentProxyRepository: AttachmentProxyRepository

    @Mock
    private lateinit var receivedEvent: MessageReceivedEvent

    @Mock
    private lateinit var jda: JDA

    @Mock
    private lateinit var channel: TextChannel

    @Mock
    private lateinit var message: Message

    @Mock
    private lateinit var author: User

    private lateinit var attachmentProxyCreator: AttachmentProxyCreator

    @BeforeEach
    fun setUp() {
        attachmentProxyCreator = AttachmentProxyCreator(
            attachmentProxyRepository,
            AttachmentCacheProperties(channelId = 1L)
        )
    }

    @Test
    fun `proxy message attachments uploads attachments in a single batch and saves their urls`() {
        stubGuildMessage(smallAttachment(), smallAttachment(), smallAttachment())
        whenever(receivedEvent.messageIdLong).thenReturn(100L)
        whenever(message.jumpUrl).thenReturn("https://discord.com/channels/1/10/100")
        stubSuccessfulUpload(uploadedAttachment(), uploadedAttachment(), uploadedAttachment())

        attachmentProxyCreator.proxyMessageAttachments(receivedEvent)

        verify(channel).sendFiles(anyVararg<FileUpload>())
        val proxyCaptor = argumentCaptor<AttachmentProxy>()
        verify(attachmentProxyRepository).save(proxyCaptor.capture())
        assertEquals(
            listOf(
                "[file.png](https://cdn.discordapp.com/attachments/1/10/100/file.png)",
                "[file.png](https://cdn.discordapp.com/attachments/1/10/100/file.png)",
                "[file.png](https://cdn.discordapp.com/attachments/1/10/100/file.png)"
            ),
            proxyCaptor.firstValue.attachmentUrls
        )
        assertFalse(proxyCaptor.firstValue.hadFailedCaches)
        assertEquals(100L, proxyCaptor.firstValue.messageId)
    }

    @Test
    fun `proxy message attachments splits uploads into batches of at most 10 files`() {
        stubGuildMessage(*Array(11) { smallAttachment() })
        whenever(receivedEvent.messageIdLong).thenReturn(100L)
        whenever(message.jumpUrl).thenReturn("https://discord.com/channels/1/10/100")
        stubSuccessfulUpload(*Array(11) { uploadedAttachment() })

        attachmentProxyCreator.proxyMessageAttachments(receivedEvent)

        verify(channel, times(2)).sendFiles(anyVararg<FileUpload>())
        val proxyCaptor = argumentCaptor<AttachmentProxy>()
        verify(attachmentProxyRepository).save(proxyCaptor.capture())
        assertEquals(11, proxyCaptor.firstValue.attachmentUrls.size)
        assertFalse(proxyCaptor.firstValue.hadFailedCaches)
    }

    @Test
    fun `proxy message attachments skips attachments of 20MB or larger and marks a failure`() {
        val oversizedAttachment = mock<Message.Attachment>()
        whenever(oversizedAttachment.size).thenReturn(20 shl 20)
        stubGuildMessage(oversizedAttachment, smallAttachment())
        whenever(receivedEvent.messageIdLong).thenReturn(100L)
        whenever(message.jumpUrl).thenReturn("https://discord.com/channels/1/10/100")
        stubSuccessfulUpload(uploadedAttachment())

        attachmentProxyCreator.proxyMessageAttachments(receivedEvent)

        verify(channel).sendFiles(anyVararg<FileUpload>())
        val proxyCaptor = argumentCaptor<AttachmentProxy>()
        verify(attachmentProxyRepository).save(proxyCaptor.capture())
        assertEquals(
            listOf("[file.png](https://cdn.discordapp.com/attachments/1/10/100/file.png)"),
            proxyCaptor.firstValue.attachmentUrls
        )
        assertTrue(proxyCaptor.firstValue.hadFailedCaches)
    }

    @Test
    fun `proxy message attachments marks a failure when uploading fails`() {
        stubGuildMessage(smallAttachment())
        whenever(receivedEvent.messageIdLong).thenReturn(100L)
        whenever(message.jumpUrl).thenReturn("https://discord.com/channels/1/10/100")
        stubFailedUpload()

        attachmentProxyCreator.proxyMessageAttachments(receivedEvent)

        verify(channel).sendFiles(anyVararg<FileUpload>())
        val proxyCaptor = argumentCaptor<AttachmentProxy>()
        verify(attachmentProxyRepository).save(proxyCaptor.capture())
        assertEquals(emptyList<String>(), proxyCaptor.firstValue.attachmentUrls)
        assertTrue(proxyCaptor.firstValue.hadFailedCaches)
    }

    @Test
    fun `proxy message attachments marks a failure when the cache channel is missing`() {
        whenever(receivedEvent.isFromGuild).thenReturn(true)
        whenever(receivedEvent.author).thenReturn(author)
        whenever(author.isBot).thenReturn(false)
        whenever(receivedEvent.jda).thenReturn(jda)
        whenever(jda.getTextChannelById(1L)).thenReturn(null)
        whenever(receivedEvent.message).thenReturn(message)
        whenever(receivedEvent.messageIdLong).thenReturn(100L)

        attachmentProxyCreator.proxyMessageAttachments(receivedEvent)

        val proxyCaptor = argumentCaptor<AttachmentProxy>()
        verify(attachmentProxyRepository).save(proxyCaptor.capture())
        assertEquals(emptyList<String>(), proxyCaptor.firstValue.attachmentUrls)
        assertTrue(proxyCaptor.firstValue.hadFailedCaches)
    }

    @Test
    fun `proxy message attachments completes the returned future only after all chunks are persisted`() {
        stubGuildMessage(*Array(11) { smallAttachment() })
        whenever(receivedEvent.messageIdLong).thenReturn(100L)
        whenever(message.jumpUrl).thenReturn("https://discord.com/channels/1/10/100")
        val queuedBatches = stubDelayedUpload(*Array(11) { uploadedAttachment() })

        val future = attachmentProxyCreator.proxyMessageAttachments(receivedEvent)

        assertFalse(future.isDone)
        verify(attachmentProxyRepository, never()).save(any())

        queuedBatches.removeFirst().succeed()

        verify(channel, times(2)).sendFiles(anyVararg<FileUpload>())
        assertFalse(future.isDone)
        verify(attachmentProxyRepository, never()).save(any())

        queuedBatches.removeFirst().succeed()

        val proxyCaptor = argumentCaptor<AttachmentProxy>()
        verify(attachmentProxyRepository).save(proxyCaptor.capture())
        assertEquals(11, proxyCaptor.firstValue.attachmentUrls.size)
        assertFalse(proxyCaptor.firstValue.hadFailedCaches)
        assertTrue(future.isDone)
    }

    @Test
    fun `proxy message attachments completes the returned future only after persisting a failed upload`() {
        stubGuildMessage(smallAttachment())
        whenever(receivedEvent.messageIdLong).thenReturn(100L)
        whenever(message.jumpUrl).thenReturn("https://discord.com/channels/1/10/100")
        val action = mock<MessageCreateAction> {
            on(it.addContent(any<String>())).thenReturn(it)
        }
        whenever(channel.sendFiles(anyVararg<FileUpload>())).thenReturn(action)
        val failureConsumer = AtomicReference<Consumer<Throwable>>()
        doAnswer { invocation ->
            failureConsumer.set(invocation.component2<Consumer<Throwable>>())
            null
        }.whenever(action).queue(any(), any())

        val future = attachmentProxyCreator.proxyMessageAttachments(receivedEvent)

        assertFalse(future.isDone)
        verify(attachmentProxyRepository, never()).save(any())

        failureConsumer.get().accept(RuntimeException("upload failed"))

        val proxyCaptor = argumentCaptor<AttachmentProxy>()
        verify(attachmentProxyRepository).save(proxyCaptor.capture())
        assertTrue(proxyCaptor.firstValue.hadFailedCaches)
        assertTrue(future.isDone)
    }

    @Test
    fun `bot messages are not proxied`() {
        whenever(receivedEvent.isFromGuild).thenReturn(true)
        whenever(receivedEvent.author).thenReturn(author)
        whenever(author.isBot).thenReturn(true)

        attachmentProxyCreator.proxyMessageAttachments(receivedEvent)

        verify(attachmentProxyRepository, never()).save(any())
    }

    @Test
    fun `private messages are not proxied`() {
        whenever(receivedEvent.isFromGuild).thenReturn(false)

        attachmentProxyCreator.proxyMessageAttachments(receivedEvent)

        verify(attachmentProxyRepository, never()).save(any())
    }

    private fun stubGuildMessage(vararg attachments: Message.Attachment) {
        whenever(receivedEvent.isFromGuild).thenReturn(true)
        whenever(receivedEvent.author).thenReturn(author)
        whenever(author.isBot).thenReturn(false)
        whenever(receivedEvent.jda).thenReturn(jda)
        whenever(jda.getTextChannelById(1L)).thenReturn(channel)
        whenever(receivedEvent.message).thenReturn(message)
        whenever(message.attachments).thenReturn(attachments.toList())
    }

    private fun smallAttachment(): Message.Attachment {
        val attachment = mock<Message.Attachment>()
        val proxy = mock<NamedAttachmentProxy>()
        whenever(attachment.size).thenReturn(1024)
        whenever(attachment.fileName).thenReturn("file.png")
        whenever(attachment.proxy).thenReturn(proxy)
        whenever(proxy.downloadAsFileUpload("file.png"))
            .thenReturn(FileUpload.fromData("data".toByteArray(), "file.png"))
        return attachment
    }

    private fun uploadedAttachment(): Message.Attachment {
        val uploadedAttachment = mock<Message.Attachment>()
        whenever(uploadedAttachment.fileName).thenReturn("file.png")
        whenever(uploadedAttachment.url).thenReturn("https://cdn.discordapp.com/attachments/1/10/100/file.png")
        return uploadedAttachment
    }

    private fun stubSuccessfulUpload(vararg uploadedAttachments: Message.Attachment): MessageCreateAction {
        val action = mock<MessageCreateAction>()
        whenever(channel.sendFiles(anyVararg<FileUpload>())).thenReturn(action)
        whenever(action.addContent(any<String>())).thenReturn(action)
        val batchResults = ArrayDeque(
            uploadedAttachments.toList().chunked(Message.MAX_FILE_AMOUNT).map { batchAttachments ->
                mock<Message> {
                    on(it.attachments).thenReturn(batchAttachments)
                }
            }
        )
        doAnswer { invocation ->
            invocation.component1<Consumer<Message>>().accept(batchResults.removeFirst())
            null
        }.whenever(action).queue(any(), any())
        return action
    }

    private fun stubFailedUpload(): MessageCreateAction {
        val action = mock<MessageCreateAction>()
        whenever(channel.sendFiles(anyVararg<FileUpload>())).thenReturn(action)
        whenever(action.addContent(any<String>())).thenReturn(action)
        doAnswer { invocation ->
            invocation.component2<Consumer<Throwable>>().accept(RuntimeException("upload failed"))
            null
        }.whenever(action).queue(any(), any())
        return action
    }

    private fun stubDelayedUpload(vararg uploadedAttachments: Message.Attachment): ArrayDeque<QueuedUploadBatch> {
        val queuedBatches = ArrayDeque<QueuedUploadBatch>()
        val batches = uploadedAttachments.toList().chunked(Message.MAX_FILE_AMOUNT)
        val batchResults = ArrayDeque(batches.map { batchAttachments ->
            mock<Message> {
                on(it.attachments).thenReturn(batchAttachments)
            }
        })
        val actions = batches.map {
            mock<MessageCreateAction> {
                on(it.addContent(any<String>())).thenReturn(it)
            }
        }
        whenever(channel.sendFiles(anyVararg<FileUpload>()))
            .thenReturn(actions.first(), *actions.drop(1).toTypedArray())
        actions.forEach { action ->
            doAnswer { invocation ->
                queuedBatches.addLast(
                    QueuedUploadBatch(
                        batchResults.removeFirst(),
                        invocation.component1<Consumer<Message>>()
                    )
                )
                null
            }.whenever(action).queue(any(), any())
        }
        return queuedBatches
    }

    private class QueuedUploadBatch(
        val uploadedMessage: Message,
        private val successConsumer: Consumer<Message>
    ) {
        fun succeed() = successConsumer.accept(uploadedMessage)
    }
}
