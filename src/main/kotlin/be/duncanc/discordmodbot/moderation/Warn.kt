package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.discord.CommandModule
import be.duncanc.discordmodbot.discord.extractReason
import be.duncanc.discordmodbot.discord.findMemberAndCheckCanInteract
import be.duncanc.discordmodbot.discord.nicknameAndUsername
import be.duncanc.discordmodbot.logging.GuildLogger
import be.duncanc.discordmodbot.moderation.persistence.GuildWarnPointsSettings
import be.duncanc.discordmodbot.moderation.persistence.GuildWarnPointsSettingsRepository
import java.awt.Color
import java.util.*
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * Created by Duncan on 24/02/2017.
 *
 *
 * This class creates a command that allowed you to warn users by sending them a dm and logging.
 */
@Component
class Warn
@Autowired constructor(
    private val guildWarnPointsSettingsRepository: GuildWarnPointsSettingsRepository
) : CommandModule(
    arrayOf("Warn"),
    "[User mention] [Reason~]",
    "Warns as user by sending the user mentioned a message and logs the warning to the log channel.",
    true,
    true,
    requiredPermissions = arrayOf(Permission.KICK_MEMBERS)
) {


    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        val guildWarnPointsSettings = guildWarnPointsSettingsRepository.findById(event.guild.idLong)
            .orElse(GuildWarnPointsSettings(event.guild.idLong, announceChannelId = -1))
        if (guildWarnPointsSettings.overrideWarnCommand) {
            return
        }
        event.author.openPrivateChannel().queue(
            { privateChannel -> commandExec(event, arguments, privateChannel) }
        ) { commandExec(event, arguments, null as PrivateChannel?) }
    }

    private fun commandExec(event: MessageReceivedEvent, arguments: String?, privateChannel: PrivateChannel?) {
        if (event.message.mentions.members.size < 1) {
            privateChannel?.sendMessage("Illegal argumentation, you need to mention a user that is still in the server.")
                ?.queue()
        } else {
            val reason: String = extractReason(arguments)

            val toWarn = findMemberAndCheckCanInteract(event)
            val guildLogger = event.jda.registeredListeners.firstOrNull { it is GuildLogger } as GuildLogger?
            if (guildLogger != null) {
                val logEmbed = EmbedBuilder()
                    .setColor(Color.YELLOW)
                    .setTitle("User warned")
                    .addField("UUID", UUID.randomUUID().toString(), false)
                    .addField("User", toWarn.nicknameAndUsername, true)
                    .addField("Moderator", event.member!!.nicknameAndUsername, true)
                    .addField("Reason", reason, false)

                guildLogger.log(logEmbed, toWarn.user, event.guild, null, GuildLogger.LogTypeAction.MODERATOR)
            }

            val userWarning = EmbedBuilder()
                .setColor(Color.YELLOW)
                .setAuthor(event.member!!.nicknameAndUsername, null, event.author.effectiveAvatarUrl)
                .setTitle(event.guild.name + ": You have been warned by " + event.member!!.nicknameAndUsername, null)
                .addField("Reason", reason, false)

            toWarn.user.openPrivateChannel().queue(
                { privateChannelUserToWarn ->
                    privateChannelUserToWarn.sendMessageEmbeds(userWarning.build()).queue(
                        { onSuccessfulWarnUser(privateChannel!!, toWarn, userWarning.build()) }
                    ) { throwable -> onFailToWarnUser(privateChannel!!, toWarn, throwable) }
                }
            ) { throwable -> onFailToWarnUser(privateChannel!!, toWarn, throwable) }
        }
    }

    private fun onSuccessfulWarnUser(privateChannel: PrivateChannel, toWarn: Member, userWarning: MessageEmbed) {
        val creatorMessage = MessageCreateBuilder()
            .addContent("Warned ").addContent(toWarn.toString())
            .addContent(".\n\nThe following message was sent to the user:")
            .setEmbeds(userWarning)
            .build()
        privateChannel.sendMessage(creatorMessage).queue()
    }

    private fun onFailToWarnUser(privateChannel: PrivateChannel, toWarn: Member, throwable: Throwable) {
        val creatorMessage = MessageCreateBuilder()
            .addContent("Warned ").addContent(toWarn.toString())
            .addContent(".\n\nWas unable to send a DM to the user please inform the user manually.\n")
            .addContent(throwable.javaClass.simpleName).addContent(": ").addContent(throwable.message ?: "")
            .build()
        privateChannel.sendMessage(creatorMessage).queue()
    }
}
