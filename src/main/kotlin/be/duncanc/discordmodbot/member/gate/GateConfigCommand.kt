package be.duncanc.discordmodbot.member.gate

import be.duncanc.discordmodbot.discord.SlashCommand
import be.duncanc.discordmodbot.member.gate.persistence.WelcomeMessage
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.InteractionContextType
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.utils.SplitUtil
import org.springframework.stereotype.Component

@Component
class GateConfigCommand(
    private val memberGateService: MemberGateService,
    private val welcomeMessageService: WelcomeMessageService
) : ListenerAdapter(), SlashCommand {
    companion object {
        private const val COMMAND = "gateconfig"
        private const val DESCRIPTION = "Configure the member gate for this server."

        private const val SUBCOMMAND_SHOW = "show"
        private const val SUBCOMMAND_ADD_QUESTION = "add-question"
        private const val SUBCOMMAND_REMOVE_QUESTION = "remove-question"
        private const val SUBCOMMAND_ADD_WELCOME = "add-welcome"
        private const val SUBCOMMAND_REMOVE_WELCOME = "remove-welcome"
        private const val SUBCOMMAND_SET_WELCOME_CHANNEL = "set-welcome-channel"
        private const val SUBCOMMAND_SET_GATE_CHANNEL = "set-gate-channel"
        private const val SUBCOMMAND_SET_MEMBER_ROLE = "set-member-role"
        private const val SUBCOMMAND_SET_RULES_CHANNEL = "set-rules-channel"
        private const val SUBCOMMAND_RESET_GATE = "reset-gate"
        private const val SUBCOMMAND_RESET_WELCOME = "reset-welcome"
        private const val SUBCOMMAND_RESET_ALL = "reset-all"
        private const val SUBCOMMAND_SET_PURGE_HOURS = "set-purge-hours"
        private const val SUBCOMMAND_DISABLE_PURGE = "disable-purge"
        private const val SUBCOMMAND_SET_REMINDER_HOURS = "set-reminder-hours"
        private const val SUBCOMMAND_DISABLE_REMINDER = "disable-reminder"

        private const val OPTION_QUESTION = "question"
        private const val OPTION_IMAGE_URL = "imageurl"
        private const val OPTION_MESSAGE = "message"
        private const val OPTION_WELCOME = "welcome"
        private const val OPTION_CHANNEL = "channel"
        private const val OPTION_ROLE = "role"
        private const val OPTION_HOURS = "hours"

        private const val AUTOCOMPLETE_LIMIT = 25
        private const val SHOW_PREVIEW_LENGTH = 80
        private const val WELCOME_CHOICE_NAME_LIMIT = 100
        private const val QUESTION_MAX_LENGTH = 100
        private const val WELCOME_MESSAGE_MAX_LENGTH = 2048
        private const val SHOW_WRAP_LENGTH = 1500
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != COMMAND) {
            return
        }

        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            event.reply("This command only works in a guild.").setEphemeral(true).queue()
            return
        }

        if (!member.hasPermission(Permission.MANAGE_ROLES)) {
            event.reply("You need manage roles permission to use this command.").setEphemeral(true).queue()
            return
        }

        when (event.subcommandName) {
            SUBCOMMAND_SHOW -> showCurrentSettings(event, guild)
            SUBCOMMAND_ADD_QUESTION -> addQuestion(event, guild.idLong)
            SUBCOMMAND_REMOVE_QUESTION -> removeQuestion(event, guild.idLong)
            SUBCOMMAND_ADD_WELCOME -> addWelcomeMessage(event, guild.idLong)
            SUBCOMMAND_REMOVE_WELCOME -> removeWelcomeMessage(event, guild.idLong)
            SUBCOMMAND_SET_WELCOME_CHANNEL -> setWelcomeChannel(event, guild.idLong)
            SUBCOMMAND_SET_GATE_CHANNEL -> setGateChannel(event, guild.idLong)
            SUBCOMMAND_SET_MEMBER_ROLE -> setMemberRole(event, guild.idLong)
            SUBCOMMAND_SET_RULES_CHANNEL -> setRulesChannel(event, guild.idLong)
            SUBCOMMAND_RESET_GATE -> resetGate(event, guild.idLong)
            SUBCOMMAND_RESET_WELCOME -> resetWelcome(event, guild.idLong)
            SUBCOMMAND_RESET_ALL -> resetAll(event, guild.idLong)
            SUBCOMMAND_SET_PURGE_HOURS -> setPurgeHours(event, guild.idLong)
            SUBCOMMAND_DISABLE_PURGE -> disablePurge(event, guild.idLong)
            SUBCOMMAND_SET_REMINDER_HOURS -> setReminderHours(event, guild.idLong)
            SUBCOMMAND_DISABLE_REMINDER -> disableReminder(event, guild.idLong)
            else -> event.reply("Please choose a valid /gateconfig subcommand.").setEphemeral(true).queue()
        }
    }

    override fun onCommandAutoCompleteInteraction(event: CommandAutoCompleteInteractionEvent) {
        if (event.name != COMMAND || event.guild == null) {
            return
        }

        when {
            event.subcommandName == SUBCOMMAND_REMOVE_QUESTION && event.focusedOption.name == OPTION_QUESTION -> {
                val query = event.focusedOption.value
                val choices = memberGateService.getQuestions(event.guild!!.idLong)
                    .asSequence()
                    .sorted()
                    .filter { query.isBlank() || it.contains(query, ignoreCase = true) }
                    .take(AUTOCOMPLETE_LIMIT)
                    .map { Command.Choice(it, it) }
                    .toList()

                event.replyChoices(choices).queue()
            }

            event.subcommandName == SUBCOMMAND_REMOVE_WELCOME && event.focusedOption.name == OPTION_WELCOME -> {
                val query = event.focusedOption.value
                val choices = welcomeMessageService.getWelcomeMessages(event.guild!!.idLong)
                    .asSequence()
                    .sortedBy { it.id }
                    .filter {
                        query.isBlank() || buildWelcomeChoiceName(it).contains(query, ignoreCase = true) ||
                                it.id.toString().startsWith(query)
                    }
                    .take(AUTOCOMPLETE_LIMIT)
                    .map { Command.Choice(buildWelcomeChoiceName(it), it.id.toString()) }
                    .toList()

                event.replyChoices(choices).queue()
            }
        }
    }

    private fun showCurrentSettings(event: SlashCommandInteractionEvent, guild: Guild) {
        val guildId = guild.idLong
        val gateChannel = memberGateService.getGateChannel(guildId, guild.jda)?.asMention ?: "Not configured"
        val welcomeChannel = memberGateService.getWelcomeChannel(guildId, guild.jda)?.asMention ?: "Not configured"
        val rulesChannel = memberGateService.getRuleChannel(guildId, guild.jda)?.asMention ?: "Not configured"
        val memberRole = memberGateService.getMemberRole(guildId, guild.jda)?.asMention ?: "Not configured"
        val questions = memberGateService.getQuestions(guildId).toList().sorted()
        val welcomeMessages = welcomeMessageService.getWelcomeMessages(guildId).sortedBy { it.id }

        val message = buildString {
            appendLine("Member gate settings for ${guild.name}")
            appendLine()
            appendLine("Channels and role")
            appendLine("- Gate channel: $gateChannel")
            appendLine("- Welcome channel: $welcomeChannel")
            appendLine("- Rules channel: $rulesChannel")
            appendLine("- Member role: $memberRole")
            appendLine("- Purge time: ${formatHours(memberGateService.getPurgeTime(guildId))}")
            appendLine("- Reminder time: ${formatHours(memberGateService.getReminderTime(guildId))}")
            appendLine()
            appendLine("Questions configured: ${questions.size}")
            if (questions.isEmpty()) {
                appendLine("- None configured")
            } else {
                questions.forEachIndexed { index, question ->
                    appendLine("${index + 1}. $question")
                }
            }
            appendLine()
            appendLine("Welcome messages configured: ${welcomeMessages.size}")
            if (welcomeMessages.isEmpty()) {
                appendLine("- None configured")
            } else {
                welcomeMessages.forEach { welcomeMessage ->
                    appendLine("- #${welcomeMessage.id}")
                    appendWrappedValue(this, "  Image URL: ", welcomeMessage.imageUrl)
                    appendLine("  Message:")
                    appendWrappedLines(this, "    ", welcomeMessage.message)
                    appendLine()
                }
            }
        }

        replySplitEphemeral(event, message)
    }

    private fun addQuestion(event: SlashCommandInteractionEvent, guildId: Long) {
        val question = event.getOption(OPTION_QUESTION)?.asString
        if (question.isNullOrBlank()) {
            event.reply("Please provide the question to add.").setEphemeral(true).queue()
            return
        }

        memberGateService.addQuestion(guildId, question)
        event.reply("Question added.").setEphemeral(true).queue()
    }

    private fun removeQuestion(event: SlashCommandInteractionEvent, guildId: Long) {
        val question = event.getOption(OPTION_QUESTION)?.asString
        if (question.isNullOrBlank()) {
            event.reply("Please choose a question to remove.").setEphemeral(true).queue()
            return
        }

        val questions = memberGateService.getQuestions(guildId)
        if (question !in questions) {
            event.reply("That question was not found. Please choose one of the suggested questions.")
                .setEphemeral(true)
                .queue()
            return
        }

        memberGateService.removeQuestion(guildId, question)
        event.reply("Question removed.").setEphemeral(true).queue()
    }

    private fun addWelcomeMessage(event: SlashCommandInteractionEvent, guildId: Long) {
        val imageUrl = event.getOption(OPTION_IMAGE_URL)?.asString
        val message = event.getOption(OPTION_MESSAGE)?.asString
        if (imageUrl.isNullOrBlank() || message.isNullOrBlank()) {
            event.reply("Please provide both an image url and welcome message.").setEphemeral(true).queue()
            return
        }

        welcomeMessageService.addWelcomeMessage(
            WelcomeMessage(
                guildId = guildId,
                imageUrl = imageUrl,
                message = message
            )
        )
        event.reply("Welcome message added.").setEphemeral(true).queue()
    }

    private fun removeWelcomeMessage(event: SlashCommandInteractionEvent, guildId: Long) {
        val welcomeId = event.getOption(OPTION_WELCOME)?.asString?.toLongOrNull()
        if (welcomeId == null) {
            event.reply("Please choose a welcome message to remove.").setEphemeral(true).queue()
            return
        }

        val welcomeMessage = welcomeMessageService.getWelcomeMessages(guildId).firstOrNull { it.id == welcomeId }
        if (welcomeMessage == null) {
            event.reply("That welcome message was not found. Please choose one of the suggested entries.")
                .setEphemeral(true)
                .queue()
            return
        }

        welcomeMessageService.removeWelcomeMessage(welcomeMessage)
        event.reply("Welcome message removed.").setEphemeral(true).queue()
    }

    private fun setWelcomeChannel(event: SlashCommandInteractionEvent, guildId: Long) {
        val channel = getRequiredTextChannel(event) ?: return
        memberGateService.setWelcomeChannel(guildId, channel)
        event.reply("Welcome channel set to ${channel.asMention}.").setEphemeral(true).queue()
    }

    private fun setGateChannel(event: SlashCommandInteractionEvent, guildId: Long) {
        val channel = getRequiredTextChannel(event) ?: return
        memberGateService.setGateChannel(guildId, channel)
        event.reply("Gate channel set to ${channel.asMention}.").setEphemeral(true).queue()
    }

    private fun setRulesChannel(event: SlashCommandInteractionEvent, guildId: Long) {
        val channel = getRequiredTextChannel(event) ?: return
        memberGateService.setRulesChannel(guildId, channel)
        event.reply("Rules channel set to ${channel.asMention}.").setEphemeral(true).queue()
    }

    private fun setMemberRole(event: SlashCommandInteractionEvent, guildId: Long) {
        val role = event.getOption(OPTION_ROLE)?.asRole
        if (role == null) {
            event.reply("Please choose the member role to set.").setEphemeral(true).queue()
            return
        }

        memberGateService.setMemberRole(guildId, role)
        event.reply("Member role set to ${role.asMention}.").setEphemeral(true).queue()
    }

    private fun resetGate(event: SlashCommandInteractionEvent, guildId: Long) {
        memberGateService.resetGateSettings(guildId)
        event.reply("Member gate settings wiped and disabled.").setEphemeral(true).queue()
    }

    private fun resetWelcome(event: SlashCommandInteractionEvent, guildId: Long) {
        memberGateService.resetWelcomeSettings(guildId)
        welcomeMessageService.removeAllWelcomeMessages(guildId)
        event.reply("Welcome settings wiped and disabled.").setEphemeral(true).queue()
    }

    private fun resetAll(event: SlashCommandInteractionEvent, guildId: Long) {
        memberGateService.resetAllSettings(guildId)
        welcomeMessageService.removeAllWelcomeMessages(guildId)
        event.reply("Member gate module settings wiped and disabled.").setEphemeral(true).queue()
    }

    private fun setPurgeHours(event: SlashCommandInteractionEvent, guildId: Long) {
        val hours = getRequiredHours(event) ?: return
        memberGateService.setPurgeTime(guildId, hours)
        event.reply("Purge time set to $hours hour(s).").setEphemeral(true).queue()
    }

    private fun disablePurge(event: SlashCommandInteractionEvent, guildId: Long) {
        memberGateService.setPurgeTime(guildId, null)
        event.reply("Purge disabled.").setEphemeral(true).queue()
    }

    private fun setReminderHours(event: SlashCommandInteractionEvent, guildId: Long) {
        val hours = getRequiredHours(event) ?: return
        memberGateService.setReminderTime(guildId, hours)
        event.reply("Reminder time set to $hours hour(s).").setEphemeral(true).queue()
    }

    private fun disableReminder(event: SlashCommandInteractionEvent, guildId: Long) {
        memberGateService.setReminderTime(guildId, null)
        event.reply("Reminder disabled.").setEphemeral(true).queue()
    }

    private fun getRequiredTextChannel(event: SlashCommandInteractionEvent): TextChannel? {
        val channel = event.getOption(OPTION_CHANNEL)?.asChannel
        if (channel == null || channel.type != ChannelType.TEXT) {
            event.reply("Please choose a text channel.").setEphemeral(true).queue()
            return null
        }

        return channel.asTextChannel()
    }

    private fun getRequiredHours(event: SlashCommandInteractionEvent): Long? {
        val hours = event.getOption(OPTION_HOURS)?.asLong
        if (hours == null || hours < 1L) {
            event.reply("Please provide a valid amount of hours (minimum 1).").setEphemeral(true).queue()
            return null
        }

        return hours
    }

    private fun buildWelcomeChoiceName(welcomeMessage: WelcomeMessage): String {
        val prefix = "#${welcomeMessage.id}: "
        return prefix + shorten(welcomeMessage.message, WELCOME_CHOICE_NAME_LIMIT - prefix.length)
    }

    private fun shorten(value: String, maxLength: Int): String {
        if (value.length <= maxLength) {
            return value
        }

        return value.take(maxLength - 3) + "..."
    }

    private fun formatHours(hours: Long?): String {
        return hours?.let { "$it hour(s)" } ?: "Disabled"
    }

    private fun appendWrappedValue(builder: StringBuilder, prefix: String, value: String) {
        chunkString(value, SHOW_WRAP_LENGTH - prefix.length).forEachIndexed { index, chunk ->
            if (index == 0) {
                builder.appendLine(prefix + chunk)
            } else {
                builder.appendLine(" ".repeat(prefix.length) + chunk)
            }
        }
    }

    private fun appendWrappedLines(builder: StringBuilder, prefix: String, value: String) {
        value.lineSequence().forEach { line ->
            if (line.isEmpty()) {
                builder.appendLine(prefix)
            } else {
                chunkString(line, SHOW_WRAP_LENGTH - prefix.length).forEach { chunk ->
                    builder.appendLine(prefix + chunk)
                }
            }
        }
    }

    private fun chunkString(value: String, chunkLength: Int): List<String> {
        if (value.isEmpty()) {
            return listOf("")
        }

        return value.chunked(chunkLength.coerceAtLeast(1))
    }

    private fun replySplitEphemeral(event: SlashCommandInteractionEvent, content: String) {
        val messages = SplitUtil.split(content, Message.MAX_CONTENT_LENGTH, SplitUtil.Strategy.NEWLINE).toMutableList()
        event.deferReply(true).queue { hook ->
            val responses = messages.ifEmpty { mutableListOf("No member gate settings found.") }
            responses.forEachIndexed { index, message ->
                val action = hook.sendMessage(message)
                if (index > 0) {
                    action.setEphemeral(true)
                }
                action.queue()
            }
        }
    }

    override fun getCommandsData(): List<SlashCommandData> {
        return listOf(
            Commands.slash(COMMAND, DESCRIPTION)
                .setContexts(InteractionContextType.GUILD)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_ROLES))
                .addSubcommands(
                    SubcommandData(SUBCOMMAND_SHOW, "Show the current member gate settings"),
                    SubcommandData(SUBCOMMAND_ADD_QUESTION, "Add a member gate question")
                        .addOptions(
                            OptionData(OptionType.STRING, OPTION_QUESTION, "The question to add")
                                .setRequired(true)
                                .setMaxLength(QUESTION_MAX_LENGTH)
                        ),
                    SubcommandData(SUBCOMMAND_REMOVE_QUESTION, "Remove a member gate question")
                        .addOption(OptionType.STRING, OPTION_QUESTION, "The question to remove", true, true),
                    SubcommandData(SUBCOMMAND_ADD_WELCOME, "Add a welcome message")
                        .addOptions(
                            OptionData(OptionType.STRING, OPTION_IMAGE_URL, "Image url for the welcome embed")
                                .setRequired(true),
                            OptionData(OptionType.STRING, OPTION_MESSAGE, "Welcome message content")
                                .setRequired(true)
                                .setMaxLength(WELCOME_MESSAGE_MAX_LENGTH)
                        ),
                    SubcommandData(SUBCOMMAND_REMOVE_WELCOME, "Remove a welcome message")
                        .addOption(OptionType.STRING, OPTION_WELCOME, "The welcome message to remove", true, true),
                    SubcommandData(SUBCOMMAND_SET_WELCOME_CHANNEL, "Set the welcome channel")
                        .addOptions(textChannelOption("The channel for welcome messages")),
                    SubcommandData(SUBCOMMAND_SET_GATE_CHANNEL, "Set the member gate channel")
                        .addOptions(textChannelOption("The channel for member gate prompts")),
                    SubcommandData(SUBCOMMAND_SET_MEMBER_ROLE, "Set the member role")
                        .addOption(OptionType.ROLE, OPTION_ROLE, "The role granted after approval", true),
                    SubcommandData(SUBCOMMAND_SET_RULES_CHANNEL, "Set the rules channel")
                        .addOptions(textChannelOption("The rules channel")),
                    SubcommandData(SUBCOMMAND_RESET_GATE, "Reset gate questions, role, and channels"),
                    SubcommandData(SUBCOMMAND_RESET_WELCOME, "Reset welcome settings and messages"),
                    SubcommandData(SUBCOMMAND_RESET_ALL, "Reset all member gate settings and welcome messages"),
                    SubcommandData(SUBCOMMAND_SET_PURGE_HOURS, "Set the purge time in hours")
                        .addOption(
                            OptionType.INTEGER,
                            OPTION_HOURS,
                            "Hours before unapproved members are purged",
                            true
                        ),
                    SubcommandData(SUBCOMMAND_DISABLE_PURGE, "Disable automatic purging"),
                    SubcommandData(SUBCOMMAND_SET_REMINDER_HOURS, "Set the entry reminder time in hours")
                        .addOption(OptionType.INTEGER, OPTION_HOURS, "Hours before a reminder is sent", true),
                    SubcommandData(SUBCOMMAND_DISABLE_REMINDER, "Disable entry reminders")
                )
        )
    }

    private fun textChannelOption(description: String): OptionData {
        return OptionData(OptionType.CHANNEL, OPTION_CHANNEL, description, true)
            .setChannelTypes(ChannelType.TEXT)
    }
}
