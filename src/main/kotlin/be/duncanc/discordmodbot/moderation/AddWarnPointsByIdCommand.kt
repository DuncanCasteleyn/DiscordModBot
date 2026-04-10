package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.discord.SlashCommand
import be.duncanc.discordmodbot.discord.nicknameAndUsername
import be.duncanc.discordmodbot.logging.GuildLogger
import be.duncanc.discordmodbot.moderation.persistence.GuildWarnPointsSettings
import be.duncanc.discordmodbot.moderation.persistence.GuildWarnPointsSettingsRepository
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.components.label.Label
import net.dv8tion.jda.api.components.textinput.TextInput
import net.dv8tion.jda.api.components.textinput.TextInputStyle
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.modals.Modal
import net.dv8tion.jda.api.utils.SplitUtil
import net.dv8tion.jda.api.utils.TimeFormat
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.awt.Color
import java.time.OffsetDateTime
import java.util.*

@Component
class AddWarnPointsByIdCommand(
    private val guildWarnPointsService: GuildWarnPointsService,
    private val guildWarnPointsSettingsRepository: GuildWarnPointsSettingsRepository,
    private val muteRoleCommandAndEventsListener: MuteRoleCommandAndEventsListener,
    private val muteService: MuteService,
    private val guildLogger: GuildLogger
) : ListenerAdapter(), SlashCommand {
    companion object {
        private const val COMMAND = "addwarnpointsbyid"
        private const val DESCRIPTION = "Add warn points to a user by ID, optionally muting them when they rejoin."
        private const val OPTION_USER = "user"
        private const val OPTION_POINTS = "points"
        private const val OPTION_DAYS = "days"
        private const val OPTION_ACTION = "action"
        private const val OPTION_REASON = "reason"
        private const val MODAL_ID = "addwarnpointsbyid_reason"

        val LOG: Logger = LoggerFactory.getLogger(AddWarnPointsByIdCommand::class.java)
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != COMMAND) return

        val moderator = event.member
        if (moderator == null) {
            event.reply("This command only works in a guild.").setEphemeral(true).queue()
            return
        }

        if (!moderator.hasPermission(Permission.KICK_MEMBERS)) {
            event.reply("You need kick members permission to add warn points.").setEphemeral(true).queue()
            return
        }

        val userOption = event.getOption(OPTION_USER)
        val targetUserId = try {
            userOption?.asLong
        } catch (_: IllegalStateException) {
            null
        }
        if (targetUserId == null) {
            event.reply("You need to provide a user.").setEphemeral(true).queue()
            return
        }

        val targetMember = userOption?.asMember
        if (targetMember != null && !moderator.canInteract(targetMember)) {
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
        if (action < 0 || action > 1) {
            event.reply("Action must be 0 (None) or 1 (Mute).").setEphemeral(true).queue()
            return
        }

        val reason = event.getOption(OPTION_REASON)?.asString
        if (reason == null) {
            event.replyModal(createReasonModal(targetUserId, points, days, action)).queue()
            return
        }
        if (reason.length > 1024) {
            event.reply("Reason must be 1024 characters or less.").setEphemeral(true).queue()
            return
        }

        processSlashCommand(event, moderator, targetUserId, targetMember, points, days, action, reason)
    }

    override fun onModalInteraction(event: ModalInteractionEvent) {
        if (!event.modalId.startsWith("$MODAL_ID:")) {
            return
        }

        val parts = event.modalId.removePrefix("$MODAL_ID:").split(":")
        if (parts.size != 4) {
            event.reply("This form is no longer valid.").setEphemeral(true).queue()
            return
        }

        val targetUserId = parts[0].toLongOrNull()
        val points = parts[1].toIntOrNull()
        val days = parts[2].toIntOrNull()
        val action = parts[3].toIntOrNull()
        val reason = event.getValue("reason")?.asString ?: ""

        if (targetUserId == null || points == null || days == null || action == null) {
            event.reply("This form is no longer valid.").setEphemeral(true).queue()
            return
        }

        if (event.member?.hasPermission(Permission.KICK_MEMBERS) != true) {
            event.reply("You need kick members permission to add warn points.").setEphemeral(true).queue()
            return
        }

        if (reason.isBlank()) {
            event.reply("Please provide a reason.").setEphemeral(true).queue()
            return
        }

        if (reason.length > 1024) {
            event.reply("Reason must be 1024 characters or less.").setEphemeral(true).queue()
            return
        }

        val guild = event.guild
        val moderator = event.member
        if (guild == null || moderator == null) {
            event.reply("This command only works in a guild.").setEphemeral(true).queue()
            return
        }

        val targetMember = guild.getMemberById(targetUserId)
        if (targetMember != null && moderator.canInteract(targetMember) != true) {
            event.reply("You can't interact with this member.").setEphemeral(true).queue()
            return
        }

        processModalCommand(event, moderator, targetUserId, targetMember, points, days, action, reason)
    }

    private fun processSlashCommand(
        event: SlashCommandInteractionEvent,
        moderator: Member,
        targetUserId: Long,
        targetMember: Member?,
        points: Int,
        days: Int,
        action: Int,
        reason: String
    ) {
        val guild = event.guild!!
        val guildPointsSettings = getGuildWarnPointsSettings(event, points)
        if (guildPointsSettings == null) {
            return
        }

        event.deferReply(true).queue { hook ->
            try {
                processWarnPoints(
                    guild,
                    moderator,
                    targetUserId,
                    targetMember,
                    points,
                    days,
                    action,
                    reason,
                    guildPointsSettings,
                    hook
                )
            } catch (t: Throwable) {
                LOG.error("Error processing warn points", t)
                hook.editOriginal("Error: ${t.message}").queue()
            }
        }
    }

    private fun processModalCommand(
        event: ModalInteractionEvent,
        moderator: Member,
        targetUserId: Long,
        targetMember: Member?,
        points: Int,
        days: Int,
        action: Int,
        reason: String
    ) {
        val guild = event.guild!!
        val guildPointsSettings = getGuildWarnPointsSettings(event)
        if (guildPointsSettings == null) {
            return
        }

        event.deferReply(true).queue { hook ->
            try {
                processWarnPoints(
                    guild,
                    moderator,
                    targetUserId,
                    targetMember,
                    points,
                    days,
                    action,
                    reason,
                    guildPointsSettings,
                    hook
                )
            } catch (t: Throwable) {
                LOG.error("Error processing warn points", t)
                hook.editOriginal("Error: ${t.message}").queue()
            }
        }
    }

    private fun getGuildWarnPointsSettings(
        event: SlashCommandInteractionEvent,
        requestedPoints: Int
    ): GuildWarnPointsSettings? {
        val guildId = event.guild!!.idLong
        val guildPointsSettings = guildWarnPointsSettingsRepository.findById(guildId)
            .orElse(null) ?: GuildWarnPointsSettings(guildId, announceChannelId = -1)

        if (requestedPoints > guildPointsSettings.maxPointsPerReason) {
            event.reply("The maximum points per reason is ${guildPointsSettings.maxPointsPerReason}.")
                .setEphemeral(true).queue()
            return null
        }

        if (guildPointsSettings.announceChannelId.let { event.jda.getTextChannelById(it) == null }) {
            event.reply("The announcement channel is not configured. Please contact an administrator.")
                .setEphemeral(true).queue()
            return null
        }

        return guildPointsSettings
    }

    private fun getGuildWarnPointsSettings(event: ModalInteractionEvent): GuildWarnPointsSettings? {
        val guildId = event.guild!!.idLong
        val guildPointsSettings = guildWarnPointsSettingsRepository.findById(guildId)
            .orElse(null) ?: GuildWarnPointsSettings(guildId, announceChannelId = -1)

        if (guildPointsSettings.announceChannelId.let { event.jda.getTextChannelById(it) == null }) {
            event.reply("The announcement channel is not configured. Please contact an administrator.")
                .setEphemeral(true).queue()
            return null
        }

        return guildPointsSettings
    }

    private fun processWarnPoints(
        guild: Guild,
        moderator: Member,
        targetUserId: Long,
        targetMember: Member?,
        points: Int,
        days: Int,
        action: Int,
        reason: String,
        guildPointsSettings: GuildWarnPointsSettings,
        hook: InteractionHook
    ) {
        val expireDate = OffsetDateTime.now().plusDays(days.toLong())

        val guildWarnPoint = guildWarnPointsService.addWarnPoint(
            targetUserId,
            guild.idLong,
            points,
            moderator.idLong,
            reason,
            expireDate
        )

        performChecks(guildPointsSettings, targetUserId, guild)

        logAddPoints(
            moderator,
            targetUserId,
            targetMember,
            reason,
            points,
            guildWarnPoint.id,
            expireDate,
            action.toByte(),
            guild
        )

        if (action == 1) {
            try {
                val muteRole = muteRoleCommandAndEventsListener.getMuteRole(guild)
                muteService.muteUserById(guild.idLong, targetUserId)
                targetMember?.let {
                    guild.addRoleToMember(it, muteRole).reason(reason).queue()
                }
            } catch (_: IllegalStateException) {
                hook.editOriginal(
                    "Added warn points to ${
                        formatTarget(
                            targetUserId,
                            targetMember
                        )
                    } but mute role is not configured."
                )
                    .queue()
                return
            }
        }

        hook.editOriginal(buildCompletionMessage(targetUserId, targetMember, action)).queue()
    }

    private fun performChecks(
        guildWarnPointsSettings: GuildWarnPointsSettings,
        userId: Long,
        guild: Guild
    ) {
        val points = guildWarnPointsService.getActivePointsCount(guild.idLong, userId)

        if (points >= guildWarnPointsSettings.announcePointsSummaryLimit) {
            val messageBuilder = StringBuilder().append("@everyone ")
                .append(formatTarget(userId, guild.getMemberById(userId)))
                .append(" has reached the limit of points set by your server administrator.\n\n")
                .append("Summary of active points:")

            guildWarnPointsService.getActiveWarnings(guild.idLong, userId).forEach {
                messageBuilder.append("\n\n").append(it.points).append(" point(s) added by ")
                    .append(guild.getMemberById(it.creatorId)?.nicknameAndUsername)
                    .append(" on ").append(TimeFormat.DATE_SHORT_TIME_SHORT.atInstant(it.creationDate.toInstant()))
                    .append('\n')
                    .append("Reason: ").append(it.reason)
                    .append("\nExpires on: ")
                    .append(TimeFormat.DATE_SHORT_TIME_SHORT.atInstant(it.expireDate.toInstant()))
                    .append('\n')
                    .append("Note: The user was warned by id, ")
                    .append("did not receive his warning by DM and might have left the server.")
            }

            val messages = SplitUtil.split(
                messageBuilder.toString(),
                net.dv8tion.jda.api.entities.Message.MAX_CONTENT_LENGTH,
                SplitUtil.Strategy.NEWLINE
            )

            messages.forEach {
                guild.getTextChannelById(guildWarnPointsSettings.announceChannelId)?.sendMessage(it)?.queue()
            }
        }
    }

    private fun logAddPoints(
        moderator: Member,
        targetUserId: Long,
        targetMember: Member?,
        reason: String,
        amount: Int,
        id: UUID,
        dateTime: OffsetDateTime,
        action: Byte,
        guild: Guild
    ) {
        val logEmbed = EmbedBuilder()
            .setColor(Color.YELLOW)
            .setTitle("Warn points added to user by id")
            .addField("UUID", id.toString(), false)
            .addField("User", formatTarget(targetUserId, targetMember), true)
            .addField("Moderator", moderator.nicknameAndUsername, true)
            .addField("Amount", amount.toString(), false)
            .addField("Reason", reason, false)
            .addField("Expires", TimeFormat.DATE_SHORT_TIME_SHORT.atInstant(dateTime.toInstant()).toString(), false)

        when (action) {
            1.toByte() -> logEmbed.addField("Punishment", "Mute", false)
        }

        guildLogger.log(logEmbed, targetMember?.user, guild, null, GuildLogger.LogTypeAction.MODERATOR)
    }

    private fun buildCompletionMessage(targetUserId: Long, targetMember: Member?, action: Int): String {
        val target = formatTarget(targetUserId, targetMember)
        return when (action) {
            1 -> if (targetMember == null) {
                """Added warn points to $target.
The mute will be applied when they rejoin.
The user was not warned by DM, please do so manually when they rejoin."""
            } else {
                """Added warn points to $target and tried to apply the mute role.
The user was present but not warned by DM, please do so manually."""
            }

            else -> """Added warn points to $target.
The user was not warned by DM, please do so manually when they rejoin."""
        }
    }

    private fun formatTarget(targetUserId: Long, targetMember: Member?): String {
        return targetMember?.asMention ?: "<@$targetUserId>"
    }

    private fun createReasonModal(targetUserId: Long, points: Int, days: Int, action: Int): Modal {
        val textInput = TextInput.create("reason", TextInputStyle.PARAGRAPH)
            .setPlaceholder("Enter the reason for this warning...")
            .setMinLength(1)
            .setMaxLength(1024)
            .build()

        return Modal.create("$MODAL_ID:$targetUserId:$points:$days:$action", "Enter Reason")
            .addComponents(Label.of("Reason", textInput))
            .build()
    }

    override fun getCommandsData(): List<SlashCommandData> {
        return listOf(
            Commands.slash(COMMAND, DESCRIPTION)
                .addOptions(
                    OptionData(OptionType.USER, OPTION_USER, "The user to add points to", true),
                    OptionData(OptionType.INTEGER, OPTION_POINTS, "Number of points to add", true),
                    OptionData(OptionType.INTEGER, OPTION_DAYS, "Number of days until points expire", true),
                    OptionData(
                        OptionType.INTEGER,
                        OPTION_ACTION,
                        "Action to perform",
                        true
                    )
                        .addChoice("None", 0L)
                        .addChoice("Mute", 1L)
                        .setMinValue(0L)
                        .setMaxValue(1L),
                    OptionData(OptionType.STRING, OPTION_REASON, "Reason for the warning", false)
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.KICK_MEMBERS))
        )
    }
}
