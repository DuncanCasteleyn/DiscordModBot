package be.duncanc.discordmodbot.discord


import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData

interface SlashCommand : DiscordCommand {
    override fun getCommandsData(): List<SlashCommandData>

    fun onSlashCommandInteraction(event: SlashCommandInteractionEvent)
}
