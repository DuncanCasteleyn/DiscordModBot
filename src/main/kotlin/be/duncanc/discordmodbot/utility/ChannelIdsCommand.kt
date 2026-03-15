package be.duncanc.discordmodbot.utility

import be.duncanc.discordmodbot.discord.SlashCommand
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.utils.MarkdownUtil
import net.dv8tion.jda.api.utils.SplitUtil
import org.springframework.stereotype.Component

@Component
class ChannelIdsCommand : ListenerAdapter(), SlashCommand {
    companion object {
        private const val COMMAND = "channelids"
        private const val DESCRIPTION = "Returns all channel ids of the guild where executed."
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != COMMAND) return

        val guild = event.guild
        if (guild == null) {
            event.reply("This command only works in a guild.").setEphemeral(true).queue()
            return
        }

        val member = event.member
        if (member?.hasPermission(Permission.MANAGE_CHANNEL) != true) {
            event.reply("You need manage channels permission to use this command.")
                .setEphemeral(true)
                .queue()
            return
        }

        val result = StringBuilder()
        guild.textChannels.forEach { channel: TextChannel ->
            result.append(channel.toString()).append("\n")
        }

        val content = MarkdownUtil.codeblock("text", result.toString())
        val messages = SplitUtil.split(content, Message.MAX_CONTENT_LENGTH, SplitUtil.Strategy.NEWLINE)

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
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_CHANNEL))
        )
    }
}
