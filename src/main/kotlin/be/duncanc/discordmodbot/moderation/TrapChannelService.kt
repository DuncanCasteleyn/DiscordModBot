package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.discord.nicknameAndUsername
import be.duncanc.discordmodbot.logging.GuildLogger
import be.duncanc.discordmodbot.moderation.persistence.GuildTrapChannel
import be.duncanc.discordmodbot.moderation.persistence.GuildTrapChannelRepository
import be.duncanc.discordmodbot.moderation.persistence.TrapChannelUnban
import be.duncanc.discordmodbot.moderation.persistence.TrapChannelUnbanRepository
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.UserSnowflake
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.requests.ErrorResponse
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.awt.Color
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@Service
@Transactional(readOnly = true)
class TrapChannelService(
    private val guildTrapChannelRepository: GuildTrapChannelRepository,
    private val trapChannelUnbanRepository: TrapChannelUnbanRepository,
    private val guildLogger: GuildLogger,
    @Lazy
    private val jda: JDA
) {
    companion object {
        private val LOG = LoggerFactory.getLogger(TrapChannelService::class.java)

        private const val TRAP_BAN_REASON = "Triggered the configured spam trap channel"
        private const val TRAP_UNBAN_REASON = "Automatic trap channel release"
        private const val AUTO_UNBAN_DELAY_HOURS = 1L
        private const val TRAP_WARNING_MESSAGE =
            "%s was automatically banned for posting in this channel. This channel is a spambot trap. Do not post here."
    }

    private val pendingTrapActions = ConcurrentHashMap.newKeySet<String>()

    fun getTrapChannelId(guildId: Long): Long? {
        return guildTrapChannelRepository.findById(guildId).orElse(null)?.channelId
    }

    fun getTrapChannel(guildId: Long, guild: Guild): TextChannel? {
        return getTrapChannelId(guildId)?.let(guild::getTextChannelById)
    }

    @Transactional
    fun setTrapChannel(guildId: Long, channelId: Long) {
        guildTrapChannelRepository.save(GuildTrapChannel(guildId, channelId))
    }

    @Transactional
    fun clearTrapChannel(guildId: Long) {
        guildTrapChannelRepository.deleteById(guildId)
    }

    @Transactional
    fun clearGuildState(guildId: Long) {
        guildTrapChannelRepository.deleteById(guildId)
        trapChannelUnbanRepository.deleteAllByGuildId(guildId)
    }

    fun handleTrapMessage(event: MessageReceivedEvent) {
        if (!event.isFromGuild || event.author.isBot || event.isWebhookMessage) {
            return
        }

        val guild = event.guild
        val configuredChannelId = getTrapChannelId(guild.idLong) ?: return
        if (configuredChannelId != event.channel.idLong) {
            return
        }

        val member = event.member ?: return
        val selfMember = guild.selfMember
        if (!selfMember.hasPermission(Permission.BAN_MEMBERS) || !selfMember.canInteract(member)) {
            LOG.warn("Unable to trap {} in guild {} due to missing permissions or role hierarchy", member.id, guild.id)
            return
        }

        if (member.hasPermission(Permission.ADMINISTRATOR) || member.hasPermission(Permission.BAN_MEMBERS)) {
            return
        }

        val actionKey = guild.id + ":" + member.id
        if (!pendingTrapActions.add(actionKey)) {
            return
        }

        val scheduledUnbanAt = OffsetDateTime.now().plusHours(AUTO_UNBAN_DELAY_HOURS)

        guild.ban(member, 10, TimeUnit.MINUTES)
            .reason(TRAP_BAN_REASON)
            .queue(
                {
                    trapChannelUnbanRepository.save(TrapChannelUnban(guild.idLong, member.idLong, scheduledUnbanAt))
                    logTrapBan(guild, member, event.channel.asMention, scheduledUnbanAt)
                    postTrapWarning(event.channel, member.idLong)
                    pendingTrapActions.remove(actionKey)
                },
                { throwable ->
                    LOG.warn("Failed to trap {} in guild {}", member.id, guild.id, throwable)
                    pendingTrapActions.remove(actionKey)
                }
            )
    }

    @Scheduled(cron = "@hourly")
    @Transactional
    fun performPendingUnbans() {
        trapChannelUnbanRepository.findAllByUnbanAtLessThanEqual(OffsetDateTime.now())
            .forEach { scheduledUnban ->
                val guild = jda.getGuildById(scheduledUnban.guildId)
                if (guild == null) {
                    LOG.warn(
                        "Dropping pending trap unban for user {} in guild {} because the guild is unavailable",
                        scheduledUnban.userId,
                        scheduledUnban.guildId
                    )
                    trapChannelUnbanRepository.delete(scheduledUnban)
                    return@forEach
                }

                guild.unban(UserSnowflake.fromId(scheduledUnban.userId))
                    .reason(TRAP_UNBAN_REASON)
                    .queue(
                        {
                            trapChannelUnbanRepository.delete(scheduledUnban)
                            logTrapUnban(guild, scheduledUnban.userId)
                        },
                        { throwable ->
                            val errorResponse = (throwable as? ErrorResponseException)?.errorResponse
                            if (errorResponse == ErrorResponse.UNKNOWN_BAN) {
                                trapChannelUnbanRepository.delete(scheduledUnban)
                                return@queue
                            }

                            LOG.warn(
                                "Failed to automatically unban {} in guild {}",
                                scheduledUnban.userId,
                                scheduledUnban.guildId,
                                throwable
                            )
                        }
                    )
            }
    }

    private fun logTrapBan(
        guild: Guild,
        member: Member,
        trapChannel: String,
        scheduledUnbanAt: OffsetDateTime
    ) {
        val logEmbed = EmbedBuilder()
            .setColor(Color.RED)
            .setTitle("User banned by trap channel")
            .addField("User", member.nicknameAndUsername, true)
            .addField("Channel", trapChannel, true)
            .addField("Reason", TRAP_BAN_REASON, false)
            .addField("Planned unban", scheduledUnbanAt.toString(), false)

        guildLogger.log(logEmbed, member.user, guild, actionType = GuildLogger.LogTypeAction.MODERATOR)
    }

    private fun logTrapUnban(guild: Guild, userId: Long) {
        val logEmbed = EmbedBuilder()
            .setColor(Color.GREEN)
            .setTitle("User unbanned after trap channel")
            .addField("User", "<@$userId>", true)
            .addField("Reason", TRAP_UNBAN_REASON, false)

        guildLogger.log(logEmbed, guild = guild, actionType = GuildLogger.LogTypeAction.MODERATOR)
    }

    private fun postTrapWarning(channel: MessageChannelUnion, userId: Long) {
        channel.sendMessage(TRAP_WARNING_MESSAGE.format("<@$userId>")).queue()
    }
}
