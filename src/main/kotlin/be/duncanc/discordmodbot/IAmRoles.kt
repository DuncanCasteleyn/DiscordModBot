/*
 * MIT License
 *
 * Copyright (c) 2017 Duncan Casteleyn
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package be.duncanc.discordmodbot

import be.duncanc.discordmodbot.commands.CommandModule
import be.duncanc.discordmodbot.sequence.Sequence
import net.dv8tion.jda.core.MessageBuilder
import net.dv8tion.jda.core.entities.*
import net.dv8tion.jda.core.events.ReadyEvent
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class IAmRoles : CommandModule {
    companion object {
        private val ALIASES = arrayOf("IAmRoles")
        private const val DESCRIPTION = "Controller for IAmRoles."
        private val ALIASES_I_AM_NOT = arrayOf("IAmNot")
        private const val DESCRIPTION_I_AM_NOT = "Can be used to remove a role from yourself."
        private val ALIASES_I_AM = arrayOf("iam")
        private const val DESCRIPTION_I_AM = "Can be used to self assign a role."

        val FILE_PATH: Path = Paths.get("IAmRoles.json")

        val INSTANCE: IAmRoles = if (IAmRoles.FILE_PATH.toFile().exists()) {
            try {
                val stringBuilder = StringBuilder()
                synchronized(IAmRoles.FILE_PATH) {
                    Files.readAllLines(IAmRoles.FILE_PATH).forEach { stringBuilder.append(it.toString()) }
                }
                IAmRoles(JSONObject(stringBuilder.toString()))
            } catch (ise: IllegalStateException) {
                IAmRoles()
            }
        } else {
            IAmRoles()
        }
    }

    private val iAmRoles: HashMap<Long, ArrayList<IAmRolesCategory>> = HashMap()
    private val subCommands = arrayOf(IAm(), IAmNot())

    private constructor() : super(ALIASES, null, DESCRIPTION)

    private constructor(json: JSONObject) : this() {
        val hashMap = HashMap<Long, ArrayList<IAmRolesCategory>>()
        json.toMap().forEach {
            val arrayList = ArrayList<IAmRolesCategory>()
            (it.value as ArrayList<*>).forEach { arrayContent ->
                arrayContent as HashMap<*, *>
                val rolesList = ArrayList<Long>()
                (arrayContent["roles"] as ArrayList<*>).forEach { rolesList.add(it as Long) }
                arrayList.add(IAmRolesCategory(arrayContent["categoryName"].toString(), arrayContent["allowedRoles"] as Int, rolesList))
            }
            hashMap.put(it.key.toLong(), arrayList)
        }
        iAmRoles.putAll(hashMap)
    }

    public override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        event.jda.addEventListener(IAmRolesSequence(event.author, event.channel))
    }

    inner class IAmRolesSequence internal constructor(user: User, channel: MessageChannel) : Sequence(user, channel) {

        private var sequenceNumber: Byte = 1
        private var newCategoryName: String? = null
        private var iAmRolesCategory: IAmRolesCategory? = null
        private val iAmRolesCategories: ArrayList<IAmRolesCategory>

        init {
            if (channel !is TextChannel) {
                super.destroy()
                throw UnsupportedOperationException("This command must be executed in a guild.")
            }
            iAmRolesCategories = getCategoriesForGuild(channel.guild)
            super.channel.sendMessage("Please select which action you want to perform:\n" +
                    "0. Add a new category\n" +
                    "1. Remove an existing category\n" +
                    "2. Modify an existing category").queue { message -> super.addMessageToCleaner(message) }
        }

        public override fun onMessageReceivedDuringSequence(event: MessageReceivedEvent) {
            when (sequenceNumber) {
                1.toByte() -> when (java.lang.Byte.parseByte(event.message.rawContent)) {
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
                    val existingCategoryNames = ArrayList<String>()
                    iAmRoles[event.guild.idLong]?.forEach { iAmRolesCategory -> existingCategoryNames.add(iAmRolesCategory.categoryName) }
                    if (existingCategoryNames.contains(event.message.rawContent)) {
                        throw IllegalArgumentException("The name you provided is already being used.")
                    }
                    newCategoryName = event.message.rawContent
                    super.channel.sendMessage("Please enter how much roles a user can have from this category.").queue { super.addMessageToCleaner(it) }
                } else {
                    synchronized(iAmRolesCategories) {
                        iAmRolesCategories.add(IAmRolesCategory(newCategoryName!!, event.message.rawContent.toInt()))
                    }
                    super.channel.sendMessage("Successfully added new category.").queue { message -> message.delete().queueAfter(1, TimeUnit.MINUTES) }
                    saveIAmRoles()
                    super.destroy()
                }
                3.toByte() -> {
                    synchronized(iAmRolesCategories) {
                        iAmRolesCategories.removeAt(Integer.parseInt(event.message.rawContent))
                    }
                    super.channel.sendMessage("Successfully removed the category").queue { message -> message.delete().queueAfter(1, TimeUnit.MINUTES) }
                    saveIAmRoles()
                    super.destroy()
                }
                4.toByte() -> {
                    iAmRolesCategory = iAmRolesCategories[Integer.parseInt(event.message.rawContent)]
                    super.channel.sendMessage("Please enter the number of the action you'd like to perform.\n" +
                            "0. Modify the name. Current value: " + iAmRolesCategory!!.categoryName + "\n" +
                            "1. Invert if the users can only have one role from the category. Current value" + iAmRolesCategory!!.allowedRoles + "\n" +
                            "2. Add or remove roles.").queue { super.addMessageToCleaner(it) }
                    sequenceNumber = 5
                }
                5.toByte() -> when (java.lang.Byte.parseByte(event.message.rawContent)) {
                    0.toByte() -> {
                        super.channel.sendMessage("Please type a new name for the category.").queue { message -> super.addMessageToCleaner(message) }
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
                    iAmRolesCategory!!.categoryName = event.message.content
                    super.channel.sendMessage("Name successfully changed.").queue { message -> message.delete().queueAfter(1, TimeUnit.MINUTES) }
                    saveIAmRoles()
                    super.destroy()
                }
                7.toByte() -> {
                    val matchedRoles = (super.channel as TextChannel).guild.getRolesByName(event.message.rawContent, true)
                    when {
                        matchedRoles.size > 1 -> throw IllegalArgumentException("The role name you provided matches multiple roles and is not supported by !IAm and !IaANot")
                        matchedRoles.isEmpty() -> throw IllegalArgumentException("Could not find any roles with that name")
                        else -> {
                            val roleId = matchedRoles[0].idLong
                            val removed: Boolean? = try {
                                iAmRolesCategory!!.addRole(roleId)
                                false
                            } catch (illegalArgumentException: IllegalArgumentException) {
                                try {
                                    iAmRolesCategory!!.removeRole(roleId)
                                    true
                                } catch (illegalArgumentException2: IllegalArgumentException) {
                                    illegalArgumentException2.addSuppressed(illegalArgumentException)
                                    throw IllegalStateException("Something went wrong while trying to add and remove the role", illegalArgumentException2)
                                }
                            }
                            when (removed) {
                                true -> super.channel.sendMessage("The role was successfully removed form the category.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                                false -> super.channel.sendMessage("The role was successfully added to the category.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                                else -> throw IllegalStateException("Boolean value removed should not be able to be null at this point.")
                            }
                            saveIAmRoles()
                            super.destroy()
                        }
                    }
                }
                8.toByte() -> {
                    iAmRolesCategory!!.allowedRoles = event.message.rawContent.toInt()
                    channel.sendMessage("Allowed roles successfully changed.").queue { it.delete().queueAfter(5, TimeUnit.MINUTES) }
                    saveIAmRoles()
                    super.destroy()
                }
            }
        }
    }

    private fun getCategoriesForGuild(guild: Guild): ArrayList<IAmRolesCategory> {
        var arrayList: ArrayList<IAmRolesCategory> = ArrayList()
        synchronized(iAmRoles) {
            val key = guild.idLong
            if (iAmRoles.containsKey(key)) {
                arrayList = iAmRoles[guild.idLong]!!
            } else {
                iAmRoles.put(key, arrayList)
            }
        }
        return arrayList
    }

    override fun onReady(event: ReadyEvent) {
        event.jda.addEventListener(*subCommands)
    }

    private fun saveIAmRoles() {
        synchronized(FILE_PATH) {
            Files.write(FILE_PATH, Collections.singletonList(JSONObject(iAmRoles).toString()))
        }
    }

    /**
     * A role category
     *
     * This class is Thread safe.
     */
    internal inner class IAmRolesCategory
    /**
     * Constructor for a new IAmRolesCategory.
     *
     * @param categoryName The name of the category.
     * @param allowedRoles The amount of roles you can have from the same category.
     */
    constructor(@get:Synchronized @set:Synchronized var categoryName: String, @get:Synchronized @set:Synchronized var allowedRoles: Int, @get:Synchronized val roles: List<Long> = ArrayList()) {

        @Synchronized
        fun removeRole(roleId: Long) {
            if (!roles.contains(roleId)) {
                throw IllegalArgumentException("The role is not part of this category")
            }
            (roles as ArrayList).remove(roleId)
        }

        @Synchronized
        fun addRole(roleId: Long) {
            if (roles.contains(roleId)) {
                throw IllegalArgumentException("The role is already part of this category")
            }
            (roles as ArrayList).add(roleId)
        }

        @Synchronized
        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other == null || javaClass != other.javaClass) {
                return false
            }

            val that = other as IAmRolesCategory?

            return categoryName == that!!.categoryName
        }

        @Synchronized
        override fun hashCode(): Int = categoryName.hashCode()
    }

    /**
     * Iam command to assign yourself roles.
     */
    internal inner class IAm : CommandModule(ALIASES_I_AM, null, DESCRIPTION_I_AM) {

        public override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
            event.jda.addEventListener(RoleModificationSequence(event.author, event.channel, remove = false))
            /*if (arguments == null) {
                throw IllegalArgumentException("Arguments are required for this command")
            }

            val iAmRolesList = iAmRoles[event.guild.idLong] ?:
                    throw UnsupportedOperationException("This server/guild has not configured any roles.")

            val matchedIAmRoles = event.guild.getRolesByName(arguments, true).filter(filterIAmRoles(iAmRolesList))

            when {
                matchedIAmRoles.isEmpty() -> throw IllegalArgumentException("No matching roles")
                matchedIAmRoles.size > 1 -> throw IllegalArgumentException("Too many matching roles")
                else -> {
                    event.guild.controller.addSingleRoleToMember(event.member, matchedIAmRoles[0]).reason("Requested with !IAm command").queue()
                    event.channel.sendMessage(event.author.asMention + " The role " + matchedIAmRoles[0].name + " was assigned.").queue()
                }
            }*/
        }
    }

    /**
     * I am not command to allow users to remove roles from them self.
     */
    internal inner class IAmNot : CommandModule(ALIASES_I_AM_NOT, null, DESCRIPTION_I_AM_NOT) {

        public override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
            event.jda.addEventListener(RoleModificationSequence(event.author, event.channel, remove = true))
            /*if (arguments == null) {
                throw IllegalArgumentException("Arguments are required for this command")
            }
            val iAmRolesList = iAmRoles[event.guild.idLong] ?:
                    throw UnsupportedOperationException("This server/guild has not configured any roles.")

            val matchedIAmRoles = event.guild.getRolesByName(arguments, true).filter(filterIAmRoles(iAmRolesList))

            when {
                matchedIAmRoles.isEmpty() -> throw IllegalArgumentException("No matching roles")
                matchedIAmRoles.size > 1 -> throw IllegalArgumentException("Too many matching roles")
                else -> {
                    event.guild.controller.removeSingleRoleFromMember(event.member, matchedIAmRoles[0]).reason("Requested with !IAm command").queue()
                    event.channel.sendMessage(event.author.asMention + " The role " + matchedIAmRoles[0].name + " was removed.").queue()
                }
            }*/
        }
    }

    /*private fun filterIAmRoles(iAmRolesList: ArrayList<IAmRolesCategory>): (Role) -> Boolean {
        return {
            var roleIsIAmRole = false
            for (iAmRole in iAmRolesList) {
                roleIsIAmRole = iAmRole.roles.contains(it.idLong)
                if (roleIsIAmRole) {
                    break
                }
            }
            roleIsIAmRole
        }
    }*/

    @Suppress("unused")
    internal inner class RoleModificationSequence(user: User, channel: MessageChannel, private val remove: Boolean) : Sequence(user, channel, cleanAfterSequence = true, informUser = false) {
        private val iAmRolesCategories: ArrayList<IAmRolesCategory>
        private var roles: List<Long>? = null
        private var selectedCategory: Int = -1

        init {
            if (channel !is TextChannel) {
                super.destroy()
                throw UnsupportedOperationException("This command must be executed in a guild.")
            }
            iAmRolesCategories = getCategoriesForGuild(channel.guild)
            val message = MessageBuilder()
            if(remove) {
                message.append(user.asMention + " Please select from which category you'd like to remove all roles.")
            } else {
                message.append(user.asMention + " Please select from which category you'd like to assign (a) role(s).")
            }
            for (i in iAmRolesCategories.indices) {
                message.append('\n').append(i).append(". ").append(iAmRolesCategories[i].categoryName)
            }
            message.buildAll(MessageBuilder.SplitPolicy.NEWLINE).forEach { super.channel.sendMessage(it).queue { super.addMessageToCleaner(it) } }
        }

        override fun onMessageReceivedDuringSequence(event: MessageReceivedEvent) {
            when(roles) {
                null -> {
                    selectedCategory = event.message.rawContent.toInt()
                    roles = iAmRolesCategories[selectedCategory].roles
                    if(roles!!.isEmpty()) {
                        throw IllegalStateException("There are no roles in this category. Please contact a server admin.")
                    }
                    if(remove) {
                        
                        super.destroy()
                    } else {
                        val message = MessageBuilder()
                        message.append(user.asMention + " Please select which role(s) you'd like to assign. For multiple roles put each number on a new line (shift enter).\n" +
                                "You are allowed to select up to " + iAmRolesCategories[selectedCategory].allowedRoles + ", all previously assigned roles from this category will be removed unless you select them.")
                        for (i in roles!!.indices) {
                            val role = (channel as TextChannel).guild.getRoleById(roles!![i])
                            message.append('\n').append(i).append(". ").append(role.name)
                        }
                        message.buildAll(MessageBuilder.SplitPolicy.NEWLINE).forEach { super.channel.sendMessage(it).queue { super.addMessageToCleaner(it) } }
                    }
                }
                else -> {
                    if(remove) {
                        throw IllegalStateException()
                    } else {
                        val requestedRoles = event.message.rawContent.split('\n')
                        if(requestedRoles.isEmpty()) {
                            throw IllegalArgumentException("You need to provide at least one role to assign.")
                        }
                        if(requestedRoles.size > iAmRolesCategories!![selectedCategory].allowedRoles) {
                            throw IllegalArgumentException("You listed more roles than allowed.")
                        }
                        val rolesToAdd = ArrayList<Role>()
                        requestedRoles.forEach {
                            val id = roles!![it.toInt()]
                            rolesToAdd.add(event.guild.getRoleById(id))
                        }

                        val member = event.guild.getMember(user)
                        val rolesToRemove = member.roles.filter { roles!!.contains(it.idLong) && !rolesToAdd.contains(it) }.toList()
                        event.guild.controller.modifyMemberRoles(member, rolesToAdd, rolesToRemove).reason("User used !iam command").queue()
                        channel.sendMessage(user.asMention + " The request roles where added.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                        super.destroy()
                    }
                }
            }

            TODO("Need to write logic to filter out roles already assigned/not yet assigned and offer those as option")
        }
    }
}