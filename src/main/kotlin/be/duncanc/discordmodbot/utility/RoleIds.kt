package be.duncanc.discordmodbot.utility

import be.duncanc.discordmodbot.discord.CommandModule
import be.duncanc.discordmodbot.discord.UserBlockService
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.utils.SplitUtil
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * Commands to get all role ids of the current guild where executed.
 */
@Component
class RoleIds(
    userBlockService: UserBlockService
) : CommandModule(
    ALIASES,
    null,
    DESCRIPTION,
    userBlockService = userBlockService
) {
    companion object {
        private val ALIASES = arrayOf("RoleIds", "GetRoleIds")
        private const val DESCRIPTION = "Get all the role ids of the guild where executed."
    }

    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        if (!event.isFromType(ChannelType.TEXT)) {
            event.channel.sendMessage("This command only works in a guild.")
                .queue { message -> message.delete().queueAfter(1, TimeUnit.MINUTES) }
        } else if (event.member?.hasPermission(Permission.MANAGE_ROLES) != true) {
            event.channel.sendMessage(event.author.asMention + " you need manage roles permission to use this command.")
                .queue { message -> message.delete().queueAfter(1, TimeUnit.MINUTES) }
        } else {
            val result = StringBuilder()
            event.guild.roles.forEach { role: Role -> result.append(role.toString()).append("\n") }
            event.author.openPrivateChannel().queue { privateChannel ->
                val messages = SplitUtil.split(
                    result.toString(),
                    Message.MAX_CONTENT_LENGTH - 10,
                    SplitUtil.Strategy.NEWLINE
                )
                messages.forEach { message -> privateChannel.sendMessage(message).queue() }
            }
        }
    }
}
