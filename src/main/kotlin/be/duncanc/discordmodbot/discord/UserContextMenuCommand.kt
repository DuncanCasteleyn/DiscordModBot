package be.duncanc.discordmodbot.discord

import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent

interface UserContextMenuCommand : DiscordCommand {
    fun onUserContextInteraction(event: UserContextInteractionEvent)
}
