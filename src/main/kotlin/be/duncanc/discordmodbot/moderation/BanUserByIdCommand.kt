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
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.modals.Modal
import org.springframework.stereotype.Component
import java.awt.Color
import java.util.*
import java.util.concurrent.TimeUnit

@Component
class BanUserByIdCommand : ListenerAdapter(), SlashCommand {
    companion object {
        private const val COMMAND = "banbyid"
        private const val DESCRIPTION =
            "Ban the user with the given ID, clear all messages from the last 24 hours and log to log channel."
        private const val OPTION_USER_ID = "user_id"
        private const val MODAL_ID = "banbyid_reason"
        private const val REASON_INPUT_ID = "reason"
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != COMMAND) return

        val member = event.member
        if (member == null) {
            event.reply("This command only works in a guild.").setEphemeral(true).queue()
            return
        }

        if (!member.hasPermission(Permission.BAN_MEMBERS)) {
            event.reply("You need ban members permission to use this command.").setEphemeral(true).queue()
            return
        }

        val userIdString = event.getOption(OPTION_USER_ID)?.asString

        if (userIdString.isNullOrBlank()) {
            event.reply("You need to provide a user ID.").setEphemeral(true).queue()
            return
        }

        val userId = userIdString.toLongOrNull()
        if (userId == null) {
            event.reply("Invalid user ID format.").setEphemeral(true).queue()
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

        if (!member.hasPermission(Permission.BAN_MEMBERS)) {
            event.reply("You need ban members permission to use this command.").setEphemeral(true).queue()
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

        processBanById(event, member, userId, reason)
    }

    private fun processBanById(
        event: ModalInteractionEvent,
        member: Member,
        userId: Long,
        reason: String
    ) {
        event.deferReply(true).queue { hook ->
            event.jda.retrieveUserById(userId).queue({ toBan ->
                val guild = event.guild!!
                val toBanMemberCheck = guild.getMember(toBan)
                if (toBanMemberCheck != null && !member.canInteract(toBanMemberCheck)) {
                    hook.editOriginal("You can't ban a user that you can't interact with.").queue()
                    return@queue
                }

                guild.ban(
                    User.fromId(userId),
                    1,
                    TimeUnit.DAYS
                ).reason(reason.toAuditReason()).queue({
                    val guildLogger = event.jda.registeredListeners.firstOrNull { it is GuildLogger } as GuildLogger?
                    if (guildLogger != null) {
                        val logEmbed = EmbedBuilder()
                            .setColor(Color.RED)
                            .setTitle("User banned by id")
                            .addField("UUID", UUID.randomUUID().toString(), false)
                            .addField("User", toBan.name, true)
                            .addField("Moderator", event.member!!.nicknameAndUsername, true)
                            .addField("Reason", reason, false)

                        guildLogger.log(logEmbed, toBan, guild, null, GuildLogger.LogTypeAction.MODERATOR)
                    }

                    hook.editOriginal("Banned $toBan").queue()
                }) { throwable ->
                    hook.editOriginal("Banning user failed: ${throwable.message}").queue()
                }
            }) { throwable ->
                hook.editOriginal("Failed retrieving the user, banning failed: ${throwable.message}").queue()
            }
        }
    }

    override fun getCommandsData(): List<SlashCommandData> {
        return listOf(
            Commands.slash(COMMAND, DESCRIPTION)
                .addOptions(
                    OptionData(OptionType.STRING, OPTION_USER_ID, "The user ID to ban").setRequired(true),
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS))
        )
    }

    private fun createReasonModal(userId: Long): Modal {
        val targetText = TextDisplay.of("Banning: <@$userId> (ID: $userId)")
        val reasonInput = TextInput.create(REASON_INPUT_ID, TextInputStyle.PARAGRAPH)
            .setPlaceholder("Enter the reason for this ban...")
            .setMinLength(1)
            .setMaxLength(1024)
            .build()

        return Modal.create("$MODAL_ID:$userId", "Enter Reason")
            .addComponents(targetText, Label.of("Reason", reasonInput))
            .build()
    }
}
