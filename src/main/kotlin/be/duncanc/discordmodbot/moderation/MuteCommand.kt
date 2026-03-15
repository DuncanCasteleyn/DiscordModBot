package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.discord.SlashCommand
import be.duncanc.discordmodbot.discord.nicknameAndUsername
import be.duncanc.discordmodbot.logging.GuildLogger
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import org.springframework.stereotype.Component
import java.awt.Color
import java.util.*

@Component
class MuteCommand(
    private val muteRole: MuteRole
) : ListenerAdapter(), SlashCommand {
    companion object {
        private const val COMMAND = "mute"
        private const val DESCRIPTION =
            "This command will put a user in the muted role and log the mute to the log channel."
        private const val OPTION_USER = "user"
        private const val OPTION_REASON = "reason"
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != COMMAND) return

        val member = event.member
        if (member == null) {
            event.reply("This command only works in a guild.").setEphemeral(true).queue()
            return
        }

        if (!member.hasPermission(Permission.MANAGE_ROLES)) {
            event.reply("You need manage roles permission to mute.").setEphemeral(true).queue()
            return
        }

        val targetMember = event.getOption(OPTION_USER)?.asMember
        if (targetMember == null) {
            event.reply("You need to mention a user that is still in the server.").setEphemeral(true).queue()
            return
        }

        if (!member.canInteract(targetMember)) {
            event.reply("You can't mute a user that you can't interact with.").setEphemeral(true).queue()
            return
        }

        val reason = event.getOption(OPTION_REASON)?.asString ?: "No reason provided"

        event.deferReply().queue { hook ->
            val guild = event.guild!!
            val muteRole = try {
                muteRole.getMuteRole(guild)
            } catch (e: IllegalStateException) {
                hook.editOriginal("Mute role is not configured for this server.").queue()
                return@queue
            }

            guild.addRoleToMember(targetMember, muteRole).queue({
                val guildLogger = event.jda.registeredListeners.firstOrNull { it is GuildLogger } as GuildLogger?
                if (guildLogger != null) {
                    val logEmbed = EmbedBuilder()
                        .setColor(Color.YELLOW)
                        .setTitle("User muted")
                        .addField("UUID", UUID.randomUUID().toString(), false)
                        .addField("User", targetMember.nicknameAndUsername, true)
                        .addField("Moderator", event.member!!.nicknameAndUsername, true)
                        .addField("Reason", reason, false)

                    guildLogger.log(
                        logEmbed,
                        targetMember.user,
                        guild,
                        null,
                        GuildLogger.LogTypeAction.MODERATOR
                    )
                }

                val userMuteWarning = EmbedBuilder()
                    .setColor(Color.YELLOW)
                    .setAuthor(event.member!!.nicknameAndUsername, null, event.user.effectiveAvatarUrl)
                    .setTitle("${guild.name}: You have been muted by ${event.member!!.nicknameAndUsername}")
                    .addField("Reason", reason, false)
                    .build()

                targetMember.user.openPrivateChannel().queue(
                    { privateChannelUserToMute ->
                        privateChannelUserToMute.sendMessageEmbeds(userMuteWarning).queue(
                            { onSuccessfulInformUser(hook, targetMember, userMuteWarning) }
                        ) { throwable -> onFailToInformUser(hook, targetMember, throwable, true) }
                    }
                ) { throwable -> onFailToInformUser(hook, targetMember, throwable, false) }

            }) { throwable ->
                val creatorMessage = MessageCreateBuilder()
                    .addContent("Failed muting ").addContent(targetMember.toString()).addContent(".\n")
                    .addContent(throwable.javaClass.simpleName).addContent(": ").addContent(throwable.message ?: "")
                    .build()
                hook.editOriginal(creatorMessage.content).queue()
            }
        }
    }

    private fun onSuccessfulInformUser(
        hook: InteractionHook,
        toMute: Member,
        userMuteWarning: MessageEmbed
    ) {
        hook.editOriginal(
            "Muted $toMute.\n\nThe following message was sent to the user:"
        ).setEmbeds(userMuteWarning).queue()
    }

    private fun onFailToInformUser(
        hook: InteractionHook,
        toMute: Member,
        throwable: Throwable,
        dmFailed: Boolean
    ) {
        val msg = if (dmFailed) {
            "Muted $toMute.\n\nWas unable to send a DM to the user, please inform the user manually.\nError: ${throwable.message}"
        } else {
            "Muted $toMute.\n\nCould not find or open a DM channel with the user, please inform the user manually.\nError: ${throwable.message}"
        }
        hook.editOriginal(msg).queue()
    }

    override fun getCommandsData(): List<SlashCommandData> {
        return listOf(
            Commands.slash(COMMAND, DESCRIPTION)
                .addOptions(
                    OptionData(OptionType.USER, OPTION_USER, "The user to mute").setRequired(true),
                    OptionData(OptionType.STRING, OPTION_REASON, "The reason for the mute").setRequired(true)
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_ROLES))
        )
    }
}
