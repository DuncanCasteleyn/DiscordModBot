package be.duncanc.discordmodbot.utility

import be.duncanc.discordmodbot.discord.SlashCommand
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.springframework.stereotype.Component

@Component
class PingCommand : ListenerAdapter(), SlashCommand {
    companion object {
        private const val COMMAND = "ping"
        private const val DESCRIPTION = "responds with \"pong!\"."
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != "ping") return

        event.deferReply().queue { hook ->
            event.jda.restPing.queue { ping ->
                hook.editOriginal(
                    """pong!
It took Discord ${event.jda.gatewayPing} milliseconds to respond to our last heartbeat (gateway).
The REST API responded within $ping milliseconds"""
                ).queue()
            }

        }

    }

    override fun getCommandsData(): List<SlashCommandData> {
        return listOf(Commands.slash("ping", DESCRIPTION))
    }


}
