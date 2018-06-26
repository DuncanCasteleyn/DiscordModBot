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


import be.duncanc.discordmodbot.bot.commands.QuitBot
import net.dv8tion.jda.core.JDA
import net.dv8tion.jda.core.entities.Message
import net.dv8tion.jda.core.events.ReadyEvent
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.core.events.message.guild.GuildMessageUpdateEvent
import net.dv8tion.jda.core.hooks.ListenerAdapter
import org.apache.commons.collections4.map.LinkedMap
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import java.util.*

/**
 * This class provides a buffer that will store Message objects so that they can
 * be accessed after being deleted on discord.
 *
 * @author Dunciboy
 * @version 22 October 2016
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
class MessageHistory
/**
 * Default constructor
 */
@Autowired
constructor(
        private val attachmentProxyCreator: AttachmentProxyCreator
) : ListenerAdapter(), QuitBot.BeforeBotQuit {
    companion object {

        private const val HISTORY_SIZE = 2000
        private val MESSAGE_HISTORY_INSTANCES = ArrayList<MessageHistory>()

        /**
         * For you convenience do not store any of the object instances from this list in object fields,
         * doing this will cause IllegalStateExceptions to be thrown when you try to access it when it has been deleted and will cause the instance to not be garbage collected.
         *
         * @return a list of existing MessageHistory objects.
         */
        val instanceList: ArrayList<MessageHistory>
            get() = ArrayList(MESSAGE_HISTORY_INSTANCES)
    }

    private val messages: LinkedMap<Long, Message> = LinkedMap()

    private var deleted: Boolean = false

    internal val instance: JDA
        @Throws(EmptyCacheException::class)
        get() {
            if (deleted) {
                throw IllegalStateException("This instance has been marked as deleted and should not be used any longer")
            }

            return if (messages.isNotEmpty()) {
                messages[messages.firstKey()]!!.jda
            } else {
                throw EmptyCacheException("The cache has either not saved any messages yet, or is empty. Please wait util a new message is cached.")
            }
        }

    init {
        deleted = false
        @Suppress("LeakingThis")
        MESSAGE_HISTORY_INSTANCES.add(this)
    }

    /**
     * Used to add a message to the list
     *
     * @param event The event that triggered this method
     */
    @Synchronized
    override fun onGuildMessageReceived(event: GuildMessageReceivedEvent?) {
        if (deleted) {
            event!!.jda.removeEventListener(this)
            return
        }

        while (messages.size > HISTORY_SIZE) {
            attachmentProxyCreator.informDeleteFromCache(messages.remove(messages.firstKey())!!.idLong)
        }
        val message = event!!.message
        if (message.contentDisplay.isNotEmpty() && message.contentDisplay[0] == '!' || message.author.isBot) {
            return
        }
        messages[message.idLong] = message
        if (message.attachments.size > 0) {
            attachmentProxyCreator.proxyMessageAttachments(event)
        }
    }

    /**
     * Used to remove a message from the list manually
     *
     * @param id Message id to remove
     */
    @Synchronized
    fun removeMessage(id: Long) {
        if (deleted) {
            throw IllegalStateException("This instance has been marked as deleted and should not be used any longer")
        }

        messages.remove(id)
    }

    override fun onReady(event: ReadyEvent?) {
        val quitBot = event!!.jda.registeredListeners.stream().filter { o -> o is QuitBot }.findFirst().orElse(null) as QuitBot
        quitBot.addCallBeforeQuit(this)
        // This class is going to be added as listener for beans but we don't want this
        event.jda.removeEventListener(this)
    }

    /**
     * This method is called to modify an existing object message in the list
     *
     * @param event The event that triggered this method.
     */
    @Synchronized
    override fun onGuildMessageUpdate(event: GuildMessageUpdateEvent?) {
        if (deleted) {
            event!!.jda.removeEventListener(this)
            return
        }

        val message = event!!.message
        messages.replace(message.idLong, message)
    }

    /**
     * This message will return an object message or null if not found.
     *
     * @param id     the message id that we want to receive
     * @param delete Should the message be deleted from the cache?
     * @return object Message, returns null if the id is not in the history
     */
    @Synchronized
    internal fun getMessage(id: Long, delete: Boolean): Message? {
        return if (delete) {
            messages.remove(id)
        } else {
            messages[id]
        }
    }

    /**
     * This message will return an object message or null if not found and remove the message from the cache.
     *
     * @param id the message id that we want to receive
     * @return object Message, returns null if the id is not in the history
     */
    @Synchronized
    internal fun getMessage(id: Long): Message? {
        return getMessage(id, true)
    }

    internal fun getAttachmentsString(id: Long): String? {
        return attachmentProxyCreator.getAttachmentUrl(id)
    }

    fun cleanAttachmentCache() {
        attachmentProxyCreator.cleanCache()
    }

    /**
     * Removes itself from it's static instance list so it can be garbage collected.
     */
    fun delete() {
        if (deleted) {
            throw IllegalStateException("This instance has been marked as deleted and should not be used any longer")
        }

        deleted = true
        MESSAGE_HISTORY_INSTANCES.remove(this)
    }

    /**
     * Actions to perform before the quit command is finished with executing.
     */
    override fun onAboutToQuit() {
        cleanAttachmentCache()
        delete()
    }

    internal inner class EmptyCacheException
    /**
     * Constructs a new exception with the specified detail message.  The
     * cause is not initialized, and may subsequently be initialized by
     * a call to [.initCause].
     *
     * @param message the detail message. The detail message is saved for
     * later retrieval by the [.getMessage] method.
     */
    (message: String) : Exception(message)
}
