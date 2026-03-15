package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.discord.SlashCommand
import be.duncanc.discordmodbot.discord.messageTimeFormat
import be.duncanc.discordmodbot.discord.nicknameAndUsername
import be.duncanc.discordmodbot.logging.GuildLogger
import be.duncanc.discordmodbot.moderation.persistence.GuildWarnPointsSettings
import be.duncanc.discordmodbot.moderation.persistence.GuildWarnPointsSettingsRepository
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.awt.Color
import java.time.OffsetDateTime

@Component
class AddWarnPointsCommand(
    private val guildWarnPointsService: GuildWarnPointsService,
    private val guildWarnPointsSettingsRepository: GuildWarnPointsSettingsRepository,
    private val muteRole: MuteRole
) : ListenerAdapter(), SlashCommand {
    companion object {
        private const val COMMAND = "addwarnpoints"
        private const val DESCRIPTION = "Add warn points to a user, optionally muting or kicking them."
        private const val OPTION_USER = "user"
        private const val OPTION_POINTS = "points"
        private const val OPTION_DAYS = "days"
        private const val OPTION_ACTION = "action"
        private const val OPTION_REASON = "reason"

        val LOG: Logger = LoggerFactory.getLogger(AddWarnPointsCommand::class.java)
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != COMMAND) return

        val member = event.member
        if (member == null) {
            event.reply("This command only works in a guild.").setEphemeral(true).queue()
            return
        }

        if (!member.hasPermission(Permission.KICK_MEMBERS)) {
            event.reply("You need kick members permission to add warn points.").setEphemeral(true).queue()
            return
        }

        val targetMember = event.getOption(OPTION_USER)?.asMember
        if (targetMember == null) {
            event.reply("You need to mention a user that is still in the server.").setEphemeral(true).queue()
            return
        }

        if (member.canInteract(targetMember) != true) {
            event.reply("You can't interact with this member.").setEphemeral(true).queue()
            return
        }

        val points = event.getOption(OPTION_POINTS)?.asInt
        if (points == null || points < 1) {
            event.reply("Points must be at least 1.").setEphemeral(true).queue()
            return
        }

        val days = event.getOption(OPTION_DAYS)?.asInt
        if (days == null || days < 1) {
            event.reply("Days must be at least 1.").setEphemeral(true).queue()
            return
        }

        val action = event.getOption(OPTION_ACTION)?.asInt ?: 0
        if (action < 0 || action > 2) {
            event.reply("Action must be 0 (None), 1 (Mute), or 2 (Kick).").setEphemeral(true).queue()
            return
        }

        val reason = event.getOption(OPTION_REASON)?.asString ?: "No reason provided"
        if (reason.length > 1024) {
            event.reply("Reason must be 1024 characters or less.").setEphemeral(true).queue()
            return
        }

        val guildId = event.guild!!.idLong
        val guildPointsSettings = guildWarnPointsSettingsRepository.findById(guildId)
            .orElse(GuildWarnPointsSettings(guildId, announceChannelId = -1))

        if (points > guildPointsSettings.maxPointsPerReason) {
            event.reply("The maximum points per reason is ${guildPointsSettings.maxPointsPerReason}.")
                .setEphemeral(true).queue()
            return
        }

        if (guildPointsSettings.announceChannelId.let { event.jda.getTextChannelById(it) == null }) {
            event.reply("The announcement channel is not configured. Please contact an administrator.")
                .setEphemeral(true).queue()
            return
        }

        event.deferReply(true).queue { hook ->
            try {
                processWarnPoints(event, member, targetMember, points, days, action, reason, guildPointsSettings, hook)
            } catch (t: Throwable) {
                LOG.error("Error processing warn points", t)
                hook.editOriginal("Error: ${t.message}").queue()
            }
        }
    }

    private fun processWarnPoints(
        event: SlashCommandInteractionEvent,
        moderator: Member,
        targetMember: Member,
        points: Int,
        days: Int,
        action: Int,
        reason: String,
        guildPointsSettings: GuildWarnPointsSettings,
        hook: InteractionHook
    ) {
        val guild = event.guild!!
        val expireDate = OffsetDateTime.now().plusDays(days.toLong())

        val guildWarnPoint = guildWarnPointsService.addWarnPoint(
            targetMember.idLong,
            guild.idLong,
            points,
            moderator.idLong,
            reason,
            expireDate
        )

        val totalPoints = guildWarnPointsService.getActivePointsCount(guild.idLong, targetMember.idLong)

        performChecks(guildPointsSettings, targetMember.user, guild)

        logAddPoints(
            moderator,
            targetMember.user,
            reason,
            points,
            guildWarnPoint.id,
            expireDate,
            action.toByte(),
            guild
        )

        when (action) {
            1 -> {
                val muteRole = try {
                    muteRole.getMuteRole(guild)
                } catch (e: IllegalStateException) {
                    hook.editOriginal("Warn points added, but mute role is not configured.").queue()
                    return
                }
                guild.addRoleToMember(targetMember, muteRole).reason(reason).queue()
            }

            2 -> {
                guild.kick(targetMember).reason(reason).queue()
            }
        }

        informUserAndModerator(moderator, targetMember, reason, totalPoints, hook, action.toByte())
    }

    private fun performChecks(
        guildWarnPointsSettings: GuildWarnPointsSettings,
        user: User,
        guild: net.dv8tion.jda.api.entities.Guild
    ) {
        val points = guildWarnPointsService.getActivePointsCount(guild.idLong, user.idLong)

        if (points >= guildWarnPointsSettings.announcePointsSummaryLimit) {
            val messageBuilder = StringBuilder().append("@everyone ")
                .append(user.asMention)
                .append(" has reached the limit of points set by your server administrator.\n\n")
                .append("Summary of active points:")

            guildWarnPointsService.getActiveWarnings(guild.idLong, user.idLong).forEach {
                messageBuilder.append("\n\n").append(it.points).append(" point(s) added by ")
                    .append(guild.getMemberById(it.creatorId)?.nicknameAndUsername)
                    .append(" on ").append(it.creationDate.format(messageTimeFormat)).append('\n')
                    .append("Reason: ").append(it.reason)
                    .append("\nExpires on: ").append(it.expireDate.format(messageTimeFormat))
            }

            val messages = net.dv8tion.jda.api.utils.SplitUtil.split(
                messageBuilder.toString(),
                net.dv8tion.jda.api.entities.Message.MAX_CONTENT_LENGTH,
                net.dv8tion.jda.api.utils.SplitUtil.Strategy.NEWLINE
            )

            messages.forEach {
                guild.getTextChannelById(guildWarnPointsSettings.announceChannelId)?.sendMessage(it)?.queue()
            }
        }
    }

    private fun logAddPoints(
        moderator: Member,
        toInform: User,
        reason: String,
        amount: Int,
        id: java.util.UUID,
        dateTime: OffsetDateTime,
        action: Byte,
        guild: net.dv8tion.jda.api.entities.Guild
    ) {
        val guildLogger = toInform.jda.registeredListeners.firstOrNull { it is GuildLogger } as GuildLogger?
        if (guildLogger != null) {
            val logEmbed = EmbedBuilder()
                .setColor(Color.YELLOW)
                .setTitle("Warn points added to user")
                .addField("UUID", id.toString(), false)
                .addField("User", guild.getMember(toInform)?.nicknameAndUsername ?: toInform.name, true)
                .addField("Moderator", moderator.nicknameAndUsername, true)
                .addField("Amount", amount.toString(), false)
                .addField("Reason", reason, false)
                .addField("Expires", dateTime.format(messageTimeFormat), false)
            when (action) {
                1.toByte() -> logEmbed.addField("Punishment", "Mute", false)
                2.toByte() -> logEmbed.addField("Punishment", "Kick", false)
            }

            guildLogger.log(logEmbed, toInform, guild, null, GuildLogger.LogTypeAction.MODERATOR)
        }
    }

    private fun informUserAndModerator(
        moderator: Member,
        toInform: Member,
        reason: String,
        amountOfWarnings: Int,
        hook: InteractionHook,
        action: Byte
    ) {
        val noteMessage = if (amountOfWarnings <= 1) {
            "Please watch your behavior in our server."
        } else {
            "You have received $amountOfWarnings warnings in recent history. Please watch your behaviour in our server."
        }

        val actionText = when (action) {
            1.toByte() -> "\nPunishment: Mute"
            2.toByte() -> "\nPunishment: Kick"
            else -> ""
        }

        val userWarning = EmbedBuilder()
            .setColor(Color.YELLOW)
            .setAuthor(moderator.nicknameAndUsername, null, moderator.user.effectiveAvatarUrl)
            .setTitle("${moderator.guild.name}: You have been warned by ${moderator.nicknameAndUsername}", null)
            .addField("Reason", reason, false)
            .addField("Note", noteMessage, false)
            .apply {
                if (actionText.isNotEmpty()) {
                    addField("Action", actionText.trim(), false)
                }
            }
            .build()

        toInform.user.openPrivateChannel().queue(
            { privateChannelUserToWarn ->
                privateChannelUserToWarn.sendMessageEmbeds(userWarning).queue(
                    { onSuccessfulInformUser(hook, toInform, userWarning) }
                ) { throwable -> onFailToInformUser(hook, toInform, throwable, true) }
            }
        ) { throwable -> onFailToInformUser(hook, toInform, throwable, false) }
    }

    private fun onSuccessfulInformUser(
        hook: InteractionHook,
        toInform: Member,
        informationMessage: MessageEmbed
    ) {
        hook.editOriginal(
            "Added warn points to $toInform.\n\nThe following message was sent to the user:"
        ).setEmbeds(informationMessage).queue()
    }

    private fun onFailToInformUser(
        hook: InteractionHook,
        toInform: Member,
        throwable: Throwable,
        dmFailed: Boolean
    ) {
        val msg =
            "Added warn points to $toInform.\n\nWas unable to send a DM to the user please inform the user manually.\nError: ${throwable.message}"
        hook.editOriginal(msg).queue()
    }

    override fun getCommandsData(): List<SlashCommandData> {
        return listOf(
            Commands.slash(COMMAND, DESCRIPTION)
                .addOptions(
                    OptionData(OptionType.USER, OPTION_USER, "The user to add points to").setRequired(true),
                    OptionData(OptionType.INTEGER, OPTION_POINTS, "Number of points to add").setRequired(true),
                    OptionData(OptionType.INTEGER, OPTION_DAYS, "Number of days until points expire").setRequired(true),
                    OptionData(
                        OptionType.INTEGER,
                        OPTION_ACTION,
                        "Action to perform: 0=None, 1=Mute, 2=Kick"
                    ).setRequired(true).setMinValue(0).setMaxValue(2),
                    OptionData(OptionType.STRING, OPTION_REASON, "Reason for the warning").setRequired(true)
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.KICK_MEMBERS))
        )
    }
}
