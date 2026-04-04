package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.discord.SlashCommand
import be.duncanc.discordmodbot.discord.nicknameAndUsername
import be.duncanc.discordmodbot.logging.GuildLogger
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
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
        private const val OPTION_REASON = "reason"
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

        val reason = event.getOption(OPTION_REASON)?.asString ?: "No reason provided"

        event.deferReply(true).queue { hook ->
            val guild = member.guild

            val muteRole = try {
                muteRoleCommandAndEventsListenerService.getMuteRole(guild)
            } catch (_: IllegalStateException) {
                hook.editOriginal("Mute role is not configured for this server.").queue()
                return@queue
            }

            muteService.muteUserById(guild.idLong, userId)

            val targetMember = event.getOption(OPTION_USER_ID)?.asMember
            targetMember?.let {
                guild.addRoleToMember(it, muteRole).reason(reason).queue()
            }


            val logEmbed = EmbedBuilder()
                .setColor(Color.YELLOW)
                .setTitle("User muted by ID")
                .addField("UUID", UUID.randomUUID().toString(), false)
                .addField("User ID", userId.toString(), true)
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
                    OptionData(OptionType.STRING, OPTION_REASON, "Reason for mute").setRequired(true)
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_ROLES))
        )
    }
}
