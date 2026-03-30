package be.duncanc.discordmodbot.voting

import be.duncanc.discordmodbot.discord.SlashCommand
import be.duncanc.discordmodbot.voting.persistence.VoteEmotes
import be.duncanc.discordmodbot.voting.persistence.VotingEmotesRepository
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.emoji.CustomEmoji
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.InteractionContextType
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import org.springframework.stereotype.Component

@Component
class VoteConfigCommand(
    private val votingEmotesRepository: VotingEmotesRepository
) : ListenerAdapter(), SlashCommand {
    companion object {
        private const val COMMAND = "voteconfig"
        private const val DESCRIPTION = "Configure custom yes and no vote emoji for this server."
        private const val SUBCOMMAND_SHOW = "show"
        private const val SUBCOMMAND_SET = "set"
        private const val SUBCOMMAND_RESET = "reset"
        private const val OPTION_YES_EMOJI = "yes-emoji"
        private const val OPTION_NO_EMOJI = "no-emoji"
        private val CUSTOM_EMOJI_REGEX = Regex("^<(a?):[A-Za-z0-9_]+:(\\d+)>$")
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

        if (!member.hasPermission(Permission.MANAGE_GUILD_EXPRESSIONS)) {
            event.reply("You need manage expressions permission to use this command.").setEphemeral(true).queue()
            return
        }

        when (event.subcommandName) {
            null, SUBCOMMAND_SHOW -> showCurrentSettings(event, guild.idLong)
            SUBCOMMAND_SET -> setEmojiConfiguration(event, guild.idLong)
            SUBCOMMAND_RESET -> {
                votingEmotesRepository.deleteById(guild.idLong)
                event.reply("Vote emoji configuration reset to the default reactions.").setEphemeral(true).queue()
            }

            else -> event.reply("Please choose a valid /voteconfig subcommand.").setEphemeral(true).queue()
        }
    }

    override fun getCommandsData(): List<SlashCommandData> {
        return listOf(
            Commands.slash(COMMAND, DESCRIPTION)
                .setContexts(InteractionContextType.GUILD)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_GUILD_EXPRESSIONS))
                .addSubcommands(
                    SubcommandData(SUBCOMMAND_SHOW, "Show the current yes and no vote emoji"),
                    SubcommandData(SUBCOMMAND_SET, "Set the custom yes and no vote emoji")
                        .addOptions(
                            emojiOption(OPTION_YES_EMOJI, "The custom emoji used for yes votes"),
                            emojiOption(OPTION_NO_EMOJI, "The custom emoji used for no votes")
                        ),
                    SubcommandData(SUBCOMMAND_RESET, "Reset the vote emoji back to the default reactions")
                )
        )
    }

    internal fun getYesEmojiInput(event: SlashCommandInteractionEvent): String? {
        return event.getOption(OPTION_YES_EMOJI)?.asString
    }

    internal fun getNoEmojiInput(event: SlashCommandInteractionEvent): String? {
        return event.getOption(OPTION_NO_EMOJI)?.asString
    }

    internal fun resolveCustomEmoji(event: SlashCommandInteractionEvent, input: String): CustomEmoji? {
        val trimmed = input.trim()
        val emojiId = CUSTOM_EMOJI_REGEX.matchEntire(trimmed)?.groupValues?.getOrNull(2)
            ?: trimmed.takeIf { it.all(Char::isDigit) }
        return emojiId?.toLongOrNull()?.let(event.jda::getEmojiById)
    }

    private fun setEmojiConfiguration(event: SlashCommandInteractionEvent, guildId: Long) {
        val yesInput = getYesEmojiInput(event)?.trim()
        val noInput = getNoEmojiInput(event)?.trim()
        if (yesInput.isNullOrBlank() || noInput.isNullOrBlank()) {
            event.reply("Please provide both a yes and no custom emoji.").setEphemeral(true).queue()
            return
        }

        val yesEmoji = resolveCustomEmoji(event, yesInput)
        if (yesEmoji == null) {
            event.reply("I could not resolve the custom yes emoji. Please provide a custom emoji mention or id.")
                .setEphemeral(true)
                .queue()
            return
        }

        val noEmoji = resolveCustomEmoji(event, noInput)
        if (noEmoji == null) {
            event.reply("I could not resolve the custom no emoji. Please provide a custom emoji mention or id.")
                .setEphemeral(true)
                .queue()
            return
        }

        if (yesEmoji.idLong == noEmoji.idLong) {
            event.reply("Please choose two different emoji.").setEphemeral(true).queue()
            return
        }

        votingEmotesRepository.save(VoteEmotes(guildId, yesEmoji.idLong, noEmoji.idLong))
        event.reply("Vote emoji updated: yes ${yesEmoji.asMention}, no ${noEmoji.asMention}.").setEphemeral(true)
            .queue()
    }

    private fun showCurrentSettings(event: SlashCommandInteractionEvent, guildId: Long) {
        val voteEmotes = votingEmotesRepository.findById(guildId).orElse(null)
        if (voteEmotes == null) {
            event.reply(
                "Vote emoji configuration for this server\n\n- Yes emoji: default ✅\n- No emoji: default ❎"
            ).setEphemeral(true).queue()
            return
        }

        val yesEmoji = event.jda.getEmojiById(voteEmotes.voteYesEmote)
        val noEmoji = event.jda.getEmojiById(voteEmotes.voteNoEmote)

        val message = buildString {
            appendLine("Vote emoji configuration for this server")
            appendLine()
            appendLine("- Yes emoji: ${formatEmoji(yesEmoji, voteEmotes.voteYesEmote)}")
            appendLine("- No emoji: ${formatEmoji(noEmoji, voteEmotes.voteNoEmote)}")
        }

        event.reply(message).setEphemeral(true).queue()
    }

    private fun formatEmoji(emoji: CustomEmoji?, emojiId: Long): String {
        return emoji?.asMention ?: "Missing custom emoji (ID: $emojiId)"
    }

    private fun emojiOption(name: String, description: String): OptionData {
        return OptionData(OptionType.STRING, name, description, true)
    }
}
