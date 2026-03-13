package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.moderation.persistence.BlockedUser
import be.duncanc.discordmodbot.moderation.persistence.BlockedUserRepository
import net.dv8tion.jda.api.entities.User
import org.springframework.stereotype.Service
import be.duncanc.discordmodbot.discord.UserBlockService as DiscordUserBlockService

@Service
class UserBlockService(
    val blockedUserRepository: BlockedUserRepository
) : DiscordUserBlockService {
    override fun blockUser(user: User) {
        val blockedUser = BlockedUser(user.idLong)
        blockedUserRepository.save(blockedUser)
        user.openPrivateChannel().queue {
            it.sendMessage("This is an automated message to inform you that you have been blocked by the bot due to spam.")
                .queue()
        }
    }

    override fun isBlocked(userId: Long): Boolean {
        return blockedUserRepository.existsById(userId)
    }
}
