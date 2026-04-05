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
import java.util.concurrent.CompletableFuture

@Component
class PurgeChannelCommand : ListenerAdapter(), SlashCommand {
    companion object {
        private const val COMMAND = "purgechannel"
        private const val DESCRIPTION = "Delete recent messages from the current channel."
        private const val SUBCOMMAND_ALL = "all"
        private const val SUBCOMMAND_FILTERED = "filtered"
        private const val OPTION_AMOUNT = "amount"
        private const val OPTION_TARGET = "target"
        internal const val MAX_PURGE_AMOUNT = 1000
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

        event.deferReply(true).queue { hook ->
            collectRecentMessageIds(channel, amount, targetUserId).whenComplete { messageIds, throwable ->
                if (throwable != null) {
                    hook.editOriginal("Failed to purge messages: ${throwable.message ?: "unknown error"}").queue()
                    return@whenComplete
                }

                try {
                    val ids = messageIds ?: ArrayList<Long>()
                    val deletedCount = ids.size
                    channel.limitLessBulkDeleteByIds(ids)
                    hook.editOriginal("Deleted $deletedCount message(s) from <@$targetUserId>.").queue()
                    logPurge(event, guild, channel, targetUserId)
                } catch (e: Exception) {
                    hook.editOriginal("Failed to purge messages: ${e.message ?: "unknown error"}").queue()
                }
            }
        }
    }

    private fun handleAllSubcommand(event: SlashCommandInteractionEvent, guild: Guild, channel: TextChannel) {
        val amount = getValidatedAmount(event) ?: return

        event.deferReply(true).queue { hook ->
            collectRecentMessageIds(channel, amount).whenComplete { messageIds, throwable ->
                if (throwable != null) {
                    hook.editOriginal("Failed to purge messages: ${throwable.message ?: "unknown error"}").queue()
                    return@whenComplete
                }

                try {
                    val ids = messageIds ?: ArrayList<Long>()
                    val deletedCount = ids.size
                    channel.limitLessBulkDeleteByIds(ids)
                    hook.editOriginal("Deleted $deletedCount message(s).").queue()
                    logPurge(event, guild, channel, null)
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

    private fun collectRecentMessageIds(
        channel: TextChannel,
        amount: Int,
        targetUserId: Long? = null
    ): CompletableFuture<ArrayList<Long>> {
        val cutoff = OffsetDateTime.now().minusWeeks(2)
        val messageIds = ArrayList<Long>()

        return channel.iterableHistory.cache(false)
            .forEachAsync { message ->
                if (message.timeCreated.isBefore(cutoff)) {
                    return@forEachAsync false
                }

                if (targetUserId == null || targetUserId == message.author.idLong) {
                    messageIds.add(message.idLong)
                    if (messageIds.size >= amount) {
                        return@forEachAsync false
                    }
                }

                true
            }
            .thenApply { messageIds }
    }

    private fun logPurge(
        event: SlashCommandInteractionEvent,
        guild: Guild,
        channel: TextChannel,
        targetUserId: Long?
    ) {
        val guildLogger = event.jda.registeredListeners.firstOrNull { it is GuildLogger } as? GuildLogger ?: return
        val logEmbed = EmbedBuilder()
            .setColor(Color.YELLOW)
            .setTitle(if (targetUserId == null) "Channel purge" else "Filtered channel purge")
            .addField("Moderator", event.member!!.nicknameAndUsername, true)
            .addField("Channel", channel.name, true)

        if (targetUserId != null) {
            logEmbed.addField("Filter", "<@$targetUserId>", true)
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
                            OptionType.USER,
                            OPTION_TARGET,
                            "User to delete messages from",
                            true
                        )
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MESSAGE_MANAGE))
        )
    }
}
