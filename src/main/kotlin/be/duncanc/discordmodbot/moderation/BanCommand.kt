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
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.requests.RestAction
import org.springframework.stereotype.Component
import java.awt.Color
import java.util.concurrent.TimeUnit

@Component
class BanCommand : ListenerAdapter(), SlashCommand {
    companion object {
        private const val COMMAND = "ban"
        private const val DESCRIPTION =
            "Will ban the mentioned user, clear all messages from the last 7 days and log it to the log channel."
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

        if (!member.hasPermission(Permission.BAN_MEMBERS)) {
            event.reply("You need ban members permission to use this command.").setEphemeral(true).queue()
            return
        }

        val targetMember = event.getOption(OPTION_USER)?.asMember
        if (targetMember == null) {
            event.reply("You need to mention a user that is still in the server.").setEphemeral(true).queue()
            return
        }

        if (member.canInteract(targetMember) != true) {
            event.reply("You can't ban a user that you can't interact with.").setEphemeral(true).queue()
            return
        }

        val reason = event.getOption(OPTION_REASON)?.asString ?: "No reason provided"

        event.deferReply().queue { hook ->
            val guild = event.guild!!
            val banRestAction = guild.ban(targetMember, 7, TimeUnit.DAYS)
            val description = StringBuilder("Reason: $reason")
            if (guild.idLong == 175856762677624832L) {
                description.append("\n\n")
                    .append("If you'd like to appeal the ban, please use this form: https://goo.gl/forms/SpWg49gaQlMt4lSG3")
            } else if (guild.idLong == 176028172729450497L) {
                description.append("\n\n")
                    .append("If you'd like to appeal the ban, please use this form: https://forms.gle/ffbDj12KcSyTT7mUA")
            }

            val userBanNotification = EmbedBuilder()
                .setColor(Color.red)
                .setAuthor(member.nicknameAndUsername, null, member.user.effectiveAvatarUrl)
                .setTitle(guild.name + ": You have been banned by " + member.nicknameAndUsername, null)
                .setDescription(description.toString())
                .build()

            targetMember.user.openPrivateChannel().queue(
                { privateChannelUserToBan ->
                    privateChannelUserToBan.sendMessageEmbeds(userBanNotification).queue(
                        { message ->
                            onSuccessfulInformUser(event, reason, hook, targetMember, message, banRestAction)
                        }
                    ) { throwable ->
                        onFailToInformUser(event, reason, hook, targetMember, throwable, banRestAction, true)
                    }
                }
            ) { throwable -> onFailToInformUser(event, reason, hook, targetMember, throwable, banRestAction, false) }
        }
    }

    private fun logBan(
        event: SlashCommandInteractionEvent,
        reason: String,
        toBan: Member,
        hook: net.dv8tion.jda.api.interactions.InteractionHook
    ) {
        val guild = event.guild!!
        val guildLogger = event.jda.registeredListeners.firstOrNull { it is GuildLogger } as GuildLogger?
        if (guildLogger != null) {
            val logEmbed = EmbedBuilder()
                .setColor(Color.RED)
                .setTitle("User banned")
                .addField("UUID", java.util.UUID.randomUUID().toString(), false)
                .addField("User", toBan.nicknameAndUsername, true)
                .addField("Moderator", event.member!!.nicknameAndUsername, true)
                .addField("Reason", reason, false)

            guildLogger.log(logEmbed, toBan.user, guild, null, GuildLogger.LogTypeAction.MODERATOR)
        }
    }

    private fun onSuccessfulInformUser(
        event: SlashCommandInteractionEvent,
        reason: String,
        hook: net.dv8tion.jda.api.interactions.InteractionHook,
        toBan: Member,
        userBanWarning: Message,
        banRestAction: RestAction<Void>
    ) {
        banRestAction.queue({
            logBan(event, reason, toBan, hook)
            hook.editOriginal(
                "Banned $toBan.\n\nThe following message was sent to the user:"
            ).setEmbeds(userBanWarning.embeds).queue()
        }) { throwable ->
            userBanWarning.delete().queue()
            hook.editOriginal(
                "Ban failed on $toBan: ${throwable.message}\n\nThe following message was sent to the user but was automatically deleted:"
            ).setEmbeds(userBanWarning.embeds).queue()
        }
    }

    private fun onFailToInformUser(
        event: SlashCommandInteractionEvent,
        reason: String,
        hook: net.dv8tion.jda.api.interactions.InteractionHook,
        toBan: Member,
        throwable: Throwable,
        banRestAction: RestAction<Void>,
        dmFailed: Boolean
    ) {
        banRestAction.queue({
            logBan(event, reason, toBan, hook)
            val msg = if (dmFailed) {
                "Banned $toBan.\n\nWas unable to send a DM to the user please inform the user manually, if possible.\nError: ${throwable.message}"
            } else {
                "Banned $toBan.\n\nWas unable to send a DM to the user please inform the user manually, if possible.\nError: ${throwable.message}"
            }
            hook.editOriginal(msg).queue()
        }) { banThrowable ->
            hook.editOriginal(
                "Ban failed on $toBan: ${banThrowable.message}\n\nWas unable to send a DM to the user.\nError: ${throwable.message}"
            ).queue()
        }
    }

    override fun getCommandsData(): List<SlashCommandData> {
        return listOf(
            Commands.slash(COMMAND, DESCRIPTION)
                .addOptions(
                    OptionData(OptionType.USER, OPTION_USER, "The user to ban").setRequired(true),
                    OptionData(OptionType.STRING, OPTION_REASON, "The reason for the ban").setRequired(true)
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS))
        )
    }
}
