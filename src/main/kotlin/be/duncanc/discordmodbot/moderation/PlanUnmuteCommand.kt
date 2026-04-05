package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.discord.SlashCommand
import be.duncanc.discordmodbot.discord.nicknameAndUsername
import be.duncanc.discordmodbot.logging.GuildLogger
import be.duncanc.discordmodbot.moderation.persistence.MuteRolesRepository
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.utils.TimeFormat
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

@Component
class PlanUnmuteCommand(
    private val muteService: MuteService,
    private val scheduledUnmuteService: ScheduledUnmuteService,
    private val guildLogger: GuildLogger,
    private val muteRolesRepository: MuteRolesRepository
) : ListenerAdapter(), SlashCommand {
    companion object {
        private const val COMMAND = "planunmute"
        private const val DESCRIPTION = "Schedule a user to be unmuted after a specified number of days."
        private const val OPTION_USER = "user"
        private const val OPTION_DAYS = "days"
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != COMMAND) return

        val member = event.member
        if (member == null) {
            event.reply("This command only works in a guild.").setEphemeral(true).queue()
            return
        }

        if (!member.hasPermission(Permission.MANAGE_ROLES)) {
            event.reply("You need manage roles permission to schedule an unmute.").setEphemeral(true).queue()
            return
        }

        val targetUserOption = event.getOption(OPTION_USER)
        if (targetUserOption == null) {
            event.reply("You need to mention a user.").setEphemeral(true).queue()
            return
        }

        val targetUserId = targetUserOption.asLong
        val targetMember = targetUserOption.asMember

        val days = event.getOption(OPTION_DAYS)?.asInt
        if (days == null || days <= 0) {
            event.reply("Please provide a valid number of days.").setEphemeral(true).queue()
            return
        }

        val guild = event.guild!!
        val muteRoles = muteRolesRepository.findById(guild.idLong)

        if (muteRoles.isEmpty) {
            event.reply("Mute role is not configured for this server.").setEphemeral(true).queue()
            return
        }

        val muteRole = guild.getRoleById(muteRoles.get().roleId)
        if (muteRole == null) {
            event.reply("Mute role is not configured for this server.").setEphemeral(true).queue()
            return
        }

        val isMuted = targetMember?.roles?.contains(muteRole) ?: muteService.isUserMuted(guild.idLong, targetUserId)

        if (!isMuted) {
            event.reply("This user is not muted.").setEphemeral(true).queue()
            return
        }

        val unmuteDateTime = OffsetDateTime.now().plusDays(days.toLong())
        scheduledUnmuteService.planUnmute(guild.idLong, targetUserId, unmuteDateTime)

        logScheduledMute(guild, targetUserId, member, unmuteDateTime)

        val targetMention = targetMember?.asMention ?: "<@$targetUserId>"

        event.reply(
            "Unmute has been planned for $targetMention on ${
                TimeFormat.DATE_SHORT_TIME_SHORT.atInstant(
                    unmuteDateTime.toInstant()
                )
            }."
        ).setEphemeral(true).queue()
    }

    private fun logScheduledMute(
        guild: net.dv8tion.jda.api.entities.Guild,
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

    override fun getCommandsData(): List<SlashCommandData> {
        return listOf(
            Commands.slash(COMMAND, DESCRIPTION)
                .addOptions(
                    OptionData(OptionType.USER, OPTION_USER, "The muted user to schedule unmute for").setRequired(true),
                    OptionData(OptionType.INTEGER, OPTION_DAYS, "Number of days until unmute").setRequired(true)
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_ROLES))
        )
    }
}
