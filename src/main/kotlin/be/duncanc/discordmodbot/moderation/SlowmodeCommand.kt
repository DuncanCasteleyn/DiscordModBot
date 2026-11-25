package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.discord.SlashCommand
import be.duncanc.discordmodbot.discord.nicknameAndUsername
import be.duncanc.discordmodbot.logging.GuildLogger
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.attribute.ISlowmodeChannel
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
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
class SlowmodeCommand(
    private val guildLogger: GuildLogger
) : ListenerAdapter(), SlashCommand {
    companion object {
        private const val COMMAND = "slowmode"
        private const val DESCRIPTION = "Slow down a channel, for example during a raid."
        private const val SUBCOMMAND_SET = "set"
        private const val SUBCOMMAND_OFF = "off"
        private const val OPTION_MINUTES = "minutes"
        private const val OPTION_CHANNEL = "channel"
        internal const val MAX_MINUTES = ISlowmodeChannel.MAX_SLOWMODE / 60L
        private const val AUDIT_REASON = "Slowmode changed with the slowmode command"
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
            event.reply("Please choose a text channel.").setEphemeral(true).queue()
            return
        }

        if (!member.hasPermission(channel, Permission.MANAGE_CHANNEL)) {
            event.reply("You need manage channels permission in this channel to use this command.")
                .setEphemeral(true).queue()
            return
        }

        if (!guild.selfMember.hasPermission(channel, Permission.MANAGE_CHANNEL)) {
            event.reply("I need manage channels permission in this channel to use this command.")
                .setEphemeral(true).queue()
            return
        }

        when (event.subcommandName) {
            SUBCOMMAND_SET -> {
                val minutes = event.getOption(OPTION_MINUTES)?.asLong
                if (minutes == null || minutes < 1 || minutes > MAX_MINUTES) {
                    event.reply("Please provide a number of minutes between 1 and $MAX_MINUTES.")
                        .setEphemeral(true).queue()
                    return
                }
                applySlowmode(
                    event, guild, channel, (minutes * 60).toInt(),
                    "Slowmode of $minutes minute(s) set on ${channel.asMention}.", "Slowmode set",
                    "$minutes minute(s)"
                )
            }

            SUBCOMMAND_OFF -> applySlowmode(
                event, guild, channel, 0,
                "Slowmode removed from ${channel.asMention}.", "Slowmode removed", "Off"
            )

            else -> event.reply("Please choose either set or off.").setEphemeral(true).queue()
        }
    }

    internal fun resolveChannel(event: SlashCommandInteractionEvent): TextChannel? {
        val option = event.getOption(OPTION_CHANNEL)
        if (option != null) {
            return option.asChannel as? TextChannel
        }

        // Slowmode can't be set on a thread this way, so use the channel the thread is in.
        val channel = event.channel
        if (channel.type.isThread) {
            return channel.asThreadChannel().parentChannel.takeIf { it.type == ChannelType.TEXT }?.asTextChannel()
        }
        return channel as? TextChannel
    }

    private fun applySlowmode(
        event: SlashCommandInteractionEvent,
        guild: Guild,
        channel: TextChannel,
        seconds: Int,
        successMessage: String,
        logTitle: String,
        logValue: String
    ) {
        // Discord rate limits channel edits, so acknowledge first to stay within the 3 second interaction window.
        event.deferReply(true).queue { hook ->
            channel.manager.setSlowmode(seconds).reason(AUDIT_REASON).queue({
                hook.editOriginal(successMessage).queue()

                val logEmbed = EmbedBuilder()
                    .setColor(Color.ORANGE)
                    .setTitle(logTitle)
                    .addField("Moderator", event.member!!.nicknameAndUsername, true)
                    .addField("Channel", channel.asMention, true)
                    .addField("Slowmode", logValue, true)
                guildLogger.log(logEmbed, event.user, guild, null, GuildLogger.LogTypeAction.MODERATOR)
            }, {
                hook.editOriginal(
                    "Failed to change slowmode of ${channel.asMention}: ${it.message ?: "unknown error"}"
                ).queue()
            })
        }
    }

    override fun getCommandsData(): List<SlashCommandData> {
        fun channelOption() = OptionData(
            OptionType.CHANNEL,
            OPTION_CHANNEL,
            "Channel to use, defaults to the current channel",
            false
        ).setChannelTypes(ChannelType.TEXT)

        return listOf(
            Commands.slash(COMMAND, DESCRIPTION)
                .setContexts(InteractionContextType.GUILD)
                .addSubcommands(
                    SubcommandData(SUBCOMMAND_SET, "Set slowmode on a channel until it is turned off.")
                        .addOptions(
                            OptionData(
                                OptionType.INTEGER,
                                OPTION_MINUTES,
                                "Minutes between messages per member (1-$MAX_MINUTES)",
                                true
                            ).setRequiredRange(1, MAX_MINUTES),
                            channelOption()
                        ),
                    SubcommandData(SUBCOMMAND_OFF, "Remove slowmode from a channel.")
                        .addOptions(channelOption())
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_CHANNEL))
        )
    }
}
