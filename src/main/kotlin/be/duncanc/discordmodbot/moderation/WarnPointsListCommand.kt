package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.discord.SlashCommand
import be.duncanc.discordmodbot.discord.nicknameAndUsername
import be.duncanc.discordmodbot.moderation.persistence.GuildWarnPointsRepository
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.springframework.stereotype.Component
import java.awt.Color
import java.time.OffsetDateTime

@Component
class WarnPointsListCommand(
    private val guildWarnPointsRepository: GuildWarnPointsRepository
) : ListenerAdapter(), SlashCommand {
    companion object {
        private const val COMMAND = "warnpointslist"
        private const val DESCRIPTION = "Shows all warn points in the server."
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != COMMAND) return

        val guild = event.guild
        if (guild == null) {
            event.reply("This command only works in a guild.").setEphemeral(true).queue()
            return
        }

        val moderator = event.member!!
        if (!moderator.hasPermission(Permission.KICK_MEMBERS)) {
            event.reply("You need kick members permission to use this command.").setEphemeral(true).queue()
            return
        }

        val guildId = guild.idLong
        val allPoints = guildWarnPointsRepository.findAllByGuildIdAndExpireDateAfter(guildId, OffsetDateTime.now())

        if (allPoints.isEmpty()) {
            event.reply("No active warn points in this server.").setEphemeral(true).queue()
            return
        }

        val embedBuilder = EmbedBuilder()
            .setTitle("Active Warn Points in ${guild.name}")
            .setColor(Color.YELLOW)

        val usersWithPoints = allPoints.groupBy { it.userId }

        usersWithPoints.forEach { (userId, points) ->
            val totalPoints = points.sumOf { it.points }
            val member = guild.getMemberById(userId)
            val userName = member?.nicknameAndUsername ?: "Unknown User (ID: $userId)"
            embedBuilder.addField(userName, "$totalPoints point(s) (${points.size} warning(s))", true)
        }

        event.replyEmbeds(embedBuilder.build()).setEphemeral(true).queue()
    }

    override fun getCommandsData(): List<SlashCommandData> {
        return listOf(
            Commands.slash(COMMAND, DESCRIPTION)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.KICK_MEMBERS))
        )
    }
}
