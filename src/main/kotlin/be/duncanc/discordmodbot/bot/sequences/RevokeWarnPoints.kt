package be.duncanc.discordmodbot.bot.sequences

import be.duncanc.discordmodbot.bot.commands.CommandModule
import be.duncanc.discordmodbot.bot.services.MuteRole
import be.duncanc.discordmodbot.data.repositories.GuildWarnPointsRepository
import be.duncanc.discordmodbot.data.repositories.GuildWarnPointsSettingsRepository
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.message.MessageReceivedEvent

class RevokeWarnPoints(
        val guildWarnPointsRepository: GuildWarnPointsRepository,
        val guildWarnPointsSettingsRepository: GuildWarnPointsSettingsRepository,
        val muteRole: MuteRole
) : CommandModule(
        arrayOf("RevokeWarnPoints", "RevokePoints"),
        "Mention a user",
        "This command is used to remove points  from a user, the user will be informed about this",
        requiredPermissions = arrayOf(Permission.KICK_MEMBERS),
        ignoreWhitelist = true
) {
    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        TODO("Not yet implemented")
    }
}
