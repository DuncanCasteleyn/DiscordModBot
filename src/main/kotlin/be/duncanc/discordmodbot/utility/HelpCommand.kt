package be.duncanc.discordmodbot.utility

import be.duncanc.discordmodbot.discord.SlashCommand
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.springframework.stereotype.Component

@Component
class HelpCommand : ListenerAdapter(), SlashCommand {
    companion object {
        private const val COMMAND = "help"
        private const val DESCRIPTION = "Show a list of commands"
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != COMMAND) return

        val helpEmbeds: MutableList<EmbedBuilder> = mutableListOf(EmbedBuilder().setTitle("Help - Slash Commands"))

        event.jda.registeredListeners.filterIsInstance<SlashCommand>().forEach { slashCommand ->
            slashCommand.getCommandsData().forEach { commandData ->
                if (helpEmbeds[helpEmbeds.lastIndex].fields.count() >= 25) {
                    helpEmbeds.add(EmbedBuilder().setTitle("Help - Slash Commands (${helpEmbeds.size})"))
                }
                helpEmbeds[helpEmbeds.lastIndex].addField(
                    "/${commandData.name}",
                    commandData.description,
                    false
                )
            }
        }

        if (helpEmbeds.isNotEmpty()) {
            event.replyEmbeds(helpEmbeds.removeFirst().build()).setEphemeral(true).queue()
            helpEmbeds.forEach { embedBuilder ->
                event.hook.sendMessageEmbeds(embedBuilder.build()).setEphemeral(true).queue()
            }
        }
    }

    override fun getCommandsData(): List<SlashCommandData> {
        return listOf(Commands.slash(COMMAND, DESCRIPTION))
    }
}
