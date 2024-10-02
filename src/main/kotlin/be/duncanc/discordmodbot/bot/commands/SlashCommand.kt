package be.duncanc.discordmodbot.bot.commands

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData

interface SlashCommand {
    fun getCommandsData(): List<SlashCommandData>

    fun onSlashCommandInteraction(event: SlashCommandInteractionEvent)
}
