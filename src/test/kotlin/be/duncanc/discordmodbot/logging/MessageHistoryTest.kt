package be.duncanc.discordmodbot.logging

import be.duncanc.discordmodbot.logging.persistence.DiscordMessage
import be.duncanc.discordmodbot.logging.persistence.DiscordMessageRepository
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageReference
import net.dv8tion.jda.api.entities.Mentions
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion
import net.dv8tion.jda.api.entities.sticker.StickerItem
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.events.message.MessageUpdateEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class MessageHistoryTest {
    @Mock
    private lateinit var discordMessageRepository: DiscordMessageRepository

    @Mock
    private lateinit var attachmentProxyCreator: AttachmentProxyCreator

    @Mock
    private lateinit var receivedEvent: MessageReceivedEvent

    @Mock
    private lateinit var updateEvent: MessageUpdateEvent

    @Mock
    private lateinit var message: Message

    @Mock
    private lateinit var guild: Guild

    @Mock
    private lateinit var channel: MessageChannelUnion

    @Mock
    private lateinit var author: User

    @Mock
    private lateinit var mentions: Mentions

    @Mock
    private lateinit var messageReference: MessageReference

    @Mock
    private lateinit var sticker: StickerItem

    private lateinit var messageContentEncryptor: MessageContentEncryptor
    private lateinit var messageHistory: MessageHistory

    @BeforeEach
    fun setUp() {
        messageContentEncryptor = MessageContentEncryptor(
            MessageEncryptionProperties(
                password = "test-message-encryption-password",
                salt = "0123456789abcdef0123456789abcdef"
            )
        )
        messageHistory = MessageHistory(discordMessageRepository, attachmentProxyCreator, messageContentEncryptor)
    }

    @Test
    fun `store message encrypts content before saving`() {
        stubReceivedMessage(content = "hello from discord")

        messageHistory.storeMessage(receivedEvent)

        val messageCaptor = argumentCaptor<DiscordMessage>()
        verify(discordMessageRepository).save(messageCaptor.capture())
        assertNotEquals("hello from discord", messageCaptor.firstValue.content)
        assertEquals("hello from discord", messageContentEncryptor.decrypt(messageCaptor.firstValue.content))
    }

    @Test
    fun `store message preserves the replied to message URL`() {
        stubReceivedMessage(content = "reply")
        whenever(message.messageReference).thenReturn(messageReference)
        whenever(messageReference.type).thenReturn(MessageReference.MessageReferenceType.DEFAULT)
        whenever(messageReference.guildIdLong).thenReturn(1L)
        whenever(messageReference.channelIdLong).thenReturn(10L)
        whenever(messageReference.messageIdLong).thenReturn(200L)

        messageHistory.storeMessage(receivedEvent)

        val messageCaptor = argumentCaptor<DiscordMessage>()
        verify(discordMessageRepository).save(messageCaptor.capture())
        assertEquals(
            "https://discord.com/channels/1/10/200",
            messageCaptor.firstValue.repliedToUrl
        )
    }

    @Test
    fun `store message preserves stickers`() {
        stubReceivedMessage(content = "sticker")
        whenever(message.stickers).thenReturn(listOf(sticker))
        whenever(sticker.name).thenReturn("wave")
        whenever(sticker.iconUrl).thenReturn("https://cdn.discordapp.com/sticker.png")

        messageHistory.storeMessage(receivedEvent)

        val messageCaptor = argumentCaptor<DiscordMessage>()
        verify(discordMessageRepository).save(messageCaptor.capture())
        assertEquals(
            "[wave](https://cdn.discordapp.com/sticker.png)\n",
            messageCaptor.firstValue.stickers
        )
    }

    @Test
    fun `update message encrypts content before saving`() {
        stubUpdatedMessage(content = "updated content")
        whenever(discordMessageRepository.existsById(100L)).thenReturn(true)
        whenever(discordMessageRepository.findById(100L)).thenReturn(
            Optional.of(
                DiscordMessage(
                    messageId = 100L,
                    guildId = 1L,
                    channelId = 10L,
                    userId = 20L,
                    content = messageContentEncryptor.encrypt("old content"),
                    emotes = "[wave](https://cdn.discordapp.com/emote.png)",
                    repliedToUrl = "https://discord.com/channels/1/10/50",
                    stickers = "[sticker](https://cdn.discordapp.com/sticker.png)"
                )
            )
        )

        messageHistory.updateMessage(updateEvent)

        val messageCaptor = argumentCaptor<DiscordMessage>()
        verify(discordMessageRepository).save(messageCaptor.capture())
        assertNotEquals("updated content", messageCaptor.firstValue.content)
        assertEquals("updated content", messageContentEncryptor.decrypt(messageCaptor.firstValue.content))
        assertEquals("[wave](https://cdn.discordapp.com/emote.png)", messageCaptor.firstValue.emotes)
        assertEquals("https://discord.com/channels/1/10/50", messageCaptor.firstValue.repliedToUrl)
        assertEquals("[sticker](https://cdn.discordapp.com/sticker.png)", messageCaptor.firstValue.stickers)
    }

    @Test
    fun `get message decrypts content before returning`() {
        whenever(discordMessageRepository.findById(100L)).thenReturn(
            Optional.of(
                DiscordMessage(
                    messageId = 100L,
                    guildId = 1L,
                    channelId = 10L,
                    userId = 20L,
                    content = messageContentEncryptor.encrypt("deleted content")
                )
            )
        )

        val result = messageHistory.getMessage(textChannelId = 10L, messageId = 100L)

        assertEquals("deleted content", result?.content)
        verify(discordMessageRepository).deleteById(100L)
    }

    @Test
    fun `bot messages are not encrypted or saved`() {
        stubReceivedMessage(content = "bot message", isBot = true)

        messageHistory.storeMessage(receivedEvent)

        verify(discordMessageRepository, never()).save(any())
    }

    private fun stubReceivedMessage(content: String, isBot: Boolean = false) {
        whenever(receivedEvent.isFromGuild).thenReturn(true)
        whenever(receivedEvent.message).thenReturn(message)
        stubMessage(
            content = content,
            isBot = isBot,
            includeStoredFields = !isBot,
            includeMentions = !isBot,
            includeAttachments = !isBot
        )
    }

    private fun stubUpdatedMessage(content: String) {
        whenever(updateEvent.isFromGuild).thenReturn(true)
        whenever(updateEvent.messageIdLong).thenReturn(100L)
        whenever(updateEvent.message).thenReturn(message)
        stubMessage(content = content, includeMentions = false, includeAttachments = false, includeBotCheck = false)
    }

    private fun stubMessage(
        content: String,
        isBot: Boolean = false,
        includeStoredFields: Boolean = true,
        includeMentions: Boolean = true,
        includeAttachments: Boolean = true,
        includeBotCheck: Boolean = true
    ) {
        whenever(message.author).thenReturn(author)
        whenever(message.contentDisplay).thenReturn(content)
        if (includeBotCheck) {
            whenever(author.isBot).thenReturn(isBot)
        }
        if (includeStoredFields) {
            whenever(message.idLong).thenReturn(100L)
            whenever(message.guild).thenReturn(guild)
            whenever(message.channel).thenReturn(channel)
            whenever(guild.idLong).thenReturn(1L)
            whenever(channel.idLong).thenReturn(10L)
            whenever(author.idLong).thenReturn(20L)
        }
        if (includeMentions) {
            whenever(message.mentions).thenReturn(mentions)
            whenever(mentions.customEmojis).thenReturn(emptyList())
            whenever(message.stickers).thenReturn(emptyList())
        }
        if (includeAttachments) {
            whenever(message.attachments).thenReturn(emptyList())
        }
    }
}
