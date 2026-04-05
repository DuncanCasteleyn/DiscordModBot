package be.duncanc.discordmodbot.discord

import net.dv8tion.jda.api.entities.Member

/**
 * Retrieves a string that contains both the nickname and username of a member.
 */
val Member.nicknameAndUsername: String
    get() = if (this.nickname != null) {
        "${this.nickname}(${this.user.name})"
    } else {
        this.user.name
    }
