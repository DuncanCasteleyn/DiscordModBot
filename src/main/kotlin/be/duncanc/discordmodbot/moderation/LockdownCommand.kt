package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.discord.SlashCommand
import be.duncanc.discordmodbot.discord.nicknameAndUsername
import be.duncanc.discordmodbot.logging.GuildLogger
import be.duncanc.discordmodbot.moderation.persistence.LockdownTargetType
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent
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
import java.awt.Color

@Component
class LockdownCommand(
    private val lockdownService: LockdownService,
    private val guildLogger: GuildLogger
) : ListenerAdapter(), SlashCommand {
    companion object {
        private const val COMMAND = "lockdown"
        private const val DESCRIPTION = "Stop members from sending messages, reacting or creating threads in a channel."
        private const val SUBCOMMAND_ON = "on"
        private const val SUBCOMMAND_OFF = "off"
        private const val OPTION_CHANNEL = "channel"
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != COMMAND) return

        val member = event.member
        val guild = event.guild
        if (member == null || guild == null) {
            event.reply("This command only works in a guild.").setEphemeral(true).queue()
            return
        }

        val channel = resolveChannel(event)
        if (channel == null) {
            event.reply("Please choose a text or announcement channel.").setEphemeral(true).queue()
            return
        }

        if (!member.hasPermission(channel, Permission.MANAGE_CHANNEL)) {
            event.reply("You need manage channels permission in this channel to use this command.")
                .setEphemeral(true).queue()
            return
        }

        if (!guild.selfMember.hasPermission(channel, Permission.MANAGE_CHANNEL, Permission.MANAGE_PERMISSIONS)) {
            event.reply(
                "I need manage channels and manage permissions permissions in this channel to use this command."
            ).setEphemeral(true).queue()
            return
        }

        when (event.subcommandName) {
            SUBCOMMAND_ON -> handleOn(event, guild, channel)
            SUBCOMMAND_OFF -> handleOff(event, guild, channel)
            else -> event.reply("Please choose either on or off.").setEphemeral(true).queue()
        }
    }

    override fun onGuildLeave(event: GuildLeaveEvent) {
        lockdownService.clearGuildState(event.guild.idLong)
    }

    internal fun resolveChannel(event: SlashCommandInteractionEvent): StandardGuildMessageChannel? {
        val option = event.getOption(OPTION_CHANNEL)
        val channel = if (option != null) option.asChannel else event.channel
        return channel as? StandardGuildMessageChannel
    }

    private fun handleOn(event: SlashCommandInteractionEvent, guild: Guild, channel: StandardGuildMessageChannel) {
        event.deferReply(true).queue { hook ->
            try {
                lockdownService.lock(channel).whenComplete { result, throwable ->
                    if (throwable != null) {
                        hook.editOriginal(
                            "Failed to lock ${channel.asMention}: ${throwable.message ?: "unknown error"}"
                        ).queue()
                        return@whenComplete
                    }

                    when (result) {
                        LockdownService.LockResult.AlreadyLocked ->
                            hook.editOriginal("${channel.asMention} is already locked down.").queue()

                        is LockdownService.LockResult.Failed ->
                            hook.editOriginal("Failed to lock ${channel.asMention}: ${result.reason}").queue()

                        is LockdownService.LockResult.Locked -> {
                            val message = StringBuilder("${channel.asMention} is locked down.")
                            val notLocked = result.couldNotLockRoleIds.map { "<@&$it>" } +
                                    result.couldNotLockMemberIds.map { "<@$it>" }
                            if (notLocked.isNotEmpty()) {
                                message.append("\nCould not lock (they can still talk): ")
                                    .append(notLocked.joinToString(", "))
                            }
                            hook.editOriginal(message.toString()).queue()
                            log(event, guild, channel, "Channel locked down", Color.RED)
                        }

                        null -> hook.editOriginal("Failed to lock ${channel.asMention}.").queue()
                    }
                }
            } catch (e: Exception) {
                hook.editOriginal("Failed to lock ${channel.asMention}: ${e.message ?: "unknown error"}").queue()
            }
        }
    }

    private fun handleOff(event: SlashCommandInteractionEvent, guild: Guild, channel: StandardGuildMessageChannel) {
        event.deferReply(true).queue { hook ->
            try {
                lockdownService.unlock(channel).whenComplete { result, throwable ->
                    if (throwable != null) {
                        hook.editOriginal(
                            "Failed to unlock ${channel.asMention}: ${throwable.message ?: "unknown error"}"
                        ).queue()
                        return@whenComplete
                    }

                    when (result) {
                        LockdownService.UnlockResult.NotLocked ->
                            hook.editOriginal("${channel.asMention} is not locked down.").queue()

                        is LockdownService.UnlockResult.Unlocked -> {
                            val message = StringBuilder("${channel.asMention} is unlocked.")
                            if (result.failedTargets.isNotEmpty()) {
                                val failed = result.failedTargets
                                    .joinToString(", ") { mention(it.targetType, it.targetId) }
                                message.append("\nCould not restore the previous permissions of: ").append(failed)
                            }
                            hook.editOriginal(message.toString()).queue()
                            log(event, guild, channel, "Channel unlocked", Color.GREEN)
                        }

                        null -> hook.editOriginal("Failed to unlock ${channel.asMention}.").queue()
                    }
                }
            } catch (e: Exception) {
                hook.editOriginal("Failed to unlock ${channel.asMention}: ${e.message ?: "unknown error"}").queue()
            }
        }
    }

    private fun mention(type: LockdownTargetType, id: Long) =
        if (type == LockdownTargetType.ROLE) "<@&$id>" else "<@$id>"

    private fun log(
        event: SlashCommandInteractionEvent,
        guild: Guild,
        channel: StandardGuildMessageChannel,
        title: String,
        color: Color
    ) {
        val logEmbed = EmbedBuilder()
            .setColor(color)
            .setTitle(title)
            .addField("Moderator", event.member!!.nicknameAndUsername, true)
            .addField("Channel", channel.asMention, true)
        guildLogger.log(logEmbed, event.user, guild, null, GuildLogger.LogTypeAction.MODERATOR)
    }

    override fun getCommandsData(): List<SlashCommandData> {
        fun channelOption() = OptionData(
            OptionType.CHANNEL,
            OPTION_CHANNEL,
            "Channel to use, defaults to the current channel",
            false
        ).setChannelTypes(ChannelType.TEXT, ChannelType.NEWS)

        return listOf(
            Commands.slash(COMMAND, DESCRIPTION)
                .setContexts(InteractionContextType.GUILD)
                .addSubcommands(
                    SubcommandData(SUBCOMMAND_ON, "Lock the channel until it is unlocked again.")
                        .addOptions(channelOption()),
                    SubcommandData(SUBCOMMAND_OFF, "Restore the permissions from before the lockdown.")
                        .addOptions(channelOption())
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_CHANNEL))
        )
    }
}
