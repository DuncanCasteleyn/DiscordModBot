package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.discord.UserContextMenuCommand
import be.duncanc.discordmodbot.discord.nicknameAndUsername
import be.duncanc.discordmodbot.logging.GuildLogger
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.requests.RestAction
import org.springframework.stereotype.Component
import java.awt.Color
import java.util.*
import java.util.concurrent.TimeUnit

@Component
class BanSpamAccountContextMenu(
    private val guildLogger: GuildLogger
) : ListenerAdapter(), UserContextMenuCommand {
    companion object {
        private const val COMMAND = "Ban Spam Account"
        private const val REASON = "Compromised account"
        private const val FAIRY_TAIL_SERVER_ID = 175856762677624832L
        private const val FAIRY_TAIL_APPEAL_LINK = "https://goo.gl/forms/SpWg49gaQlMt4lSG3"
        private const val REZERO_SERVER_ID = 176028172729450497L
        private const val REZERO_APPEAL_LINK = "https://forms.gle/ffbDj12KcSyTT7mUA"
    }

    override fun onUserContextInteraction(event: UserContextInteractionEvent) {
        if (event.name != COMMAND) return

        val moderator = event.member
        if (moderator == null) {
            event.reply("This command only works in a guild.").setEphemeral(true).queue()
            return
        }

        if (!moderator.hasPermission(Permission.BAN_MEMBERS)) {
            event.reply("You need ban members permission to use this command.").setEphemeral(true).queue()
            return
        }

        val targetMember = event.targetMember
        if (targetMember == null) {
            event.reply("You need to select a user that is still in the server.").setEphemeral(true).queue()
            return
        }

        if (targetMember.idLong == event.jda.selfUser.idLong) {
            event.reply("You can't ban the bot itself.").setEphemeral(true).queue()
            return
        }

        if (!moderator.canInteract(targetMember)) {
            event.reply("You can't ban a user that you can't interact with.").setEphemeral(true).queue()
            return
        }

        event.deferReply(true).queue { hook ->
            val guild = event.guild!!
            val banRestAction = guild.ban(targetMember, 1, TimeUnit.DAYS).reason(REASON)
            val userBanNotification = EmbedBuilder()
                .setColor(Color.RED)
                .setAuthor(moderator.nicknameAndUsername, null, moderator.user.effectiveAvatarUrl)
                .setTitle(guild.name + ": You have been banned by " + moderator.nicknameAndUsername, null)
                .setDescription(createBanDescription(guild))
                .build()

            targetMember.user.openPrivateChannel().queue(
                { privateChannel ->
                    privateChannel.sendMessageEmbeds(userBanNotification).queue(
                        { message ->
                            onSuccessfulInformUser(hook, moderator, targetMember, message, banRestAction)
                        }
                    ) { throwable ->
                        onFailToInformUser(hook, moderator, targetMember, throwable, banRestAction)
                    }
                }
            ) { throwable ->
                onFailToInformUser(hook, moderator, targetMember, throwable, banRestAction)
            }
        }
    }

    private fun createBanDescription(guild: Guild): String {
        val description = StringBuilder("Reason: $REASON")
        when (guild.idLong) {
            FAIRY_TAIL_SERVER_ID -> description.append("\n\nIf you'd like to appeal the ban, please use this form: ")
                .append(FAIRY_TAIL_APPEAL_LINK)

            REZERO_SERVER_ID -> description.append("\n\nIf you'd like to appeal the ban, please use this form: ")
                .append(REZERO_APPEAL_LINK)
        }
        return description.toString()
    }

    private fun logBan(
        moderator: Member,
        toBan: Member
    ) {
        val logEmbed = EmbedBuilder()
            .setColor(Color.RED)
            .setTitle("User banned")
            .addField("UUID", UUID.randomUUID().toString(), false)
            .addField("User", toBan.nicknameAndUsername, true)
            .addField("Moderator", moderator.nicknameAndUsername, true)
            .addField("Reason", REASON, false)

        guildLogger.log(logEmbed, toBan.user, moderator.guild, null, GuildLogger.LogTypeAction.MODERATOR)
    }

    private fun onSuccessfulInformUser(
        hook: net.dv8tion.jda.api.interactions.InteractionHook,
        moderator: Member,
        toBan: Member,
        userBanWarning: Message,
        banRestAction: RestAction<Void>
    ) {
        banRestAction.queue({
            logBan(moderator, toBan)
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
        hook: net.dv8tion.jda.api.interactions.InteractionHook,
        moderator: Member,
        toBan: Member,
        throwable: Throwable,
        banRestAction: RestAction<Void>
    ) {
        banRestAction.queue({
            logBan(moderator, toBan)

            val msg =
                """Banned $toBan.

Was unable to send a DM to the user please inform the user manually, if possible.
Error: ${throwable.message}"""

            hook.editOriginal(msg).queue()
        }) { banThrowable ->
            hook.editOriginal(
                "Ban failed on $toBan: ${banThrowable.message}\n\nWas unable to send a DM to the user.\nError: ${throwable.message}"
            ).queue()
        }
    }

    override fun getCommandsData(): List<CommandData> {
        return listOf(
            Commands.user(COMMAND)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS))
        )
    }
}
