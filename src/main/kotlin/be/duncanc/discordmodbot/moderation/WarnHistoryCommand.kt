package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.discord.SlashCommand
import be.duncanc.discordmodbot.discord.nicknameAndUsername
import be.duncanc.discordmodbot.moderation.persistence.GuildWarnPointsRepository
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.utils.TimeFormat
import org.springframework.stereotype.Component
import java.awt.Color
import java.time.OffsetDateTime

@Component
class WarnHistoryCommand(
    private val guildWarnPointsRepository: GuildWarnPointsRepository
) : ListenerAdapter(), SlashCommand {
    companion object {
        private const val COMMAND = "warnhistory"
        private const val DESCRIPTION = "Shows the warn history for a user."
        private const val OPTION_USER = "user"
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

        val targetUserOption = event.getOption(OPTION_USER)
        if (targetUserOption == null) {
            event.reply("This command requires a user.").setEphemeral(true).queue()
            return
        }

        val targetMember = targetUserOption.asMember
        if (targetMember != null) {
            showWarnHistory(event, guild, targetMember.user)
        } else {
            showWarnHistory(event, guild, targetUserOption.asUser)
        }
    }

    private fun showWarnHistory(event: SlashCommandInteractionEvent, guild: Guild, user: User) {
        val guildId = guild.idLong
        val userId = user.idLong

        event.deferReply(true).queue { hook ->
            if (!guildWarnPointsRepository.existsByGuildIdAndUserId(guildId, userId)) {
                hook.sendMessage("The user has not received any points.")
                    .setEphemeral(true).queue()
                return@queue
            }

            val warnings = guildWarnPointsRepository.findAllByGuildIdAndUserIdAndExpireDateAfter(
                guildId,
                userId,
                OffsetDateTime.now()
            )

            if (warnings.isEmpty()) {
                hook.sendMessage("The user has no active points.")
                    .setEphemeral(true).queue()
                return@queue
            }

            val embeds = mutableListOf<net.dv8tion.jda.api.entities.MessageEmbed>()

            warnings.forEach { warning ->
                val embedBuilder = EmbedBuilder()
                    .setColor(Color.ORANGE)
                    .setTitle("Warning for ${user.name}")
                    .addField("Points", warning.points.toString(), true)
                    .addField(
                        "Moderator",
                        guild.getMemberById(warning.creatorId)?.nicknameAndUsername ?: "Unknown",
                        true
                    )
                    .addField(
                        "Created",
                        TimeFormat.DATE_SHORT_TIME_SHORT.atInstant(warning.creationDate.toInstant()).toString(),
                        true
                    )
                    .addField("Reason", warning.reason, false)
                    .addField(
                        "Expires",
                        TimeFormat.DATE_SHORT_TIME_SHORT.atInstant(warning.expireDate.toInstant()).toString(),
                        true
                    )

                embeds.add(embedBuilder.build())
            }

            hook.sendMessage("Here is the list of points the user collected:")
                .setEphemeral(true)
                .queue()
            embeds.forEach { embed ->
                hook.setEphemeral(true).sendMessageEmbeds(embed).queue()
            }
        }
    }

    override fun getCommandsData(): List<SlashCommandData> {
        return listOf(
            Commands.slash(COMMAND, DESCRIPTION)
                .addOptions(
                    OptionData(
                        OptionType.USER,
                        OPTION_USER,
                        "The user to check"
                    ).setRequired(true)
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.KICK_MEMBERS))
        )
    }
}
