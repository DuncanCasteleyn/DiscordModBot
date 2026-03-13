package be.duncanc.discordmodbot.utility

import be.duncanc.discordmodbot.discord.CommandModule
import be.duncanc.discordmodbot.discord.UserBlockService
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.springframework.stereotype.Component
import java.awt.Color

/**
 * Information commands for the bot.
 *
 * @since 1.0.0
 */
@Component
class Info(
    userBlockService: UserBlockService
) : CommandModule(
    arrayOf("Info"),
    null,
    "Returns information about the bot.",
    userBlockService = userBlockService
) {

    companion object {
        private val INFO_MESSAGE: MessageEmbed = EmbedBuilder()
            .setTitle("Discord bot", null)
            .setDescription("**Author:** Dunciboy\n**Language:** Java & Kotlin\n**Discord-lib:** JDA")
            .setColor(Color.RED)
            .build()
    }

    /**
     * Sends information about the bot to the user.
     */
    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        event.author.openPrivateChannel().queue { it.sendMessageEmbeds(INFO_MESSAGE).queue() }
    }
}
