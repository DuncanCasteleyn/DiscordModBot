package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.discord.SlashCommand
import be.duncanc.discordmodbot.discord.nicknameAndUsername
import be.duncanc.discordmodbot.logging.GuildLogger
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.requests.RestAction
import org.springframework.stereotype.Component
import java.awt.Color
import java.util.*

@Component
class KickCommand : ListenerAdapter(), SlashCommand {
    companion object {
        private const val COMMAND = "kick"
        private const val DESCRIPTION = "This command will kick the mentioned user and log this to the log channel."
        private const val OPTION_USER = "user"
        private const val OPTION_REASON = "reason"
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != COMMAND) return

        val member = event.member
        if (member == null) {
            event.reply("This command only works in a guild.").setEphemeral(true).queue()
            return
        }

        if (!member.hasPermission(Permission.KICK_MEMBERS)) {
            event.reply("You need kick members permission to use this command.").setEphemeral(true).queue()
            return
        }

        val targetMember = event.getOption(OPTION_USER)?.asMember
        if (targetMember == null) {
            event.reply("You need to mention a user that is still in the server.").setEphemeral(true).queue()
            return
        }

        if (!member.canInteract(targetMember)) {
            event.reply("You can't kick a user that you can't interact with.").setEphemeral(true).queue()
            return
        }

        val reason = event.getOption(OPTION_REASON)?.asString ?: "No reason provided"

        event.deferReply(true).queue { hook ->
            val guild = event.guild!!
            val kickRestAction = guild.kick(targetMember)

            val userKickNotification = EmbedBuilder()
                .setColor(Color.RED)
                .setAuthor(member.nicknameAndUsername, null, member.user.effectiveAvatarUrl)
                .setTitle("${guild.name}: You have been kicked by ${member.nicknameAndUsername}", null)
                .setDescription("Reason: $reason")
                .build()

            targetMember.user.openPrivateChannel().queue(
                { privateChannelUserToKick ->
                    privateChannelUserToKick.sendMessageEmbeds(userKickNotification).queue(
                        { message ->
                            onSuccessfulInformUser(event, reason, hook, targetMember, message, kickRestAction)
                        }
                    ) { throwable ->
                        onFailToInformUser(event, reason, hook, targetMember, throwable, kickRestAction)
                    }
                }
            ) { throwable ->
                onFailToInformUser(event, reason, hook, targetMember, throwable, kickRestAction)
            }
        }
    }

    private fun logKick(event: SlashCommandInteractionEvent, reason: String, toKick: Member) {
        val guild = event.guild!!
        val guildLogger = event.jda.registeredListeners.firstOrNull { it is GuildLogger } as GuildLogger?
        if (guildLogger != null) {
            val logEmbed = EmbedBuilder()
                .setColor(Color.RED)
                .setTitle("User kicked")
                .addField("UUID", UUID.randomUUID().toString(), false)
                .addField("User", toKick.nicknameAndUsername, true)
                .addField("Moderator", event.member!!.nicknameAndUsername, true)
                .addField("Reason", reason, false)

            guildLogger.log(logEmbed, toKick.user, guild, null, GuildLogger.LogTypeAction.MODERATOR)
        }
    }

    private fun onSuccessfulInformUser(
        event: SlashCommandInteractionEvent,
        reason: String,
        hook: InteractionHook,
        toKick: Member,
        userKickWarning: Message,
        kickRestAction: RestAction<Void>
    ) {
        kickRestAction.queue({
            logKick(event, reason, toKick)
            hook.editOriginal(
                "Kicked $toKick.\n\nThe following message was sent to the user:"
            ).setEmbeds(userKickWarning.embeds).queue()
        }) { throwable ->
            userKickWarning.delete().queue()
            hook.editOriginal(
                "Kick failed $toKick: ${throwable.message}\n\nThe following message was sent to the user but was automatically deleted:"
            ).setEmbeds(userKickWarning.embeds).queue()
        }
    }

    private fun onFailToInformUser(
        event: SlashCommandInteractionEvent,
        reason: String,
        hook: InteractionHook,
        toKick: Member,
        throwable: Throwable,
        kickRestAction: RestAction<Void>
    ) {
        kickRestAction.queue({
            logKick(event, reason, toKick)
            val msg =
                "Kicked $toKick.\n\nWas unable to send a DM to the user please inform the user manually, if possible.\nError: ${throwable.message}"
            hook.editOriginal(msg).queue()
        }) { banThrowable ->
            hook.editOriginal(
                "Kick failed $toKick: ${banThrowable.message}\n\nWas unable to send a DM to the user.\nError: ${throwable.message}"
            ).queue()
        }
    }

    override fun getCommandsData(): List<SlashCommandData> {
        return listOf(
            Commands.slash(COMMAND, DESCRIPTION)
                .addOptions(
                    OptionData(OptionType.USER, OPTION_USER, "The user to kick").setRequired(true),
                    OptionData(OptionType.STRING, OPTION_REASON, "The reason for the kick").setRequired(true)
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.KICK_MEMBERS))
        )
    }
}
