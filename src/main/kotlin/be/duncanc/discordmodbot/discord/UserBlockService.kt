package be.duncanc.discordmodbot.discord

import net.dv8tion.jda.api.entities.User

interface UserBlockService {
    fun blockUser(user: User)

    fun isBlocked(userId: Long): Boolean
}
