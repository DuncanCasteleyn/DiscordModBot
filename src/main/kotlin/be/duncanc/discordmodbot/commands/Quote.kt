package be.duncanc.discordmodbot.commands

import be.duncanc.discordmodbot.utils.JDALibHelper
import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.events.message.MessageReceivedEvent

object Quote : CommandModule(arrayOf("Quote"), "[message id to quote] [response text]", "Will quote text and put a response under it, response text is optional", ignoreWhiteList = true) {

    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        if (arguments == null) {
            throw IllegalArgumentException("This command requires at least a message id.")
        }
        val channelId = arguments.split(" ")[0]
        val messageToQuote = event.textChannel.getMessageById(channelId).complete()
        if(messageToQuote.contentDisplay.isEmpty()) {
            throw IllegalArgumentException("The message you want to quote has no content to quote.")
        }
        val quoteEmbed = EmbedBuilder()
                .setAuthor(JDALibHelper.getEffectiveNameAndUsername(messageToQuote.member), null, messageToQuote.author.effectiveAvatarUrl)
                .setDescription(messageToQuote.contentDisplay)
        val response = arguments.substring(channelId.length)
        val responseEmbed = if (response.isEmpty()) {
            null
        } else {
            EmbedBuilder()
                    .setAuthor(JDALibHelper.getEffectiveNameAndUsername(event.member), null, event.author.effectiveAvatarUrl)
                    .setDescription(response)
        }
        event.textChannel.sendMessage(quoteEmbed.build()).queue()
        if (responseEmbed != null) {
            event.textChannel.sendMessage(responseEmbed.build()).queue()
        }
    }
}