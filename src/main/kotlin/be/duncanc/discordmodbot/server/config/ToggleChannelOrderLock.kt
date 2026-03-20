package be.duncanc.discordmodbot.server.config

import be.duncanc.discordmodbot.discord.SlashCommand
import be.duncanc.discordmodbot.server.config.persistence.ChannelOrderLock
import be.duncanc.discordmodbot.server.config.persistence.ChannelOrderLockRepository
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ToggleChannelOrderLock(
    val channelOrderLockRepository: ChannelOrderLockRepository
) : ListenerAdapter(), SlashCommand {

    companion object {
        private const val COMMAND = "togglechannelorderlock"
        private const val DESCRIPTION = "Enables or disables channel order locking for this server."
    }

    @Transactional
    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != COMMAND) return

        val member = event.member
        if (member == null) {
            event.reply("This command only works in a guild.").setEphemeral(true).queue()
            return
        }

        if (!member.hasPermission(Permission.MANAGE_CHANNEL)) {
            event.reply("You need manage channel permission to use this command.").setEphemeral(true).queue()
            return
        }

        val guildId = member.guild.idLong
        val channelOrderLock = channelOrderLockRepository.findById(guildId)
            .orElse(null) ?: ChannelOrderLock(guildId)

        val updatedChannelOrderLock = channelOrderLock.copy(enabled = !channelOrderLock.enabled)

        channelOrderLockRepository.save(updatedChannelOrderLock)
        val statusText = if (updatedChannelOrderLock.enabled) "enabled" else "disabled"
        event.reply("Channel order locking is now $statusText.").setEphemeral(true).queue()
    }

    override fun getCommandsData(): List<SlashCommandData> {
        return listOf(
            Commands.slash(COMMAND, DESCRIPTION)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_CHANNEL))
        )
    }
}
