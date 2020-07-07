package be.duncanc.discordmodbot.bot.sequences

import be.duncanc.discordmodbot.bot.commands.CommandModule
import be.duncanc.discordmodbot.bot.services.GuildLogger
import be.duncanc.discordmodbot.bot.utils.messageTimeFormat
import be.duncanc.discordmodbot.bot.utils.nicknameAndUsername
import be.duncanc.discordmodbot.data.repositories.MuteRolesRepository
import be.duncanc.discordmodbot.data.services.ScheduledUnmuteService
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit


open class PlanUnmuteSequence(
        user: User,
        channel: MessageChannel,
        private val scheduledUnmuteService: ScheduledUnmuteService,
        private val targetUser: User,
        private val guildLogger: GuildLogger
) : Sequence(
        user,
        channel
) {
    init {
        super.channel.sendMessage("In how much days should the user be unmuted?").queue {
            addMessageToCleaner(it)
        }
    }

    override fun onMessageReceivedDuringSequence(event: MessageReceivedEvent) {
        val days = event.message.contentRaw.toLong()
        if (days <= 0) {
            throw IllegalArgumentException("The numbers of days should not be negative or 0")
        }
        val unmuteDateTime = OffsetDateTime.now().plusDays(days)
        scheduledUnmuteService.planUnmute((channel as TextChannel).guild.idLong, targetUser.idLong, unmuteDateTime)
        confirmationMessage()
        val guild = channel.guild
        logScheduledMute(guild, unmuteDateTime)
        super.destroy()
    }

    private fun confirmationMessage() {
        super.channel.sendMessage("Unmute has been planned.").queue {
            it.delete().queueAfter(1, TimeUnit.MINUTES)
        }
    }

    private fun logScheduledMute(guild: Guild, unmuteDateTime: OffsetDateTime) {
        val logEmbed = EmbedBuilder()
                .setColor(GuildLogger.LIGHT_BLUE)
                .setTitle("User unmute planned")
                .addField("User", guild.getMember(targetUser)?.nicknameAndUsername ?: targetUser.name, true)
                .addField("Moderator", guild.getMember(user)?.nicknameAndUsername ?: user.name, true)
                .addField("Unmute planned after", unmuteDateTime.format(messageTimeFormat), false)

        guildLogger.log(logEmbed, targetUser, guild, null, GuildLogger.LogTypeAction.MODERATOR)
    }
}

@Component
class PlanUnmuteCommand(
        private val scheduledUnmuteService: ScheduledUnmuteService,
        private val guildLogger: GuildLogger,
        private val muteRolesRepository: MuteRolesRepository
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
        val guild = event.guild
        muteRolesRepository.findById(guild.idLong).ifPresent {
            val memberById = guild.getMemberById(userId)
            if (memberById != null && memberById.roles.contains(memberById.guild.getRoleById(it.roleId))
            ) {
                event.jda.addEventListener(PlanUnmuteSequence(event.author, event.channel, scheduledUnmuteService, memberById.user, guildLogger))
            } else {
                userNotMutedMessage(event)
            }
        }
    }

    private fun userNotMutedMessage(event: MessageReceivedEvent) {
        event.channel.sendMessage("${event.author.asMention} This user is not muted.").queue {
            it.delete().queueAfter(1, TimeUnit.MINUTES)
        }
    }
}
