package be.duncanc.discordmodbot.bot.sequences

import be.duncanc.discordmodbot.bot.commands.CommandModule
import be.duncanc.discordmodbot.data.entities.GuildWarnPoint
import be.duncanc.discordmodbot.data.services.GuildWarnPointsService
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.utils.SplitUtil
import org.springframework.stereotype.Component
import java.util.*
import java.util.concurrent.TimeUnit

@Component
class RevokeWarnPoints(
    val guildWarnPointsService: GuildWarnPointsService
) : CommandModule(
    arrayOf("RevokeWarnPoints", "RevokePoints"),
    "Mention a user",
    "This command is used to remove points  from a user, the user will be informed about this",
    requiredPermissions = arrayOf(Permission.KICK_MEMBERS),
    ignoreWhitelist = true
) {
    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        if (event.message.mentions.users.size != 1) {
            throw IllegalArgumentException("A single mention is required to use this command")
        }

        val guildId = event.guild.idLong
        val userId = event.message.mentions.users[0].idLong


        if (guildWarnPointsService.userHasGuildWarnings(guildId, userId)) {
            val userGuildWarnPoints = guildWarnPointsService.getGuildWarningsFromUser(
                guildId, userId
            )

            event.jda.addEventListener(RevokePointsSequence(event.author, event.channel, userGuildWarnPoints))
        } else {
            event.channel.sendMessage("This user id has no warnings or the user does not exist.").queue {
                it.delete().queueAfter(1, TimeUnit.MINUTES)
            }
        }
    }

    inner class RevokePointsSequence(
        user: User,
        channel: MessageChannel,
        private val guildWarnPoints: Collection<GuildWarnPoint>,
    ) : Sequence(
        user,
        channel
    ), MessageSequence {
        init {
            val messageBuilder = StringBuilder("The user has had the following warnings in the past:\n\n")
            guildWarnPoints
                .forEach { userWarnPoints ->
                    messageBuilder.append("Id ${userWarnPoints.id} - Reason: ${userWarnPoints.reason} - Created by <@${userWarnPoints.creatorId}>\n\n")
                }
            messageBuilder.append("\n\nPlease enter the ID of the warning to revoke it.")

            SplitUtil.split(messageBuilder.toString(), Message.MAX_CONTENT_LENGTH, SplitUtil.Strategy.NEWLINE)
                .forEach {
                    channel.sendMessage(it).queue { sendMessage -> super.addMessageToCleaner(sendMessage) }
                }
        }

        override fun onMessageReceivedDuringSequence(event: MessageReceivedEvent) {
            val selectedUUID: UUID = UUID.fromString(event.message.contentRaw)

            guildWarnPointsService.revokePoint(selectedUUID)

            channel.sendMessage("The warning has been revoked.").queue {
                it.delete().queueAfter(1, TimeUnit.MINUTES)
            }
            super.destroy()
        }
    }
}
