package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.discord.nicknameAndUsername
import be.duncanc.discordmodbot.logging.GuildLogger
import be.duncanc.discordmodbot.moderation.persistence.AudioFilterSettings
import be.duncanc.discordmodbot.moderation.persistence.AudioFilterSettingsRepository
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.awt.Color
import java.time.Duration
import java.time.Instant
import java.util.Locale
import java.util.concurrent.TimeUnit

@Service
@Transactional(readOnly = true)
class AudioFilterService(
    private val audioFilterSettingsRepository: AudioFilterSettingsRepository,
    private val guildLogger: GuildLogger
) {
    companion object {
        private val LOG = LoggerFactory.getLogger(AudioFilterService::class.java)

        private const val AUDIO_DELETE_REASON = "Posted a blocked audio file"
        private const val AUDIO_TIMEOUT_REASON = "Posted a blocked audio file"

        private const val WARNING_DELETE_DELAY_SECONDS = 30L

        private val AUDIO_EXTENSIONS =
            setOf("mp3", "wav", "ogg", "oga", "flac", "m4a", "m4b", "aac", "wma", "opus", "mid", "midi", "amr", "aiff", "aif")

        const val MAX_TIMEOUT_MINUTES = 40320L // 28 days, the maximum Discord allows
    }

    fun getSettings(guildId: Long): AudioFilterSettings? {
        return audioFilterSettingsRepository.findById(guildId).orElse(null)
    }

    @Transactional
    fun enableFilter(guildId: Long, timeoutMinutes: Long?): AudioFilterSettings {
        return audioFilterSettingsRepository.save(AudioFilterSettings(guildId, timeoutMinutes))
    }

    @Transactional
    fun disableFilter(guildId: Long) {
        audioFilterSettingsRepository.deleteById(guildId)
    }

    @Transactional
    fun setTimeout(guildId: Long, timeoutMinutes: Long?): AudioFilterSettings? {
        val settings = getSettings(guildId) ?: return null
        return audioFilterSettingsRepository.save(settings.copy(timeoutMinutes = timeoutMinutes))
    }

    @Transactional
    fun clearGuildState(guildId: Long) {
        audioFilterSettingsRepository.deleteById(guildId)
    }

    fun handleMessage(event: MessageReceivedEvent) {
        if (!event.isFromGuild || event.author.isBot || event.isWebhookMessage) {
            return
        }

        val guild = event.guild
        val settings = getSettings(guild.idLong) ?: return

        val member = event.member ?: return
        val selfMember = guild.selfMember
        if (!selfMember.hasPermission(Permission.MESSAGE_MANAGE)) {
            return
        }

        if (member.hasPermission(Permission.ADMINISTRATOR) || !selfMember.canInteract(member)) {
            return
        }

        val audioAttachments = event.message.attachments.filter { isAudio(it) }
        if (audioAttachments.isEmpty()) {
            return
        }

        val fileNames = audioAttachments.joinToString(", ") { it.fileName }

        event.message.delete().reason(AUDIO_DELETE_REASON).queue(
            {
                logAudioDeletion(guild, member, event.channel.asMention, fileNames, settings.timeoutMinutes)
                applyTimeout(guild, member, settings.timeoutMinutes)
                postWarning(event.channel, member)
            },
            { throwable ->
                LOG.warn("Failed to delete message with audio from {} in guild {}", member.id, guild.id, throwable)
            }
        )
    }

    private fun isAudio(attachment: Message.Attachment): Boolean {
        val contentType = attachment.contentType
        return when {
            contentType != null && contentType.startsWith("audio/") -> true
            contentType == null || contentType == "application/octet-stream" -> {
                val extension = attachment.fileExtension?.removePrefix(".")?.lowercase(Locale.ROOT)
                extension != null && extension in AUDIO_EXTENSIONS
            }

            else -> false
        }
    }

    private fun applyTimeout(guild: Guild, member: Member, timeoutMinutes: Long?) {
        if (timeoutMinutes == null) {
            return
        }

        val selfMember = guild.selfMember
        if (!selfMember.hasPermission(Permission.MODERATE_MEMBERS) || !selfMember.canInteract(member)) {
            LOG.warn("Unable to timeout {} in guild {} due to missing permissions or role hierarchy", member.id, guild.id)
            return
        }

        member.timeoutFor(Duration.ofMinutes(timeoutMinutes))
            .reason(AUDIO_TIMEOUT_REASON)
            .queue()
    }

    private fun logAudioDeletion(
        guild: Guild,
        member: Member,
        channelMention: String,
        fileNames: String,
        timeoutMinutes: Long?
    ) {
        val logEmbed = EmbedBuilder()
            .setColor(Color.ORANGE)
            .setTitle("Message with audio file deleted")
            .addField("User", member.nicknameAndUsername, true)
            .addField("Channel", channelMention, true)
            .addField("File(s)", fileNames, false)
            .addField("Reason", AUDIO_DELETE_REASON, false)
            .addField("Timeout", timeoutMinutes?.let { "$it minutes" } ?: "None", true)

        guildLogger.log(logEmbed, member.user, guild, actionType = GuildLogger.LogTypeAction.MODERATOR)
    }

    private fun postWarning(channel: MessageChannelUnion, member: Member) {
        val user = member.user

        val embed = EmbedBuilder()
            .setColor(Color.RED)
            .setAuthor(user.name, null, user.effectiveAvatarUrl)
            .setTitle("Audio file removed")
            .setDescription("${member.asMention} audio files are not allowed in this server.")
            .setTimestamp(Instant.now())
            .build()

        channel.sendMessageEmbeds(embed).queue(
            { warning -> warning.delete().queueAfter(WARNING_DELETE_DELAY_SECONDS, TimeUnit.SECONDS) },
            { throwable -> LOG.warn("Failed to send audio filter warning in guild {}", member.guild.id, throwable) }
        )
    }
}
