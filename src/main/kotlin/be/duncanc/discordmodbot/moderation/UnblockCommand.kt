package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.bootstrap.DiscordModBotConfig
import be.duncanc.discordmodbot.discord.SlashCommand
import be.duncanc.discordmodbot.moderation.persistence.BlockedUserRepository
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.springframework.stereotype.Component

@Component
class UnblockCommand(
    private val blockedUserRepository: BlockedUserRepository,
    private val discordModBotConfig: DiscordModBotConfig
) : ListenerAdapter(), SlashCommand {
    companion object {
        private const val COMMAND = "unblock"
        private const val DESCRIPTION = "Unblock a user from using bot commands (owner only)."
        private const val OPTION_USER_ID = "user_id"
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != COMMAND) return

        if (event.user.idLong != discordModBotConfig.ownerId) {
            event.reply("This command is only available to the bot owner.").setEphemeral(true).queue()
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

        blockedUserRepository.deleteById(userId)
        event.reply("User <@$userId> has been unblocked.").setEphemeral(true).queue()
    }

    override fun getCommandsData(): List<SlashCommandData> {
        return listOf(
            Commands.slash(COMMAND, DESCRIPTION)
                .addOptions(
                    OptionData(OptionType.STRING, OPTION_USER_ID, "The user ID to unblock").setRequired(true)
                )
                .setDefaultPermissions(DefaultMemberPermissions.DISABLED)
        )
    }
}
