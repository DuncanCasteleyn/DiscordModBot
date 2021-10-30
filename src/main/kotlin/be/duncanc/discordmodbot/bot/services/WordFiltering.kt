package be.duncanc.discordmodbot.bot.services

import be.duncanc.discordmodbot.bot.commands.Command
import be.duncanc.discordmodbot.bot.utils.nicknameAndUsername
import be.duncanc.discordmodbot.data.entities.BlackListedWord
import be.duncanc.discordmodbot.data.entities.BlackListedWord.FilterMethod
import be.duncanc.discordmodbot.data.repositories.jpa.BlackListedWordRepository
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.events.guild.GenericGuildEvent
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageUpdateEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import org.springframework.stereotype.Component
import java.awt.Color
import java.util.*
import java.util.concurrent.TimeUnit

@Component
class WordFiltering(
    val blackListedWordRepository: BlackListedWordRepository,
    val logger: GuildLogger
) : ListenerAdapter(), Command {


    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        val argSplit = arguments?.split(' ')
        if (argSplit == null || argSplit.size < 2) {
            throw IllegalArgumentException("You need to specify a word followed by a filtering method")
        }
        val blackListedWord =
            BlackListedWord(
                event.guild.idLong,
                argSplit[0],
                FilterMethod.valueOf(argSplit[1].uppercase(Locale.getDefault()))
            )
        blackListedWordRepository.save(blackListedWord)
        event.channel.sendMessage("Word has been added to the filter list.")
            .queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
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
        doFilter()
    }

    fun doFilter(event: GenericGuildEvent) {
        if (event.member?.user == event.jda.selfUser) {
            return
        }
        val message: Message = event.message
        val channel: MessageChannel = message.channel
        val guild = event.guild
        checkForBlacklistedWords(message, guild, channel)
    }

    private fun checkForBlacklistedWords(message: Message, guild: Guild, channel: MessageChannel) {
        val messageContent = message.contentStripped
        val messageWords = messageContent.split(" ")
        val blackListedGuildWords = blackListedWordRepository.findAllByGuildId(guild.idLong)
        val containsBlackListedWord = blackListedGuildWords.any {
            when (it.filterMethod) {
                FilterMethod.EXACT -> messageWords.any { word -> word.equals(it.word, ignoreCase = true) }
                FilterMethod.CONTAINS -> messageWords.any { word ->
                    it.word.let { blackListedWord -> word.contains(blackListedWord, ignoreCase = true) }
                }
                FilterMethod.STARTS_WITH -> messageWords.any { word ->
                    it.word.let { blackListedWord -> word.startsWith(blackListedWord, ignoreCase = true) }
                }
                FilterMethod.ENDS_WITH -> messageWords.any { word ->
                    it.word.let { blacklistedWord -> word.endsWith(blacklistedWord, ignoreCase = true) }
                }
            }
        }
        if (containsBlackListedWord) {
            message.delete().reason("Contains blacklisted word(s)").queue()
            val embedBuilder: EmbedBuilder = EmbedBuilder()
                .setTitle("#" + channel.name + ": Message was removed due to blacklisted word(s)!")
                .setDescription("Old message was:\n" + message.contentDisplay)
                .addField("Author", message.member?.nicknameAndUsername, true)
                .addField("Message URL", "[Link](${message.jumpUrl})", false)
                .setColor(Color.RED)
            logger.log(embedBuilder, message.author, guild, actionType = GuildLogger.LogTypeAction.MODERATOR)
            channel.sendMessage("${message.author.asMention} Your message has been deleted as it violates discords TOS or our rules.")
                .queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
        }
    }

    @Deprecated("TODO remove", level = DeprecationLevel.ERROR)
    inner class RemoveWord : ListenerAdapter() {
        fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
            if (arguments == null || arguments.contains(' ')) {
                throw IllegalArgumentException("One word is required to remove a word from the filter")
            }
            blackListedWordRepository.deleteById(BlackListedWord.BlackListedWordId(event.guild.idLong, arguments))
            event.channel.sendMessage("The word has been removed from the filter.")
                .queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
        }
    }

    @Deprecated("TODO remove", level = DeprecationLevel.ERROR)
    inner class ListWords : ListenerAdapter() {
        fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
            val blackListedWords =
                blackListedWordRepository.findAllByGuildId(event.guild.idLong).toCollection(arrayListOf())
            event.channel.sendMessage("The following words are blacklisted: \n${blackListedWords.joinToString("\n")}")
                .queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
        }
    }

    override fun onSlashCommand(event: SlashCommandEvent) {
        if (event.guild == null && event.name != "wordFilter") {
            return
        }

        if (event.subcommandName == "list") {
            TODO()
        }

        if (event.subcommandName == "add") {
            val regex = event.getOption("regex")?.asString
            TODO()
        }

        if (event.subcommandName == "remove") {
            TODO()
        }
    }

    override fun getCommandsData(): List<CommandData> {
        return listOf(
            CommandData("wordFilter", "Configure and control word filtering")
                .addSubcommands(
                    SubcommandData("list", "lists all the filters"),

                    SubcommandData("add", "add a filter")
                        .addOptions(OptionData(OptionType.STRING, "regex", "The regex expression to add")),

                    SubcommandData("remove", "remove a filter")
                        .addOptions(
                            OptionData(
                                OptionType.INTEGER,
                                "index",
                                "Integer from the list of filters to remove"
                            )
                        )
                )
        )
    }
}
