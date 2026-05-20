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
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.User
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
import net.dv8tion.jda.api.utils.TimeFormat
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.awt.Color
import java.time.OffsetDateTime

@Component
class AddWarnPointsCommand(
    private val guildWarnPointsService: GuildWarnPointsService,
    private val guildWarnPointsSettingsRepository: GuildWarnPointsSettingsRepository,
    private val muteRoleCommandAndEventsListener: MuteRoleCommandAndEventsListener,
    private val unmutePlanningService: UnmutePlanningService
) : ListenerAdapter(), SlashCommand {
    private data class UnmuteSchedulingResult(
        val effectiveUnmuteDays: Int?,
        val unmutePlanMessage: String? = null,
        val moderatorNote: String? = null
    )

    companion object {
        private const val COMMAND = "addwarnpoints"
        private const val DESCRIPTION = "Add warn points to a user, optionally muting or kicking them."
        private const val OPTION_USER = "user"
        private const val OPTION_POINTS = "points"
        private const val OPTION_DAYS = "days"
        private const val OPTION_ACTION = "action"
        private const val MODAL_ID = "addwarnpoints_reason"
        private const val UNMUTE_DAYS_INPUT_ID = "unmute_days"
        private const val REASON_INPUT_ID = "reason"

        val LOG: Logger = LoggerFactory.getLogger(AddWarnPointsCommand::class.java)
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != COMMAND) return

        val moderator = event.member
        if (moderator == null) {
            event.reply("This command only works in a guild.").setEphemeral(true).queue()
            return
        }

        if (!moderator.hasPermission(Permission.MANAGE_ROLES)) {
            event.reply("You need manage roles permission to add warn points.").setEphemeral(true).queue()
            return
        }

        val targetMember = event.getOption(OPTION_USER)?.asMember
        if (targetMember == null) {
            event.reply("You need to mention a user that is still in the server.").setEphemeral(true).queue()
            return
        }

        if (moderator.canInteract(targetMember) != true) {
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
        if (action !in 0..2) {
            event.reply("Action must be 0 (None), 1 (Mute), or 2 (Kick).").setEphemeral(true).queue()
            return
        }

        if (action == 2 && !moderator.hasPermission(Permission.KICK_MEMBERS)) {
            event.reply("You need kick members permission to apply the kick punishment.").setEphemeral(true).queue()
            return
        }

        event.replyModal(createReasonModal(targetMember, points, days, action)).queue()
    }

    override fun onModalInteraction(event: ModalInteractionEvent) {
        when {
            event.modalId.startsWith(MODAL_ID) -> handleReasonModal(event)
        }
    }

    private fun handleReasonModal(event: ModalInteractionEvent) {
        val parts = event.modalId.split(":")

        if (parts.size != 5) {
            event.reply("This form is no longer valid.").setEphemeral(true).queue()
            return
        }

        val targetMember = event.guild?.getMemberById(parts[1]) ?: run {
            event.reply("User not found.").setEphemeral(true).queue()
            return
        }

        val moderator = event.member
        if (moderator == null) {
            event.reply("This command only works in a guild.").setEphemeral(true).queue()
            return
        }

        if (!moderator.hasPermission(Permission.MANAGE_ROLES)) {
            event.reply("You need manage roles permission to add warn points.").setEphemeral(true).queue()
            return
        }

        if (moderator.canInteract(targetMember) != true) {
            event.reply("You can't interact with this member.").setEphemeral(true).queue()
            return
        }

        val points = parts[2].toInt()
        val days = parts[3].toInt()
        val action = parts[4].toInt()

        if (action == 2 && !moderator.hasPermission(Permission.KICK_MEMBERS)) {
            event.reply("You need kick members permission to apply the kick punishment.").setEphemeral(true).queue()
            return
        }

        val reason = event.getValue(REASON_INPUT_ID)?.asString ?: ""
        val unmuteDays = if (action == 1) {
            val rawUnmuteDays = event.getValue(UNMUTE_DAYS_INPUT_ID)?.asString?.trim().orEmpty()
            when {
                rawUnmuteDays.isBlank() -> null
                else -> rawUnmuteDays.toIntOrNull()?.takeIf { it > 0 } ?: run {
                    event.reply("Please provide a valid number of days.").setEphemeral(true).queue()
                    return
                }
            }
        } else {
            null
        }

        if (reason.length > 1024) {
            event.reply("Reason must be 1024 characters or less.").setEphemeral(true).queue()
            return
        }

        val guildId = event.guild!!.idLong
        val guildPointsSettings = guildWarnPointsSettingsRepository.findById(guildId)
            .orElse(null) ?: GuildWarnPointsSettings(guildId, announceChannelId = -1)

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
                processWarnPoints(
                    moderator.guild,
                    event.jda,
                    moderator,
                    targetMember,
                    points,
                    days,
                    action,
                    unmuteDays,
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

    private fun processWarnPoints(
        guild: net.dv8tion.jda.api.entities.Guild,
        jda: net.dv8tion.jda.api.JDA,
        moderator: Member,
        targetMember: Member,
        points: Int,
        days: Int,
        action: Int,
        unmuteDays: Int?,
        reason: String,
        guildPointsSettings: GuildWarnPointsSettings,
        hook: InteractionHook
    ) {
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

        when (action) {
            1 -> {
                val muteRole = try {
                    muteRoleCommandAndEventsListener.getMuteRole(guild)
                } catch (_: IllegalStateException) {
                    finishWarnPointsProcessing(
                        jda,
                        moderator,
                        targetMember,
                        reason,
                        points,
                        guildWarnPoint.id,
                        expireDate,
                        0,
                        null,
                        totalPoints,
                        hook,
                        moderatorNote = "Mute role is not configured."
                    )

                    return
                }

                guild.addRoleToMember(targetMember, muteRole).reason(reason).queue(
                    {
                        try {
                            val unmuteSchedulingResult =
                                scheduleUnmuteIfRequested(guild, targetMember, moderator, unmuteDays)
                            finishWarnPointsProcessing(
                                jda,
                                moderator,
                                targetMember,
                                reason,
                                points,
                                guildWarnPoint.id,
                                expireDate,
                                action.toByte(),
                                unmuteSchedulingResult.effectiveUnmuteDays,
                                totalPoints,
                                hook,
                                unmuteSchedulingResult.unmutePlanMessage,
                                unmuteSchedulingResult.moderatorNote
                            )
                        } catch (t: Throwable) {
                            LOG.error("Error processing warn points", t)
                            hook.editOriginal("Error: ${t.message}").queue()
                        }
                    },
                    {
                        try {
                            finishWarnPointsProcessing(
                                jda,
                                moderator,
                                targetMember,
                                reason,
                                points,
                                guildWarnPoint.id,
                                expireDate,
                                0,
                                null,
                                totalPoints,
                                hook,
                                moderatorNote = "Unable to add mute role to user."
                            )
                        } catch (t: Throwable) {
                            LOG.error("Error processing warn points", t)
                            hook.editOriginal("Error: ${t.message}").queue()
                        }
                    }
                )
                return
            }

            2 -> {
                guild.kick(targetMember).reason(reason).queue()
            }
        }

        finishWarnPointsProcessing(
            jda,
            moderator,
            targetMember,
            reason,
            points,
            guildWarnPoint.id,
            expireDate,
            action.toByte(),
            unmuteDays,
            totalPoints,
            hook
        )
    }

    private fun finishWarnPointsProcessing(
        jda: net.dv8tion.jda.api.JDA,
        moderator: Member,
        targetMember: Member,
        reason: String,
        points: Int,
        warnPointId: java.util.UUID,
        expireDate: OffsetDateTime,
        action: Byte,
        unmuteDays: Int?,
        totalPoints: Int,
        hook: InteractionHook,
        unmutePlanMessage: String? = null,
        moderatorNote: String? = null
    ) {
        logAddPoints(
            jda,
            moderator,
            targetMember.user,
            reason,
            points,
            warnPointId,
            expireDate,
            action,
            unmuteDays,
            targetMember.guild
        )

        informUserAndModerator(
            moderator,
            targetMember,
            reason,
            totalPoints,
            hook,
            action,
            unmuteDays,
            unmutePlanMessage,
            moderatorNote
        )
    }

    private fun scheduleUnmuteIfRequested(
        guild: net.dv8tion.jda.api.entities.Guild,
        targetMember: Member,
        moderator: Member,
        unmuteDays: Int?
    ): UnmuteSchedulingResult {
        if (unmuteDays == null) {
            return UnmuteSchedulingResult(effectiveUnmuteDays = null)
        }

        return try {
            val unmuteDateTime = unmutePlanningService.planUnmute(guild, targetMember.idLong, moderator, unmuteDays)
            UnmuteSchedulingResult(
                effectiveUnmuteDays = unmuteDays,
                unmutePlanMessage = "Unmute planned for ${TimeFormat.DATE_SHORT_TIME_SHORT.atInstant(unmuteDateTime.toInstant())}."
            )
        } catch (e: IllegalArgumentException) {
            UnmuteSchedulingResult(
                effectiveUnmuteDays = null,
                moderatorNote = e.message ?: "Unable to plan an unmute."
            )
        } catch (e: IllegalStateException) {
            UnmuteSchedulingResult(
                effectiveUnmuteDays = null,
                moderatorNote = e.message ?: "Unable to plan an unmute."
            )
        }
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
                    .append(" on ").append(TimeFormat.DATE_SHORT_TIME_SHORT.atInstant(it.creationDate.toInstant()))
                    .append('\n')
                    .append("Reason: ").append(it.reason)
                    .append("\nExpires on: ")
                    .append(TimeFormat.DATE_SHORT_TIME_SHORT.atInstant(it.expireDate.toInstant()))
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
        jda: net.dv8tion.jda.api.JDA,
        moderator: Member,
        toInform: User,
        reason: String,
        amount: Int,
        id: java.util.UUID,
        dateTime: OffsetDateTime,
        action: Byte,
        unmuteDays: Int?,
        guild: net.dv8tion.jda.api.entities.Guild
    ) {
        val guildLogger = jda.registeredListeners.firstOrNull { it is GuildLogger } as GuildLogger?
        if (guildLogger != null) {
            val logEmbed = EmbedBuilder()
                .setColor(Color.YELLOW)
                .setTitle("Warn points added to user")
                .addField("UUID", id.toString(), false)
                .addField("User", guild.getMember(toInform)?.nicknameAndUsername ?: toInform.name, true)
                .addField("Moderator", moderator.nicknameAndUsername, true)
                .addField("Amount", amount.toString(), false)
                .addField("Reason", reason, false)
                .addField("Expires", TimeFormat.DATE_SHORT_TIME_SHORT.atInstant(dateTime.toInstant()).toString(), false)
            when (action) {
                1.toByte() -> logEmbed.addField("Punishment", buildPunishmentText(unmuteDays), false)
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
        action: Byte,
        unmuteDays: Int?,
        unmutePlanMessage: String?,
        moderatorNote: String?
    ) {
        val noteMessage = if (amountOfWarnings <= 1) {
            "Please watch your behavior in our server."
        } else {
            "You have received $amountOfWarnings warnings in recent history. Please watch your behaviour in our server."
        }

        val punishmentText = when (action) {
            1.toByte() -> buildPunishmentText(unmuteDays)
            2.toByte() -> "Kick"
            else -> ""
        }

        val userWarning = EmbedBuilder()
            .setColor(Color.YELLOW)
            .setAuthor(moderator.nicknameAndUsername, null, moderator.user.effectiveAvatarUrl)
            .setTitle("${moderator.guild.name}: You have been warned by ${moderator.nicknameAndUsername}", null)
            .addField("Reason", reason, false)
            .addField("Note", noteMessage, false)
            .apply {
                if (punishmentText.isNotEmpty()) {
                    addField("Punishment", punishmentText, false)
                }
            }
            .build()

        toInform.user.openPrivateChannel().queue(
            { privateChannelUserToWarn ->
                privateChannelUserToWarn.sendMessageEmbeds(userWarning).queue(
                    { onSuccessfulInformUser(hook, toInform, userWarning, unmutePlanMessage, moderatorNote) }
                ) { throwable -> onFailToInformUser(hook, toInform, throwable, unmutePlanMessage, moderatorNote) }
            }
        ) { throwable -> onFailToInformUser(hook, toInform, throwable, unmutePlanMessage, moderatorNote) }
    }

    private fun onSuccessfulInformUser(
        hook: InteractionHook,
        toInform: Member,
        informationMessage: MessageEmbed,
        unmutePlanMessage: String?,
        moderatorNote: String?
    ) {
        hook.sendMessage(
            buildModeratorResultMessage(
                "Added warn points to $toInform.",
                "The following message was sent to the user:",
                unmutePlanMessage,
                moderatorNote
            )
        )
            .setEphemeral(true)
            .setEmbeds(informationMessage).queue()
    }

    private fun onFailToInformUser(
        hook: InteractionHook,
        toInform: Member,
        throwable: Throwable,
        unmutePlanMessage: String?,
        moderatorNote: String?
    ) {
        hook.sendMessage(
            buildModeratorResultMessage(
                "Added warn points to $toInform.",
                "Was unable to send a DM to the user please inform the user manually.\nError: ${throwable.message}",
                unmutePlanMessage,
                moderatorNote
            )
        )
            .setEphemeral(true)
            .queue()
    }

    private fun createReasonModal(targetMember: Member, points: Int, days: Int, action: Int): Modal {
        val modalId = "$MODAL_ID:${targetMember.idLong}:$points:$days:$action"
        val textInput = TextInput.create(REASON_INPUT_ID, TextInputStyle.PARAGRAPH)
            .setPlaceholder("Enter the reason for this warning...")
            .setMinLength(1)
            .setMaxLength(1024)
            .build()

        val modalBuilder = Modal.create(modalId, "Enter Reason")
            .addComponents(Label.of("Reason", textInput))

        if (action == 1) {
            val unmuteDaysInput = TextInput.create(UNMUTE_DAYS_INPUT_ID, TextInputStyle.SHORT)
                .setPlaceholder("Optional: days until unmute")
                .setRequired(false)
                .setMaxLength(4)
                .build()

            modalBuilder.addComponents(Label.of("Days until unmute", unmuteDaysInput))
        }

        return modalBuilder.build()
    }

    private fun buildPunishmentText(unmuteDays: Int?): String {
        return when (unmuteDays) {
            null -> "Mute"
            1 -> "1 day mute"
            else -> "$unmuteDays days mute"
        }
    }

    private fun buildModeratorResultMessage(
        summary: String,
        detail: String,
        unmutePlanMessage: String?,
        moderatorNote: String?
    ): String {
        return buildString {
            append(summary)
            if (unmutePlanMessage != null) {
                append('\n')
                append(unmutePlanMessage)
            }
            if (moderatorNote != null) {
                append('\n')
                append(moderatorNote)
            }
            append("\n\n")
            append(detail)
        }
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
                        .addChoice("Kick", 2L)
                        .setMinValue(0L)
                        .setMaxValue(2L),
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_ROLES))
        )
    }
}
