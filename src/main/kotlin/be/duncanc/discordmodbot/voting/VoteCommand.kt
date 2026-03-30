package be.duncanc.discordmodbot.voting

import be.duncanc.discordmodbot.discord.SlashCommand
import be.duncanc.discordmodbot.voting.persistence.VotingEmotesRepository
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.InteractionContextType
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import org.springframework.stereotype.Component

@Component
class VoteCommand(
    private val votingEmotesRepository: VotingEmotesRepository
) : ListenerAdapter(), SlashCommand {
    companion object {
        private const val COMMAND = "vote"
        private const val DESCRIPTION = "Create a vote message with reactions."
        private const val SUBCOMMAND_YES_NO = "yesno"
        private const val SUBCOMMAND_NUMERIC = "numeric"
        private const val OPTION_PROMPT = "prompt"
        private const val OPTION_COUNT = "count"
        private const val PROMPT_MAX_LENGTH = 1000

        private val DEFAULT_YES_NO_REACTIONS = listOf(
            Emoji.fromUnicode("✅"),
            Emoji.fromUnicode("❎")
        )

        private val NUMERIC_VOTE_EMOTES = listOf(
            "1⃣",
            "2⃣",
            "3⃣",
            "4⃣",
            "5⃣",
            "6⃣",
            "7⃣",
            "8⃣",
            "9⃣",
            "🔟",
            "0⃣"
        )
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != COMMAND) {
            return
        }

        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            event.reply("This command only works in a guild.").setEphemeral(true).queue()
            return
        }

        val prompt = getPrompt(event)?.trim()
        if (prompt.isNullOrBlank()) {
            event.reply("Please provide a vote prompt.").setEphemeral(true).queue()
            return
        }

        when (event.subcommandName) {
            SUBCOMMAND_YES_NO -> createVote(
                event = event,
                content = buildVoteMessage(member.asMention, prompt, null),
                reactions = resolveYesNoReactions(guild.idLong, event)
            )

            SUBCOMMAND_NUMERIC -> {
                val count = getCount(event)
                if (count == null || count !in 2..11) {
                    event.reply("Please provide a number of voting options between 2 and 11.")
                        .setEphemeral(true)
                        .queue()
                    return
                }

                createVote(
                    event = event,
                    content = buildVoteMessage(member.asMention, prompt, count),
                    reactions = numericReactions(count)
                )
            }

            else -> event.reply("Please choose a valid /vote subcommand.").setEphemeral(true).queue()
        }
    }

    override fun getCommandsData(): List<SlashCommandData> {
        return listOf(
            Commands.slash(COMMAND, DESCRIPTION)
                .setContexts(InteractionContextType.GUILD)
                .addSubcommands(
                    SubcommandData(SUBCOMMAND_YES_NO, "Create a yes or no vote")
                        .addOptions(promptOption()),
                    SubcommandData(SUBCOMMAND_NUMERIC, "Create a numeric vote")
                        .addOptions(
                            promptOption(),
                            OptionData(OptionType.INTEGER, OPTION_COUNT, "Amount of options, from 2 to 11", true)
                                .setMinValue(2)
                                .setMaxValue(11)
                        )
                )
        )
    }

    internal fun getPrompt(event: SlashCommandInteractionEvent): String? {
        return event.getOption(OPTION_PROMPT)?.asString
    }

    internal fun getCount(event: SlashCommandInteractionEvent): Int? {
        return event.getOption(OPTION_COUNT)?.asInt
    }

    internal fun createVoteMessage(
        event: SlashCommandInteractionEvent,
        content: String,
        onSuccess: (Message) -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        event.channel.sendMessage(content).queue(onSuccess, onFailure)
    }

    internal fun resolveYesNoReactions(guildId: Long, event: SlashCommandInteractionEvent): List<Emoji> {
        val config = votingEmotesRepository.findById(guildId).orElse(null) ?: return DEFAULT_YES_NO_REACTIONS
        val yesEmoji = event.jda.getEmojiById(config.voteYesEmote)
        val noEmoji = event.jda.getEmojiById(config.voteNoEmote)
        return if (yesEmoji != null && noEmoji != null) {
            listOf(Emoji.fromCustom(yesEmoji), Emoji.fromCustom(noEmoji))
        } else {
            DEFAULT_YES_NO_REACTIONS
        }
    }

    internal fun addReactions(
        message: Message,
        reactions: List<Emoji>,
        onSuccess: () -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        addReactionSequentially(message, reactions, 0, onSuccess, onFailure)
    }

    private fun createVote(event: SlashCommandInteractionEvent, content: String, reactions: List<Emoji>) {
        event.deferReply(true).queue { hook ->
            createVoteMessage(
                event = event,
                content = content,
                onSuccess = { message ->
                    addReactions(
                        message = message,
                        reactions = reactions,
                        onSuccess = {
                            hook.sendMessage("Vote created in ${event.channel.asMention}.").setEphemeral(true).queue()
                        },
                        onFailure = {
                            hook.sendMessage("The vote message was posted, but I could not add all reactions.")
                                .setEphemeral(true)
                                .queue()
                        }
                    )
                },
                onFailure = {
                    hook.sendMessage("I could not post the vote message in this channel.").setEphemeral(true).queue()
                }
            )
        }
    }

    private fun addReactionSequentially(
        message: Message,
        reactions: List<Emoji>,
        index: Int,
        onSuccess: () -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        if (index >= reactions.size) {
            onSuccess()
            return
        }

        message.addReaction(reactions[index]).queue(
            { addReactionSequentially(message, reactions, index + 1, onSuccess, onFailure) },
            onFailure
        )
    }

    private fun buildVoteMessage(authorMention: String, prompt: String, count: Int?): String {
        return buildString {
            appendLine("Vote started by $authorMention")
            appendLine()
            appendLine(prompt)
            if (count != null) {
                appendLine()
                append("Options: 1-$count")
            }
        }.trimEnd()
    }

    private fun numericReactions(count: Int): List<Emoji> {
        return NUMERIC_VOTE_EMOTES.take(count).map(Emoji::fromUnicode)
    }

    private fun promptOption(): OptionData {
        return OptionData(OptionType.STRING, OPTION_PROMPT, "The vote question or prompt", true)
            .setMaxLength(PROMPT_MAX_LENGTH)
    }
}
