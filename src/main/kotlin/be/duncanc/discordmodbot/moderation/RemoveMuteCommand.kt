package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.discord.SlashCommand
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.springframework.stereotype.Component

@Component
class RemoveMuteCommand(
    private val muteRole: MuteRole
) : ListenerAdapter(), SlashCommand {
    companion object {
        private const val COMMAND = "unmute"
        private const val DESCRIPTION = "This command will remove the muted role from a user."
        private const val OPTION_USER = "user"
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != COMMAND) return

        val member = event.member
        if (member == null) {
            event.reply("This command only works in a guild.").setEphemeral(true).queue()
            return
        }

        if (!member.hasPermission(Permission.MANAGE_ROLES)) {
            event.reply("You need manage roles permission to unmute.").setEphemeral(true).queue()
            return
        }

        val targetMember = event.getOption(OPTION_USER)?.asMember
        if (targetMember == null) {
            event.reply("You need to mention a user that is still in the server.").setEphemeral(true).queue()
            return
        }

        if (member.canInteract(targetMember) != true) {
            event.reply("You can't unmute a user that you can't interact with.").setEphemeral(true).queue()
            return
        }

        val muteRole = try {
            muteRole.getMuteRole(event.guild!!)
        } catch (e: IllegalStateException) {
            event.reply("Mute role is not configured for this server.").setEphemeral(true).queue()
            return
        }

        event.deferReply().queue { hook ->
            val guild = event.guild!!
            guild.removeRoleFromMember(targetMember, muteRole).queue({
                hook.editOriginal("Unmuted ${targetMember.asMention}.").queue()
            }) { throwable ->
                hook.editOriginal("Failed to unmute ${targetMember.asMention}: ${throwable.message}").queue()
            }
        }
    }

    override fun getCommandsData(): List<SlashCommandData> {
        return listOf(
            Commands.slash(COMMAND, DESCRIPTION)
                .addOptions(
                    OptionData(OptionType.USER, OPTION_USER, "The user to unmute").setRequired(true)
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_ROLES))
        )
    }
}
