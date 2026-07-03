package be.duncanc.discordmodbot.reddit

import be.duncanc.discordmodbot.discord.SlashCommand
import be.duncanc.discordmodbot.reddit.persistence.RedditAlertSettings
import be.duncanc.discordmodbot.reddit.persistence.RedditAlertSettingsRepository
import be.duncanc.discordmodbot.reddit.persistence.RedditPendingPostRepository
import be.duncanc.discordmodbot.reddit.persistence.RedditPostMirrorRepository
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
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

@Component
class RedditConfigCommand(
    private val redditAlertSettingsRepository: RedditAlertSettingsRepository,
    private val redditPostMirrorRepository: RedditPostMirrorRepository,
    private val redditPendingPostRepository: RedditPendingPostRepository,
    private val redditPollingService: RedditPollingService,
    private val redditProperties: RedditProperties
) : ListenerAdapter(), SlashCommand {
    companion object {
        private const val COMMAND = "reddit"
        private const val DESCRIPTION = "Configure Reddit post mirroring for this server."
        private const val OPTION_CHANNEL = "channel"
        private const val OPTION_SUBREDDIT = "subreddit"
        private const val SUBCOMMAND_SHOW = "show"
        private const val SUBCOMMAND_SET_CHANNEL = "set-channel"
        private const val SUBCOMMAND_SET_SUBREDDIT = "set-subreddit"
        private const val SUBCOMMAND_DISABLE = "disable"
        private val SUBREDDIT_REGEX = Regex("[A-Za-z0-9_]{2,21}")
    }

    override fun onGuildLeave(event: GuildLeaveEvent) {
        clearAlertConfiguration(event.guild.idLong)
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

        if (!member.hasPermission(Permission.MANAGE_CHANNEL)) {
            event.reply("You need manage channel permission to use this command.").setEphemeral(true).queue()
            return
        }

        when (event.subcommandName) {
            null, SUBCOMMAND_SHOW -> showCurrentSettings(event, guild)
            SUBCOMMAND_SET_CHANNEL -> {
                val channel = getRequiredTextChannel(event) ?: return
                val settings = redditAlertSettingsRepository.findById(guild.idLong).orElseGet {
                    RedditAlertSettings(
                        guildId = guild.idLong,
                        channelId = channel.idLong,
                        subreddit = redditProperties.subreddit
                    )
                }
                val subreddit = settings.subreddit
                try {
                    redditPollingService.baselineCurrentPosts(guild.idLong, subreddit)
                } catch (exception: IllegalStateException) {
                    event.reply(exception.message ?: "Failed to enable Reddit alerts. Please try again later.")
                        .setEphemeral(true)
                        .queue()
                    return
                }
                settings.channelId = channel.idLong
                redditAlertSettingsRepository.save(settings)
                event.reply("Reddit posts from r/$subreddit will be mirrored to ${channel.asMention}.")
                    .setEphemeral(true)
                    .queue()
            }

            SUBCOMMAND_SET_SUBREDDIT -> {
                val subreddit = getRequiredSubreddit(event) ?: return
                val settings = redditAlertSettingsRepository.findById(guild.idLong).orElseGet {
                    RedditAlertSettings(guildId = guild.idLong, subreddit = subreddit)
                }
                val posts = try {
                    redditPollingService.baselineCurrentPosts(guild.idLong, subreddit)
                } catch (exception: IllegalStateException) {
                    event.reply(exception.message ?: "Failed to enable Reddit alerts. Please try again later.")
                        .setEphemeral(true)
                        .queue()
                    return
                }
                settings.subreddit = subreddit
                clearTrackedPostsExcept(guild.idLong, posts.map { it.id }.toSet())
                redditAlertSettingsRepository.save(settings)
                event.reply("Reddit post mirroring now watches r/$subreddit.").setEphemeral(true).queue()
            }

            SUBCOMMAND_DISABLE -> {
                clearAlertConfiguration(guild.idLong)
                event.reply("Reddit post mirroring disabled.").setEphemeral(true).queue()
            }

            else -> event.reply("Please choose a valid /reddit subcommand.").setEphemeral(true).queue()
        }
    }

    override fun getCommandsData(): List<SlashCommandData> {
        return listOf(
            Commands.slash(COMMAND, DESCRIPTION)
                .setContexts(InteractionContextType.GUILD)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_CHANNEL))
                .addSubcommands(
                    SubcommandData(SUBCOMMAND_SHOW, "Show the current Reddit mirror settings"),
                    SubcommandData(SUBCOMMAND_SET_CHANNEL, "Set the channel that receives Reddit posts")
                        .addOptions(textChannelOption("The channel used for Reddit post mirrors")),
                    SubcommandData(SUBCOMMAND_SET_SUBREDDIT, "Set the subreddit to mirror")
                        .addOptions(subredditOption()),
                    SubcommandData(SUBCOMMAND_DISABLE, "Disable Reddit post mirroring for this server")
                )
        )
    }

    internal fun getRequiredTextChannel(event: SlashCommandInteractionEvent): TextChannel? {
        val channel = event.getOption(OPTION_CHANNEL)?.asChannel?.asTextChannel()
        if (channel == null) {
            event.reply("Please choose a text channel.").setEphemeral(true).queue()
            return null
        }

        return channel
    }

    internal fun getRequiredSubreddit(event: SlashCommandInteractionEvent): String? {
        val subreddit = event.getOption(OPTION_SUBREDDIT)?.asString
            ?.trim()
            ?.removePrefix("r/")
            ?.removePrefix("/r/")
        if (subreddit.isNullOrBlank() || !SUBREDDIT_REGEX.matches(subreddit)) {
            event.reply("Please provide a valid subreddit name.").setEphemeral(true).queue()
            return null
        }

        return subreddit
    }

    private fun showCurrentSettings(event: SlashCommandInteractionEvent, guild: Guild) {
        val settings = redditAlertSettingsRepository.findById(guild.idLong).orElse(null)
        val message = buildString {
            appendLine("Reddit mirror settings for ${guild.name}")
            appendLine()
            appendLine("- Subreddit: r/${settings?.subreddit ?: redditProperties.subreddit}")
            appendLine("- Mirror channel: ${formatChannel(guild, settings?.channelId)}")
            appendLine("- Mentions: Disabled")
        }

        event.reply(message).setEphemeral(true).queue()
    }

    private fun formatChannel(guild: Guild, channelId: Long?): String {
        if (channelId == null) {
            return "Disabled"
        }

        return guild.getTextChannelById(channelId)?.asMention ?: "Channel not found (ID: $channelId)"
    }

    private fun clearAlertConfiguration(guildId: Long) {
        redditAlertSettingsRepository.deleteById(guildId)
        clearTrackedPosts(guildId)
    }

    private fun clearTrackedPosts(guildId: Long) {
        clearTrackedPostsExcept(guildId, emptySet())
    }

    private fun clearTrackedPostsExcept(guildId: Long, keepPostIds: Set<String>) {
        redditPostMirrorRepository.findAll()
            .filter { it.guildId == guildId && it.redditPostId !in keepPostIds }
            .forEach { redditPostMirrorRepository.delete(it) }
        redditPendingPostRepository.findAll()
            .filter { it.id.startsWith("$guildId:") }
            .forEach { redditPendingPostRepository.delete(it) }
    }

    private fun textChannelOption(description: String): OptionData {
        return OptionData(OptionType.CHANNEL, OPTION_CHANNEL, description, true)
            .setChannelTypes(ChannelType.TEXT)
    }

    private fun subredditOption(): OptionData {
        return OptionData(OptionType.STRING, OPTION_SUBREDDIT, "Subreddit name, for example Re_Zero", true)
    }
}
