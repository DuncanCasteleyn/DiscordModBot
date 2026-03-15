package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.discord.SlashCommand
import be.duncanc.discordmodbot.discord.messageTimeFormat
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
import net.dv8tion.jda.api.utils.SplitUtil
import org.springframework.stereotype.Component
import java.awt.Color
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Component
class WarnHistoryCommand(
    private val guildWarnPointsRepository: GuildWarnPointsRepository
) : ListenerAdapter(), SlashCommand {
    companion object {
        private const val COMMAND = "warnhistory"
        private const val DESCRIPTION = "Shows the warn history for a user."
        private const val OPTION_USER = "user"

        private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd-M-yyyy hh:mm:ss a O")
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

        val member = event.member
        val targetUserOption = event.getOption(OPTION_USER)

        if (targetUserOption != null && member != null && member.hasPermission(Permission.KICK_MEMBERS)) {
            val targetMember = targetUserOption.asMember
            if (targetMember != null) {
                showWarnHistory(event, guild, targetMember.user, true)
            } else {
                val targetUser = targetUserOption.asUser
                showWarnHistory(event, guild, targetUser, true)
            }
        } else if (member != null) {
            showWarnHistory(event, guild, member.user, false)
        } else {
            event.reply("This command requires a user.").setEphemeral(true).queue()
        }
    }

    private fun showWarnHistory(event: SlashCommandInteractionEvent, guild: Guild, user: User, moderator: Boolean) {
        val guildId = guild.idLong
        val userId = user.idLong

        if (!guildWarnPointsRepository.existsByGuildIdAndUserId(guildId, userId)) {
            event.reply(if (moderator) "The user has not received any points." else "You haven't received any points. Good job!")
                .setEphemeral(true).queue()
            return
        }

        val warnings = guildWarnPointsRepository.findAllByGuildIdAndUserIdAndExpireDateAfter(
            guildId,
            userId,
            OffsetDateTime.now()
        )

        if (warnings.isEmpty()) {
            event.reply(if (moderator) "The user has no active points." else "You have no active points. Good job!")
                .setEphemeral(true).queue()
            return
        }

        val embeds = mutableListOf<net.dv8tion.jda.api.entities.MessageEmbed>()
        val targetUser = user.jda.getUserById(userId)

        warnings.forEach { warning ->
            val embedBuilder = EmbedBuilder()
                .setColor(if (moderator) Color.ORANGE else Color.YELLOW)
                .setTitle("Warning ${if (moderator) "for ${targetUser?.name ?: "Unknown"}" else ""}")
                .addField("Points", warning.points.toString(), true)
                .addField("Moderator", guild.getMemberById(warning.creatorId)?.nicknameAndUsername ?: "Unknown", true)
                .addField("Created", warning.creationDate.format(messageTimeFormat), true)
                .addField("Reason", warning.reason, false)
                .addField("Expires", warning.expireDate.format(messageTimeFormat), true)

            embeds.add(embedBuilder.build())
        }

        if (moderator) {
            event.reply("The list of points the user collected has been sent in a private message.").setEphemeral(true)
                .queue()
            sendDmToUser(event.user, embeds, false)
        } else {
            event.reply("Your list of points has been sent in a private message. If you didn't receive any messages, make sure you have enabled DMs from server members.")
                .setEphemeral(true).queue()
            sendDmToUser(user, embeds, true)
        }
    }

    private fun sendDmToUser(user: User, embeds: List<net.dv8tion.jda.api.entities.MessageEmbed>, simple: Boolean) {
        user.openPrivateChannel().queue { privateChannel ->
            if (simple) {
                val message = StringBuilder("Your warning history:\n\n")
                embeds.forEach { embed ->
                    embed.fields.forEach { field ->
                        if (field.name == "Reason") {
                            message.append("\nReason: ${field.value}")
                        }
                    }
                }
                val messages = SplitUtil.split(
                    message.toString(),
                    net.dv8tion.jda.api.entities.Message.MAX_CONTENT_LENGTH,
                    SplitUtil.Strategy.NEWLINE
                )
                messages.forEach {
                    privateChannel.sendMessage(it).queue()
                }
            } else {
                embeds.forEach { embed ->
                    privateChannel.sendMessageEmbeds(embed).queue()
                }
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
                        "The user to check (leave empty to check yourself)"
                    ).setRequired(false)
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.KICK_MEMBERS))
        )
    }
}
