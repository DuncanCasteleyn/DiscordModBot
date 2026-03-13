package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.discord.CommandModule
import be.duncanc.discordmodbot.moderation.persistence.GuildWarnPoint
import be.duncanc.discordmodbot.moderation.persistence.GuildWarnPointsRepository
import java.time.OffsetDateTime
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.utils.SplitUtil
import org.springframework.stereotype.Component

@Component
class WarnPointsList(
    private val guildWarnPointsRepository: GuildWarnPointsRepository
) : CommandModule(
    arrayOf("WarnPointsList", "WarnList"),
    null,
    null,
    requiredPermissions = arrayOf(Permission.KICK_MEMBERS)
) {
    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        val userWarnPoints =
            guildWarnPointsRepository.findAllByGuildIdAndExpireDateAfter(
                event.guild.idLong, OffsetDateTime.now()
            )

        val message = StringBuilder()
        message.append("Summary of active points per user:\n")

        val groupByUserAndGuild =
            userWarnPoints.groupBy { guildWarnPoint: GuildWarnPoint ->
                guildWarnPoint.guildId.toString() + "-" + guildWarnPoint.userId.toString()
            }

        groupByUserAndGuild.forEach { (_, userWarnings) ->
            val totalPoints = userWarnings.size
            message.append("\n")
                .append("<@${userWarnings.first().userId}>")
                .append(" [$totalPoints]")
        }
        val messages = SplitUtil.split(message.toString(), Message.MAX_CONTENT_LENGTH, SplitUtil.Strategy.NEWLINE)

        messages.forEach { event.channel.sendMessage(it).queue() }
    }
}
