package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.discord.SlashCommand
import be.duncanc.discordmodbot.discord.nicknameAndUsername
import be.duncanc.discordmodbot.logging.GuildLogger
import be.duncanc.discordmodbot.moderation.persistence.GuildWarnPointsSettings
import be.duncanc.discordmodbot.moderation.persistence.GuildWarnPointsSettingsRepository
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.components.label.Label
import net.dv8tion.jda.api.components.textinput.TextInput
import net.dv8tion.jda.api.components.textinput.TextInputStyle
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
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
    companion object {
        private const val COMMAND = "addwarnpoints"
        private const val DESCRIPTION = "Add warn points to a user, optionally muting or kicking them."
        private const val OPTION_USER = "user"
        private const val OPTION_POINTS = "points"
        private const val OPTION_DAYS = "days"
        private const val OPTION_ACTION = "action"
        private const val OPTION_REASON = "reason"
        private const val MODAL_ID = "addwarnpoints_reason"
        private const val UNMUTE_BUTTON_PREFIX = "addwarnpoints_unmute:"
        private const val SKIP_UNMUTE_BUTTON_PREFIX = "addwarnpoints_skip_unmute:"
        private const val UNMUTE_MODAL_ID = "addwarnpoints_plan_unmute"
        private const val UNMUTE_DAYS_INPUT_ID = "unmute_days"

        val LOG: Logger = LoggerFactory.getLogger(AddWarnPointsCommand::class.java)
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
        if (action < 0 || action > 2) {
            event.reply("Action must be 0 (None), 1 (Mute), or 2 (Kick).").setEphemeral(true).queue()
            return
        }

        val reason = event.getOption(OPTION_REASON)?.asString
        if (reason == null) {
            val modal = createReasonModal(targetMember, points, days, action)
            event.replyModal(modal).queue()
            return
        }
        if (reason.length > 1024) {
            event.reply("Reason must be 1024 characters or less.").setEphemeral(true).queue()
            return
        }

        val guildId = event.guild!!.idLong
        val guildPointsSettings =
            guildWarnPointsSettingsRepository.findById(guildId)
                .orElse(null) ?: GuildWarnPointsSettings(
                guildId,
                announceChannelId = -1
            )

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
                    event.guild!!,
                    event.jda,
                    moderator,
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

    override fun onModalInteraction(event: ModalInteractionEvent) {
        when {
            event.modalId.startsWith(MODAL_ID) -> handleReasonModal(event)
            event.modalId.startsWith(UNMUTE_MODAL_ID) -> handlePlanUnmuteModal(event)
        }
    }

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        when {
            event.componentId.startsWith(UNMUTE_BUTTON_PREFIX) -> handlePlanUnmuteButton(event)
            event.componentId.startsWith(SKIP_UNMUTE_BUTTON_PREFIX) -> handleSkipUnmuteButton(event)
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

        if (!moderator.hasPermission(Permission.KICK_MEMBERS)) {
            event.reply("You need kick members permission to add warn points.").setEphemeral(true).queue()
            return
        }

        if (moderator.canInteract(targetMember) != true) {
            event.reply("You can't interact with this member.").setEphemeral(true).queue()
            return
        }

        val points = parts[2].toInt()
        val days = parts[3].toInt()
        val action = parts[4].toInt()
        val reason = event.getValue("reason")?.asString ?: ""

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

    private fun handlePlanUnmuteButton(event: ButtonInteractionEvent) {
        val buttonAction = parseModeratorTargetComponent(event.componentId, UNMUTE_BUTTON_PREFIX)
        if (buttonAction == null) {
            event.reply("This unmute action is no longer available.").setEphemeral(true).queue()
            return
        }

        if (event.member?.hasPermission(Permission.MANAGE_ROLES) != true) {
            event.reply("You need manage roles permission to schedule an unmute.").setEphemeral(true).queue()
            return
        }

        if (buttonAction.moderatorId != event.user.idLong) {
            event.reply("You cannot plan an unmute initiated by another moderator.").setEphemeral(true).queue()
            return
        }

        event.replyModal(createPlanUnmuteModal(buttonAction)).queue()
    }

    private fun handleSkipUnmuteButton(event: ButtonInteractionEvent) {
        val buttonAction = parseModeratorTargetComponent(event.componentId, SKIP_UNMUTE_BUTTON_PREFIX)
        if (buttonAction == null) {
            event.reply("This unmute action is no longer available.").setEphemeral(true).queue()
            return
        }

        if (buttonAction.moderatorId != event.user.idLong) {
            event.reply("You cannot skip an unmute prompt initiated by another moderator.").setEphemeral(true).queue()
            return
        }

        event.editMessage("Skipped planning an unmute for <@${buttonAction.targetUserId}>.")
            .setComponents(emptyList())
            .queue()
    }

    private fun handlePlanUnmuteModal(event: ModalInteractionEvent) {
        val modalAction = parseModeratorTargetComponent(event.modalId, "$UNMUTE_MODAL_ID:")
        if (modalAction == null) {
            event.reply("This unmute action is no longer available.").setEphemeral(true).queue()
            return
        }

        if (event.member?.hasPermission(Permission.MANAGE_ROLES) != true) {
            event.reply("You need manage roles permission to schedule an unmute.").setEphemeral(true).queue()
            return
        }

        if (modalAction.moderatorId != event.user.idLong) {
            event.reply("You cannot plan an unmute initiated by another moderator.").setEphemeral(true).queue()
            return
        }

        val days = event.getValue(UNMUTE_DAYS_INPUT_ID)?.asString?.trim()?.toIntOrNull()
        if (days == null || days <= 0) {
            event.reply("Please provide a valid number of days.").setEphemeral(true).queue()
            return
        }

        val guild = event.guild
        val moderator = event.member
        if (guild == null || moderator == null) {
            event.reply("This command only works in a guild.").setEphemeral(true).queue()
            return
        }

        try {
            val unmuteDateTime = unmutePlanningService.planUnmute(guild, modalAction.targetUserId, moderator, days)
            val targetMention = guild.getMemberById(modalAction.targetUserId)?.asMention ?: "<@${modalAction.targetUserId}>"

            event.reply(
                "Unmute has been planned for $targetMention on ${
                    TimeFormat.DATE_SHORT_TIME_SHORT.atInstant(unmuteDateTime.toInstant())
                }."
            ).setEphemeral(true).queue()
        } catch (e: IllegalArgumentException) {
            event.reply(e.message ?: "Please provide a valid number of days.").setEphemeral(true).queue()
        } catch (e: IllegalStateException) {
            event.reply(e.message ?: "Unable to plan an unmute.").setEphemeral(true).queue()
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

        logAddPoints(
            jda,
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
                    muteRoleCommandAndEventsListener.getMuteRole(guild)
                } catch (_: IllegalStateException) {
                    hook.sendMessage("Warn points added, but mute role is not configured.").queue()

                    null
                }
                muteRole?.let { role ->
                    guild.addRoleToMember(targetMember, role).reason(reason).queue(
                        { sendPlanUnmutePrompt(hook, moderator, targetMember) },
                        { hook.sendMessage("Unable to add mute role to user.").queue() }
                    )
                }
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
                ) { throwable -> onFailToInformUser(hook, toInform, throwable) }
            }
        ) { throwable -> onFailToInformUser(hook, toInform, throwable) }
    }

    private fun onSuccessfulInformUser(
        hook: InteractionHook,
        toInform: Member,
        informationMessage: MessageEmbed
    ) {
        hook.sendMessage(
            "Added warn points to $toInform.\n\nThe following message was sent to the user:"
        ).setEmbeds(informationMessage).queue()
    }

    private fun onFailToInformUser(
        hook: InteractionHook,
        toInform: Member,
        throwable: Throwable
    ) {
        hook.sendMessage(
            "Added warn points to $toInform.\n\nWas unable to send a DM to the user please inform the user manually.\nError: ${throwable.message}"
        ).queue()
    }

    private fun sendPlanUnmutePrompt(hook: InteractionHook, moderator: Member, targetMember: Member) {
        if (!moderator.hasPermission(Permission.MANAGE_ROLES)) {
            return
        }

        val planButtonId = "$UNMUTE_BUTTON_PREFIX${moderator.idLong}:${targetMember.idLong}"
        val skipButtonId = "$SKIP_UNMUTE_BUTTON_PREFIX${moderator.idLong}:${targetMember.idLong}"

        hook.sendMessage("Do you want to plan an unmute for ${targetMember.asMention}?")
            .setEphemeral(true)
            .addComponents(ActionRow.of(
                Button.primary(planButtonId, "Plan unmute"),
                Button.secondary(skipButtonId, "Skip")
            ))
            .queue()
    }

    private fun createPlanUnmuteModal(action: ModeratorTargetAction): Modal {
        val daysInput = TextInput.create(UNMUTE_DAYS_INPUT_ID, TextInputStyle.SHORT)
            .setPlaceholder("Enter the number of days...")
            .setMinLength(1)
            .setMaxLength(4)
            .build()

        return Modal.create("$UNMUTE_MODAL_ID:${action.moderatorId}:${action.targetUserId}", "Plan Unmute")
            .addComponents(Label.of("Days until unmute", daysInput))
            .build()
    }

    private fun parseModeratorTargetComponent(componentId: String, prefix: String): ModeratorTargetAction? {
        if (!componentId.startsWith(prefix)) {
            return null
        }

        val segments = componentId.removePrefix(prefix).split(":", limit = 2)
        if (segments.size != 2) {
            return null
        }

        val moderatorId = segments[0].toLongOrNull() ?: return null
        val targetUserId = segments[1].toLongOrNull() ?: return null
        return ModeratorTargetAction(moderatorId, targetUserId)
    }

    private data class ModeratorTargetAction(
        val moderatorId: Long,
        val targetUserId: Long
    )

    private fun createReasonModal(targetMember: Member, points: Int, days: Int, action: Int): Modal {
        val modalId = "$MODAL_ID:${targetMember.idLong}:$points:$days:$action"
        val textInput = TextInput.create("reason", TextInputStyle.PARAGRAPH)
            .setPlaceholder("Enter the reason for this warning...")
            .setMinLength(1)
            .setMaxLength(1024)
            .build()

        return Modal.create(modalId, "Enter Reason")
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
                        .addChoice("Kick", 2L)
                        .setMinValue(0L)
                        .setMaxValue(2L),
                    OptionData(OptionType.STRING, OPTION_REASON, "Reason for the warning", false)
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.KICK_MEMBERS))
        )
    }
}
