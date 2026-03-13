package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.discord.CommandModule
import be.duncanc.discordmodbot.discord.extractReason
import be.duncanc.discordmodbot.discord.findMemberAndCheckCanInteract
import be.duncanc.discordmodbot.discord.nicknameAndUsername
import be.duncanc.discordmodbot.logging.GuildLogger
import java.awt.Color
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import org.springframework.stereotype.Component

/**
 * This class creates an RemoveMute command that is logged.
 */
@Component
class RemoveMute : CommandModule(
    arrayOf("Unmute", "RemoveMute"),
    "[User mention] [Reason~]",
    "This command will remove a mute from a user and log it to the log channel.",
    true,
    true
) {

    /**
     * Do something with the event, command and arguments.
     *
     * @param event     A MessageReceivedEvent that came with the command
     * @param command   The command alias that was used to trigger this commandExec
     * @param arguments The arguments that where entered after the command alias
     */
    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        event.author.openPrivateChannel().queue(
            { privateChannel -> commandExec(event, arguments, privateChannel) }
        ) { commandExec(event, arguments, null as PrivateChannel?) }
    }

    private fun commandExec(event: MessageReceivedEvent, arguments: String?, privateChannel: PrivateChannel?) {
        if (!event.isFromType(ChannelType.TEXT)) {
            privateChannel?.sendMessage("This command only works in a guild.")?.queue()
        } else if (event.member?.hasPermission(Permission.MANAGE_ROLES) != true) {
            privateChannel?.sendMessage(event.author.asMention + " you need manage roles permission to remove a mute!")
                ?.queue()
        } else if (event.message.mentions.members.size < 1) {
            privateChannel?.sendMessage("Illegal argumentation, you need to mention a user that is still in the server.")
                ?.queue()
        } else {
            val reason: String = extractReason(arguments)

            val toRemoveMute = findMemberAndCheckCanInteract(event)
            event.guild.removeRoleFromMember(toRemoveMute, event.guild.getRoleById("221678882342830090")!!)
                .queue({ _ ->
                    val guildLogger = event.jda.registeredListeners.firstOrNull { it is GuildLogger } as GuildLogger?
                    if (guildLogger != null) {
                        val logEmbed = EmbedBuilder()
                            .setColor(Color.GREEN)
                            .setTitle("User's mute was removed", null)
                            .addField("User", toRemoveMute.nicknameAndUsername, true)
                            .addField("Moderator", event.member!!.nicknameAndUsername, true)
                            .addField("Reason", reason, false)

                        guildLogger.log(
                            logEmbed,
                            toRemoveMute.user,
                            event.guild,
                            null,
                            GuildLogger.LogTypeAction.MODERATOR
                        )
                    }
                    val muteRemoveNotification = EmbedBuilder()
                        .setColor(Color.green)
                        .setAuthor(event.member!!.nicknameAndUsername, null, event.author.effectiveAvatarUrl)
                        .setTitle(event.guild.name + ": Your mute has been removed by " + event.member!!.nicknameAndUsername)
                        .addField("Reason", reason, false)
                        .build()

                    toRemoveMute.user.openPrivateChannel().queue(
                        { privateChannelUserToRemoveMute ->
                            privateChannelUserToRemoveMute.sendMessageEmbeds(muteRemoveNotification).queue(
                                { onSuccessfulInformUser(privateChannel, toRemoveMute, muteRemoveNotification) }
                            ) { throwable -> onFailToInformUser(privateChannel, toRemoveMute, throwable) }
                        }
                    ) { throwable -> onFailToInformUser(privateChannel, toRemoveMute, throwable) }
                }) { throwable ->
                    if (privateChannel == null) {
                        return@queue
                    }

                    val creatorMessage = MessageCreateBuilder()
                        .addContent("Failed removing mute ").addContent(toRemoveMute.toString()).addContent(".\n")
                        .addContent(throwable.javaClass.simpleName).addContent(": ").addContent(throwable.message ?: "")
                        .build()
                    privateChannel.sendMessage(creatorMessage).queue()
                }
        }
    }

    private fun onSuccessfulInformUser(
        privateChannel: PrivateChannel?,
        toRemoveMute: Member,
        muteRemoveNotification: MessageEmbed
    ) {
        if (privateChannel == null) {
            return
        }

        val creatorMessage = MessageCreateBuilder()
            .addContent("Removed mute from ").addContent(toRemoveMute.toString())
            .addContent(".\n\nThe following message was sent to the user:")
            .setEmbeds(muteRemoveNotification)
            .build()
        privateChannel.sendMessage(creatorMessage).queue()
    }

    private fun onFailToInformUser(privateChannel: PrivateChannel?, toRemoveMute: Member, throwable: Throwable) {
        if (privateChannel == null) {
            return
        }

        val creatorMessage = MessageCreateBuilder()
            .addContent("Removed mute from ").addContent(toRemoveMute.toString())
            .addContent(".\n\nWas unable to send a DM to the user please inform the user manually.\n")
            .addContent(throwable.javaClass.simpleName).addContent(": ").addContent(throwable.message ?: "")
            .build()
        privateChannel.sendMessage(creatorMessage).queue()
    }
}
