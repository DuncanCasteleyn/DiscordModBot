package be.duncanc.discordmodbot.logging

import be.duncanc.discordmodbot.logging.persistence.DiscordMessage
import be.duncanc.discordmodbot.logging.persistence.DiscordMessageRepository
import net.dv8tion.jda.api.entities.emoji.CustomEmoji
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.events.message.MessageUpdateEvent
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

/**
 * This class provides a buffer that will store Message objects so that they can
 * be accessed after being deleted on discord.
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
    fun storeMessage(event: MessageReceivedEvent) {
        if (!event.isFromGuild) {
            return
        }

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
            message.timeCreated.toInstant().toEpochMilli(),
            linkEmotes(message.mentions.customEmojis)
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
    fun updateMessage(event: MessageUpdateEvent) {
        if (!event.isFromGuild) {
            return
        }

        if (discordMessageRepository.existsById(event.messageIdLong)) {
            val message = event.message
            val existingMessage = discordMessageRepository.findById(message.idLong).orElse(null)
            val discordMessage = DiscordMessage(
                message.idLong,
                message.guild.idLong,
                message.channel.idLong,
                message.author.idLong,
                message.contentDisplay,
                existingMessage?.createdAtEpochMillis ?: message.timeCreated.toInstant().toEpochMilli(),
                existingMessage?.emotes
            )
            discordMessageRepository.save(discordMessage)
        }
    }

    fun findRecentMessages(guildId: Long, userId: Long, since: OffsetDateTime): List<StoredMessageReference> {
        val cutoffEpochMillis = since.toInstant().toEpochMilli()

        return discordMessageRepository.findAll()
            .filter {
                it.guildId == guildId &&
                        it.userId == userId &&
                        it.createdAtEpochMillis >= cutoffEpochMillis
            }
            .map {
                StoredMessageReference(
                    it.messageId,
                    it.guildId,
                    it.channelId,
                    it.userId,
                    it.content,
                    it.createdAtEpochMillis
                )
            }
            .toList()
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

    private fun linkEmotes(emotes: List<CustomEmoji>): String? {
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
