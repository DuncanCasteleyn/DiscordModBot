package be.duncanc.discordmodbot.roles

import be.duncanc.discordmodbot.discord.SlashCommand
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Message
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
class RoleConfigCommand(
    private val iAmRolesService: IAmRolesService
) : ListenerAdapter(), SlashCommand {
    companion object {
        private const val COMMAND = "roleconfig"
        private const val DESCRIPTION = "Configure self-assignable role categories."
        private const val OPTION_CATEGORY = "category"
        private const val OPTION_NAME = "name"
        private const val OPTION_LIMIT = "limit"
        private const val OPTION_ROLE = "role"
        private const val AUTOCOMPLETE_LIMIT = 25

        private const val SUBCOMMAND_SHOW = "show"
        private const val SUBCOMMAND_ADD_CATEGORY = "add-category"
        private const val SUBCOMMAND_REMOVE_CATEGORY = "remove-category"
        private const val SUBCOMMAND_RENAME_CATEGORY = "rename-category"
        private const val SUBCOMMAND_SET_LIMIT = "set-limit"
        private const val SUBCOMMAND_ADD_ROLE = "add-role"
        private const val SUBCOMMAND_REMOVE_ROLE = "remove-role"
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

        try {
            when (event.subcommandName) {
                null, SUBCOMMAND_SHOW -> showCurrentSettings(event, guild)
                SUBCOMMAND_ADD_CATEGORY -> addCategory(event, guild.idLong)
                SUBCOMMAND_REMOVE_CATEGORY -> removeCategory(event, guild.idLong)
                SUBCOMMAND_RENAME_CATEGORY -> renameCategory(event, guild.idLong)
                SUBCOMMAND_SET_LIMIT -> setLimit(event, guild.idLong)
                SUBCOMMAND_ADD_ROLE -> addRole(event, guild.idLong)
                SUBCOMMAND_REMOVE_ROLE -> removeRole(event, guild.idLong)
                else -> event.reply("Please choose a valid /roleconfig subcommand.").setEphemeral(true).queue()
            }
        } catch (e: IllegalArgumentException) {
            event.reply(e.message ?: "Unable to update role settings.").setEphemeral(true).queue()
        }
    }

    override fun onCommandAutoCompleteInteraction(event: CommandAutoCompleteInteractionEvent) {
        if (event.name != COMMAND || event.guild == null || event.focusedOption.name != OPTION_CATEGORY) {
            return
        }

        val query = event.focusedOption.value
        val choices = iAmRolesService.getSortedCategoriesForGuild(event.guild!!.idLong)
            .asSequence()
            .filter { query.isBlank() || it.categoryName.contains(query, ignoreCase = true) }
            .take(AUTOCOMPLETE_LIMIT)
            .mapNotNull { category ->
                val categoryId = category.categoryId ?: return@mapNotNull null
                Command.Choice(category.categoryName, categoryId.toString())
            }
            .toList()

        event.replyChoices(choices).queue()
    }

    override fun getCommandsData(): List<SlashCommandData> {
        return listOf(
            Commands.slash(COMMAND, DESCRIPTION)
                .setContexts(InteractionContextType.GUILD)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_ROLES))
                .addSubcommands(
                    SubcommandData(SUBCOMMAND_SHOW, "Show current self-assignable role categories"),
                    SubcommandData(SUBCOMMAND_ADD_CATEGORY, "Add a new self-assignable role category")
                        .addOptions(
                            OptionData(OptionType.STRING, OPTION_NAME, "Category name", true),
                            OptionData(
                                OptionType.INTEGER,
                                OPTION_LIMIT,
                                "Maximum roles allowed in this category. Use 0 for unlimited.",
                                true
                            ).setMinValue(0)
                        ),
                    SubcommandData(SUBCOMMAND_REMOVE_CATEGORY, "Remove a role category")
                        .addOptions(categoryOption("Category to remove")),
                    SubcommandData(SUBCOMMAND_RENAME_CATEGORY, "Rename a role category")
                        .addOptions(
                            categoryOption("Category to rename"),
                            OptionData(OptionType.STRING, OPTION_NAME, "New category name", true)
                        ),
                    SubcommandData(SUBCOMMAND_SET_LIMIT, "Set the role limit for a category")
                        .addOptions(
                            categoryOption("Category to update"),
                            OptionData(
                                OptionType.INTEGER,
                                OPTION_LIMIT,
                                "Maximum roles allowed. Use 0 for unlimited.",
                                true
                            )
                                .setMinValue(0)
                        ),
                    SubcommandData(SUBCOMMAND_ADD_ROLE, "Add a role to a category")
                        .addOptions(
                            categoryOption("Category to update"),
                            OptionData(OptionType.ROLE, OPTION_ROLE, "Role to add", true)
                        ),
                    SubcommandData(SUBCOMMAND_REMOVE_ROLE, "Remove a role from a category")
                        .addOptions(
                            categoryOption("Category to update"),
                            OptionData(OptionType.ROLE, OPTION_ROLE, "Role to remove", true)
                        )
                )
        )
    }

    private fun showCurrentSettings(event: SlashCommandInteractionEvent, guild: Guild) {
        val categories = iAmRolesService.getSortedCategoriesForGuild(guild.idLong)
        val content = buildString {
            appendLine("Self-assignable role categories for ${guild.name}")
            if (categories.isEmpty()) {
                appendLine()
                appendLine("No categories configured.")
                return@buildString
            }

            categories.forEach { category ->
                appendLine()
                appendLine("${category.categoryName} (limit: ${formatLimit(category.allowedRoles)})")
                val roles = category.roles
                    .map { roleId -> guild.getRoleById(roleId)?.name ?: "Missing role (ID: $roleId)" }
                    .sorted()
                if (roles.isEmpty()) {
                    appendLine("- No roles configured")
                } else {
                    roles.forEach { appendLine("- $it") }
                }
            }
        }

        replySplitEphemeral(event, content)
    }

    private fun addCategory(event: SlashCommandInteractionEvent, guildId: Long) {
        val name = getNameOption(event)?.trim()
        val limit = getLimitOption(event)
        if (name.isNullOrBlank() || limit == null || limit < 0) {
            event.reply("Please provide a category name and a valid limit.").setEphemeral(true).queue()
            return
        }

        iAmRolesService.addNewCategory(guildId, name, limit)
        event.reply("Category $name added with limit ${formatLimit(limit)}.").setEphemeral(true).queue()
    }

    private fun removeCategory(event: SlashCommandInteractionEvent, guildId: Long) {
        val category = getRequiredCategory(guildId, event) ?: return
        iAmRolesService.removeCategory(guildId, category.categoryId!!)
        event.reply("Category ${category.categoryName} removed.").setEphemeral(true).queue()
    }

    private fun renameCategory(event: SlashCommandInteractionEvent, guildId: Long) {
        val category = getRequiredCategory(guildId, event) ?: return
        val newName = getNameOption(event)?.trim()
        if (newName.isNullOrBlank()) {
            event.reply("Please provide a new category name.").setEphemeral(true).queue()
            return
        }

        iAmRolesService.changeCategoryName(guildId, category.categoryId!!, newName)
        event.reply("Category renamed to $newName.").setEphemeral(true).queue()
    }

    private fun setLimit(event: SlashCommandInteractionEvent, guildId: Long) {
        val category = getRequiredCategory(guildId, event) ?: return
        val limit = getLimitOption(event)
        if (limit == null || limit < 0) {
            event.reply("Please provide a valid limit.").setEphemeral(true).queue()
            return
        }

        iAmRolesService.changeAllowedRoles(guildId, category.categoryId!!, limit)
        event.reply("Category ${category.categoryName} now allows ${formatLimit(limit)}.").setEphemeral(true).queue()
    }

    private fun addRole(event: SlashCommandInteractionEvent, guildId: Long) {
        val category = getRequiredCategory(guildId, event) ?: return
        val role = getRoleOption(event)
        if (role == null) {
            event.reply("Please choose a role to add.").setEphemeral(true).queue()
            return
        }

        iAmRolesService.addRoleToCategory(guildId, category.categoryId!!, role.idLong)
        event.reply("Added ${role.asMention} to ${category.categoryName}.").setEphemeral(true).queue()
    }

    private fun removeRole(event: SlashCommandInteractionEvent, guildId: Long) {
        val category = getRequiredCategory(guildId, event) ?: return
        val role = getRoleOption(event)
        if (role == null) {
            event.reply("Please choose a role to remove.").setEphemeral(true).queue()
            return
        }

        iAmRolesService.removeRoleFromCategory(guildId, category.categoryId!!, role.idLong)
        event.reply("Removed ${role.asMention} from ${category.categoryName}.").setEphemeral(true).queue()
    }

    private fun getRequiredCategory(guildId: Long, event: SlashCommandInteractionEvent) = try {
        val categoryId = getCategoryId(event)
        if (categoryId == null) {
            event.reply("Please choose one of the suggested categories.").setEphemeral(true).queue()
            null
        } else {
            iAmRolesService.getCategory(guildId, categoryId)
        }
    } catch (e: IllegalArgumentException) {
        event.reply(e.message ?: "Please choose one of the suggested categories.").setEphemeral(true).queue()
        null
    }

    private fun categoryOption(description: String): OptionData {
        return OptionData(OptionType.STRING, OPTION_CATEGORY, description, true, true)
    }

    internal fun getCategoryId(event: SlashCommandInteractionEvent): Long? {
        return event.getOption(OPTION_CATEGORY)?.asString?.toLongOrNull()
    }

    internal fun getNameOption(event: SlashCommandInteractionEvent): String? {
        return event.getOption(OPTION_NAME)?.asString
    }

    internal fun getLimitOption(event: SlashCommandInteractionEvent): Int? {
        return event.getOption(OPTION_LIMIT)?.asInt
    }

    internal fun getRoleOption(event: SlashCommandInteractionEvent): net.dv8tion.jda.api.entities.Role? {
        return event.getOption(OPTION_ROLE)?.asRole
    }

    private fun replySplitEphemeral(event: SlashCommandInteractionEvent, content: String) {
        val messages = SplitUtil.split(content, Message.MAX_CONTENT_LENGTH, SplitUtil.Strategy.NEWLINE).toMutableList()
        val responses = messages.ifEmpty { mutableListOf("No role categories found.") }
        event.deferReply(true).queue { hook ->
            responses.forEachIndexed { index, message ->
                val action = hook.sendMessage(message)
                if (index > 0) {
                    action.setEphemeral(true)
                }
                action.queue()
            }
        }
    }

    private fun formatLimit(limit: Int): String {
        return if (limit == 0) "unlimited roles" else "$limit role(s)"
    }
}
