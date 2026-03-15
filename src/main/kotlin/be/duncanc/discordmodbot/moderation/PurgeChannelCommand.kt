package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.discord.SlashCommand
import be.duncanc.discordmodbot.discord.limitLessBulkDeleteByIds
import be.duncanc.discordmodbot.discord.nicknameAndUsername
import be.duncanc.discordmodbot.logging.GuildLogger
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import org.springframework.stereotype.Component
import java.awt.Color
import java.time.OffsetDateTime

@Component
class PurgeChannelCommand : ListenerAdapter(), SlashCommand {
    data class ParsedPurgeTargets(
        val userIds: Set<Long>,
        val invalidTargets: List<String>
    )

    companion object {
        private const val COMMAND = "purgechannel"
        private const val DESCRIPTION = "Delete recent messages from the current channel."
        private const val SUBCOMMAND_ALL = "all"
        private const val SUBCOMMAND_FILTERED = "filtered"
        private const val OPTION_AMOUNT = "amount"
        private const val OPTION_TARGETS = "targets"
        internal const val MAX_PURGE_AMOUNT = 1000

        private val USER_MENTION_REGEX = Regex("<@!?(\\d+)>")

        internal fun parseTargets(rawTargets: String): ParsedPurgeTargets {
            val validTargets = linkedSetOf<Long>()
            val invalidTargets = mutableListOf<String>()

            rawTargets.split(Regex("[,\\s]+"))
                .filter { it.isNotBlank() }
                .forEach { token ->
                    val mentionMatch = USER_MENTION_REGEX.matchEntire(token)
                    val userId = mentionMatch?.groupValues?.get(1)?.toLongOrNull() ?: token.toLongOrNull()
                    if (userId == null) {
                        invalidTargets.add(token)
                    } else {
                        validTargets.add(userId)
                    }
                }

            return ParsedPurgeTargets(validTargets, invalidTargets)
        }
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != COMMAND) return

        val member = event.member
        if (member == null) {
            event.reply("This command only works in a guild.").setEphemeral(true).queue()
            return
        }

        val guild = event.guild
        if (guild == null) {
            event.reply("This command only works in a guild.").setEphemeral(true).queue()
            return
        }

        val channel = event.channel.asTextChannel()
        if (!member.hasPermission(channel, Permission.MESSAGE_MANAGE)) {
            event.reply("You need manage messages permission in this channel to use this command.").setEphemeral(true)
                .queue()
            return
        }

        if (!guild.selfMember.hasPermission(channel, Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY)) {
            event.reply("I need manage messages and read message history permissions in this channel to use this command.")
                .setEphemeral(true).queue()
            return
        }

        when (event.subcommandName) {
            SUBCOMMAND_ALL -> handleAllSubcommand(event, guild, channel)
            SUBCOMMAND_FILTERED -> handleFilteredSubcommand(event, guild, channel)
            else -> event.reply("Please choose either the all or filtered purge mode.").setEphemeral(true).queue()
        }
    }

    private fun handleFilteredSubcommand(event: SlashCommandInteractionEvent, guild: Guild, channel: TextChannel) {
        val amount = getValidatedAmount(event) ?: return
        val targets = event.getOption(OPTION_TARGETS)?.asString
        if (targets.isNullOrBlank()) {
            event.reply("Please provide one or more user mentions or IDs in the targets option.").setEphemeral(true)
                .queue()
            return
        }

        val parsedTargets = parseTargets(targets)
        if (parsedTargets.invalidTargets.isNotEmpty()) {
            val invalidTargets = parsedTargets.invalidTargets.joinToString(", ")
            event.reply("Invalid target(s): $invalidTargets. Use user mentions or raw user IDs.").setEphemeral(true)
                .queue()
            return
        }

        if (parsedTargets.userIds.isEmpty()) {
            event.reply("Please provide at least one valid user mention or ID in the targets option.")
                .setEphemeral(true).queue()
            return
        }

        event.deferReply(true).queue { hook ->
            val messageIds = collectRecentMessageIds(channel, amount, parsedTargets.userIds)
            channel.limitLessBulkDeleteByIds(messageIds)
            val targetMentions = parsedTargets.userIds.joinToString(", ") { "<@$it>" }
            hook.editOriginal("Deleted ${messageIds.size} message(s) from $targetMentions.").queue()
            logPurge(event, guild, channel, parsedTargets.userIds)
        }
    }

    private fun handleAllSubcommand(event: SlashCommandInteractionEvent, guild: Guild, channel: TextChannel) {
        val amount = getValidatedAmount(event) ?: return

        event.deferReply(true).queue { hook ->
            val messageIds = collectRecentMessageIds(channel, amount)
            channel.limitLessBulkDeleteByIds(messageIds)
            hook.editOriginal("Deleted ${messageIds.size} message(s).").queue()
            logPurge(event, guild, channel, null)
        }
    }

    private fun getValidatedAmount(event: SlashCommandInteractionEvent): Int? {
        val amount = event.getOption(OPTION_AMOUNT)?.asInt
        if (amount == null || amount < 1 || amount > MAX_PURGE_AMOUNT) {
            event.reply("Please provide an amount between 1 and $MAX_PURGE_AMOUNT.").setEphemeral(true).queue()
            return null
        }
        return amount
    }

    private fun collectRecentMessageIds(
        channel: TextChannel,
        amount: Int,
        targetUserIds: Set<Long> = emptySet()
    ): ArrayList<Long> {
        val cutoff = OffsetDateTime.now().minusWeeks(2)
        val messageIds = ArrayList<Long>()

        for (message in channel.iterableHistory.cache(false)) {
            if (message.timeCreated.isBefore(cutoff)) {
                break
            }

            if (targetUserIds.isEmpty() || targetUserIds.contains(message.author.idLong)) {
                messageIds.add(message.idLong)
            }

            if (messageIds.size >= amount) {
                break
            }
        }

        return messageIds
    }

    private fun logPurge(
        event: SlashCommandInteractionEvent,
        guild: Guild,
        channel: TextChannel,
        targetUserIds: Set<Long>?
    ) {
        val guildLogger = event.jda.registeredListeners.firstOrNull { it is GuildLogger } as? GuildLogger ?: return
        val logEmbed = EmbedBuilder()
            .setColor(Color.YELLOW)
            .setTitle(if (targetUserIds == null) "Channel purge" else "Filtered channel purge")
            .addField("Moderator", event.member!!.nicknameAndUsername, true)
            .addField("Channel", channel.name, true)

        if (targetUserIds != null) {
            logEmbed.addField("Filter", targetUserIds.joinToString("\n") { "<@$it>" }, true)
        }

        guildLogger.log(logEmbed, event.user, guild, null, GuildLogger.LogTypeAction.MODERATOR)
    }

    override fun getCommandsData(): List<SlashCommandData> {
        return listOf(
            Commands.slash(COMMAND, DESCRIPTION)
                .addSubcommands(
                    SubcommandData(SUBCOMMAND_ALL, "Delete up to the specified number of recent messages.")
                        .addOption(
                            OptionType.INTEGER,
                            OPTION_AMOUNT,
                            "Number of recent messages to delete (1-$MAX_PURGE_AMOUNT)",
                            true
                        ),
                    SubcommandData(
                        SUBCOMMAND_FILTERED,
                        "Delete up to the specified number of recent messages from targets."
                    )
                        .addOption(
                            OptionType.INTEGER,
                            OPTION_AMOUNT,
                            "Number of recent matching messages to delete (1-$MAX_PURGE_AMOUNT)",
                            true
                        )
                        .addOption(
                            OptionType.STRING,
                            OPTION_TARGETS,
                            "User mentions or IDs separated by spaces or commas",
                            true
                        )
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MESSAGE_MANAGE))
        )
    }
}
