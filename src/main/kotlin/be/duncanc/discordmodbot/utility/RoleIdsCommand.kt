package be.duncanc.discordmodbot.utility

import be.duncanc.discordmodbot.discord.SlashCommand
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.utils.SplitUtil
import org.springframework.stereotype.Component

@Component
class RoleIdsCommand : ListenerAdapter(), SlashCommand {
    companion object {
        private const val COMMAND = "roleids"
        private const val DESCRIPTION = "Get all the role ids of the guild where executed."
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != COMMAND) return

        val guild = event.guild
        if (guild == null) {
            event.reply("This command only works in a guild.").setEphemeral(true).queue()
            return
        }

        val member = event.member
        if (member?.hasPermission(Permission.MANAGE_ROLES) != true) {
            event.reply("You need manage roles permission to use this command.")
                .setEphemeral(true)
                .queue()
            return
        }

        val result = StringBuilder()
        guild.roles.forEach { role: Role -> result.append(role.toString()).append("\n") }

        val messages = SplitUtil.split(result.toString(), Message.MAX_CONTENT_LENGTH, SplitUtil.Strategy.NEWLINE)

        if (messages.isNotEmpty()) {
            event.reply(messages.removeFirst()).setEphemeral(true).queue()
            messages.forEach { message ->
                event.hook.sendMessage(message).setEphemeral(true).queue()
            }
        }
    }

    override fun getCommandsData(): List<SlashCommandData> {
        return listOf(
            Commands.slash(COMMAND, DESCRIPTION)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_ROLES))
        )
    }
}
