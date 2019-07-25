package be.duncanc.discordmodbot.bot.services

import be.duncanc.discordmodbot.bot.commands.CommandModule
import be.duncanc.discordmodbot.bot.utils.nicknameAndUsername
import be.duncanc.discordmodbot.data.entities.BlackListedWord
import be.duncanc.discordmodbot.data.entities.BlackListedWord.FilterMethod
import be.duncanc.discordmodbot.data.repositories.BlackListedWordRepository
import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.Guild
import net.dv8tion.jda.core.entities.Message
import net.dv8tion.jda.core.entities.MessageChannel
import net.dv8tion.jda.core.events.ReadyEvent
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.core.events.message.guild.GuildMessageUpdateEvent
import org.springframework.stereotype.Component
import java.awt.Color
import java.util.*
import java.util.concurrent.TimeUnit

@Component
class WordFiltering(
        val blackListedWordRepository: BlackListedWordRepository,
        val logger: GuildLogger
) : CommandModule(
        arrayOf("AddWordFilter"),
        "[Word] [${Arrays.toString(FilterMethod.values())}",
        "Adds a word to the blacklist and filters it based on the supplied filter method",
        requiredPermissions = *arrayOf(Permission.MESSAGE_MANAGE)
) {
    companion object {
        val signFilter = Regex("[^A-Za-z0-9 ]")
    }

    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        val argSplit = arguments?.split(' ')
        if (argSplit == null || argSplit.size < 2) {
            throw IllegalArgumentException("You need to specify a word followed by a filtering method")
        }
        val blackListedWord = BlackListedWord(event.guild.idLong, argSplit[0], FilterMethod.valueOf(argSplit[1].toUpperCase()))
        blackListedWordRepository.save(blackListedWord)
        event.channel.sendMessage("Word has been added to the filter list.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
    }

    override fun onGuildMessageUpdate(event: GuildMessageUpdateEvent) {
        if (event.member?.user == event.jda.selfUser) {
            return
        }
        val message: Message = event.message
        val channel: MessageChannel = message.channel
        val guild = event.guild
        checkForBlacklistedWords(message, guild, channel)
    }

    override fun onGuildMessageReceived(event: GuildMessageReceivedEvent) {
        if (event.member?.user == event.jda.selfUser) {
            return
        }
        val message: Message = event.message
        val channel: MessageChannel = message.channel
        val guild = event.guild
        checkForBlacklistedWords(message, guild, channel)
    }

    private fun checkForBlacklistedWords(message: Message, guild: Guild, channel: MessageChannel) {
        val messageContent = signFilter.replace(message.contentStripped, " ")
        val messageWords = messageContent.split(" ")
        val blackListedGuildWords = blackListedWordRepository.findAllByGuildId(guild.idLong)
        val containsBlackListedWord = blackListedGuildWords.any {
            when (it.filterMethod) {
                FilterMethod.EXACT -> messageWords.any { word -> word.equals(it.word, ignoreCase = true) }
                FilterMethod.CONTAINS -> messageWords.any { word ->
                    it.word?.let { blackListedWord -> word.contains(blackListedWord, ignoreCase = true) } ?: false
                }
                FilterMethod.STARTS_WITH -> messageWords.any { word ->
                    it.word?.let { blackListedWord -> word.startsWith(blackListedWord, ignoreCase = true) } ?: false
                }
                FilterMethod.ENDS_WITH -> messageWords.any { word ->
                    it.word?.let { blacklistedWord -> word.endsWith(blacklistedWord, ignoreCase = true) } ?: false
                }
            }
        }
        if (containsBlackListedWord) {
            message.delete().reason("Contains blacklisted word(s)").queue()
            val embedBuilder: EmbedBuilder = EmbedBuilder()
                    .setTitle("#" + channel.name + ": Message was removed due to blacklisted word(s)!")
                    .setDescription("Old message was:\n" + message.contentDisplay)
                    .addField("Author", message.member.nicknameAndUsername, true)
                    .addField("Message URL", "[Link](${message.jumpUrl})", false)
                    .setColor(Color.RED)
            logger.log(embedBuilder, message.author, guild, actionType = GuildLogger.LogTypeAction.MODERATOR)
            channel.sendMessage("${message.author.asMention} Your message has been deleted it contained word(s) banned under Discord ToS. Please watch your language.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
        }
    }

    override fun onReady(event: ReadyEvent) {
        event.jda.addEventListener(RemoveWord(), ListWords())
    }

    inner class RemoveWord : CommandModule(
            arrayOf("RemoveWordFilter"),
            "[word]",
            "Removes the word from the filter",
            requiredPermissions = *arrayOf(Permission.MESSAGE_MANAGE)
    ) {
        override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
            if (arguments == null || arguments.contains(' ')) {
                throw IllegalArgumentException("One word is required to remove a word from the filter")
            }
            blackListedWordRepository.deleteById(BlackListedWord.BlackListedWordId(event.guild.idLong, arguments))
            event.channel.sendMessage("The word has been removed from the filter.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
        }
    }

    inner class ListWords : CommandModule(
            arrayOf("ListWordFilters"),
            null,
            "List all the black listed words",
            requiredPermissions = *arrayOf(Permission.MESSAGE_MANAGE)
    ) {
        override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
            val blackListedWords = blackListedWordRepository.findAllByGuildId(event.guild.idLong).toCollection(arrayListOf())
            event.channel.sendMessage("The following words are blacklisted: \n${blackListedWords.joinToString("\n")}").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
        }
    }
}