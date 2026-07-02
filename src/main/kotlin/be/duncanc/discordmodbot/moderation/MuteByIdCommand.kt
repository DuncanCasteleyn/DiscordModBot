package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.discord.SlashCommand
import be.duncanc.discordmodbot.discord.nicknameAndUsername
import be.duncanc.discordmodbot.logging.GuildLogger
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.components.label.Label
import net.dv8tion.jda.api.components.textdisplay.TextDisplay
import net.dv8tion.jda.api.components.textinput.TextInput
import net.dv8tion.jda.api.components.textinput.TextInputStyle
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.modals.Modal
import org.springframework.stereotype.Component
import java.awt.Color
import java.util.*

@Component
class MuteByIdCommand(
    private val muteRoleCommandAndEventsListenerService: MuteRoleCommandAndEventsListener,
    private val muteService: MuteService,
    private val guildLogger: GuildLogger
) : ListenerAdapter(), SlashCommand {
    companion object {
        private const val COMMAND = "mutebyid"
        private const val DESCRIPTION = "Mute a user by their ID (for users who left the server)"
        private const val OPTION_USER_ID = "user_id"
        private const val MODAL_ID = "mutebyid_reason"
        private const val REASON_INPUT_ID = "reason"
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != COMMAND) return

        val member = event.member
        if (member == null) {
            event.reply("This command only works in a guild.").setEphemeral(true).queue()
            return
        }

        if (!member.hasPermission(Permission.MANAGE_ROLES)) {
            event.reply("You need manage roles permission to mute.").setEphemeral(true).queue()
            return
        }

        val userId = try {
            event.getOption(OPTION_USER_ID)?.asLong
        } catch (_: IllegalStateException) {
            null
        }
        if (userId == null) {
            event.reply("User ID option is missing or invalid").setEphemeral(true).queue()
            return
        }

        event.replyModal(createReasonModal(userId)).queue()
    }

    override fun onModalInteraction(event: ModalInteractionEvent) {
        if (!event.modalId.startsWith("$MODAL_ID:")) return

        val userId = event.modalId.removePrefix("$MODAL_ID:").toLongOrNull()
        if (userId == null) {
            event.reply("This form is no longer valid.").setEphemeral(true).queue()
            return
        }

        val member = event.member
        if (member == null) {
            event.reply("This command only works in a guild.").setEphemeral(true).queue()
            return
        }

        if (!member.hasPermission(Permission.MANAGE_ROLES)) {
            event.reply("You need manage roles permission to mute.").setEphemeral(true).queue()
            return
        }

        val reason = event.getValue(REASON_INPUT_ID)?.asString?.trim().orEmpty()
        if (reason.isBlank()) {
            event.reply("Please provide a reason.").setEphemeral(true).queue()
            return
        }

        if (reason.length > 1024) {
            event.reply("Reason must be 1024 characters or less.").setEphemeral(true).queue()
            return
        }

        processMuteById(event, member, userId, reason)
    }

    private fun processMuteById(
        event: ModalInteractionEvent,
        member: Member,
        userId: Long,
        reason: String
    ) {
        event.deferReply(true).queue { hook ->
            val guild = member.guild

            val muteRole = try {
                muteRoleCommandAndEventsListenerService.getMuteRole(guild)
            } catch (_: IllegalStateException) {
                hook.editOriginal("Mute role is not configured for this server.").queue()
                return@queue
            }

            muteService.muteUserById(guild.idLong, userId)

            val targetMember = guild.getMemberById(userId)
            targetMember?.let {
                guild.addRoleToMember(it, muteRole).reason(reason.toAuditReason()).queue()
            }

            val logEmbed = EmbedBuilder()
                .setColor(Color.YELLOW)
                .setTitle("User muted by ID")
                .addField("UUID", UUID.randomUUID().toString(), false)
                .addField("User", "<@$userId>", true)
                .addField("Moderator", member.nicknameAndUsername, true)
                .addField("Reason", reason, false)

            guildLogger.log(
                logEmbed,
                null,
                guild,
                null,
                GuildLogger.LogTypeAction.MODERATOR
            )

            if (targetMember == null) {
                hook.editOriginal("User (ID: $userId) has been muted. The mute will be applied when they rejoin.")
                    .queue()
            } else {
                hook.editOriginal("User $targetMember has been muted.").queue()
            }
        }
    }

    override fun getCommandsData(): List<SlashCommandData> {
        return listOf(
            Commands.slash(COMMAND, DESCRIPTION)
                .addOptions(
                    OptionData(OptionType.USER, OPTION_USER_ID, "The user's Discord ID to mute").setRequired(true),
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_ROLES))
        )
    }

    private fun createReasonModal(userId: Long): Modal {
        val targetText = TextDisplay.of("Muting: <@$userId> (ID: $userId)")
        val reasonInput = TextInput.create(REASON_INPUT_ID, TextInputStyle.PARAGRAPH)
            .setPlaceholder("Enter the reason for this mute...")
            .setMinLength(1)
            .setMaxLength(1024)
            .build()

        return Modal.create("$MODAL_ID:$userId", "Enter Reason")
            .addComponents(targetText, Label.of("Reason", reasonInput))
            .build()
    }
}
