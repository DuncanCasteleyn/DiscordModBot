package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.discord.SlashCommand
import be.duncanc.discordmodbot.discord.nicknameAndUsername
import be.duncanc.discordmodbot.moderation.persistence.GuildWarnPointsRepository
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
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
        private const val SUBCOMMAND_ACTIVE = "active"
        private const val SUBCOMMAND_ALL = "all"
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
        if (!moderator.hasPermission(Permission.MANAGE_ROLES)) {
            event.reply("You need manage roles permission to use this command.").setEphemeral(true).queue()
            return
        }

        when (event.subcommandName) {
            SUBCOMMAND_ACTIVE -> showWarnHistory(event, guild, activeOnly = true)
            SUBCOMMAND_ALL -> showWarnHistory(event, guild, activeOnly = false)
            else -> event.reply("Please choose a valid /warnhistory subcommand.").setEphemeral(true).queue()
        }
    }

    private fun showWarnHistory(event: SlashCommandInteractionEvent, guild: Guild, activeOnly: Boolean) {
        val targetUserOption = event.getOption(OPTION_USER)
        if (targetUserOption == null) {
            event.reply("This command requires a user.").setEphemeral(true).queue()
            return
        }

        val targetMember = targetUserOption.asMember
        val user = if (targetMember != null) targetMember.user else targetUserOption.asUser
        val guildId = guild.idLong
        val userId = user.idLong

        event.deferReply(true).queue { hook ->
            val warnings = if (activeOnly) {
                guildWarnPointsRepository.findAllByGuildIdAndUserIdAndExpireDateAfter(
                    guildId,
                    userId,
                    OffsetDateTime.now()
                )
            } else {
                guildWarnPointsRepository.findAllByGuildIdAndUserId(guildId, userId)
            }

            if (warnings.isEmpty()) {
                val emptyMessage = if (activeOnly) {
                    "The user has no active points."
                } else {
                    "The user has not received any points."
                }

                hook.sendMessage(emptyMessage)
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
                .addSubcommands(
                    SubcommandData(SUBCOMMAND_ACTIVE, "Show the active warn history for a user")
                        .addOptions(
                            OptionData(
                                OptionType.USER,
                                OPTION_USER,
                                "The user to check"
                            ).setRequired(true)
                        ),
                    SubcommandData(SUBCOMMAND_ALL, "Show the full warn history for a user")
                        .addOptions(
                            OptionData(
                                OptionType.USER,
                                OPTION_USER,
                                "The user to check"
                            ).setRequired(true)
                        )
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_ROLES))
        )
    }
}
