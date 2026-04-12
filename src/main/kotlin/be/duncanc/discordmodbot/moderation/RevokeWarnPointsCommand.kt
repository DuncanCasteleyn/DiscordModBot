package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.discord.SlashCommand
import be.duncanc.discordmodbot.discord.nicknameAndUsername
import be.duncanc.discordmodbot.logging.GuildLogger
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.label.Label
import net.dv8tion.jda.api.components.selections.StringSelectMenu
import net.dv8tion.jda.api.components.textinput.TextInput
import net.dv8tion.jda.api.components.textinput.TextInputStyle
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.modals.Modal
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import org.springframework.stereotype.Component
import java.awt.Color
import java.util.*

private const val COMMAND = "revokewarnpoints"
private const val SUBCOMMAND_DIRECT = "direct"
private const val SUBCOMMAND_GUIDED = "guided"
private const val OPTION_USER = "user"
private const val OPTION_WARN_POINT_ID = "warn_point_id"
private const val OPTION_REASON = "reason"
private const val SELECT_MENU_PREFIX = "revokewarnpoints-select"
private const val MODAL_PREFIX = "revokewarnpoints-reason"
private const val MODAL_REASON_INPUT = "reason"

@Component
class RevokeWarnPointsCommand(
    private val guildWarnPointsService: GuildWarnPointsService
) : ListenerAdapter(), SlashCommand {

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != COMMAND) return

        val moderator = event.member
        if (moderator == null) {
            event.reply("This command only works in a guild.").setEphemeral(true).queue()
            return
        }

        if (!moderator.hasPermission(Permission.KICK_MEMBERS)) {
            event.reply("You need kick members permission to revoke warn points.").setEphemeral(true).queue()
            return
        }

        when (event.subcommandName) {
            SUBCOMMAND_DIRECT -> handleDirectRevoke(event, moderator)
            SUBCOMMAND_GUIDED -> handleGuidedRevoke(event, moderator)
        }
    }

    override fun onStringSelectInteraction(event: StringSelectInteractionEvent) {
        val state = parseSelectState(event.componentId) ?: return

        val member = event.member
        if (member == null) {
            event.reply("This command only works in a guild.").setEphemeral(true).queue()
            return
        }

        if (!member.hasPermission(Permission.KICK_MEMBERS)) {
            event.reply("You need kick members permission to revoke warn points.").setEphemeral(true).queue()
            return
        }

        if (state.moderatorId != event.user.idLong) {
            event.reply("You cannot revoke warn points initiated by another moderator.").setEphemeral(true).queue()
            return
        }

        val warnPointIdStr = event.values.firstOrNull() ?: return
        val warnPointId = parseWarnPointId(warnPointIdStr) ?: run {
            event.reply("Invalid warn point ID.").setEphemeral(true).queue()
            return
        }

        val targetWarning = guildWarnPointsService.getWarningById(warnPointId)
        if (targetWarning == null || targetWarning.guildId != event.guild?.idLong || targetWarning.userId != state.targetUserId) {
            event.reply("Warn point not found. It may have already been revoked or the menu is out of date.")
                .setEphemeral(true)
                .queue()
            return
        }

        event.replyModal(createReasonModal(state.moderatorId, state.targetUserId, warnPointId)).queue()
    }

    override fun onModalInteraction(event: ModalInteractionEvent) {
        val state = parseModalState(event.modalId) ?: return

        val member = event.member
        if (member == null) {
            event.reply("This command only works in a guild.").setEphemeral(true).queue()
            return
        }

        if (!member.hasPermission(Permission.KICK_MEMBERS)) {
            event.reply("You need kick members permission to revoke warn points.").setEphemeral(true).queue()
            return
        }

        if (state.moderatorId != event.user.idLong) {
            event.reply("You cannot revoke warn points initiated by another moderator.").setEphemeral(true).queue()
            return
        }

        val reason = event.getValue(MODAL_REASON_INPUT)?.asString?.trim().orEmpty()
        if (reason.isBlank()) {
            event.reply("Please provide a reason.").setEphemeral(true).queue()
            return
        }

        if (reason.length > 1024) {
            event.reply("Reason must be 1024 characters or less.").setEphemeral(true).queue()
            return
        }

        val guild = event.guild ?: return
        val targetWarning = guildWarnPointsService.getWarningById(state.warnPointId)
        if (targetWarning == null || targetWarning.guildId != guild.idLong || targetWarning.userId != state.targetUserId) {
            event.reply("Warn point not found. It may have already been revoked or the menu is out of date.")
                .setEphemeral(true)
                .queue()
            return
        }

        guildWarnPointsService.revokePoint(state.warnPointId)
        logRevoke(
            event.jda,
            guild,
            targetWarning.id,
            targetWarning.points,
            reason,
            event.member
        )

        event.reply("Revoked ${targetWarning.points} warn point(s) from <@${state.targetUserId}>. Reason: $reason")
            .setEphemeral(true)
            .queue()
    }

    private fun handleDirectRevoke(event: SlashCommandInteractionEvent, moderator: Member) {
        val guild = event.guild ?: return
        val targetUserId = event.getOption(OPTION_USER)?.asLong
        if (targetUserId == null) {
            event.reply("You need to mention a user.").setEphemeral(true).queue()
            return
        }

        val targetMember = guild.getMemberById(targetUserId)
        if (targetMember != null && !moderator.canInteract(targetMember)) {
            event.reply("You can't revoke warn points from a user that you can't interact with.").setEphemeral(true)
                .queue()
            return
        }

        val warnPointIdStr = event.getOption(OPTION_WARN_POINT_ID)?.asString
        if (warnPointIdStr == null) {
            event.reply("You need to provide a warn point ID.").setEphemeral(true).queue()
            return
        }

        val warnPointId = parseWarnPointId(warnPointIdStr)
        if (warnPointId == null) {
            event.reply("Invalid warn point ID format. Please provide a valid UUID.").setEphemeral(true).queue()
            return
        }

        val reason = event.getOption(OPTION_REASON)?.asString?.trim()
        if (reason.isNullOrBlank()) {
            event.reply("You need to provide a reason.").setEphemeral(true).queue()
            return
        }

        if (reason.length > 1024) {
            event.reply("Reason must be 1024 characters or less.").setEphemeral(true).queue()
            return
        }

        val targetWarning = guildWarnPointsService.getWarningById(warnPointId)
        if (targetWarning == null || targetWarning.guildId != guild.idLong || targetWarning.userId != targetUserId) {
            event.reply("No warn point found with ID `$warnPointIdStr` for this user.").setEphemeral(true).queue()
            return
        }

        guildWarnPointsService.revokePoint(warnPointId)
        logRevoke(
            event.jda,
            guild,
            targetWarning.id,
            targetWarning.points,
            reason,
            moderator
        )

        event.reply("Revoked ${targetWarning.points} warn point(s) from <@$targetUserId>. Reason: $reason")
            .setEphemeral(true)
            .queue()
    }

    private fun handleGuidedRevoke(event: SlashCommandInteractionEvent, moderator: Member) {
        val guild = event.guild ?: return
        val targetUserId = event.getOption(OPTION_USER)?.asLong
        if (targetUserId == null) {
            event.reply("You need to mention a user.").setEphemeral(true).queue()
            return
        }

        val targetMember = guild.getMemberById(targetUserId)
        if (targetMember != null && !moderator.canInteract(targetMember)) {
            event.reply("You can't revoke warn points from a user that you can't interact with.").setEphemeral(true)
                .queue()
            return
        }

        val warnings = guildWarnPointsService.getActiveWarnings(guild.idLong, targetUserId)
        if (warnings.isEmpty()) {
            event.reply("This user has no active warn points to revoke.").setEphemeral(true).queue()
            return
        }

        val selectWarnings = warnings.take(25)
        val content = buildString {
            append("Select a warn point to revoke from <@$targetUserId>:")
            if (warnings.size > selectWarnings.size) {
                append(" Showing the first ")
                append(selectWarnings.size)
                append(" active warn points.")
            }
        }

        val selectMenu = StringSelectMenu.create("$SELECT_MENU_PREFIX:${moderator.idLong}:$targetUserId")
            .setPlaceholder("Select a warn point to revoke")

        selectWarnings.forEach { warning ->
            val label = "${warning.points} points - ${warning.reason.take(50)}"
            selectMenu.addOption(label, warning.id.toString())
        }

        val message = MessageCreateBuilder()
            .setContent(content)
            .addComponents(ActionRow.of(selectMenu.build()))
            .build()

        event.reply(message).setEphemeral(true).queue()
    }

    private fun logRevoke(
        jda: JDA,
        guild: Guild,
        warnId: UUID,
        points: Int,
        reason: String,
        moderator: Member?
    ) {
        val guildLogger = jda.registeredListeners.firstOrNull { it is GuildLogger } as GuildLogger?
        if (guildLogger != null) {
            val logEmbed = EmbedBuilder()
                .setColor(Color.YELLOW)
                .setTitle("Warn point revoked")
                .addField("Revoked warning UUID", warnId.toString(), false)
                .addField("Moderator", moderator?.nicknameAndUsername ?: "Unknown", true)
                .addField("Points revoked", points.toString(), false)
                .addField("Reason", reason, false)

            guildLogger.log(
                logEmbed,
                null,
                guild,
                null,
                GuildLogger.LogTypeAction.MODERATOR
            )
        }
    }

    private fun createReasonModal(moderatorId: Long, targetUserId: Long, warnPointId: UUID): Modal {
        val textInput = TextInput.create(MODAL_REASON_INPUT, TextInputStyle.PARAGRAPH)
            .setPlaceholder("Enter the reason for revoking this warning...")
            .setMinLength(1)
            .setMaxLength(1024)
            .build()

        return Modal.create("$MODAL_PREFIX:$moderatorId:$targetUserId:$warnPointId", "Enter Reason")
            .addComponents(Label.of("Reason", textInput))
            .build()
    }

    private fun parseSelectState(componentId: String): RevokeWarnPointSelectionState? {
        if (!componentId.startsWith("$SELECT_MENU_PREFIX:")) {
            return null
        }

        val parts = componentId.removePrefix("$SELECT_MENU_PREFIX:").split(":", limit = 2)
        if (parts.size != 2) {
            return null
        }

        val moderatorId = parts[0].toLongOrNull() ?: return null
        val targetUserId = parts[1].toLongOrNull() ?: return null
        return RevokeWarnPointSelectionState(moderatorId, targetUserId)
    }

    private fun parseModalState(modalId: String): RevokeWarnPointModalState? {
        if (!modalId.startsWith("$MODAL_PREFIX:")) {
            return null
        }

        val parts = modalId.removePrefix("$MODAL_PREFIX:").split(":", limit = 3)
        if (parts.size != 3) {
            return null
        }

        val moderatorId = parts[0].toLongOrNull() ?: return null
        val targetUserId = parts[1].toLongOrNull() ?: return null
        val warnPointId = parseWarnPointId(parts[2]) ?: return null
        return RevokeWarnPointModalState(moderatorId, targetUserId, warnPointId)
    }

    private fun parseWarnPointId(warnPointId: String): UUID? {
        return try {
            UUID.fromString(warnPointId)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    override fun getCommandsData(): List<SlashCommandData> {
        return listOf(
            Commands.slash(COMMAND, "Revoke warn points from a user")
                .addSubcommands(
                    SubcommandData(SUBCOMMAND_DIRECT, "Revoke a specific warn point immediately")
                        .addOptions(
                            OptionData(OptionType.USER, OPTION_USER, "The user to revoke points from", true),
                            OptionData(
                                OptionType.STRING,
                                OPTION_WARN_POINT_ID,
                                "The warn point ID to revoke",
                                true
                            ),
                            OptionData(OptionType.STRING, OPTION_REASON, "Reason for revoking points", true)
                        ),
                    SubcommandData(SUBCOMMAND_GUIDED, "Select a warn point and then provide a reason")
                        .addOptions(
                            OptionData(OptionType.USER, OPTION_USER, "The user to revoke points from", true)
                        )
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.KICK_MEMBERS))
        )
    }

    private data class RevokeWarnPointSelectionState(
        val moderatorId: Long,
        val targetUserId: Long
    )

    private data class RevokeWarnPointModalState(
        val moderatorId: Long,
        val targetUserId: Long,
        val warnPointId: UUID
    )
}
