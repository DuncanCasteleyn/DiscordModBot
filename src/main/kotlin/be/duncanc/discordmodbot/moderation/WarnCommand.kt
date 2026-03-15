package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.discord.SlashCommand
import be.duncanc.discordmodbot.discord.nicknameAndUsername
import be.duncanc.discordmodbot.logging.GuildLogger
import be.duncanc.discordmodbot.moderation.persistence.GuildWarnPointsSettings
import be.duncanc.discordmodbot.moderation.persistence.GuildWarnPointsSettingsRepository
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.springframework.stereotype.Component
import java.awt.Color
import java.util.*

@Component
class WarnCommand(
    private val guildWarnPointsSettingsRepository: GuildWarnPointsSettingsRepository
) : ListenerAdapter(), SlashCommand {
    companion object {
        private const val COMMAND = "warn"
        private const val DESCRIPTION = "Warns a user by sending them a DM and logging the warning to the log channel."
        private const val OPTION_USER = "user"
        private const val OPTION_REASON = "reason"
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != COMMAND) return

        val guild = event.guild
        if (guild == null) {
            event.reply("This command only works in a guild.").setEphemeral(true).queue()
            return
        }

        val moderator = event.member!!
        if (!moderator.hasPermission(Permission.KICK_MEMBERS)) {
            event.reply("You need kick members permission to use this command.").setEphemeral(true).queue()
            return
        }

        val guildWarnPointsSettings = guildWarnPointsSettingsRepository.findById(guild.idLong)
            .orElse(GuildWarnPointsSettings(guild.idLong, announceChannelId = -1))
        if (guildWarnPointsSettings.overrideWarnCommand) {
            event.reply("This command has been disabled. Use /addwarnpoints instead.").setEphemeral(true).queue()
            return
        }

        val member = event.member
        if (member == null) {
            event.reply("This command only works in a guild.").setEphemeral(true).queue()
            return
        }

        if (!member.hasPermission(Permission.KICK_MEMBERS)) {
            event.reply("You need kick members permission to warn.").setEphemeral(true).queue()
            return
        }

        val targetMember = event.getOption(OPTION_USER)?.asMember
        if (targetMember == null) {
            event.reply("You need to mention a user that is still in the server.").setEphemeral(true).queue()
            return
        }

        if (!member.canInteract(targetMember)) {
            event.reply("You can't warn a user that you can't interact with.").setEphemeral(true).queue()
            return
        }

        val reason = event.getOption(OPTION_REASON)?.asString ?: "No reason provided"

        event.deferReply().queue { hook ->
            val guild = event.guild!!
            val guildLogger = event.jda.registeredListeners.firstOrNull { it is GuildLogger } as GuildLogger?
            if (guildLogger != null) {
                val logEmbed = EmbedBuilder()
                    .setColor(Color.YELLOW)
                    .setTitle("User warned")
                    .addField("UUID", UUID.randomUUID().toString(), false)
                    .addField("User", targetMember.nicknameAndUsername, true)
                    .addField("Moderator", event.member!!.nicknameAndUsername, true)
                    .addField("Reason", reason, false)

                guildLogger.log(logEmbed, targetMember.user, guild, null, GuildLogger.LogTypeAction.MODERATOR)
            }

            val userWarning = EmbedBuilder()
                .setColor(Color.YELLOW)
                .setAuthor(event.member!!.nicknameAndUsername, null, event.user.effectiveAvatarUrl)
                .setTitle(guild.name + ": You have been warned by " + event.member!!.nicknameAndUsername, null)
                .addField("Reason", reason, false)
                .build()

            targetMember.user.openPrivateChannel().queue(
                { privateChannelUserToWarn ->
                    privateChannelUserToWarn.sendMessageEmbeds(userWarning).queue(
                        { onSuccessfulWarnUser(hook, targetMember, userWarning) }
                    ) { throwable -> onFailToWarnUser(hook, targetMember, throwable, true) }
                }
            ) { throwable -> onFailToWarnUser(hook, targetMember, throwable, false) }
        }
    }

    private fun onSuccessfulWarnUser(
        hook: InteractionHook,
        toWarn: Member,
        userWarning: MessageEmbed
    ) {
        hook.editOriginal(
            "Warned $toWarn.\n\nThe following message was sent to the user:"
        ).setEmbeds(userWarning).queue()
    }

    private fun onFailToWarnUser(
        hook: InteractionHook,
        toWarn: Member,
        throwable: Throwable,
        dmFailed: Boolean
    ) {
        val msg =
            "Warned $toWarn.\n\nWas unable to send a DM to the user please inform the user manually.\nError: ${throwable.message}"
        hook.editOriginal(msg).queue()
    }

    override fun getCommandsData(): List<SlashCommandData> {
        return listOf(
            Commands.slash(COMMAND, DESCRIPTION)
                .addOptions(
                    OptionData(OptionType.USER, OPTION_USER, "The user to warn").setRequired(true),
                    OptionData(OptionType.STRING, OPTION_REASON, "The reason for the warning").setRequired(true)
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.KICK_MEMBERS))
        )
    }
}
