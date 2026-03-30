package be.duncanc.discordmodbot.reporting

import be.duncanc.discordmodbot.discord.SlashCommand
import be.duncanc.discordmodbot.reporting.persistence.ActivityReportSettings
import be.duncanc.discordmodbot.reporting.persistence.ActivityReportSettingsRepository
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.User
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
import net.dv8tion.jda.api.utils.SplitUtil
import org.springframework.stereotype.Component

@Component
class WeeklyActivitySettingsCommand(
    private val activityReportSettingsRepository: ActivityReportSettingsRepository
) : ListenerAdapter(), SlashCommand {
    companion object {
        private const val COMMAND = "weeklyactivitysettings"
        private const val DESCRIPTION = "Configure weekly activity report settings."
        private const val OPTION_CHANNEL = "channel"
        private const val OPTION_ROLE = "role"
        private const val OPTION_MEMBER = "member"
        private const val SUBCOMMAND_SHOW = "show"
        private const val SUBCOMMAND_SET_CHANNEL = "set-channel"
        private const val SUBCOMMAND_ADD_ROLE = "add-role"
        private const val SUBCOMMAND_REMOVE_ROLE = "remove-role"
        private const val SUBCOMMAND_ADD_MEMBER = "add-member"
        private const val SUBCOMMAND_REMOVE_MEMBER = "remove-member"
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
            SUBCOMMAND_SET_CHANNEL -> {
                val channel = getRequiredTextChannel(event) ?: return
                updateSettings(guild.idLong) { it.reportChannel = channel.idLong }
                event.reply("Weekly activity report channel set to ${channel.asMention}.").setEphemeral(true).queue()
            }

            SUBCOMMAND_ADD_ROLE -> {
                val role = getRequiredRole(event) ?: return
                updateSettings(guild.idLong) { it.trackedRoleOrMember.add(role.idLong) }
                event.reply("Added ${role.asMention} to the weekly activity report tracking list.").setEphemeral(true)
                    .queue()
            }

            SUBCOMMAND_REMOVE_ROLE -> {
                val role = getRequiredRole(event) ?: return
                updateSettings(guild.idLong) { it.trackedRoleOrMember.remove(role.idLong) }
                event.reply("Removed ${role.asMention} from the weekly activity report tracking list.")
                    .setEphemeral(true).queue()
            }

            SUBCOMMAND_ADD_MEMBER -> {
                val user = getRequiredUser(event) ?: return
                updateSettings(guild.idLong) { it.trackedRoleOrMember.add(user.idLong) }
                event.reply("Added ${user.asMention} to the weekly activity report tracking list.").setEphemeral(true)
                    .queue()
            }

            SUBCOMMAND_REMOVE_MEMBER -> {
                val user = getRequiredUser(event) ?: return
                updateSettings(guild.idLong) { it.trackedRoleOrMember.remove(user.idLong) }
                event.reply("Removed ${user.asMention} from the weekly activity report tracking list.")
                    .setEphemeral(true).queue()
            }

            else -> event.reply("Please choose a valid /weeklyactivitysettings subcommand.").setEphemeral(true).queue()
        }
    }

    override fun getCommandsData(): List<SlashCommandData> {
        return listOf(
            Commands.slash(COMMAND, DESCRIPTION)
                .setContexts(InteractionContextType.GUILD)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
                .addSubcommands(
                    SubcommandData(SUBCOMMAND_SHOW, "Show the current weekly activity report settings"),
                    SubcommandData(SUBCOMMAND_SET_CHANNEL, "Set the report channel")
                        .addOptions(textChannelOption("The channel used for weekly reports")),
                    SubcommandData(SUBCOMMAND_ADD_ROLE, "Track a role in weekly reports")
                        .addOptions(OptionData(OptionType.ROLE, OPTION_ROLE, "Role to track", true)),
                    SubcommandData(SUBCOMMAND_REMOVE_ROLE, "Stop tracking a role in weekly reports")
                        .addOptions(OptionData(OptionType.ROLE, OPTION_ROLE, "Role to remove", true)),
                    SubcommandData(SUBCOMMAND_ADD_MEMBER, "Track a member in weekly reports")
                        .addOptions(OptionData(OptionType.USER, OPTION_MEMBER, "Member to track", true)),
                    SubcommandData(SUBCOMMAND_REMOVE_MEMBER, "Stop tracking a member in weekly reports")
                        .addOptions(OptionData(OptionType.USER, OPTION_MEMBER, "Member to remove", true))
                )
        )
    }

    internal fun getRequiredTextChannel(event: SlashCommandInteractionEvent): TextChannel? {
        val channel = event.getOption(OPTION_CHANNEL)?.asChannel?.asTextChannel()
        if (channel == null) {
            event.reply("Please choose a text channel.").setEphemeral(true).queue()
            return null
        }

        return channel
    }

    internal fun getRequiredRole(event: SlashCommandInteractionEvent): Role? {
        val role = event.getOption(OPTION_ROLE)?.asRole
        if (role == null) {
            event.reply("Please choose a role.").setEphemeral(true).queue()
            return null
        }

        return role
    }

    internal fun getRequiredUser(event: SlashCommandInteractionEvent): User? {
        val user = event.getOption(OPTION_MEMBER)?.asUser
        if (user == null) {
            event.reply("Please choose a member.").setEphemeral(true).queue()
            return null
        }

        return user
    }

    private fun showCurrentSettings(event: SlashCommandInteractionEvent, guild: Guild) {
        val settings =
            activityReportSettingsRepository.findById(guild.idLong).orElse(null)
                ?: ActivityReportSettings(guild.idLong)

        val trackedTargets = settings.trackedRoleOrMember
            .map { trackedId -> formatTrackedTarget(guild, trackedId) }
            .sorted()

        val content = buildString {
            appendLine("Weekly activity report settings for ${guild.name}")
            appendLine()
            appendLine("- Report channel: ${formatChannel(guild, settings.reportChannel)}")
            appendLine("- Tracked roles and members:")
            if (trackedTargets.isEmpty()) {
                appendLine("- None configured")
            } else {
                trackedTargets.forEach { appendLine("- $it") }
            }
        }

        replySplitEphemeral(event, content)
    }

    private fun formatTrackedTarget(guild: Guild, trackedId: Long): String {
        val role = guild.getRoleById(trackedId)
        if (role != null) {
            return "Role ${role.asMention}"
        }

        val member = guild.getMemberById(trackedId)
        if (member != null) {
            return "Member ${member.asMention}"
        }

        return "Missing role/member (ID: $trackedId)"
    }

    private fun formatChannel(guild: Guild, channelId: Long?): String {
        if (channelId == null) {
            return "Not configured"
        }

        return guild.getTextChannelById(channelId)?.asMention ?: "Channel not found (ID: $channelId)"
    }

    private fun updateSettings(guildId: Long, update: (ActivityReportSettings) -> Unit) {
        val settings = activityReportSettingsRepository.findById(guildId).orElse(null)
            ?: ActivityReportSettings(guildId)

        update(settings)

        activityReportSettingsRepository.save(settings)
    }

    private fun replySplitEphemeral(event: SlashCommandInteractionEvent, content: String) {
        val messages = SplitUtil.split(content, Message.MAX_CONTENT_LENGTH, SplitUtil.Strategy.NEWLINE).toMutableList()
        val responses = messages.ifEmpty { mutableListOf("No weekly activity report settings found.") }
        event.deferReply(true).queue { hook ->
            responses.forEachIndexed { index, message ->
                val action = hook.sendMessage(message)
                if (index > 0) {
                    action.setEphemeral(true)
                }
                action.queue()
            }
        }
    }

    private fun textChannelOption(description: String): OptionData {
        return OptionData(OptionType.CHANNEL, OPTION_CHANNEL, description, true)
            .setChannelTypes(ChannelType.TEXT)
    }
}
