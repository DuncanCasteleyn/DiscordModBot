/*
 * Copyright 2018.  Duncan Casteleyn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package be.duncanc.discordmodbot.bot.services

import be.duncanc.discordmodbot.bot.commands.CommandModule
import be.duncanc.discordmodbot.bot.sequences.Sequence
import be.duncanc.discordmodbot.data.entities.IAmRolesCategory
import be.duncanc.discordmodbot.data.services.IAmRolesService
import net.dv8tion.jda.core.MessageBuilder
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.MessageChannel
import net.dv8tion.jda.core.entities.Role
import net.dv8tion.jda.core.entities.TextChannel
import net.dv8tion.jda.core.entities.User
import net.dv8tion.jda.core.events.ReadyEvent
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.events.role.RoleDeleteEvent
import net.dv8tion.jda.core.exceptions.PermissionException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class IAmRoles
@Autowired constructor(
        private val iAmRolesService: IAmRolesService
) : CommandModule(
        ALIASES,
        null,
        DESCRIPTION
) {
    companion object {
        private val ALIASES = arrayOf("IAmRoles")
        private const val DESCRIPTION = "Controller for IAmRoles."
        private val ALIASES_I_AM_NOT = arrayOf("IAmNot")
        private const val DESCRIPTION_I_AM_NOT = "Can be used to remove all roles from a category from yourself."
        private val ALIASES_I_AM = arrayOf("iam")
        private const val DESCRIPTION_I_AM = "Can be used to self assign a role, this will remove all existing roles except those requested."
    }

    private val subCommands = arrayOf(IAm(), IAmNot())

    public override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        event.jda.addEventListener(IAmRolesSequence(event.author, event.channel))
    }

    override fun onRoleDelete(event: RoleDeleteEvent) {
        iAmRolesService.removeRole(event.guild.idLong, event.role.idLong)
    }

    inner class IAmRolesSequence internal constructor(user: User, channel: MessageChannel) : Sequence(user, channel) {

        private var sequenceNumber: Byte = 1
        private var newCategoryName: String? = null
        private var iAmRolesCategory: IAmRolesCategory? = null
        private val iAmRolesCategories: List<IAmRolesCategory>

        init {
            if (channel !is TextChannel) {
                super.destroy()
                throw UnsupportedOperationException("This command must be executed in a guild.")
            }
            if (!channel.guild.getMember(user).hasPermission(Permission.MANAGE_ROLES)) {
                throw PermissionException("You do not have permission to modify IAmRoles categories.")
            }
            iAmRolesCategories = iAmRolesService.getAllCategoriesForGuild(channel.guild.idLong)
            super.channel.sendMessage("Please select which action you want to perform:\n" +
                    "0. Add a new category\n" +
                    "1. Remove an existing category\n" +
                    "2. Modify an existing category").queue { message -> super.addMessageToCleaner(message) }
        }

        public override fun onMessageReceivedDuringSequence(event: MessageReceivedEvent) {
            when (sequenceNumber) {
                1.toByte() -> when (java.lang.Byte.parseByte(event.message.contentRaw)) {
                    0.toByte() -> {
                        super.channel.sendMessage("Please enter a unique category name.").queue { message -> super.addMessageToCleaner(message) }
                        sequenceNumber = 2
                    }
                    1.toByte() -> {
                        if (iAmRolesCategories.isEmpty()) {
                            throw IllegalStateException("No categories have been set up, there is nothing to delete.")
                        }
                        val deleteCategoryMessage = MessageBuilder().append("Please select which role category you'd like to delete.")
                        for (i in iAmRolesCategories.indices) {
                            deleteCategoryMessage.append('\n').append(i).append(". ").append(iAmRolesCategories[i].categoryName)
                        }
                        deleteCategoryMessage.buildAll(MessageBuilder.SplitPolicy.NEWLINE).forEach { message -> super.channel.sendMessage(message).queue { message1 -> super.addMessageToCleaner(message1) } }
                        sequenceNumber = 3
                    }
                    2.toByte() -> {
                        if (iAmRolesCategories.isEmpty()) {
                            throw IllegalStateException("No categories have been set up, there is nothing to modify.")
                        }
                        val modifyCategoryMessage = MessageBuilder().append("Please select which role category you'd like to modify.")
                        for (i in iAmRolesCategories.indices) {
                            modifyCategoryMessage.append('\n').append(i).append(". ").append(iAmRolesCategories[i].categoryName)
                        }
                        modifyCategoryMessage.buildAll(MessageBuilder.SplitPolicy.NEWLINE).forEach { super.channel.sendMessage(it).queue { super.addMessageToCleaner(it) } }
                        sequenceNumber = 4
                    }
                    else -> channel.sendMessage("Wrong answer please answer with a valid number").queue { super.addMessageToCleaner(it) }
                }
                2.toByte() -> if (newCategoryName == null) {
                    val existingCategoryNames = iAmRolesService.getExistingCategoryNames(event.guild.idLong)
                    if (existingCategoryNames.contains(event.message.contentRaw)) {
                        throw IllegalArgumentException("The name you provided is already being used.")
                    }
                    newCategoryName = event.message.contentRaw
                    super.channel.sendMessage("Please enter how much roles a user can have from this category? Use 0 for unlimited.").queue { super.addMessageToCleaner(it) }
                } else {
                    synchronized(iAmRolesCategories) {
                        iAmRolesService.addNewCategory(event.guild.idLong, newCategoryName!!, event.message.contentRaw.toInt())
                    }
                    super.channel.sendMessage(user.asMention + " Successfully added new category.").queue { message -> message.delete().queueAfter(1, TimeUnit.MINUTES) }
                    super.destroy()
                }
                3.toByte() -> {
                    synchronized(iAmRolesCategories) {
                        iAmRolesService.removeCategory(event.guild.idLong, iAmRolesCategories[event.message.contentRaw.toInt()].categoryId!!)
                    }
                    super.channel.sendMessage(user.asMention + " Successfully removed the category").queue { message -> message.delete().queueAfter(1, TimeUnit.MINUTES) }
                    super.destroy()
                }
                4.toByte() -> {
                    iAmRolesCategory = iAmRolesCategories.elementAt(Integer.parseInt(event.message.contentRaw))
                    super.channel.sendMessage("Please enter the number of the action you'd like to perform.\n" +
                            "0. Modify the name. Current value: " + iAmRolesCategory!!.categoryName + "\n" +
                            "1. Change how much roles you can assign from the category. Current value: " + iAmRolesCategory!!.allowedRoles + "\n" +
                            "2. Add or remove roles.").queue { super.addMessageToCleaner(it) }
                    sequenceNumber = 5
                }
                5.toByte() -> when (java.lang.Byte.parseByte(event.message.contentRaw)) {
                    0.toByte() -> {
                        super.channel.sendMessage("Please enter a new name for the category.").queue { message -> super.addMessageToCleaner(message) }
                        sequenceNumber = 6
                    }
                    1.toByte() -> {
                        super.channel.sendMessage("Please enter a new value for the amount of allowed roles.").queue { super.addMessageToCleaner(it) }
                        sequenceNumber = 8
                    }
                    2.toByte() -> {
                        super.channel.sendMessage("Please enter the full name of the role you'd like to remove or add. This will automatically detect if the role is already in the list and remove or add it.").queue { super.addMessageToCleaner(it) }
                        sequenceNumber = 7
                    }
                }
                6.toByte() -> {
                    iAmRolesService.changeCategoryName(event.guild.idLong, iAmRolesCategory!!.categoryId!!, event.message.contentStripped)
                    super.channel.sendMessage("Name successfully changed.").queue { message -> message.delete().queueAfter(1, TimeUnit.MINUTES) }
                    super.destroy()
                }
                7.toByte() -> {
                    val matchedRoles = (super.channel as TextChannel).guild.getRolesByName(event.message.contentRaw, true)
                    when {
                        matchedRoles.size > 1 -> throw IllegalArgumentException("The role name you provided matches multiple roles and is not supported by !IAm and !IaANot")
                        matchedRoles.isEmpty() -> throw IllegalArgumentException("Could not find any roles with that name")
                        else -> {
                            val roleId = matchedRoles[0].idLong
                            when (iAmRolesService.addOrRemoveRole(event.guild.idLong, iAmRolesCategory!!.categoryId!!, roleId)) {
                                false -> super.channel.sendMessage(user.asMention + " The role was successfully removed form the category.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                                true -> super.channel.sendMessage(user.asMention + " The role was successfully added to the category.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                            }
                            super.destroy()
                        }
                    }
                }
                8.toByte() -> {
                    iAmRolesService.changeAllowedRoles(event.guild.idLong, iAmRolesCategory!!.categoryId!!, event.message.contentRaw.toInt())
                    channel.sendMessage(user.asMention + " Allowed roles successfully changed.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                    super.destroy()
                }
            }
        }
    }

    override fun onReady(event: ReadyEvent) {
        event.jda.addEventListener(*subCommands)
    }

    /**
     * Iam command to assign yourself roles.
     */
    internal inner class IAm : CommandModule(ALIASES_I_AM, null, DESCRIPTION_I_AM) {

        public override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
            event.jda.addEventListener(RoleModificationSequence(event.author, event.channel, remove = false))
        }
    }

    /**
     * I am not command to allow users to remove roles from them self.
     */
    internal inner class IAmNot : CommandModule(ALIASES_I_AM_NOT, null, DESCRIPTION_I_AM_NOT) {

        public override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
            event.jda.addEventListener(RoleModificationSequence(event.author, event.channel, remove = true))
        }
    }

    internal inner class RoleModificationSequence(user: User, channel: MessageChannel, private val remove: Boolean) : Sequence(user, channel, cleanAfterSequence = true, informUser = true) {
        private val iAmRolesCategories: List<IAmRolesCategory>
        private var roles: ArrayList<Long>? = null
        private var assignedRoles: ArrayList<Role>? = null
        private var selectedCategory: Int = -1

        init {
            if (channel !is TextChannel) {
                super.destroy()
                throw UnsupportedOperationException("This command must be executed in a guild.")
            }
            iAmRolesCategories = iAmRolesService.getAllCategoriesForGuild(channel.guild.idLong)
            val message = MessageBuilder()
            if (remove) {
                message.append(user.asMention + " Please select from which category you'd like to remove roles.")
            } else {
                message.append(user.asMention + " Please select from which category you'd like to assign (a) role(s).")
            }
            for (i in iAmRolesCategories.indices) {
                message.append('\n').append(i).append(". ").append(iAmRolesCategories[i].categoryName)
            }
            message.buildAll(MessageBuilder.SplitPolicy.NEWLINE).forEach { super.channel.sendMessage(it).queue { super.addMessageToCleaner(it) } }
        }

        override fun onMessageReceivedDuringSequence(event: MessageReceivedEvent) {
            when (roles) {
                null -> {
                    selectedCategory = event.message.contentRaw.toInt()
                    roles = ArrayList(iAmRolesService.getRoleIds(event.guild.idLong, iAmRolesCategories[selectedCategory].categoryId!!))
                    if (roles!!.isEmpty()) {
                        throw IllegalStateException("There are no roles in this category. Please contact a server admin.")
                    }
                    assignedRoles = ArrayList(event.member.roles)
                    val notMutableRoles = roles!!
                    assignedRoles!!.removeIf { assignedRole ->
                        notMutableRoles.none { it == assignedRole.idLong }
                    }
                    roles!!.removeIf { roleId -> assignedRoles!!.any { it.idLong == roleId } }

                    val message = MessageBuilder(user.asMention)
                    message.append(" Please select which role(s) you'd like to ").append(if (remove) "remove" else "add").append(". For multiple roles put each number on a new line (shift enter).\n")
                    val maxAssignableRoles = iAmRolesCategories[selectedCategory].allowedRoles - assignedRoles!!.size
                    if (!remove) {
                        message.append("You are allowed to select up to ").append(if (iAmRolesCategories[selectedCategory].allowedRoles > 0) maxAssignableRoles else "unlimited").append(" role(s) from this category")
                    }
                    if (remove) {
                        if (assignedRoles!!.isEmpty()) {
                            throw IllegalStateException("You have no roles to remove from this category.")
                        }
                        for (i in assignedRoles!!.indices) {
                            message.append('\n').append(i).append(". ").append(assignedRoles!![i].name)
                        }
                    } else {
                        if (roles!!.isEmpty()) {
                            throw IllegalStateException("You already have all the roles from this category.")
                        } else if (iAmRolesCategories[selectedCategory].allowedRoles > 0 && maxAssignableRoles <= 0) {
                            throw IllegalStateException("You already have the max amount of roles you can assign for this category.")
                        }
                        for (i in roles!!.indices) {
                            val role = (channel as TextChannel).guild.getRoleById(roles!![i])
                            message.append('\n').append(i).append(". ").append(role.name)
                        }
                    }
                    message.buildAll(MessageBuilder.SplitPolicy.NEWLINE).forEach { super.channel.sendMessage(it).queue { super.addMessageToCleaner(it) } }
                }
                else -> {
                    val requestedRoles = event.message.contentRaw.split('\n')
                    if (requestedRoles.isEmpty()) {
                        throw IllegalArgumentException("You need to provide at least one role to " + if (remove) "remove" else "assign." + ".")
                    }
                    if (!remove && requestedRoles.size > iAmRolesCategories[selectedCategory].allowedRoles - assignedRoles!!.size && iAmRolesCategories[selectedCategory].allowedRoles > 0) {
                        throw IllegalArgumentException("You listed more roles than allowed.")
                    }
                    val rRoles = HashSet<Role>()
                    requestedRoles.forEach {
                        if (remove) {
                            rRoles.add(assignedRoles!![it.toInt()])
                        } else {
                            val id = this.roles!![it.toInt()]
                            rRoles.add(event.guild.getRoleById(id))
                        }
                    }

                    val member = event.guild.getMember(user)
                    val controller = event.guild.controller
                    if (remove) {
                        controller.removeRolesFromMember(member, rRoles).reason("User used !iam command").queue()
                    } else {
                        controller.addRolesToMember(member, rRoles).reason("User used !iam command").queue()
                    }
                    channel.sendMessage(user.asMention + " The requested role(s) were/was " + if (remove) "removed" else "added.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                    super.destroy()
                }
            }
        }
    }
}
