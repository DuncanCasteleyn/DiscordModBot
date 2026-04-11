package be.duncanc.discordmodbot.discord

import net.dv8tion.jda.api.interactions.commands.build.CommandData

interface DiscordCommand {
    fun getCommandsData(): List<CommandData>
}
