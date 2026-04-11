package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.discord.SlashCommand
import be.duncanc.discordmodbot.discord.nicknameAndUsername
import be.duncanc.discordmodbot.logging.GuildLogger
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.selections.StringSelectMenu
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import org.springframework.stereotype.Component
import java.awt.Color
import java.util.*

private const val COMMAND = "revokewarnpoints"
private const val OPTION_USER = "user"
private const val OPTION_WARN_POINT_ID = "warn_point_id"
private const val OPTION_REASON = "reason"
private const val COMPONENT_ID = "revokewarnpoints-select"

@Component
class RevokeWarnPointsCommand(
    private val guildWarnPointsService: GuildWarnPointsService
) : ListenerAdapter(), SlashCommand {

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != COMMAND) return

        val moderator = event.member
        if (moderator == null) {
            event.reply("This command only works in a guild.").setEphemeral(true).queue()
            return
        }

        if (!moderator.hasPermission(Permission.KICK_MEMBERS)) {
            event.reply("You need kick members permission to revoke warn points.").setEphemeral(true).queue()
            return
        }

        val targetUserId = event.getOption(OPTION_USER)?.asLong
        if (targetUserId == null) {
            event.reply("You need to mention a user.").setEphemeral(true).queue()
            return
        }

        val targetMember = event.getOption(OPTION_USER)?.asMember
        val guild = event.guild ?: return
        if (targetMember != null && !moderator.canInteract(targetMember)) {
            event.reply("You can't revoke warn points from a user that you can't interact with.").setEphemeral(true)
                .queue()
            return
        }

        val guildId = guild.idLong
        val reason = event.getOption(OPTION_REASON)?.asString ?: "No reason provided"

        val warnPointIdStr = event.getOption(OPTION_WARN_POINT_ID)?.asString

        if (warnPointIdStr != null) {
            val warnPointId = try {
                UUID.fromString(warnPointIdStr)
            } catch (e: IllegalArgumentException) {
                event.reply("Invalid warn point ID format. Please provide a valid UUID.").setEphemeral(true).queue()
                return
            }

            val warnings = guildWarnPointsService.getGuildWarningsFromUser(guildId, targetUserId)
            val targetWarning = warnings.find { it.id == warnPointId }

            if (targetWarning == null) {
                event.reply("No warn point found with ID `$warnPointIdStr` for this user.").setEphemeral(true).queue()
                return
            }

            guildWarnPointsService.revokePoint(warnPointId)
            logRevoke(event.jda, guild, targetWarning.points, reason, moderator)

            event.reply("Revoked ${targetWarning.points} warn point(s) from <@$targetUserId>. Reason: $reason")
                .setEphemeral(true).queue()
        } else {
            val warnings = guildWarnPointsService.getActiveWarnings(guildId, targetUserId)

            if (warnings.isEmpty()) {
                event.reply("This user has no active warn points to revoke.").setEphemeral(true).queue()
                return
            }

            val selectMenu = StringSelectMenu.create("$COMPONENT_ID-${moderator.idLong}")
                .setPlaceholder("Select a warn point to revoke")

            warnings.forEach { warning ->
                val label = "${warning.points} points - ${warning.reason.take(50)}"
                selectMenu.addOption(label, warning.id.toString())
            }

            val message = MessageCreateBuilder()
                .setContent("Select a warn point to revoke from <@$targetUserId>:")
                .addComponents(ActionRow.of(selectMenu.build()))
                .build()

            event.reply(message).setEphemeral(true).queue()
        }
    }

    override fun onStringSelectInteraction(event: StringSelectInteractionEvent) {
        val componentId = event.componentId

        if (!componentId.startsWith(COMPONENT_ID)) return

        val member = event.member
        if (member == null) {
            event.reply("This command only works in a guild.").setEphemeral(true).queue()
            return
        }

        if (!member.hasPermission(Permission.KICK_MEMBERS)) {
            event.reply("You need kick members permission to revoke warn points.").setEphemeral(true).queue()
            return
        }

        val invokerId = componentId.removePrefix("$COMPONENT_ID-").toLongOrNull()
        if (invokerId != event.user.idLong) {
            event.reply("You cannot revoke warn points initiated by another moderator.").setEphemeral(true).queue()
            return
        }

        val guild = event.guild ?: return
        val warnPointIdStr = event.values.firstOrNull() ?: return

        val warnPointId = try {
            UUID.fromString(warnPointIdStr)
        } catch (e: IllegalArgumentException) {
            event.reply("Invalid warn point ID.").setEphemeral(true).queue()
            return
        }

        event.deferReply(true).queue { hook ->

            val targetWarning = guildWarnPointsService.getWarningById(warnPointId)

            if (targetWarning == null) {
                hook.sendMessage("Warn point not found. It may have already been revoked.")
                    .setEphemeral(true)
                    .queue()

                return@queue
            }


            guildWarnPointsService.revokePoint(warnPointId)
            logRevoke(event.jda, guild, targetWarning.points, targetWarning.reason, event.member)

            hook.sendMessage("Revoked ${targetWarning.points} warn point(s) from user ID ${targetWarning.userId}: Reason: ${targetWarning.reason}")
                .setEphemeral(true)
                .queue()
        }

    }

    private fun logRevoke(
        jda: net.dv8tion.jda.api.JDA,
        guild: net.dv8tion.jda.api.entities.Guild,
        points: Int,
        reason: String,
        moderator: net.dv8tion.jda.api.entities.Member?
    ) {
        val guildLogger = jda.registeredListeners.firstOrNull { it is GuildLogger } as GuildLogger?
        if (guildLogger != null) {
            val logEmbed = EmbedBuilder()
                .setColor(Color.YELLOW)
                .setTitle("Warn point revoked")
                .addField("UUID", UUID.randomUUID().toString(), false)
                .addField("Moderator", moderator?.nicknameAndUsername ?: "Unknown", true)
                .addField("Points revoked", points.toString(), false)
                .addField("Reason", reason, false)

            guildLogger.log(
                logEmbed,
                null,
                guild,
                null,
                GuildLogger.LogTypeAction.MODERATOR
            )
        }
    }

    override fun getCommandsData(): List<SlashCommandData> {
        return listOf(
            Commands.slash(COMMAND, "Revoke warn points from a user")
                .addOptions(
                    OptionData(OptionType.USER, OPTION_USER, "The user to revoke points from").setRequired(true),
                    OptionData(OptionType.STRING, OPTION_REASON, "Reason for revoking points").setRequired(true),
                    OptionData(
                        OptionType.STRING,
                        OPTION_WARN_POINT_ID,
                        "Specific warn point ID to revoke (optional - leave empty to see selection menu)"
                    ).setRequired(false)
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.KICK_MEMBERS))
        )
    }
}
