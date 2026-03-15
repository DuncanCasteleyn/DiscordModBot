package be.duncanc.discordmodbot.utility

import be.duncanc.discordmodbot.discord.SlashCommand
import be.duncanc.discordmodbot.discord.nicknameAndUsername
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.springframework.stereotype.Component
import java.time.format.DateTimeFormatter

@Component
class UserInfoCommand : ListenerAdapter(), SlashCommand {
    companion object {
        private const val COMMAND = "userinfo"
        private const val DESCRIPTION = "Prints out user information of the user given as argument"
        private const val OPTION_USER = "user"

        private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd-M-yyyy hh:mm:ss a O")
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != COMMAND) return

        if (event.guild == null) {
            event.reply("This command only works in a guild text channel.").setEphemeral(true).queue()
            return
        }

        val member = event.getOption(OPTION_USER)?.asMember
        if (member == null) {
            event.reply("Please mention the user you want to get information about.").setEphemeral(true).queue()
            return
        }

        val userDateMessage = EmbedBuilder()
            .setAuthor(member.nicknameAndUsername, null, member.user.effectiveAvatarUrl)
            .setThumbnail(member.user.effectiveAvatarUrl)
            .setTitle("Guild: " + member.guild.name, null)
            .addField("User id", member.user.id, false)
            .addField("Discriminator", member.user.discriminator, false)
            .addField("Online status", member.onlineStatus.name, false)
            .addField("In voice channel?", member.voiceState?.inAudioChannel().toString(), true)
            .addField("Guild owner?", member.isOwner.toString(), true)
            .addField("is a bot?", member.user.isBot.toString(), true)
            .addField("Permissions", member.permissions.toString(), false)
            .addField("Roles", member.roles.toString(), false)
            .addField("Guild join date", member.timeJoined.format(DATE_TIME_FORMATTER), true)
            .addField(
                "Account creation date",
                member.user.timeCreated.format(DATE_TIME_FORMATTER),
                true
            )
            .build()

        event.replyEmbeds(userDateMessage).setEphemeral(true).queue()
    }

    override fun getCommandsData(): List<SlashCommandData> {
        return listOf(
            Commands.slash(COMMAND, DESCRIPTION)
                .addOptions(
                    OptionData(OptionType.USER, OPTION_USER, "The user to get information about")
                        .setRequired(true)
                )
        )
    }
}
