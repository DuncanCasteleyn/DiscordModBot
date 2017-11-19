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
import be.duncanc.discordmodbot.utils.jsontojavaobject.JSONKey
import be.duncanc.discordmodbot.utils.jsontojavaobject.JSONToJavaObject
import net.dv8tion.jda.core.MessageBuilder
import net.dv8tion.jda.core.entities.MessageChannel
import net.dv8tion.jda.core.entities.TextChannel
import net.dv8tion.jda.core.entities.User
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import org.json.JSONArray
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

@Suppress("unused")
class IAmRoles : CommandModule {
    companion object {
        private val ALIASES = arrayOf("IAmRoles")
        private val DESCRIPTION = "Controller for IAmRoles."
        private val ALIASES_I_AM_NOT = arrayOf("IAmNot")
        private val DESCRIPTION_I_AM_NOT = "Can be used to remove a role from yourself."
        private val ALIASES_I_AM = arrayOf("iam")
        private val DESCRIPTION_I_AM = "Can be used to self assign a role."
    }

    private val iAmRoles: HashMap<Long, ArrayList<IAmRolesCategory>> = HashMap()

    constructor() : super(ALIASES, null, DESCRIPTION)

    constructor(iAmRoles: HashMap<String, JSONArray>) : super(ALIASES, null, DESCRIPTION) {
        throw UnsupportedOperationException("Not yet implemented")
        /*this.iAmRoles = new HashMap<>();
        iAmRoles.forEach((s, ) -> {
            //this.iAmRoles.put(Long.parseLong(s), );
            //todo
        });*/
    }

    public override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        event.jda.addEventListener(IAmRolesSequence(event.author, event.channel))
    }

    inner class IAmRolesSequence internal constructor(user: User, channel: MessageChannel) : Sequence(user, channel) {

        private var sequenceNumber: Byte = 0
        private var newCategoryName: String? = null
        private var iAmRolesCategory: IAmRolesCategory? = null
        private val iAmRolesCategories: ArrayList<IAmRolesCategory>

        init {
            if (channel !is TextChannel) {
                super.destroy()
                throw UnsupportedOperationException("This command must be executed in a guild.")
            }
            var tempVar: ArrayList<IAmRolesCategory> = ArrayList()
            synchronized(iAmRoles) {
                if (iAmRoles.containsKey(channel.guild.idLong)) {
                    tempVar = iAmRoles[channel.guild.idLong]!!
                }
            }
            iAmRolesCategories = tempVar
        }

        public override fun onMessageReceivedDuringSequence(event: MessageReceivedEvent) {
            when (sequenceNumber) {
                0.toByte() -> {
                    super.channel.sendMessage("Please select which action you want to perform:\n" +
                            "0. Add a new category\n" +
                            "1. Remove an existing category\n" +
                            "2. Modify an existing category").queue { message -> super.addMessageToCleaner(message) }
                    sequenceNumber = 1
                }
                1.toByte() -> when (java.lang.Byte.parseByte(event.message.rawContent)) {
                    0.toByte() -> {
                        super.channel.sendMessage("Please enter a unique category name and if you can only have one role of this category (true if the a user can only have on role out of this category). Syntax: \"Role name | true or false\"").queue { message -> super.addMessageToCleaner(message) }
                        sequenceNumber = 2
                    }
                    1.toByte() -> {
                        val deleteCategoryMessage = MessageBuilder().append("Please select which role category you'd like to delete.")
                        for (i in iAmRolesCategories.indices) {
                            deleteCategoryMessage.append('\n').append(i).append(". ").append(iAmRolesCategories[i].categoryName)
                        }
                        deleteCategoryMessage.buildAll(MessageBuilder.SplitPolicy.NEWLINE).forEach { message -> super.channel.sendMessage(message).queue { message1 -> super.addMessageToCleaner(message1) } }
                        sequenceNumber = 3
                    }
                    2.toByte() -> {
                        val modifyCategoryMessage = MessageBuilder().append("Please select which role category you'd like to modify.")
                        for (i in iAmRolesCategories.indices) {
                            modifyCategoryMessage.append('\n').append(i).append(". ").append(iAmRolesCategories[i].categoryName)
                        }
                        modifyCategoryMessage.buildAll(MessageBuilder.SplitPolicy.NEWLINE).forEach { message -> super.channel.sendMessage(message).queue { message1 -> super.addMessageToCleaner(message1) } }
                        sequenceNumber = 4
                    }
                    else -> channel.sendMessage("Wrong answer please answer with a valid number").queue { message -> super.addMessageToCleaner(message) }
                }
                2.toByte() -> if (newCategoryName == null) {
                    val existingCategoryNames = ArrayList<String>()
                    iAmRoles[event.guild.idLong]!!.forEach { iAmRolesCategory -> existingCategoryNames.add(iAmRolesCategory.categoryName) }
                    if (existingCategoryNames.contains(event.message.rawContent)) {
                        throw IllegalArgumentException("The name you provided is already being used.")
                    }
                    newCategoryName = event.message.rawContent
                    super.channel.sendMessage("Please enter if a user can only have one role of this category. true or false?").queue { message -> super.addMessageToCleaner(message) }
                } else {
                    synchronized(iAmRolesCategories) {
                        iAmRolesCategories.add(IAmRolesCategory(newCategoryName!!, java.lang.Boolean.parseBoolean(event.message.rawContent)))
                    }
                    super.channel.sendMessage("Successfully added new category.").queue { message -> message.delete().queueAfter(1, TimeUnit.MINUTES) }
                    super.destroy()
                }
                3.toByte() -> {
                    synchronized(iAmRolesCategories) {
                        iAmRolesCategories.removeAt(Integer.parseInt(event.message.rawContent))
                    }
                    super.channel.sendMessage("Successfully removed the category").queue { message -> message.delete().queueAfter(1, TimeUnit.MINUTES) }
                    super.destroy()
                }
                4.toByte() -> {
                    iAmRolesCategory = iAmRolesCategories[Integer.parseInt(event.message.rawContent)]
                    super.channel.sendMessage("Please enter the number of the action you'd like to perform.\n" +
                            "0. Modify the name. Current value: " + iAmRolesCategory!!.categoryName + "\n" +
                            "1. Invert if the users can only have one role from the category. Current value" + iAmRolesCategory!!.canOnlyHaveOne + "\n" +
                            "2. Add or remove roles.").queue { message -> super.addMessageToCleaner(message) }
                    sequenceNumber = 5
                }
                5.toByte() -> when (java.lang.Byte.parseByte(event.message.rawContent)) {
                    0.toByte() -> {
                        super.channel.sendMessage("Please type a new name for the category.").queue { message -> super.addMessageToCleaner(message) }
                        sequenceNumber = 6
                    }
                    1.toByte() -> {
                        iAmRolesCategory!!.canOnlyHaveOne = !iAmRolesCategory!!.canOnlyHaveOne
                        super.channel.sendMessage("Inverted setting.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                        super.destroy()
                    }
                    2.toByte() -> {
                        super.channel.sendMessage("Please enter the name of the role you'd like to remove or add. This will automatically detect if the role is already in the list and remove or add it.").queue { super.addMessageToCleaner(it) }
                        sequenceNumber = 7
                    }
                }
                6.toByte() -> {
                    iAmRolesCategory!!.categoryName = event.message.content
                    super.channel.sendMessage("Name successfully changed.").queue { message -> message.delete().queueAfter(1, TimeUnit.MINUTES) }
                    super.destroy()
                }
                7.toByte() -> {
                    val matchedRoles = (super.channel as TextChannel).guild.getRolesByName(event.message.rawContent, true)
                    when {
                        matchedRoles.size > 1 -> throw IllegalArgumentException("The role name you provided matches multiple roles and is not supported by !IAm and !IaANot")
                        matchedRoles.isEmpty() -> throw IllegalArgumentException("Could not find any roles with that name")
                        else -> {
                            val roleId = matchedRoles[0].idLong
                            var removed: Boolean? = null
                            try {
                                iAmRolesCategory!!.addRole(roleId)
                                removed = false
                            } catch (illegalArgumentException: IllegalArgumentException) {
                                try {
                                    iAmRolesCategory!!.removeRole(roleId)
                                    removed = true
                                } catch (illegalArgumentException2: IllegalArgumentException) {
                                    illegalArgumentException2.addSuppressed(illegalArgumentException)
                                    throw IllegalStateException("Something went wrong while trying to add and remove the role", illegalArgumentException2)
                                }
                            }
                            when (removed) {
                                true -> super.channel.sendMessage("The role was successfully removed form the category.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                                false -> super.channel.sendMessage("The role was successfully added to the category.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                                else -> throw IllegalStateException("Boolean value removed should no be able to be null at this point.")
                            }
                            super.destroy()
                        }
                    }
                }
            }
        }
    }

    /**
     * A role category
     *
     * This class is Thread safe.
     */
    internal inner class IAmRolesCategory {

        @get:JSONKey(jsonKey = "categoryName")
        @get:Synchronized
        @set:Synchronized
        var categoryName: String

        @get:JSONKey(jsonKey = "categoryName")
        @get:Synchronized
        @set:Synchronized
        var canOnlyHaveOne: Boolean = false

        @get:JSONKey(jsonKey = "categoryName")
        @get:Synchronized
        private val roles: List<Long>
            get() = Collections.unmodifiableList(field)

        /**
         * Constructor for a new IAmRolesCategory.
         *
         * @param categoryName The name of the category.
         * @param canOnlyHaveOne If only one role can be self assigned from this category or multiple.
         */
        constructor(categoryName: String, canOnlyHaveOne: Boolean) {
            this.categoryName = categoryName
            this.canOnlyHaveOne = canOnlyHaveOne
            this.roles = ArrayList()
        }

        /**
         * A constructor to load an existing IAmRolesCategory from a JSON file.
         *
         * @param categoryName The name of the category.
         * @param canOnlyHaveOne If only one role can be self assigned from this category or multiple.
         * @param roles
         */
        constructor(@JSONKey(jsonKey = "categoryName") categoryName: String, @JSONKey(jsonKey = "canOnlyHaveOne") canOnlyHaveOne: Boolean, @JSONKey(jsonKey = "roles") roles: JSONArray) {
            this.categoryName = categoryName
            this.canOnlyHaveOne = canOnlyHaveOne
            this.roles = ArrayList(JSONToJavaObject.toTypedList(roles, Long::class.java))
        }

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
     * I am not command to allow users to remove roles from them self.
     *
     *
     * Created by Duncan on 23/02/2017.
     */
    internal inner class IAmNot : CommandModule(ALIASES_I_AM_NOT, null, DESCRIPTION_I_AM_NOT) {

        public override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
            //todo rewrite
        }
    }

    /**
     * Iam command to assign yourself roles.
     *
     *
     * Created by Duncan on 23/02/2017.
     */
    internal inner class IAm : CommandModule(ALIASES_I_AM, null, DESCRIPTION_I_AM) {

        /**
         * Do something with the event, command and arguments.
         *
         * @param event     A MessageReceivedEvent that came with the command
         * @param command   The command alias that was used to trigger this commandExec
         * @param arguments The arguments that where entered after the command alias
         */
        public override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
            //todo rewrite
        }
    }
}
