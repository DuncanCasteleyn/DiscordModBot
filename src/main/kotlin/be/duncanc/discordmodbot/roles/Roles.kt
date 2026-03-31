package be.duncanc.discordmodbot.roles

import be.duncanc.discordmodbot.discord.SlashCommand
import be.duncanc.discordmodbot.roles.persistence.IAmRolesCategory
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.components.selections.StringSelectMenu
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.InteractionContextType
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction
import org.springframework.stereotype.Component

@Component
class Roles(
    private val iAmRolesService: IAmRolesService
) : ListenerAdapter(), SlashCommand {
    companion object {
        private const val COMMAND = "role"
        private const val DESCRIPTION = "Assign or remove self-assignable roles."
        private const val SUBCOMMAND_ASSIGN = "assign"
        private const val SUBCOMMAND_REMOVE = "remove"
        private const val OPTION_CATEGORY = "category"
        private const val AUTOCOMPLETE_LIMIT = 25
        private const val COMPONENT_PREFIX = "role"
        private const val COMPONENT_TYPE_MENU = "menu"
        private const val COMPONENT_TYPE_PAGE = "page"
        private const val OUT_OF_DATE_MESSAGE = "This role menu is out of date. Run the command again."
        private const val ROLE_UPDATE_FAILURE_MESSAGE = "I couldn't update your roles right now. Please try again."
    }

    override fun getCommandsData(): List<SlashCommandData> {
        return listOf(
            Commands.slash(COMMAND, DESCRIPTION)
                .addSubcommands(
                    SubcommandData(SUBCOMMAND_ASSIGN, "Assign yourself self-assignable roles")
                        .addOptions(categoryOption("Category to assign roles from")),
                    SubcommandData(SUBCOMMAND_REMOVE, "Remove self-assignable roles from yourself")
                        .addOptions(categoryOption("Category to remove roles from"))
                )
                .setContexts(InteractionContextType.GUILD)
        )
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

        if (!guild.selfMember.hasPermission(Permission.MANAGE_ROLES)) {
            event.reply("I need manage roles permission to update self-assignable roles.").setEphemeral(true).queue()
            return
        }

        if (!guild.selfMember.canInteract(member)) {
            event.reply("I can't manage your roles because your highest role is above mine.").setEphemeral(true).queue()
            return
        }

        val action = RoleAction.fromSubcommand(event.subcommandName)
        if (action == null) {
            event.reply("Please choose a valid /role subcommand.").setEphemeral(true).queue()
            return
        }

        val categoryId = getCategoryId(event)
        if (categoryId == null) {
            event.reply("Please choose one of the suggested categories.").setEphemeral(true).queue()
            return
        }

        val category = getRequiredCategory(guild.idLong, categoryId)
        if (category == null) {
            event.reply(OUT_OF_DATE_MESSAGE).setEphemeral(true).queue()
            return
        }

        val menuMessage = buildRoleMenuMessage(guild, member, action, category, 0)
        if (menuMessage.errorMessage != null) {
            event.reply(menuMessage.errorMessage).setEphemeral(true).queue()
            return
        }

        sendRoleMenu(event, menuMessage)
    }

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        val state = parseComponentState(event.componentId, COMPONENT_TYPE_PAGE) ?: return
        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            event.reply("This action only works in a guild.").setEphemeral(true).queue()
            return
        }

        if (state.userId != event.user.idLong) {
            event.reply("This role menu belongs to someone else. Run the command yourself.").setEphemeral(true).queue()
            return
        }

        val category = try {
            iAmRolesService.getCategory(guild.idLong, state.categoryId)
        } catch (_: IllegalArgumentException) {
            event.reply("This role menu is out of date. Run the command again.").setEphemeral(true).queue()
            return
        }

        val menuMessage = buildRoleMenuMessage(guild, member, state.action, category, state.page)
        if (menuMessage.errorMessage != null) {
            event.reply(menuMessage.errorMessage).setEphemeral(true).queue()
            return
        }

        sendRoleMenu(event, menuMessage)
    }

    override fun onStringSelectInteraction(event: StringSelectInteractionEvent) {
        val state = parseComponentState(event.componentId, COMPONENT_TYPE_MENU) ?: return
        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            event.reply("This action only works in a guild.").setEphemeral(true).queue()
            return
        }

        if (state.userId != event.user.idLong) {
            event.reply("This role menu belongs to someone else. Run the command yourself.").setEphemeral(true).queue()
            return
        }

        event.deferReply(true).queue { hook ->
            handleRoleSelection(event, guild, member, state, hook)
        }
    }

    private fun handleRoleSelection(
        event: StringSelectInteractionEvent,
        guild: Guild,
        member: Member,
        state: RoleComponentState,
        hook: InteractionHook
    ) {
        if (!guild.selfMember.hasPermission(Permission.MANAGE_ROLES)) {
            hook.editOriginal("I need manage roles permission to update self-assignable roles.").queue()
            return
        }

        if (!guild.selfMember.canInteract(member)) {
            hook.editOriginal("I can't manage your roles because your highest role is above mine.").queue()
            return
        }

        val category = try {
            iAmRolesService.getCategory(guild.idLong, state.categoryId)
        } catch (_: IllegalArgumentException) {
            hook.editOriginal(OUT_OF_DATE_MESSAGE).queue()
            return
        }

        val roleSelection = buildRoleSelection(guild, member, state.action, category)
        if (roleSelection.errorMessage != null) {
            hook.editOriginal(roleSelection.errorMessage).queue()
            return
        }
        require(roleSelection.maxSelectable > 0) {
            "Invariant violation: maxSelectable should be > 0 when errorMessage is null"
        }

        val pageRoles = roleSelection.rolePages.getOrNull(state.page)
        if (pageRoles == null) {
            hook.editOriginal(OUT_OF_DATE_MESSAGE).queue()
            return
        }

        val selectedRoleIds = event.values.mapNotNull { it.toLongOrNull() }
        val allowedRoleIds = pageRoles.map { it.id }.toSet()
        if (selectedRoleIds.isEmpty() || selectedRoleIds.any { it.toString() !in allowedRoleIds }) {
            hook.editOriginal(OUT_OF_DATE_MESSAGE).queue()
            return
        }

        val selectedRoles = selectedRoleIds.mapNotNull(guild::getRoleById)
        if (selectedRoles.size != selectedRoleIds.size || selectedRoles.any { !guild.selfMember.canInteract(it) }) {
            hook.editOriginal("I can't manage one or more of the selected roles.").queue()
            return
        }

        val remainingAllowance = roleSelection.maxSelectable
        if (selectedRoles.size > remainingAllowance) {
            hook.editOriginal("You selected more roles than allowed.").queue()
            return
        }

        val successMessage =
            if (state.action == RoleAction.ASSIGN) "Your requested role(s) were added." else "Your requested role(s) were removed."
        updateSelectedRoles(guild, member, selectedRoles, state.action, hook, successMessage)
    }

    private fun updateSelectedRoles(
        guild: Guild,
        member: Member,
        selectedRoles: List<Role>,
        action: RoleAction,
        hook: InteractionHook,
        successMessage: String
    ) {
        roleUpdateAction(guild, member, selectedRoles, action)
            .reason("User used /role ${action.value} slash command.")
            .queue(
                {
                    hook.editOriginal(successMessage).queue()
                },
                {
                    hook.editOriginal(ROLE_UPDATE_FAILURE_MESSAGE).queue()
                }
            )
    }

    private fun roleUpdateAction(
        guild: Guild,
        member: Member,
        selectedRoles: List<Role>,
        action: RoleAction
    ): AuditableRestAction<Void> {
        return if (action == RoleAction.ASSIGN) {
            guild.modifyMemberRoles(member, selectedRoles, null)
        } else {
            guild.modifyMemberRoles(member, null, selectedRoles)
        }
    }

    private fun buildRoleMenuMessage(
        guild: Guild,
        member: Member,
        action: RoleAction,
        category: IAmRolesCategory,
        page: Int
    ): RoleMenuMessage {
        val roleSelection = buildRoleSelection(guild, member, action, category)
        if (roleSelection.errorMessage != null) {
            return RoleMenuMessage(errorMessage = roleSelection.errorMessage)
        }
        require(roleSelection.maxSelectable > 0) {
            "Invariant violation: maxSelectable should be > 0 when errorMessage is null"
        }

        val rolePages = roleSelection.rolePages
        val pageRoles = rolePages.getOrNull(page)
            ?: return RoleMenuMessage(errorMessage = OUT_OF_DATE_MESSAGE)
        val maxSelections = minOf(roleSelection.maxSelectable, pageRoles.size)
        val menu = StringSelectMenu.create(
            componentId(
                COMPONENT_TYPE_MENU,
                action,
                member.idLong,
                category.categoryId!!,
                page
            )
        )
            .setPlaceholder("Choose role(s) to ${action.verb}")
            .setRequiredRange(1, maxSelections)
        pageRoles.forEach { role ->
            menu.addOption(role.name, role.id)
        }

        val buttons = buildList {
            if (page > 0) {
                add(
                    Button.secondary(
                        componentId(
                            COMPONENT_TYPE_PAGE,
                            action,
                            member.idLong,
                            category.categoryId!!,
                            page - 1
                        ), "Previous"
                    )
                )
            }
            if (page + 1 < rolePages.size) {
                add(
                    Button.secondary(
                        componentId(
                            COMPONENT_TYPE_PAGE,
                            action,
                            member.idLong,
                            category.categoryId!!,
                            page + 1
                        ), "Next"
                    )
                )
            }
        }

        val content = buildString {
            append("Select the role(s) you'd like to ${action.verb} from ${category.categoryName}.")
            if (rolePages.size > 1) {
                append(" Page ${page + 1}/${rolePages.size}.")
            }
            if (action == RoleAction.ASSIGN) {
                append(" You may choose up to ")
                append(if (category.allowedRoles == 0) "${maxSelections}" else roleSelection.maxSelectable)
                append(" role(s) in this step.")
            }
        }

        return RoleMenuMessage(
            content = content,
            selectMenu = menu.build(),
            buttons = buttons
        )
    }

    private fun buildRoleSelection(
        guild: Guild,
        member: Member,
        action: RoleAction,
        category: IAmRolesCategory
    ): RoleSelection {
        val categoryRoles = category.roles
            .mapNotNull(guild::getRoleById)
            .sortedBy { it.name.lowercase() }
        val assignedRoles = member.roles
            .filter { category.roles.contains(it.idLong) }
            .sortedBy { it.name.lowercase() }

        return if (action == RoleAction.ASSIGN) {
            if (categoryRoles.isEmpty()) {
                RoleSelection(errorMessage = "There are no roles in this category. Please contact a server admin.")
            } else {
                val remainingAllowance =
                    if (category.allowedRoles == 0) Int.MAX_VALUE else category.allowedRoles - assignedRoles.size
                when {
                    remainingAllowance <= 0 -> RoleSelection(errorMessage = "You already have the max amount of roles you can assign for this category.")
                    else -> {
                        val addableRoles =
                            categoryRoles.filter { role -> assignedRoles.none { it.idLong == role.idLong } }
                        when {
                            addableRoles.isEmpty() -> RoleSelection(errorMessage = "You already have all the roles from this category.")
                            else -> RoleSelection(
                                rolePages = addableRoles.chunked(StringSelectMenu.OPTIONS_MAX_AMOUNT),
                                maxSelectable = minOf(
                                    addableRoles.size,
                                    remainingAllowance,
                                    StringSelectMenu.OPTIONS_MAX_AMOUNT
                                )
                            )
                        }
                    }
                }
            }
        } else {
            if (assignedRoles.isEmpty()) {
                RoleSelection(errorMessage = "You have no roles to remove from this category.")
            } else {
                RoleSelection(
                    rolePages = assignedRoles.chunked(StringSelectMenu.OPTIONS_MAX_AMOUNT),
                    maxSelectable = minOf(assignedRoles.size, StringSelectMenu.OPTIONS_MAX_AMOUNT)
                )
            }
        }
    }

    internal fun getCategoryId(event: SlashCommandInteractionEvent): Long? {
        return event.getOption(OPTION_CATEGORY)?.asString?.toLongOrNull()
    }

    private fun getRequiredCategory(guildId: Long, categoryId: Long): IAmRolesCategory? {
        return try {
            iAmRolesService.getCategory(guildId, categoryId)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun sendRoleMenu(callback: IReplyCallback, menuMessage: RoleMenuMessage) {
        val replyAction = callback.reply(menuMessage.content!!)
            .setEphemeral(true)
            .addComponents(ActionRow.of(menuMessage.selectMenu!!))

        if (menuMessage.buttons.isNotEmpty()) {
            replyAction.addComponents(ActionRow.of(menuMessage.buttons))
        }

        replyAction.queue()
    }

    private fun categoryOption(description: String): OptionData {
        return OptionData(OptionType.STRING, OPTION_CATEGORY, description, true, true)
    }

    private fun componentId(type: String, action: RoleAction, userId: Long, categoryId: Long, page: Int): String {
        return listOf(COMPONENT_PREFIX, type, action.value, userId, categoryId, page).joinToString(":")
    }

    private fun parseComponentState(componentId: String, expectedType: String): RoleComponentState? {
        val tokens = componentId.split(':')
        if (tokens.size != 6 || tokens[0] != COMPONENT_PREFIX || tokens[1] != expectedType) {
            return null
        }

        val action = RoleAction.fromSubcommand(tokens[2]) ?: return null
        val userId = tokens[3].toLongOrNull() ?: return null
        val categoryId = tokens[4].toLongOrNull() ?: return null
        val page = tokens[5].toIntOrNull() ?: return null
        return RoleComponentState(action, userId, categoryId, page)
    }

    private data class RoleSelection(
        val rolePages: List<List<Role>> = emptyList(),
        val maxSelectable: Int = 0,
        val errorMessage: String? = null
    )

    private data class RoleMenuMessage(
        val content: String? = null,
        val selectMenu: StringSelectMenu? = null,
        val buttons: List<Button> = emptyList(),
        val errorMessage: String? = null
    )

    private data class RoleComponentState(
        val action: RoleAction,
        val userId: Long,
        val categoryId: Long,
        val page: Int
    )

    private enum class RoleAction(val value: String, val verb: String) {
        ASSIGN(SUBCOMMAND_ASSIGN, "add"),
        REMOVE(SUBCOMMAND_REMOVE, "remove");

        companion object {
            fun fromSubcommand(subcommand: String?): RoleAction? {
                return entries.firstOrNull { it.value == subcommand }
            }
        }
    }
}
