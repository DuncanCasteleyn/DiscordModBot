package be.duncanc.discordmodbot.bot.commands

import be.duncanc.discordmodbot.data.services.UserBlockService
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import org.springframework.stereotype.Component

@Component
class Spoiler(
    userBlockService: UserBlockService
) : CommandModule(
    arrayOf("Spoiler"),
    "[text/attachments attach spoiler]",
    "Will take message and all its contents and spoiler tag it and send it back.",
    ignoreWhitelist = true,
    userBlockService = userBlockService
) {
    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        // TODO: Use a webhook trick in order to send messages "as" the user.
        // TODO: "properly" tag images as spoilers instead of hijacking discord link embeds
        if (arguments == null && event.message.attachments.isEmpty()) {
            throw IllegalArgumentException("This command requires something to spoiler tag.")
        }

        val spoilerTaggedStringMessage = "||$arguments||\n"
        val embedLinkList = event.message.attachments.map {
            "||${it.url}||\n"
        }

        val returnMessageCreateBuilder = MessageCreateBuilder()
            .addContent("From: ${event.message.author.asMention}\n")
            .addContent(spoilerTaggedStringMessage)
            .addContent(embedLinkList.joinToString { it })
        event.guildChannel.sendMessage(returnMessageCreateBuilder.build()).queue()
    }
}
