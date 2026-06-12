package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.discord.SlashCommand
import be.duncanc.discordmodbot.discord.nicknameAndUsername
import be.duncanc.discordmodbot.logging.GuildLogger
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Message
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
import java.util.concurrent.CompletableFuture

@Component
class PurgeChannelCommand : ListenerAdapter(), SlashCommand {
    companion object {
        private const val COMMAND = "purgechannel"
        private const val DESCRIPTION = "Delete messages from the current channel."
        private const val SUBCOMMAND_ALL = "all"
        private const val SUBCOMMAND_FILTERED = "filtered"
        private const val OPTION_AMOUNT = "amount"
        private const val OPTION_TARGET = "target"
        private const val OPTION_FROM = "from"
        private const val OPTION_TO = "to"
        internal const val MAX_PURGE_AMOUNT = 100

        private sealed interface MessageIdOption {
            data object Omitted : MessageIdOption
            data object Invalid : MessageIdOption

            data class Present(val messageId: Long) : MessageIdOption
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
        val targetUserId = event.getOption(OPTION_TARGET)?.asLong
        if (targetUserId == null) {
            event.reply("Please select a user to delete messages from.").setEphemeral(true).queue()
            return
        }

        val messageRange = getValidatedMessageRange(event) ?: return

        event.deferReply(true).queue { hook ->
            collectMessages(
                channel = channel,
                amount = amount,
                targetUserId = targetUserId,
                fromMessageId = messageRange.first,
                toMessageId = messageRange.second
            ).whenComplete { messages, throwable ->
                if (throwable != null) {
                    hook.editOriginal("Failed to purge messages: ${throwable.message ?: "unknown error"}").queue()
                    return@whenComplete
                }

                try {
                    val messagesToDelete = messages ?: ArrayList()
                    val deletingCount = messagesToDelete.size
                    val transcript = buildPurgeTranscript(channel, messagesToDelete)
                    channel.purgeMessages(messagesToDelete)
                    hook.editOriginal("Attempting to delete $deletingCount message(s) from <@$targetUserId>.").queue()
                    logPurge(event, guild, channel, targetUserId, deletingCount, transcript)
                } catch (e: Exception) {
                    hook.editOriginal("Failed to purge messages: ${e.message ?: "unknown error"}").queue()
                }
            }
        }
    }

    private fun handleAllSubcommand(event: SlashCommandInteractionEvent, guild: Guild, channel: TextChannel) {
        val amount = getValidatedAmount(event) ?: return
        val messageRange = getValidatedMessageRange(event) ?: return

        event.deferReply(true).queue { hook ->
            collectMessages(
                channel = channel,
                amount = amount,
                fromMessageId = messageRange.first,
                toMessageId = messageRange.second
            ).whenComplete { messages, throwable ->
                if (throwable != null) {
                    hook.editOriginal("Failed to purge messages: ${throwable.message ?: "unknown error"}").queue()
                    return@whenComplete
                }

                try {
                    val messagesToDelete = messages ?: ArrayList()
                    val deletingCount = messagesToDelete.size
                    val transcript = buildPurgeTranscript(channel, messagesToDelete)
                    channel.purgeMessages(messagesToDelete)
                    hook.editOriginal("Attempting to delete $deletingCount message(s).").queue()
                    logPurge(event, guild, channel, null, deletingCount, transcript)
                } catch (e: Exception) {
                    hook.editOriginal("Failed to purge messages: ${e.message ?: "unknown error"}").queue()
                }
            }
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

    private fun getValidatedMessageRange(event: SlashCommandInteractionEvent): Pair<Long?, Long?>? {
        val fromMessageId = getValidatedMessageId(event, OPTION_FROM)
        val toMessageId = getValidatedMessageId(event, OPTION_TO)
        if (fromMessageId == MessageIdOption.Invalid || toMessageId == MessageIdOption.Invalid) {
            return null
        }

        val fromId = (fromMessageId as? MessageIdOption.Present)?.messageId
        val toId = (toMessageId as? MessageIdOption.Present)?.messageId

        if (fromId != null && toId != null && fromId < toId) {
            event.reply("The from message must be newer than or the same as the to message.")
                .setEphemeral(true)
                .queue()
            return null
        }

        return fromId to toId
    }

    private fun getValidatedMessageId(event: SlashCommandInteractionEvent, optionName: String): MessageIdOption {
        val option = event.getOption(optionName) ?: return MessageIdOption.Omitted
        val rawValue = option.asString.trim()
        if (rawValue.isBlank()) {
            event.reply("Please provide a valid message ID for $optionName.").setEphemeral(true).queue()
            return MessageIdOption.Invalid
        }

        val messageId = rawValue.toLongOrNull()
        if (messageId == null) {
            event.reply("Please provide a valid message ID for $optionName.").setEphemeral(true).queue()
            return MessageIdOption.Invalid
        }

        return MessageIdOption.Present(messageId)
    }

    private fun collectMessages(
        channel: TextChannel,
        amount: Int,
        targetUserId: Long? = null,
        fromMessageId: Long? = null,
        toMessageId: Long? = null
    ): CompletableFuture<ArrayList<Message>> {
        val messages = ArrayList<Message>()
        val oldestPurgeableMessageDate = OffsetDateTime.now().minusWeeks(2)
        var collecting = fromMessageId == null

        return channel.iterableHistory.cache(false)
            .forEachAsync { message ->
                val messageId = message.idLong

                if (message.timeCreated.isBefore(oldestPurgeableMessageDate)) {
                    return@forEachAsync false
                }

                if (!collecting) {
                    if (fromMessageId != null && messageId > fromMessageId) {
                        return@forEachAsync true
                    }

                    collecting = true
                }

                if (toMessageId != null && messageId < toMessageId) {
                    return@forEachAsync false
                }

                if (targetUserId == null || targetUserId == message.author.idLong) {
                    messages.add(message)
                    if (messages.size >= amount) {
                        return@forEachAsync false
                    }
                }

                if (toMessageId != null && messageId == toMessageId) {
                    return@forEachAsync false
                }

                true
            }
            .thenApply { messages }
    }

    private fun logPurge(
        event: SlashCommandInteractionEvent,
        guild: Guild,
        channel: TextChannel,
        targetUserId: Long?,
        deletingCount: Int,
        transcript: ByteArray
    ) {
        val guildLogger = runCatching {
            event.jda.registeredListeners.firstOrNull { it is GuildLogger } as? GuildLogger
        }.getOrNull() ?: return
        val logEmbed = EmbedBuilder()
            .setColor(Color.YELLOW)
            .setTitle(if (targetUserId == null) "Channel purge" else "Filtered channel purge")
            .addField("Moderator", event.member!!.nicknameAndUsername, true)
            .addField("Channel", channel.name, true)
            .addField("Amount of deleted messages", deletingCount.toString(), true)

        if (targetUserId != null) {
            logEmbed.addField("Filter", "<@$targetUserId>", true)
        }

        guildLogger.log(logEmbed, event.user, guild, null, GuildLogger.LogTypeAction.MODERATOR, transcript)
    }

    private fun buildPurgeTranscript(channel: TextChannel, messages: List<Message>): ByteArray {
        val logWriter = StringBuilder(channel.toString()).append("\n")

        messages.forEach { message ->
            logWriter.append(message.author.toString()).append(":\n")
                .append(message.contentDisplay).append("\n\n")

            if (message.attachments.isNotEmpty()) {
                logWriter.append("Attachment(s):\n")
                message.attachments.forEach { attachment ->
                    logWriter.append("[").append(attachment.fileName)
                        .append("](").append(attachment.url).append(")\n")
                }
                logWriter.append("\n")
            }

            if (message.mentions.customEmojis.isNotEmpty()) {
                logWriter.append("Emote(s):\n")
                message.mentions.customEmojis.forEach { emote ->
                    logWriter.append("[").append(emote.name)
                        .append("](").append(emote.imageUrl).append(")\n")
                }
                logWriter.append("\n")
            }
        }

        logWriter.append("Logged on ").append(OffsetDateTime.now().toString())
        return logWriter.toString().toByteArray()
    }

    override fun getCommandsData(): List<SlashCommandData> {
        return listOf(
            Commands.slash(COMMAND, DESCRIPTION)
                .addSubcommands(
                    SubcommandData(SUBCOMMAND_ALL, "Delete up to the specified number of messages, not older than 2 weeks.")
                        .addOption(
                            OptionType.INTEGER,
                            OPTION_AMOUNT,
                            "Number of messages to delete (1-$MAX_PURGE_AMOUNT)",
                            true
                        )
                        .addOption(
                            OptionType.STRING,
                            OPTION_FROM,
                            "Newest message ID to start deleting from",
                            false
                        )
                        .addOption(
                            OptionType.STRING,
                            OPTION_TO,
                            "Oldest message ID to stop deleting at",
                            false
                        ),
                    SubcommandData(
                        SUBCOMMAND_FILTERED,
                        "Delete up to the specified number of messages from targets, not older than 2 weeks."
                    )
                        .addOption(
                            OptionType.INTEGER,
                            OPTION_AMOUNT,
                            "Number of matching messages to delete (1-$MAX_PURGE_AMOUNT)",
                            true
                        )
                        .addOption(
                            OptionType.USER,
                            OPTION_TARGET,
                            "User to delete messages from",
                            true
                        )
                        .addOption(
                            OptionType.STRING,
                            OPTION_FROM,
                            "Newest message ID to start deleting from",
                            false
                        )
                        .addOption(
                            OptionType.STRING,
                            OPTION_TO,
                            "Oldest message ID to stop deleting at",
                            false
                        )
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MESSAGE_MANAGE))
        )
    }
}
