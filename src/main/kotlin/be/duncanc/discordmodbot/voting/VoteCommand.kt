package be.duncanc.discordmodbot.voting

import be.duncanc.discordmodbot.discord.SlashCommand
import be.duncanc.discordmodbot.voting.persistence.VotingEmotesRepository
import net.dv8tion.jda.api.components.label.Label
import net.dv8tion.jda.api.components.textinput.TextInput
import net.dv8tion.jda.api.components.textinput.TextInputStyle
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.InteractionContextType
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.modals.Modal
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
        private const val MODAL_PREFIX = "vote"
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

        when (event.subcommandName) {
            SUBCOMMAND_YES_NO -> event.replyModal(createVoteModal(member.asMention, SUBCOMMAND_YES_NO)).queue()

            SUBCOMMAND_NUMERIC -> {
                val count = getCount(event)
                if (count == null || count !in 2..11) {
                    event.reply("Please provide a number of voting options between 2 and 11.")
                        .setEphemeral(true)
                        .queue()
                    return
                }

                event.replyModal(createVoteModal(member.asMention, SUBCOMMAND_NUMERIC, count)).queue()
            }

            else -> event.reply("Please choose a valid /vote subcommand.").setEphemeral(true).queue()
        }
    }

    override fun onModalInteraction(event: ModalInteractionEvent) {
        if (!event.modalId.startsWith("$MODAL_PREFIX:")) {
            return
        }

        val member = event.member
        val guild = event.guild
        if (guild == null || member == null) {
            event.reply("This command only works in a guild.").setEphemeral(true).queue()
            return
        }

        val modalAction = parseModalAction(event.modalId)
        if (modalAction == null) {
            event.reply("This vote form is no longer valid. Run `/vote` again.").setEphemeral(true).queue()
            return
        }

        val prompt = event.getValue(OPTION_PROMPT)?.asString?.trim().orEmpty()
        if (prompt.isBlank()) {
            event.reply("Please provide a vote prompt.").setEphemeral(true).queue()
            return
        }

        when (modalAction.subcommand) {
            SUBCOMMAND_YES_NO -> createVote(
                event = event,
                content = buildVoteMessage(member.asMention, prompt, null),
                reactions = resolveYesNoReactions(guild.idLong, event)
            )

            SUBCOMMAND_NUMERIC -> {
                val count = modalAction.count
                if (count == null || count !in 2..11) {
                    event.reply("This vote form is no longer valid. Run `/vote` again.").setEphemeral(true).queue()
                    return
                }

                createVote(
                    event = event,
                    content = buildVoteMessage(member.asMention, prompt, count),
                    reactions = numericReactions(count)
                )
            }

            else -> event.reply("This vote form is no longer valid. Run `/vote` again.").setEphemeral(true).queue()
        }
    }

    override fun getCommandsData(): List<SlashCommandData> {
        return listOf(
            Commands.slash(COMMAND, DESCRIPTION)
                .setContexts(InteractionContextType.GUILD)
                .addSubcommands(
                    SubcommandData(SUBCOMMAND_YES_NO, "Create a yes or no vote"),
                    SubcommandData(SUBCOMMAND_NUMERIC, "Create a numeric vote")
                        .addOptions(
                            OptionData(OptionType.INTEGER, OPTION_COUNT, "Amount of options, from 2 to 11", true)
                                .setMinValue(2)
                                .setMaxValue(11)
                        )
                )
        )
    }

    internal fun getCount(event: SlashCommandInteractionEvent): Int? {
        return event.getOption(OPTION_COUNT)?.asInt
    }

    internal fun createVoteMessage(
        deferReply: (Boolean) -> net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction,
        content: String,
        onSuccess: (Message) -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        deferReply(false).queue(
            { hook ->
                hook.editOriginal(content).queue(onSuccess, onFailure)
            },
            onFailure
        )
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

    internal fun resolveYesNoReactions(guildId: Long, event: ModalInteractionEvent): List<Emoji> {
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
        createVote(
            deferReply = event::deferReply,
            hookProvider = { event.hook },
            content = content,
            reactions = reactions
        )
    }

    private fun createVote(event: ModalInteractionEvent, content: String, reactions: List<Emoji>) {
        createVote(
            deferReply = event::deferReply,
            hookProvider = { event.hook },
            content = content,
            reactions = reactions
        )
    }

    private fun createVote(
        deferReply: (Boolean) -> net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction,
        hookProvider: () -> net.dv8tion.jda.api.interactions.InteractionHook,
        content: String,
        reactions: List<Emoji>
    ) {
        createVoteMessage(
            deferReply = deferReply,
            content = content,
            onSuccess = { message ->
                addReactions(
                    message = message,
                    reactions = reactions,
                    onSuccess = {},
                    onFailure = {
                        hookProvider().sendMessage("The vote message was posted, but I could not add all reactions.")
                            .setEphemeral(true)
                            .queue()
                    }
                )
            },
            onFailure = {
                hookProvider().sendMessage("I could not post the vote message in this channel.").setEphemeral(true).queue()
            }
        )
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

    private fun createVoteModal(authorMention: String, subcommand: String, count: Int? = null): Modal {
        val promptInput = TextInput.create(OPTION_PROMPT, TextInputStyle.PARAGRAPH)
            .setPlaceholder("Enter the vote question or prompt...")
            .setMinLength(1)
            .setMaxLength(PROMPT_MAX_LENGTH)
            .build()

        val title = when (subcommand) {
            SUBCOMMAND_NUMERIC -> "Create Numeric Vote"
            else -> "Create Vote"
        }

        return Modal.create(buildModalId(subcommand, count), title)
            .addComponents(Label.of("Vote prompt", "Posting as $authorMention", promptInput))
            .build()
    }

    private fun buildModalId(subcommand: String, count: Int? = null): String {
        return if (count == null) {
            "$MODAL_PREFIX:$subcommand"
        } else {
            "$MODAL_PREFIX:$subcommand:$count"
        }
    }

    private fun parseModalAction(modalId: String): VoteModalAction? {
        val parts = modalId.split(":")
        if (parts.size !in 2..3 || parts[0] != MODAL_PREFIX) {
            return null
        }

        val subcommand = parts[1]
        val count = parts.getOrNull(2)?.toIntOrNull()
        return VoteModalAction(subcommand, count)
    }

    private data class VoteModalAction(
        val subcommand: String,
        val count: Int?
    )
}
