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
import java.util.concurrent.TimeUnit

@Component
class PurgeChannelCommand : ListenerAdapter(), SlashCommand {
    companion object {
        private const val COMMAND = "purgechannel"
        private const val DESCRIPTION = "Delete a specified number of messages from the current channel."
        private const val OPTION_AMOUNT = "amount"
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != COMMAND) return

        val member = event.member
        if (member == null) {
            event.reply("This command only works in a guild.").setEphemeral(true).queue()
            return
        }

        if (!member.hasPermission(Permission.MANAGE_CHANNEL)) {
            event.reply("You need manage channels permission to use this command.").setEphemeral(true).queue()
            return
        }

        val amount = event.getOption(OPTION_AMOUNT)?.asInt
        if (amount == null || amount < 1 || amount > 100) {
            event.reply("Please provide an amount between 1 and 100.").setEphemeral(true).queue()
            return
        }

        val channel = event.channel

        event.deferReply().queue { hook ->
            val messages = channel.history.retrievePast(amount).complete()
            channel.purgeMessages(messages)
            hook.editOriginal("Deleted ${messages.size} message(s).").queue {
                it.delete().queueAfter(5, TimeUnit.SECONDS)
            }
        }
    }

    override fun getCommandsData(): List<SlashCommandData> {
        return listOf(
            Commands.slash(COMMAND, DESCRIPTION)
                .addOptions(
                    OptionData(OptionType.INTEGER, OPTION_AMOUNT, "Number of messages to delete (1-100)").setRequired(
                        true
                    )
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_CHANNEL))
        )
    }
}
