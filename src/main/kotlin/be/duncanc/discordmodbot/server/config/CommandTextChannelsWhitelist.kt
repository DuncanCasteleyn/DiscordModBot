package be.duncanc.discordmodbot.server.config

import be.duncanc.discordmodbot.discord.CommandChannelWhitelist
import be.duncanc.discordmodbot.discord.CommandModule
import be.duncanc.discordmodbot.server.config.persistence.GuildCommandChannels
import be.duncanc.discordmodbot.server.config.persistence.GuildCommandChannelsRepository
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.TimeUnit

@Component
class CommandTextChannelsWhitelist
@Autowired constructor(
    private val guildCommandChannelsRepository: GuildCommandChannelsRepository
) : CommandModule(
    arrayOf("CommandWhitelistChannel", "WhitelistChannel"),
    null,
    "Whitelists the channel so commands can be used in it.",
    ignoreWhitelist = true,
    requiredPermissions = arrayOf(Permission.MANAGE_CHANNEL)
), CommandChannelWhitelist {

    @Transactional
    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        val guildCommandChannels = guildCommandChannelsRepository.findById(event.guild.idLong).orElse(null)
        val mutableSet = guildCommandChannels?.whitelistedChannels
        if (mutableSet?.contains(event.channel.idLong) == true) {
            mutableSet.remove(event.channel.idLong)
            if (mutableSet.isEmpty()) {
                guildCommandChannelsRepository.findById(event.guild.idLong)
                event.channel.sendMessage("The channel was removed from the whitelist. There are no channels left on the whitelist commands can be used in all channels now.")
                    .queue(cleanMessages())
            } else {
                event.channel.sendMessage("The channel was removed from the whitelist.").queue(cleanMessages())
            }
        } else if (mutableSet == null) {
            val newHashSet = HashSet<Long>()
            newHashSet.add(event.channel.idLong)
            val newGuildCommandChannels = GuildCommandChannels(event.guild.idLong, newHashSet)
            guildCommandChannelsRepository.save(newGuildCommandChannels)
            event.channel.sendMessage("The channel was added to the whitelist. Commands can now only be used in whitelisted channels.")
                .queue(cleanMessages())
        } else {
            mutableSet.add(event.channel.idLong)
            guildCommandChannelsRepository.save(guildCommandChannels)
            event.channel.sendMessage("The channel was added to the whitelist.").queue(cleanMessages())
        }
    }

    private fun cleanMessages(): (Message) -> Unit =
        { it.delete().queueAfter(1, TimeUnit.MINUTES) }

    @Transactional(readOnly = true)
    override fun isWhitelisted(textChannel: TextChannel): Boolean {
        val contains = guildCommandChannelsRepository.findById(textChannel.guild.idLong).orElse(null)
            ?.whitelistedChannels?.contains(textChannel.idLong)
        return contains == true || contains == null
    }
}
