package be.duncanc.discordmodbot.discord

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel

interface CommandChannelWhitelist {
    fun isWhitelisted(textChannel: TextChannel): Boolean
}
