package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.discord.nicknameAndUsername
import be.duncanc.discordmodbot.logging.GuildLogger
import be.duncanc.discordmodbot.moderation.persistence.MuteRolesRepository
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.utils.TimeFormat
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

@Service
class UnmutePlanningService(
    private val muteService: MuteService,
    private val scheduledUnmuteService: ScheduledUnmuteService,
    private val guildLogger: GuildLogger,
    private val muteRolesRepository: MuteRolesRepository
) {
    fun planUnmute(guild: Guild, targetUserId: Long, moderator: Member, days: Int): OffsetDateTime {
        val muteRoles = muteRolesRepository.findById(guild.idLong)

        if (muteRoles.isEmpty) {
            throw IllegalStateException("Mute role is not configured for this server.")
        }

        val muteRole = guild.getRoleById(muteRoles.get().roleId)
            ?: throw IllegalStateException("Mute role is not configured for this server.")

        val targetMember = guild.getMemberById(targetUserId)
        val isMuted = targetMember?.roles?.contains(muteRole) ?: muteService.isUserMuted(guild.idLong, targetUserId)

        if (!isMuted) {
            throw IllegalStateException("This user is not muted.")
        }

        val unmuteDateTime = OffsetDateTime.now().plusDays(days.toLong())
        scheduledUnmuteService.planUnmute(guild.idLong, targetUserId, unmuteDateTime)

        logScheduledUnmute(guild, targetUserId, moderator, unmuteDateTime)
        return unmuteDateTime
    }

    private fun logScheduledUnmute(
        guild: Guild,
        targetUserId: Long,
        moderator: Member,
        unmuteDateTime: OffsetDateTime
    ) {
        val targetMember = guild.getMemberById(targetUserId)
        val targetUser = guild.jda.getUserById(targetUserId)

        val logEmbed = EmbedBuilder()
            .setColor(GuildLogger.LIGHT_BLUE)
            .setTitle("User unmute planned")
            .addField(
                "User",
                targetMember?.nicknameAndUsername ?: targetUser?.name ?: "<@$targetUserId>",
                true
            )
            .addField("Moderator", guild.getMember(moderator)?.nicknameAndUsername ?: moderator.user.name, true)
            .addField(
                "Unmute planned after",
                TimeFormat.DATE_SHORT_TIME_SHORT.atInstant(unmuteDateTime.toInstant()).toString(),
                false
            )

        guildLogger.log(logEmbed, targetUser, guild, actionType = GuildLogger.LogTypeAction.MODERATOR)
    }
}
