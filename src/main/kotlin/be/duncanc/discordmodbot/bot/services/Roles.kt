package be.duncanc.discordmodbot.bot.services

import be.duncanc.discordmodbot.bot.commands.SlashCommand
import be.duncanc.discordmodbot.data.configs.properties.DiscordModBotConfig
import be.duncanc.discordmodbot.data.services.IAmRolesService
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu
import org.springframework.stereotype.Component

private const val CATEGORY_COMPONENT_ID = "role-choose-category"
private const val ROLE_COMPONENT_ID = "role-choose-role"
private const val MORE_BUTTON_ID_PREFIX = "role-more-"

@Component
class Roles(
    private val iAmRolesService: IAmRolesService,
    private val discordModBotConfig: DiscordModBotConfig
) : ListenerAdapter(), SlashCommand {
    override fun getCommandsData(): List<SlashCommandData> {
        return listOf(
            Commands.slash("role", "Allows you to select roles that you can freely assign to yourself")
                .addSubcommands(
                    SubcommandData("assign", "assign yourself a role")
                )
                .setGuildOnly(true)
        )
    }


    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        val guild = event.guild

        if (event.name != "role" || guild == null) return

        when (event.subcommandName) {
            "assign" -> {
                event.deferReply(true).queue {
                    val categoryMenu = StringSelectMenu.create(CATEGORY_COMPONENT_ID)

                    iAmRolesService.getAllCategoriesForGuild(guild.idLong).forEach { category ->
                        categoryMenu.addOption(category.categoryName, category.categoryId.toString())
                    }

                    it.sendMessage("Select the category you would like a role from")
                        .addActionRow(categoryMenu.build())
                        .queue()
                }
            }

            else -> {
                event.deferReply(true).queue { reply ->
                    reply.sendMessage("That's not a valid subcommand for this command.").queue()
                }
            }
        }
    }

    override fun onStringSelectInteraction(event: StringSelectInteractionEvent) {
        val guild = event.guild
        val componentId = event.componentId

        if ((componentId != CATEGORY_COMPONENT_ID && componentId != ROLE_COMPONENT_ID) || guild == null) return

        if (componentId == CATEGORY_COMPONENT_ID) {
            event.deferReply(true).queue { reply ->
                val categoryId = event.values.first().toLong()

                replyWithRoleMenu(reply, guild, categoryId, 0)
            }
        }

        if (componentId == ROLE_COMPONENT_ID) {
            val ephemeral = event.user.idLong != discordModBotConfig.ownerId

            event.deferReply(ephemeral).queue { reply ->
                val roleId = event.values.first().toLong()

                val roleCategory = iAmRolesService.getCategoryByRoleId(guild.idLong, roleId)

                val member = event.member ?: return@queue

                val assignedRolesFromCategory = member.roles.count { role: Role? ->
                    if (role == null) {
                        return@count false
                    }

                    roleCategory.roles.contains(role.idLong)
                }

                if (assignedRolesFromCategory >= roleCategory.allowedRoles) {
                    reply.sendMessage("You already have the max amount of roles from this category.").queue()
                    return@queue
                }


                guild.getRoleById(roleId)?.let {
                    guild.addRoleToMember(event.user, it)
                        .reason("User request role through \"/role assign\" slash command.").queue {
                            reply.sendMessage("You're role was assigned.").queue()
                        }
                }
            }

        }
    }

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        val guild = event.guild
        val componentId = event.componentId

        if (!componentId.startsWith(MORE_BUTTON_ID_PREFIX) || guild == null) return

        val ephemeral = event.user.idLong != discordModBotConfig.ownerId

        event.deferReply(ephemeral).queue { reply ->
            val metaData = componentId.removePrefix(MORE_BUTTON_ID_PREFIX).split("-")

            val categoryId = metaData[0].toLong()
            val pageNumber = metaData[1].toInt() + 1

            replyWithRoleMenu(reply, guild, categoryId, pageNumber)
        }


    }

    private fun replyWithRoleMenu(
        reply: InteractionHook,
        guild: Guild,
        categoryId: Long,
        page: Int
    ) {
        val chunkedRoles = getChunkedRoles(guild, categoryId)

        val roleSelectMenu = StringSelectMenu.create(ROLE_COMPONENT_ID)

        chunkedRoles[page].forEach {
            roleSelectMenu.addOption(it.name, it.id)
        }

        reply.sendMessage("Select the role you would like to get assigned.")
            .addActionRow(roleSelectMenu.build())
            .addActionRow(
                Button.primary("$MORE_BUTTON_ID_PREFIX$categoryId-$page", "More roles")
                    .withDisabled(chunkedRoles.size <= page + 1)
            )
            .queue()
    }

    private fun getChunkedRoles(
        guild: Guild,
        categoryId: Long
    ) = iAmRolesService.getRoleIds(guild.idLong, categoryId)
        .mapNotNull { guild.getRoleById(it) }
        .chunked(StringSelectMenu.OPTIONS_MAX_AMOUNT)
}
