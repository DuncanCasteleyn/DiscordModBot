package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.discord.SlashCommand
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.InteractionContextType
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import org.springframework.stereotype.Component

@Component
class TrapChannelConfigCommand(
    private val trapChannelService: TrapChannelService
) : ListenerAdapter(), SlashCommand {
    companion object {
        private const val COMMAND = "trapchannel"
        private const val DESCRIPTION = "Configure the anti-spambot trap channel."

        private const val SUBCOMMAND_SHOW = "show"
        private const val SUBCOMMAND_SET = "set"
        private const val SUBCOMMAND_CLEAR = "clear"

        private const val OPTION_CHANNEL = "channel"
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != COMMAND) {
            return
        }

        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            event.reply("This command only works in a guild.").setEphemeral(true).queue()
            return
        }

        if (!member.hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("You need administrator permission to use this command.").setEphemeral(true).queue()
            return
        }

        when (event.subcommandName) {
            null, SUBCOMMAND_SHOW -> showCurrentSettings(event, guild)
            SUBCOMMAND_SET -> {
                val channel = getRequiredTextChannel(event) ?: return
                trapChannelService.setTrapChannel(guild.idLong, channel.idLong)
                event.reply("Trap channel set to ${channel.asMention}.").setEphemeral(true).queue()
            }

            SUBCOMMAND_CLEAR -> {
                trapChannelService.clearTrapChannel(guild.idLong)
                event.reply("Trap channel cleared.").setEphemeral(true).queue()
            }

            else -> event.reply("Please choose a valid /trapchannel subcommand.").setEphemeral(true).queue()
        }
    }

    override fun getCommandsData(): List<SlashCommandData> {
        return listOf(
            Commands.slash(COMMAND, DESCRIPTION)
                .addSubcommands(
                    SubcommandData(SUBCOMMAND_SHOW, "Show the current trap channel"),
                    SubcommandData(SUBCOMMAND_SET, "Set the trap channel")
                        .addOptions(textChannelOption("The text channel used to trap spambots")),
                    SubcommandData(SUBCOMMAND_CLEAR, "Disable the trap channel")
                )
                .setContexts(InteractionContextType.GUILD)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
        )
    }

    private fun showCurrentSettings(event: SlashCommandInteractionEvent, guild: Guild) {
        val configuredChannelId = trapChannelService.getTrapChannelId(guild.idLong)
        val configuredChannel = if (configuredChannelId == null) {
            "Not configured"
        } else {
            guild.getTextChannelById(configuredChannelId)?.asMention ?: "Channel not found (ID: $configuredChannelId)"
        }

        val message = buildString {
            appendLine("Trap channel settings for ${guild.name}")
            appendLine()
            appendLine("- Trap channel: $configuredChannel")
            appendLine("- Action: Ban, delete the last hour of messages, then unban shortly after")
        }

        event.reply(message).setEphemeral(true).queue()
    }

    internal open fun getRequiredTextChannel(event: SlashCommandInteractionEvent): TextChannel? {
        val channel = event.getOption(OPTION_CHANNEL)?.asChannel?.asTextChannel()
        if (channel == null) {
            event.reply("Please mention a text channel.").setEphemeral(true).queue()
            return null
        }

        return channel
    }

    private fun textChannelOption(description: String): OptionData {
        return OptionData(OptionType.CHANNEL, OPTION_CHANNEL, description, true)
            .setChannelTypes(ChannelType.TEXT)
    }
}
