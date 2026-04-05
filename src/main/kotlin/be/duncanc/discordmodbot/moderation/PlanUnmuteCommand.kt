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
import net.dv8tion.jda.api.utils.TimeFormat
import org.springframework.stereotype.Component

@Component
class PlanUnmuteCommand(
    private val unmutePlanningService: UnmutePlanningService
) : ListenerAdapter(), SlashCommand {
    companion object {
        private const val COMMAND = "planunmute"
        private const val DESCRIPTION = "Schedule a user to be unmuted after a specified number of days."
        private const val OPTION_USER = "user"
        private const val OPTION_DAYS = "days"
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != COMMAND) return

        val member = event.member
        if (member == null) {
            event.reply("This command only works in a guild.").setEphemeral(true).queue()
            return
        }

        if (!member.hasPermission(Permission.MANAGE_ROLES)) {
            event.reply("You need manage roles permission to schedule an unmute.").setEphemeral(true).queue()
            return
        }

        val targetUserId = event.getOption(OPTION_USER)?.asLong
        if (targetUserId == null) {
            event.reply("You need to mention a user.").setEphemeral(true).queue()
            return
        }

        val days = event.getOption(OPTION_DAYS)?.asInt
        if (days == null || days <= 0) {
            event.reply("Please provide a valid number of days.").setEphemeral(true).queue()
            return
        }

        val guild = event.guild!!
        try {
            val unmuteDateTime = unmutePlanningService.planUnmute(guild, targetUserId, member, days)
            val targetMention = guild.getMemberById(targetUserId)?.asMention ?: "<@$targetUserId>"

            event.reply(
                "Unmute has been planned for $targetMention on ${
                    TimeFormat.DATE_SHORT_TIME_SHORT.atInstant(
                        unmuteDateTime.toInstant()
                    )
                }."
            ).setEphemeral(true).queue()
        } catch (e: IllegalArgumentException) {
            event.reply(e.message ?: "Please provide a valid number of days.").setEphemeral(true).queue()
        } catch (e: IllegalStateException) {
            event.reply(e.message ?: "Unable to plan an unmute.").setEphemeral(true).queue()
        }
    }

    override fun getCommandsData(): List<SlashCommandData> {
        return listOf(
            Commands.slash(COMMAND, DESCRIPTION)
                .addOptions(
                    OptionData(OptionType.USER, OPTION_USER, "The muted user to schedule unmute for").setRequired(true),
                    OptionData(OptionType.INTEGER, OPTION_DAYS, "Number of days until unmute").setRequired(true)
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_ROLES))
        )
    }
}
