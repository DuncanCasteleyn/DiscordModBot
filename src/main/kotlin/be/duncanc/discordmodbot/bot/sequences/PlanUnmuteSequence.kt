package be.duncanc.discordmodbot.bot.sequences

import be.duncanc.discordmodbot.bot.commands.CommandModule
import be.duncanc.discordmodbot.data.services.ScheduledUnmuteService
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.springframework.stereotype.Component
import java.time.OffsetDateTime


open class PlanUnmuteSequence(
        user: User,
        channel: MessageChannel,
        private val scheduledUnmuteService: ScheduledUnmuteService,
        private val targetUser: User
) : Sequence(
        user,
        channel
) {
    init {
        super.channel.sendMessage("In how much days should the user be unmute starting today.")
    }

    override fun onMessageReceivedDuringSequence(event: MessageReceivedEvent) {
        val days = event.message.contentRaw.toLong()
        if (days <= 0) {
            throw IllegalArgumentException("The numbers of days should not be negative")
        }
        scheduledUnmuteService.planUnmute((channel as TextChannel).guild.idLong, targetUser.idLong, OffsetDateTime.now().plusHours(days))
        super.channel.sendMessage("Unmute has been been planned.").queue()
    }
}

@Component
class PlanUnmuteCommand(
        private val scheduledUnmuteService: ScheduledUnmuteService
) : CommandModule(
        arrayOf("PlanUnmute"),
        null,
        null,
        ignoreWhitelist = true,
        requiredPermissions = *arrayOf(Permission.MANAGE_ROLES)
) {
    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        val userId = try {
            event.message.contentRaw.substring(command.length + 2).trimStart('<', '@', '!').trimEnd('>').toLong()
        } catch (ignored: IndexOutOfBoundsException) {
            throw IllegalArgumentException("This command requires a user id or mention")
        } catch (ignored: NumberFormatException) {
            throw IllegalArgumentException("This command requires a user id or mention")
        }
        event.guild.getMemberById(userId)?.user?.let { user ->
            event.jda.addEventListener(PlanUnmuteSequence(event.author, event.channel, scheduledUnmuteService, user))
        }

    }
}
