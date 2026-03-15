package be.duncanc.discordmodbot.utility

import be.duncanc.discordmodbot.discord.SlashCommand
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.springframework.stereotype.Component

@Component
class InfoCommand : ListenerAdapter(), SlashCommand {
    companion object {
        private const val COMMAND = "info"
        private const val DESCRIPTION = "Returns information about the bot."

        private val INFO_MESSAGE: MessageEmbed = EmbedBuilder()
            .setTitle("Discord bot", null)
            .setDescription("**Author:** dunci.\n**Language:** Java & Kotlin\n**Discord-lib:** JDA")
            .setColor(java.awt.Color.RED)
            .build()
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != COMMAND) return

        event.replyEmbeds(INFO_MESSAGE).setEphemeral(true).queue()
    }

    override fun getCommandsData(): List<SlashCommandData> {
        return listOf(Commands.slash(COMMAND, DESCRIPTION))
    }
}
