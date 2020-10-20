package be.duncanc.discordmodbot.bot.sequences

import be.duncanc.discordmodbot.bot.commands.CommandModule
import be.duncanc.discordmodbot.bot.services.MuteRole
import be.duncanc.discordmodbot.data.entities.GuildWarnPoints
import be.duncanc.discordmodbot.data.repositories.GuildWarnPointsRepository
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.springframework.stereotype.Component
import java.util.*
import java.util.concurrent.TimeUnit

@Component
class RevokeWarnPoints(
        val guildWarnPointsRepository: GuildWarnPointsRepository,
        val muteRole: MuteRole
) : CommandModule(
        arrayOf("RevokeWarnPoints", "RevokePoints"),
        "Mention a user",
        "This command is used to remove points  from a user, the user will be informed about this",
        requiredPermissions = arrayOf(Permission.KICK_MEMBERS),
        ignoreWhitelist = true
) {
    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        val userId = try {
            event.message.contentRaw.substring(command.length + 2).trimStart('<', '@', '!').trimEnd('>').toLong()
        } catch (stringIndexOutOfBoundsException: StringIndexOutOfBoundsException) {
            throw IllegalArgumentException("A mention is required to use this command", stringIndexOutOfBoundsException)
        }
        val userGuildWarnPoints = guildWarnPointsRepository.findById(GuildWarnPoints.GuildWarnPointsId(userId, event.guild.idLong))
        userGuildWarnPoints.ifPresentOrElse(
                {
                    event.jda.addEventListener(RevokePointsSequence(event.author, event.channel, it))
                },
                {
                    event.channel.sendMessage("This user id has no warnings or the user does not exist.").queue {
                        it.delete().queueAfter(1, TimeUnit.MINUTES)
                    }
                }
        )
    }

    inner class RevokePointsSequence(
            user: User,
            channel: MessageChannel,
            private val userGuildWarnPoints: GuildWarnPoints
    ) : Sequence(
            user,
            channel
    ) {
        init {
            val messageBuilder = MessageBuilder("The user has had the following warnings in the past:\n\n")
            userGuildWarnPoints.points.forEach { userWarnPoints ->
                messageBuilder.append("$userWarnPoints\n\n")
            }
            messageBuilder.append("\n\nPlease enter the ID of the warning to revoke it.")
            messageBuilder.buildAll(MessageBuilder.SplitPolicy.NEWLINE).forEach {
                channel.sendMessage(it).queue { sendMessage -> super.addMessageToCleaner(sendMessage) }
            }

        }

        override fun onMessageReceivedDuringSequence(event: MessageReceivedEvent) {
            val selectedUUID: UUID = UUID.fromString(event.message.contentRaw)
            val selectedUserWarnPoints = userGuildWarnPoints.points.find { it.id == selectedUUID }
            if (selectedUserWarnPoints != null) {
                userGuildWarnPoints.points.remove(selectedUserWarnPoints)
                guildWarnPointsRepository.save(userGuildWarnPoints)
                channel.sendMessage("The warning has been revoked.").queue {
                    it.delete().queueAfter(1, TimeUnit.MINUTES)
                }
                super.destroy()
            } else {
                channel.sendMessage("The warning with that id was not found.").queue {
                    it.delete().queueAfter(1, TimeUnit.MINUTES)
                }
                super.destroy()
            }
        }
    }
}
