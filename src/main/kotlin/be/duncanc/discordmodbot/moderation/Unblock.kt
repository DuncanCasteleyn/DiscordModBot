package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.bootstrap.DiscordModBotConfig
import be.duncanc.discordmodbot.discord.CommandModule
import be.duncanc.discordmodbot.moderation.persistence.BlockedUserRepository
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.springframework.stereotype.Component

@Component
class Unblock(
    val blockedUserRepository: BlockedUserRepository,
    val discordModBotConfig: DiscordModBotConfig
) : CommandModule(
    arrayOf("Unblock"),
    null,
    null
) {
    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        if (event.author.idLong == discordModBotConfig.ownerId && arguments != null) {
            blockedUserRepository.deleteById(arguments.toLong())
        }
    }
}
