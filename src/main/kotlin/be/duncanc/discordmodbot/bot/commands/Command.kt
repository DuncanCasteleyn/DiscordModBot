package be.duncanc.discordmodbot.bot.commands

import net.dv8tion.jda.api.interactions.commands.build.CommandData

interface Command {
    fun getCommandsData(): List<CommandData>
}
